// Numbers behind the web dashboard's insights (NAH-30). Pure functions, no
// DOM and no app state, so they run in the browser and under `node --test`
// (tests/web/insights.test.mjs). app.js imports them.
//
// Spending rules mirror app/src/main/java/com/financeapp/data/model/Spending.kt
// and the Analytics numbers mirror viewmodel/SpendOverview.kt, so the web
// and the phone agree.

export const DAY = 864e5;

// ── Spending rules ──────────────────────────────────────────────────────────
export const EXPENSE_TYPES = new Set(["DEBIT", "TRANSFER_OUT", "PAYMENT"]);
export const OWN_ACCOUNTS = "Own accounts";
export const isOwnAccountTransfer = (tx) =>
  tx.type === "INTERNAL_TRANSFER" || (tx.category || "").trim() === OWN_ACCOUNTS;
export const isSpending = (tx) => EXPENSE_TYPES.has(tx.type) && !isOwnAccountTransfer(tx);
export const isIncome = (tx) => tx.type === "CREDIT" && !isOwnAccountTransfer(tx);

const timeOf = (tx) => Date.parse(tx.occurred_at);
const amountOf = (tx) => Number(tx.amount) || 0;
const sum = (rows) => rows.reduce((s, t) => s + amountOf(t), 0);
const median = (xs) => {
  if (xs.length === 0) return null;
  const s = [...xs].sort((a, b) => a - b);
  const m = Math.floor(s.length / 2);
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
};

// ── Ethiopian calendar ──────────────────────────────────────────────────────
export const ETHIOPIAN_MONTHS = [
  "Meskerem", "Tikimt", "Hidar", "Tahsas", "Tir", "Yekatit",
  "Megabit", "Miazia", "Ginbot", "Sene", "Hamle", "Nehase", "Pagume",
];

export function gregorianToEthiopian(date) {
  const y = date.getFullYear();
  const m = date.getMonth() + 1;
  const d = date.getDate();
  const a = Math.floor((14 - m) / 12);
  const yy = y + 4800 - a;
  const mm = m + 12 * a - 3;
  const jdn = d + Math.floor((153 * mm + 2) / 5) + 365 * yy
    + Math.floor(yy / 4) - Math.floor(yy / 100) + Math.floor(yy / 400) - 32045;
  const r = (jdn - 1723856) % 1461;
  const n = (r % 365) + 365 * Math.floor(r / 1460);
  const year = 4 * Math.floor((jdn - 1723856) / 1461) + Math.floor(r / 365) - Math.floor(r / 1460);
  return { year, month: Math.floor(n / 30) + 1, day: (n % 30) + 1 };
}

/** Ethiopian (year, month 1-13, day 1-30) → Gregorian Date at local 00:00. */
export function ethiopianToGregorian(ethYear, ethMonth, ethDay) {
  const jdn = 1723856 + 365 * ethYear + Math.floor(ethYear / 4) + 30 * (ethMonth - 1) + (ethDay - 1);
  const a = jdn + 32044;
  const b = Math.floor((4 * a + 3) / 146097);
  const c = a - Math.floor((146097 * b) / 4);
  const d = Math.floor((4 * c + 3) / 1461);
  const e = c - Math.floor((1461 * d) / 4);
  const mm = Math.floor((5 * e + 2) / 153);
  const day = e - Math.floor((153 * mm + 2) / 5) + 1;
  const month = mm + 3 - 12 * Math.floor(mm / 10);
  const year = 100 * b + d - 4800 + Math.floor(mm / 10);
  return new Date(year, month - 1, day);
}

export function prevEthiopianMonth(ethYear, ethMonth) {
  return ethMonth - 1 < 1 ? { year: ethYear - 1, month: 13 } : { year: ethYear, month: ethMonth - 1 };
}

// ── Days, weeks and months (cal = "gregorian" | "ethiopian") ────────────────
export function startOfDay(ms) {
  const d = new Date(ms);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

/** Whole days from one day start to the day of [ms]. */
export function dayIndex(fromDayStart, ms) {
  return Math.round((startOfDay(ms) - fromDayStart) / DAY);
}

/** Monday 00:00 of the week [ms] is in. */
export function weekStart(ms) {
  const d = new Date(startOfDay(ms));
  const back = (d.getDay() + 6) % 7;
  d.setDate(d.getDate() - back);
  return d.getTime();
}

/** [start, end) of the month [ms] is in. */
export function monthBounds(ms, cal) {
  const date = new Date(ms);
  if (cal === "ethiopian") {
    const e = gregorianToEthiopian(date);
    const start = ethiopianToGregorian(e.year, e.month, 1).getTime();
    const next = e.month === 13 ? { year: e.year + 1, month: 1 } : { year: e.year, month: e.month + 1 };
    return [start, ethiopianToGregorian(next.year, next.month, 1).getTime()];
  }
  return [
    new Date(date.getFullYear(), date.getMonth(), 1).getTime(),
    new Date(date.getFullYear(), date.getMonth() + 1, 1).getTime(),
  ];
}

/** Day of the month in the user's calendar (1-30 or 1-31). */
export function dayOfMonth(ms, cal) {
  return cal === "ethiopian" ? gregorianToEthiopian(new Date(ms)).day : new Date(ms).getDate();
}

/** % change, rounded; null when there's nothing to compare against. */
export function changePct(now, before) {
  return before > 0 ? Math.round(((now - before) / before) * 100) : null;
}

// ── Accounts ────────────────────────────────────────────────────────────────
/**
 * Last digits of a masked account number. Only digits after the last mask
 * character count: Berhan writes one account as "*********5167" and
 * "105001XXXXX67", and joining digits across the X's made a second account.
 * Mirrors AccountGrouping.accountSuffix on the phone.
 */
export function accountSuffix(accountNumber) {
  const raw = String(accountNumber || "").trim().replace(/\.+$/, "");
  if (!raw) return null;
  const tail = (raw.match(/\d*$/) || [""])[0];
  const masked = /[*Xx]/.test(raw);
  if (tail.length >= 4) return tail.slice(-4);
  if (masked) return tail.length >= 2 ? tail : null;
  const digits = raw.replace(/\D/g, "");
  return digits.length >= 4 ? digits.slice(-4) : null;
}

/** Same account: equal, or a short masked tail ("67") ending the full one ("5167"). */
export function sameSuffix(a, b) {
  return a === b || (a.length < 4 && b.endsWith(a)) || (b.length < 4 && a.endsWith(b));
}

export function shortBankLabel(name) {
  if (/commercial bank/i.test(name)) return "CBE";
  if (/telebirr/i.test(name)) return "Telebirr";
  if (/abyssinia/i.test(name)) return "BOA";
  if (/awash/i.test(name)) return "Awash";
  if (/dashen/i.test(name)) return "Dashen";
  return name;
}

/**
 * One entry per account. Mirrors AccountGrouping.group on the phone: one
 * known account at a bank stays one entry; several are "CBE 1", "CBE 2", …
 * by first-seen date; a short masked tail joins the one account ending
 * with it. Keeps each list in the input's order (newest first in app.js).
 */
export function groupAccounts(txs) {
  const byBank = new Map();
  for (const t of txs) {
    const bank = t.bank_name || "Unknown";
    if (!byBank.has(bank)) byBank.set(bank, []);
    byBank.get(bank).push(t);
  }
  const out = [];
  for (const [bank, list] of byBank) {
    const bySuffix = new Map();
    for (const t of list) {
      const s = accountSuffix(t.account_number);
      if (!s) continue;
      if (!bySuffix.has(s)) bySuffix.set(s, []);
      bySuffix.get(s).push(t);
    }
    // Fold short tails into the one full suffix that ends with them.
    const full = [...bySuffix.keys()].filter((s) => s.length >= 4);
    for (const s of [...bySuffix.keys()]) {
      if (s.length >= 4) continue;
      const match = full.filter((f) => f.endsWith(s));
      if (match.length === 1) {
        bySuffix.get(match[0]).push(...bySuffix.get(s));
        bySuffix.delete(s);
      }
    }
    if (bySuffix.size <= 1) {
      out.push({ name: bank, bank, suffix: null, list });
      continue;
    }
    const order = new Map(list.map((t, i) => [t, i]));
    const firstSeen = (rows) => Math.min(...rows.map(timeOf));
    [...bySuffix.entries()]
      .sort((a, b) => firstSeen(a[1]) - firstSeen(b[1]))
      .forEach(([suffix, rows], i) => out.push({
        name: `${shortBankLabel(bank)} ${i + 1}`, bank, suffix,
        list: rows.sort((a, b) => order.get(a) - order.get(b)),
      }));
  }
  return out;
}

// ── Transaction detail: "In context" ────────────────────────────────────────
/**
 * Where one transaction stands in its own month (the user's calendar):
 * money out against the month's spending, money in against what came in.
 * Ties share a rank (two 500s are both "biggest"). Null for transfers
 * between the user's own accounts.
 */
export function inContext(tx, txs, cal) {
  const out = isSpending(tx);
  if (!out && !isIncome(tx)) return null;
  const at = timeOf(tx);
  if (!Number.isFinite(at)) return null;
  const [from, to] = monthBounds(at, cal);
  const same = out ? isSpending : isIncome;
  const peers = txs.filter((t) => same(t) && timeOf(t) >= from && timeOf(t) < to);
  const a = amountOf(tx);
  const total = sum(peers);
  let catPct = null;
  const cat = (tx.category || "").trim();
  if (out && cat) {
    const catTotal = sum(peers.filter((t) => (t.category || "").trim() === cat));
    if (catTotal > 0) catPct = (a / catTotal) * 100;
  }
  return {
    kind: out ? "out" : "in",
    rank: 1 + peers.filter((t) => amountOf(t) > a).length,
    count: peers.length,
    pct: total > 0 ? (a / total) * 100 : 0,
    category: cat || null,
    catPct,
    monthStart: from,
  };
}

// ── Overview: today, this week, this month, and one range ───────────────────
export const PRESETS = ["week", "month", "30d", "custom"];

/** [from, to) for a range preset; "custom" uses [custom]. */
export function spendRange(preset, now, cal, custom) {
  const today = startOfDay(now);
  switch (preset) {
    case "week": { const w = weekStart(now); return [w, w + 7 * DAY]; }
    case "30d": return [today - 29 * DAY, today + DAY];
    case "custom": if (custom) return custom; // fall through to the month
    default: return monthBounds(now, cal);
  }
}

function previousStart(preset, from, to, cal) {
  if (preset === "week") return from - 7 * DAY;
  if (preset === "30d") return from - 30 * DAY;
  if (preset === "custom") return from - (to - from);
  return monthBounds(from - 1, cal)[0];
}

function payees(rows) {
  const by = new Map();
  for (const t of rows) {
    const name = (t.counterparty || "").trim();
    if (!name) continue;
    const e = by.get(name) || { name, count: 0, total: 0 };
    e.count += 1; e.total += amountOf(t);
    by.set(name, e);
  }
  return [...by.values()].sort((a, b) => b.total - a.total);
}

/** The phone's Analytics numbers (SpendOverview.kt), for the web overview. */
export function spendOverview(txs, { cal, now, preset = "month", custom = null }) {
  const spending = txs.filter(isSpending);
  const spentIn = (from, to) => sum(spending.filter((t) => timeOf(t) >= from && timeOf(t) < to));
  const today = startOfDay(now);
  const endOfToday = today + DAY;

  const todayRows = spending.filter((t) => timeOf(t) >= today && timeOf(t) < endOfToday);
  const wk = weekStart(now);
  const weekSpent = spentIn(wk, endOfToday);
  const lastWeek = spentIn(wk - 7 * DAY, endOfToday - 7 * DAY);
  const [monthStart] = monthBounds(now, cal);
  const monthSpent = spentIn(monthStart, endOfToday);
  const [prevMonthStart] = monthBounds(monthStart - 1, cal);
  const lastMonth = spentIn(prevMonthStart, Math.min(prevMonthStart + (endOfToday - monthStart), monthStart));

  const [from, to] = spendRange(preset, now, cal, custom);
  const dayCount = dayIndex(from, to - 1) + 1;
  const days = Array.from({ length: dayCount }, (_, i) => (from + i * DAY >= endOfToday ? null : 0));
  const shownEnd = Math.min(to, endOfToday);
  const rangeRows = spending.filter((t) => timeOf(t) >= from && timeOf(t) < shownEnd);
  for (const t of rangeRows) {
    const i = dayIndex(from, timeOf(t));
    if (i >= 0 && i < dayCount) days[i] = (days[i] || 0) + amountOf(t);
  }
  const total = sum(rangeRows);
  const elapsed = Math.max(1, days.filter((d) => d !== null).length);
  const prevFrom = previousStart(preset, from, to, cal);
  const prevTotal = spentIn(prevFrom, Math.min(prevFrom + (shownEnd - from), from));

  const accounts = new Map();
  for (const t of rangeRows) accounts.set(t.bank_name || "Unknown", (accounts.get(t.bank_name || "Unknown") || 0) + amountOf(t));
  const cats = new Map();
  for (const t of rangeRows) {
    const c = (t.category || "").trim();
    if (c) cats.set(c, (cats.get(c) || 0) + amountOf(t));
  }

  return {
    todaySpent: sum(todayRows),
    todayCount: todayRows.length,
    weekSpent,
    weekChangePct: changePct(weekSpent, lastWeek),
    monthSpent,
    monthChangePct: changePct(monthSpent, lastMonth),
    preset, from, to, days, total,
    avgPerDay: total / elapsed,
    changePct: changePct(total, prevTotal),
    topPayees: payees(rangeRows).slice(0, 5),
    perAccount: [...accounts.entries()].map(([name, total]) => ({ name, total })).sort((a, b) => b.total - a.total),
    categories: [...cats.entries()].map(([name, total]) => ({ name, total })).sort((a, b) => b.total - a.total),
  };
}

// ── Money in vs out by month, with what was kept ────────────────────────────
/** The last [count] months (oldest first) with in, out, kept and kept %. */
export function monthlyInOut(txs, { cal, now, count = 6 }) {
  const starts = [monthBounds(now, cal)[0]];
  while (starts.length < count) starts.push(monthBounds(starts.at(-1) - 1, cal)[0]);
  return starts.reverse().map((start) => {
    const [, end] = monthBounds(start, cal);
    const rows = txs.filter((t) => timeOf(t) >= start && timeOf(t) < end);
    const moneyIn = sum(rows.filter(isIncome));
    const out = sum(rows.filter(isSpending));
    const kept = moneyIn - out;
    return { start, moneyIn, out, kept, keptPct: moneyIn > 0 ? Math.round((kept / moneyIn) * 100) : null };
  });
}

// ── Payday ──────────────────────────────────────────────────────────────────
/**
 * Credits that aren't someone paying the user: wallet top-ups (Telebirr's
 * "your account has been credited" names no sender), loans, deposits,
 * interest, refunds and bank names. Mirrors NotificationRules.isPayerName.
 */
const NOT_A_PAYER = /top-?up|wallet|\bmela\b|loan|credit|deposit|cash|interest|refund|revers|own account|^from |bank|^telebirr|^cbe\b|^boa\b|^awash|^dashen/i;
export const isPayerName = (name) => !!name && !!name.trim() && !NOT_A_PAYER.test(name.trim());

/** One key per sender: lower case, phone tails and account suffixes cut. */
export function normaliseSource(name) {
  if (!name) return "";
  return String(name)
    .toLowerCase()
    .replace(/\(2519[\d*]+\)/g, "")
    .replace(/account\s+number\s+\d+/gi, "")
    .replace(/\s+/g, " ")
    .trim();
}

/**
 * Everyone who paid the user in the last [months] months, for "pick your
 * salary": one row per sender with how often, the usual amount and the
 * total. Real payers first, then the rest (the user may know better).
 */
export function incomeSenders(txs, { now, months = 6 }) {
  const since = now - months * 31 * DAY;
  const by = new Map();
  for (const t of txs) {
    if (!isIncome(t) || !(timeOf(t) >= since && timeOf(t) <= now)) continue;
    const name = (t.counterparty || "").trim();
    const key = normaliseSource(name);
    if (!key) continue;
    if (!by.has(key)) by.set(key, { key, name, count: 0, total: 0, amounts: [], last: 0 });
    const e = by.get(key);
    e.count += 1; e.total += amountOf(t); e.amounts.push(amountOf(t));
    if (timeOf(t) > e.last) { e.last = timeOf(t); e.name = name; }
  }
  return [...by.values()]
    .map(({ amounts, ...e }) => ({ ...e, usual: median(amounts), payer: isPayerName(e.name) }))
    .sort((a, b) => (b.payer - a.payer) || (b.total - a.total))
    .slice(0, 10);
}

/**
 * The user's regular income: credits from one sender, within ±20% of their
 * usual amount, in 2 or more of the last 3 months (this one included).
 * Mirrors NotificationRules.looksLikeSalary. The biggest such income wins.
 */
export function regularIncome(txs, { cal, now, salarySource = null, salarySources = null }) {
  // The user said who pays them (one or several senders): the biggest sets
  // the payday rhythm; each paycheck is everything the picked senders paid
  // from that payday to the next.
  const picked = [...new Set([...(salarySources || []), ...(salarySource ? [salarySource] : [])]
    .map(normaliseSource).filter(Boolean))];
  if (picked.length) {
    const rows = txs.filter((t) => isIncome(t) && picked.includes(normaliseSource(t.counterparty)) && Number.isFinite(timeOf(t)))
      .sort((a, b) => timeOf(a) - timeOf(b));
    if (rows.length) {
      const bySender = new Map();
      for (const t of rows) {
        const k = normaliseSource(t.counterparty);
        if (!bySender.has(k)) bySender.set(k, []);
        bySender.get(k).push(t);
      }
      const [mainKey, mainRows] = [...bySender.entries()].sort((a, b) => median(b[1].map(amountOf)) - median(a[1].map(amountOf)))[0];
      const mainUsual = median(mainRows.map(amountOf));
      const pays = mainRows.filter((t) => Math.abs(amountOf(t) - mainUsual) <= mainUsual * 0.5).map(timeOf);
      const paychecks = pays.map((at, i) => {
        const until = i + 1 < pays.length ? pays[i + 1] : Infinity;
        return { at, amount: sum(rows.filter((t) => timeOf(t) >= startOfDay(at) && timeOf(t) < until)) };
      });
      const names = [...bySender.values()]
        .sort((a, b) => median(b.map(amountOf)) - median(a.map(amountOf)))
        .map((list) => list.at(-1).counterparty.trim());
      const full = paychecks.slice(0, -1).map((p) => p.amount);
      return {
        source: names.join(" + "),
        sources: names,
        amount: median(full.length ? full : paychecks.map((p) => p.amount)),
        paychecks,
        paydays: pays,
        usualDay: Math.round(median(pays.map((at) => dayOfMonth(at, cal)))),
        picked: true,
      };
    }
  }
  const [thisMonth] = monthBounds(now, cal);
  const m1 = monthBounds(thisMonth - 1, cal)[0];
  const m2 = monthBounds(m1 - 1, cal)[0];
  const windowStart = m2;
  const monthKey = (ms) => monthBounds(ms, cal)[0];

  const bySender = new Map();
  for (const t of txs) {
    if (!isIncome(t)) continue;
    const who = (t.counterparty || "").trim();
    if (!isPayerName(who) || !Number.isFinite(timeOf(t))) continue;
    const key = who.toLowerCase();
    if (!bySender.has(key)) bySender.set(key, { source: who, rows: [] });
    bySender.get(key).rows.push(t);
  }
  let best = null;
  for (const { source, rows } of bySender.values()) {
    const recent = rows.filter((t) => timeOf(t) >= windowStart && timeOf(t) <= now);
    const usual = median(recent.map(amountOf));
    if (!usual) continue;
    const similar = (t) => Math.abs(amountOf(t) - usual) <= usual * 0.2;
    const months = new Set(recent.filter(similar).map((t) => monthKey(timeOf(t))));
    if (months.size < 2) continue;
    const paychecks = rows.filter(similar).map((t) => ({ at: timeOf(t), amount: amountOf(t) })).sort((a, b) => a.at - b.at);
    const score = usual * months.size;
    if (!best || score > best.score) best = { source, amount: usual, paychecks, score };
  }
  if (!best) return null;
  // A "payday" has to matter: at least a quarter of a usual month's spending
  // (a small regular transfer isn't what the month runs on).
  const monthSpend = [m2, m1].map((start) => {
    const [, end] = monthBounds(start, cal);
    return sum(txs.filter((t) => isSpending(t) && timeOf(t) >= start && timeOf(t) < end));
  });
  const usualMonth = median(monthSpend.filter((x) => x > 0)) || 0;
  if (usualMonth > 0 && best.amount < usualMonth * 0.25) return null;
  return {
    source: best.source,
    amount: best.amount,
    paychecks: best.paychecks,
    paydays: best.paychecks.map((p) => p.at),
    usualDay: Math.round(median(best.paychecks.map((p) => dayOfMonth(p.at, cal)))),
  };
}

/** The next date on [day] of the month after [after] (and after [now]). */
function nextUsualDay(after, day, cal, now) {
  let [start] = monthBounds(after, cal);
  for (let i = 0; i < 3; i++) {
    const [, end] = monthBounds(start, cal);
    const len = Math.round((end - start) / DAY);
    const candidate = start + (Math.min(day, len) - 1) * DAY;
    if (candidate > after + 10 * DAY && candidate > startOfDay(now)) return candidate;
    start = end;
  }
  return startOfDay(after) + 30 * DAY;
}

/** Spending per day after a payday, as a running total, for [days] days. */
function cumulativeSpend(spending, from, days) {
  const out = new Array(days).fill(0);
  for (const t of spending) {
    const i = dayIndex(startOfDay(from), timeOf(t));
    if (timeOf(t) >= from && i >= 0 && i < days) out[i] += amountOf(t);
  }
  for (let i = 1; i < days; i++) out[i] += out[i - 1];
  return out;
}

/**
 * When income lands and how fast it goes. Each pay cycle runs from one
 * payday to the next; "spent" is all spending in the cycle against that
 * paycheck. Returns { found: false } with no regular income.
 */
export function paydayPattern(txs, { cal, now, salarySource = null, salarySources = null }) {
  const reg = regularIncome(txs, { cal, now, salarySource, salarySources });
  if (!reg) return { found: false };
  const spending = txs.filter(isSpending);
  // Each cycle is measured against its own paycheck.
  const checks = reg.paychecks.filter((p) => p.at <= now);
  const pays = checks.map((p) => p.at);
  const lengths = [];
  const cycles = [];
  const cyclesEtb = [];
  for (let i = 0; i + 1 < checks.length; i++) {
    const len = Math.max(1, dayIndex(startOfDay(pays[i]), pays[i + 1]));
    lengths.push(len);
    const etb = cumulativeSpend(spending, pays[i], len);
    cyclesEtb.push(etb);
    cycles.push(etb.map((c) => c / checks[i].amount));
  }
  const cycleLen = Math.round(median(lengths) || 30);

  // Day (after payday, 1-based) when 70% of the paycheck was gone.
  const reached = cycles.map((c) => c.findIndex((f) => f >= 0.7)).filter((i) => i >= 0).map((i) => i + 1);
  const daysTo70 = reached.length >= Math.ceil(cycles.length / 2) && reached.length > 0 ? Math.round(median(reached)) : null;
  const spentShare = cycles.length ? Math.round(median(cycles.map((c) => c.at(-1))) * 100) : null;

  // Burn-down: share of the paycheck left, by day. Usual = average of past cycles.
  const usual = Array.from({ length: cycleLen }, (_, d) => {
    if (!cycles.length) return null;
    const left = cycles.map((c) => 1 - c[Math.min(d, c.length - 1)]);
    return left.reduce((s, x) => s + x, 0) / left.length;
  });
  const last = pays.at(-1);
  const elapsed = Math.min(cycleLen, dayIndex(startOfDay(last), now) + 1);
  const currentEtb = cumulativeSpend(spending, last, elapsed);
  const current = currentEtb.map((c) => 1 - c / checks.at(-1).amount);

  // The chart, in ETB spent since payday (a share left goes flat at 0 when
  // spending passes the paycheck). Until this cycle has 3 days, show the
  // last complete one instead.
  const usualSpent = Array.from({ length: cycleLen }, (_, d) =>
    cyclesEtb.length ? median(cyclesEtb.map((c) => c[Math.min(d, c.length - 1)])) : null);
  const useLast = elapsed < 3 && cyclesEtb.length > 0;
  const chart = useLast
    ? { which: "last", start: pays.at(-2), paycheck: checks.at(-2).amount, spent: cyclesEtb.at(-1).slice(0, cycleLen) }
    : { which: "this", start: last, paycheck: checks.at(-1).amount, spent: currentEtb };

  return {
    found: true,
    picked: !!reg.picked,
    source: reg.source,
    amount: reg.amount,
    usualDay: reg.usualDay,
    lastPayday: last,
    nextPayday: nextUsualDay(last, reg.usualDay, cal, now),
    cycleLen,
    daysTo70,
    spentShare,
    usual,
    current,
    usualSpent,
    chart,
  };
}

// ── Low-balance days ────────────────────────────────────────────────────────
/**
 * Per account, the days in [month] its balance sat under its floor: the
 * user's floor, or 2× that account's average daily spend this month.
 * Balances come from the SMS; a day with no SMS carries the last known
 * balance forward. Days before any known balance are unknown (null).
 * [usualPayday] (day of month) marks low days that fall in the week before.
 */
export function lowBalanceDays(txs, { cal, now, month = now, userFloor = null, usualPayday = null }) {
  const [from, to] = monthBounds(month, cal);
  const end = Math.min(to, startOfDay(now) + DAY);
  const dayCount = Math.max(0, dayIndex(from, end - 1) + 1);
  const out = [];
  for (const acc of groupAccounts(txs)) {
    const withBalance = acc.list
      .filter((t) => typeof t.balance === "number" && t.balance >= 0 && Number.isFinite(timeOf(t)))
      .sort((a, b) => timeOf(a) - timeOf(b));
    if (!withBalance.some((t) => timeOf(t) < end)) continue;
    const spend = sum(acc.list.filter((t) => isSpending(t) && timeOf(t) >= from && timeOf(t) < end));
    const floor = userFloor ?? (2 * spend) / Math.max(1, dayCount);
    if (!(floor > 0)) continue;
    let k = 0;
    let bal = null;
    const days = [];
    for (let d = 0; d < dayCount; d++) {
      const dayEnd = from + (d + 1) * DAY;
      while (k < withBalance.length && timeOf(withBalance[k]) < dayEnd) bal = withBalance[k++].balance;
      days.push({ balance: bal, low: bal !== null && bal < floor });
    }
    const lowIdx = days.map((x, i) => (x.low ? i : -1)).filter((i) => i >= 0);
    if (!days.some((x) => x.balance !== null)) continue;
    let beforePayday = false;
    if (usualPayday && lowIdx.length) {
      const near = lowIdx.filter((i) => {
        const dom = dayOfMonth(from + i * DAY + DAY / 2, cal);
        return dom >= usualPayday - 7 && dom < usualPayday;
      }).length;
      beforePayday = near * 2 >= lowIdx.length;
    }
    out.push({ name: acc.suffix ? `${acc.name} ··${acc.suffix}` : acc.name, bank: acc.bank, floor, days, lowCount: lowIdx.length, beforePayday });
  }
  return out.sort((a, b) => b.lowCount - a.lowCount);
}
