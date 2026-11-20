// Numbers behind the web dashboard's Habits tab. Pure functions, no DOM and no
// app state, so they run in the browser and under `node --test`
// (tests/web/habits.test.mjs). app.js imports them.
//
// The methods are the same for everyone; the labels are each user's own.
// A user maps the people and places they pay to habits ("Food", "Coffee"…);
// those rules live in i_habit_rules (supabase/add_habit_rules.sql) and come in
// here as [{ match, habit, shared }]:
//   match  — a name key from personKey(): a payment belongs to the rule when
//            its counterparty's nameKey() starts with it (longest match wins)
//   habit  — the user's habit name
//   shared — friends usually pay the user back here (group lunch, coffee)
//
// Every figure keeps its source transactions so the page can show them.

import {
  DAY, EXPENSE_TYPES, ETHIOPIAN_MONTHS, gregorianToEthiopian, isOwnAccountTransfer,
  isPayerName, normaliseSource, startOfDay,
} from "./insights.js";

const HOUR = 36e5;
const timeOf = (tx) => Date.parse(tx.occurred_at);
const amountOf = (tx) => Number(tx.amount) || 0;
const round = (x) => Math.round(x);
const median = (xs) => {
  if (!xs.length) return 0;
  const s = [...xs].sort((a, b) => a - b);
  const m = Math.floor(s.length / 2);
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
};

/** Habit names offered first when the user maps someone. Only suggestions. */
export const SUGGESTED_HABITS = ["Food", "Coffee", "Transport", "Internet", "Games", "Sport", "Drinks", "Snacks"];

// ── Who is who ──────────────────────────────────────────────────────────────
// Bank-side names for the user's own accounts: "to Commercial Bank of
// Ethiopia 1****0000", "from CBE", wallet top-ups and ATM withdrawals.
const OWN_NAME = /^(?:to|from) |cbe ?birr|cbe atm|mela loan/i;

/** A move between the user's own accounts: never a habit, never a payback. */
export const isOwnMove = (tx) => isOwnAccountTransfer(tx) || OWN_NAME.test((tx.counterparty || "").trim());

/**
 * One spelling-proof key per counterparty: lower case, no "(2519****0000)"
 * phone tail or "(Shop Name)" note, no stray full stop, single spaces.
 */
export function nameKey(name) {
  return String(name || "")
    .toLowerCase()
    .replace(/\s*\(.*$/, "")
    .replace(/[.\s]+$/, "")
    .replace(/\s+/g, " ")
    .trim();
}

/** The name without the phone tail, for showing. */
export const displayName = (name) =>
  String(name || "").replace(/\s*\(\+?\d[\d*]+\)\s*/g, " ").replace(/\s+/g, " ").replace(/\.$/, "").trim();

/**
 * The key a rule stores: the first word plus the first three letters of the
 * second, so "Abebe Kebede", "abebe kebed" and "Abebe Kebede Alemu" are one
 * person and "Telecom package: 1GB" and "Telecom package: 2GB" one habit.
 */
export function personKey(name) {
  const words = nameKey(name).split(" ").filter(Boolean);
  if (!words.length) return "";
  return words.length === 1 ? words[0] : `${words[0]} ${words[1].slice(0, 3)}`;
}

// Payments for others and friend loans (rules.js) aren't the user's habits.
const isOut = (tx) => EXPENSE_TYPES.has(tx.type) && !isOwnMove(tx) && !tx.pass_through && !tx.for_others && !tx.friend_loan;

/**
 * Everyone the user pays, grouped by personKey, for the "map a person to a
 * habit" list: how often, the usual amount and the usual hour. Most paid
 * first.
 */
export function payees(txs, { limit = 60 } = {}) {
  const by = new Map();
  for (const t of txs) {
    if (!isOut(t)) continue;
    const key = personKey(t.counterparty);
    if (!key) continue;
    if (!by.has(key)) by.set(key, { key, names: new Map(), count: 0, total: 0, amounts: [], hours: [], last: 0 });
    const e = by.get(key);
    const shown = displayName(t.counterparty);
    e.names.set(shown, (e.names.get(shown) || 0) + 1);
    e.count += 1; e.total += amountOf(t); e.amounts.push(amountOf(t));
    e.hours.push(new Date(timeOf(t)).getHours());
    e.last = Math.max(e.last, timeOf(t));
  }
  return [...by.values()]
    .map(({ names, amounts, hours, ...e }) => ({
      ...e,
      name: [...names.entries()].sort((a, b) => b[1] - a[1])[0][0],
      spellings: [...names.keys()],
      usual: round(median(amounts)),
      usualHour: round(median(hours)),
    }))
    .sort((a, b) => b.count - a.count || b.total - a.total)
    .slice(0, limit);
}

/** The rule a counterparty falls under, or null. Longest match wins. */
export function ruleFor(counterparty, rules) {
  const k = nameKey(counterparty);
  if (!k) return null;
  let best = null;
  for (const r of rules || []) {
    const m = (r.match || "").trim().toLowerCase();
    if (m && k.startsWith(m) && (!best || m.length > best.match.length)) best = { ...r, match: m };
  }
  return best;
}

// ── Payments and paybacks ───────────────────────────────────────────────────
export const PAYBACK_MAX = 1000;          // a friend's share, not a loan
export const PAYBACK_WINDOW = 2.5 * HOUR; // within 2.5 h, same day; 24 h over-counts

/**
 * The user's habit payments with friends' paybacks attached.
 * A payback is money in under [maxAmount] from a person (not salary, not an
 * own move) that lands the same day within [window] after a payment at a
 * shared place; it goes to the latest such payment before it. Matching is by
 * timing only, so now and then more comes back than was paid ([mine] < 0):
 * the page flags those for the user to check.
 * [salaryKeys] are normaliseSource() keys of salary payers, never paybacks.
 */
export function habitPayments(txs, rules, { salaryKeys = [], maxAmount = PAYBACK_MAX, window = PAYBACK_WINDOW } = {}) {
  const pays = [];
  for (const t of txs) {
    if (!isOut(t)) continue;
    const r = ruleFor(t.counterparty, rules);
    if (!r) continue;
    const ms = timeOf(t);
    if (!Number.isFinite(ms)) continue;
    pays.push({ tx: t, ms, habit: r.habit, shared: !!r.shared, place: displayName(t.counterparty), amount: amountOf(t), back: 0, paybacks: [] });
  }
  pays.sort((a, b) => a.ms - b.ms);
  const shared = pays.filter((p) => p.shared);
  const salary = new Set(salaryKeys.map(normaliseSource));
  const credits = txs
    .filter((t) => t.type === "CREDIT" && !isOwnMove(t) && amountOf(t) < maxAmount
      && (t.category || "").trim() !== "Salary" && isPayerName(t.counterparty)
      && !salary.has(normaliseSource(t.counterparty)) && Number.isFinite(timeOf(t)))
    .sort((a, b) => timeOf(a) - timeOf(b));
  let i = 0;
  for (const c of credits) {
    const ms = timeOf(c);
    while (i < shared.length && shared[i].ms <= ms) i++;
    const p = shared[i - 1];
    if (!p || ms - p.ms > window || startOfDay(p.ms) !== startOfDay(ms)) continue;
    p.back += amountOf(c);
    p.paybacks.push({ tx: c, ms, from: displayName(c.counterparty), amount: amountOf(c) });
  }
  for (const p of pays) p.mine = p.amount - p.back;
  return pays;
}

// ── Months ──────────────────────────────────────────────────────────────────
const GREGORIAN_MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];

/** { key, label, month } of the month [ms] is in, in the user's calendar. */
export function monthOf(ms, cal) {
  const d = new Date(ms);
  if (cal === "ethiopian") {
    const e = gregorianToEthiopian(d);
    return { key: `${e.year}-${String(e.month).padStart(2, "0")}`, label: `${ETHIOPIAN_MONTHS[e.month - 1]} ${e.year}`, month: e.month };
  }
  const m = d.getMonth() + 1;
  return { key: `${d.getFullYear()}-${String(m).padStart(2, "0")}`, label: `${GREGORIAN_MONTHS[m - 1]} ${d.getFullYear()}`, month: m };
}

/** "Hidar 4, 2019" or "4 Nov 2026" (with " 12:58" when [time]). */
export function dateLabel(ms, cal, { time = false } = {}) {
  const d = new Date(ms);
  const hm = time ? ` ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}` : "";
  if (cal === "ethiopian") {
    const e = gregorianToEthiopian(d);
    return `${ETHIOPIAN_MONTHS[e.month - 1]} ${e.day}, ${e.year}${hm}`;
  }
  return `${d.getDate()} ${GREGORIAN_MONTHS[d.getMonth()].slice(0, 3)} ${d.getFullYear()}${hm}`;
}

/**
 * Your share of each habit per month. The first and last month of the data
 * and Pagume are partial and stay out of [average].
 */
export function monthlyHabits(pays, txs, { cal }) {
  const times = txs.map(timeOf).filter(Number.isFinite);
  if (!times.length) return { months: [], habits: [], average: {} };
  const first = monthOf(times.reduce((a, b) => Math.min(a, b)), cal).key;
  const last = monthOf(times.reduce((a, b) => Math.max(a, b)), cal).key;
  const by = new Map();
  // Every month in the data gets a row, so a quiet month shows as zero.
  for (const ms of times) {
    const m = monthOf(ms, cal);
    if (!by.has(m.key)) by.set(m.key, { ...m, partial: m.key === first || m.key === last || (cal === "ethiopian" && m.month === 13), byHabit: {}, total: 0, sources: [] });
  }
  const totals = {};
  for (const p of pays) {
    const row = by.get(monthOf(p.ms, cal).key);
    row.byHabit[p.habit] = (row.byHabit[p.habit] || 0) + p.mine;
    row.total += p.mine;
    row.sources.push(p);
    totals[p.habit] = (totals[p.habit] || 0) + p.mine;
  }
  const months = [...by.values()].sort((a, b) => a.key.localeCompare(b.key));
  const habits = Object.keys(totals).sort((a, b) => totals[b] - totals[a]);
  const full = months.filter((m) => !m.partial);
  const average = {};
  for (const h of habits) average[h] = full.length ? round(full.reduce((s, m) => s + (m.byHabit[h] || 0), 0) / full.length) : null;
  return { months, habits, average, fullMonths: full.length };
}

/** Totals per habit over [pays]: your share, paid, paid back, count. */
export function habitTotals(pays) {
  const t = {};
  for (const p of pays) {
    const e = (t[p.habit] ||= { habit: p.habit, mine: 0, paid: 0, back: 0, count: 0 });
    e.mine += p.mine; e.paid += p.amount; e.back += p.back; e.count += 1;
  }
  return Object.values(t).sort((a, b) => b.mine - a.mine);
}

/** Payments friends paid back part of, newest first. */
export function groupPayments(pays) {
  const rows = pays.filter((p) => p.back > 0).sort((a, b) => b.ms - a.ms);
  return {
    rows,
    paid: rows.reduce((s, p) => s + p.amount, 0),
    back: rows.reduce((s, p) => s + p.back, 0),
  };
}

// ── When ────────────────────────────────────────────────────────────────────
/** Your share per hour of the day (0-23), per habit. */
export function byHour(pays) {
  const hours = Array.from({ length: 24 }, (_, hour) => ({ hour, byHabit: {}, total: 0, sources: [] }));
  for (const p of pays) {
    const h = hours[new Date(p.ms).getHours()];
    h.byHabit[p.habit] = (h.byHabit[p.habit] || 0) + p.mine;
    h.total += p.mine;
    h.sources.push(p);
  }
  return hours;
}

/** The [span]-hour stretch with the most money, e.g. 13:00-18:00. */
export function busiestHours(hours, span = 5) {
  const all = hours.reduce((s, h) => s + h.total, 0);
  if (!all) return null;
  let best = { from: 0, sum: -1 };
  for (let from = 0; from <= 24 - span; from++) {
    const sum = hours.slice(from, from + span).reduce((s, h) => s + h.total, 0);
    if (sum > best.sum) best = { from, sum };
  }
  return { from: best.from, to: best.from + span, share: round((100 * best.sum) / all) };
}

/**
 * Habits that come together: on days with habit A, how often B happens too,
 * against days with other habits but not A. Only pairs seen on at least
 * [minDays] days on both sides; biggest difference first.
 */
export function habitChains(pays, { minDays = 5, limit = 3 } = {}) {
  const days = new Map();
  for (const p of pays) {
    const d = startOfDay(p.ms);
    if (!days.has(d)) days.set(d, new Set());
    days.get(d).add(p.habit);
  }
  const sets = [...days.values()];
  const habits = [...new Set(pays.map((p) => p.habit))];
  const out = [];
  for (const a of habits) for (const b of habits) {
    if (a === b) continue;
    const withA = sets.filter((s) => s.has(a));
    const without = sets.filter((s) => !s.has(a));
    if (withA.length < minDays || without.length < minDays) continue;
    const w = withA.filter((s) => s.has(b)).length;
    const wo = without.filter((s) => s.has(b)).length;
    if (w + wo < minDays) continue;
    out.push({
      first: a, then: b,
      with: round((100 * w) / withA.length), without: round((100 * wo) / without.length),
      daysWith: withA.length, daysWithout: without.length,
    });
  }
  return out.filter((c) => c.with > c.without).sort((x, y) => (y.with - y.without) - (x.with - x.without)).slice(0, limit);
}

/**
 * Habit money paid out per day (before paybacks) in the [days] days from
 * each payday against the other days, from the first payday to the last
 * habit payment. [paydays] are the salary credits (timestamps).
 */
export function paydayEffect(pays, paydays, { days = 7 } = {}) {
  const pd = [...new Set(paydays.filter(Number.isFinite).map(startOfDay))].sort((a, b) => a - b);
  if (!pd.length || !pays.length) return null;
  const lastDay = startOfDay(pays.reduce((m, p) => Math.max(m, p.ms), 0));
  if (lastDay < pd[0]) return null;
  const after = new Set();
  for (const s of pd) for (let i = 0; i < days; i++) {
    const d = startOfDay(s + i * DAY + 2 * HOUR); // +2h keeps clear of DST edges
    if (d <= lastDay) after.add(d);
  }
  const allDays = Math.round((lastDay - pd[0]) / DAY) + 1;
  let inAfter = 0, inOther = 0;
  for (const p of pays) {
    const d = startOfDay(p.ms);
    if (d < pd[0]) continue;
    if (after.has(d)) inAfter += p.amount; else inOther += p.amount;
  }
  const otherDays = allDays - after.size;
  return {
    paydays: pd,
    after: { perDay: after.size ? round(inAfter / after.size) : 0, days: after.size },
    other: { perDay: otherDays > 0 ? round(inOther / otherDays) : 0, days: otherDays },
  };
}

/**
 * Salary credits for the payday effect: everything the picked salary payers
 * sent, minus small ones (under a quarter of their usual amount).
 */
export function paydaysFrom(txs, salaryKeys) {
  const keys = new Set(salaryKeys.map(normaliseSource).filter(Boolean));
  if (!keys.size) return [];
  const rows = txs.filter((t) => t.type === "CREDIT" && keys.has(normaliseSource(t.counterparty)));
  const usual = median(rows.map(amountOf));
  return rows.filter((t) => amountOf(t) >= usual / 4).map(timeOf);
}

// ── Plain words ─────────────────────────────────────────────────────────────
const fmt = (n) => `ETB ${Math.round(n).toLocaleString("en-US")}`;
const hourWord = (h) => (h === 0 || h === 24 ? "midnight" : h === 12 ? "noon" : h < 12 ? `${h}am` : `${h - 12}pm`);

export function explainMonthly(m) {
  if (!m.habits.length) return "";
  const top = m.habits[0];
  const avg = m.average[top];
  const full = m.months.filter((x) => !x.partial);
  if (!full.length) return `${top} is your biggest habit so far. Come back after a full month for averages.`;
  const latest = full[full.length - 1];
  const now = latest.byHabit[top] || 0;
  const vs = avg ? (now > avg * 1.1 ? "more than" : now < avg * 0.9 ? "less than" : "about the same as") : null;
  return `${top} is your biggest habit: about ${fmt(avg)} a month of your own money. `
    + (vs ? `In ${latest.label} it was ${fmt(now)}, ${vs} usual.` : "");
}

export function explainHours(hours) {
  const b = busiestHours(hours);
  if (!b) return "";
  return `${b.share}% of your habit money goes out between ${hourWord(b.from)} and ${hourWord(b.to)}.`;
}

export function explainChains(chains) {
  if (!chains.length) return "No habit clearly pulls another one along yet.";
  const c = chains[0];
  return `On days you spend on ${c.first}, you spend on ${c.then} ${c.with}% of the time, against ${c.without}% on other days. One tends to lead to the other.`;
}

export function explainPayday(p) {
  if (!p) return "";
  const diff = p.other.perDay ? Math.round(((p.after.perDay - p.other.perDay) / p.other.perDay) * 100) : null;
  if (diff == null) return "";
  if (Math.abs(diff) < 10) return `Your habits cost about the same right after payday as the rest of the month (${fmt(p.after.perDay)} vs ${fmt(p.other.perDay)} a day).`;
  return `In the week after payday you pay ${fmt(p.after.perDay)} a day on habits, ${Math.abs(diff)}% ${diff > 0 ? "more" : "less"} than the ${fmt(p.other.perDay)} on other days.`;
}

export function explainGroups(g) {
  if (!g.rows.length) return "";
  const share = Math.round((100 * g.back) / g.paid);
  return `${g.rows.length} times you paid for a group and friends sent money back. Of ${fmt(g.paid)} you paid, ${fmt(g.back)} (${share}%) came back, so your share was ${fmt(g.paid - g.back)}.`;
}
