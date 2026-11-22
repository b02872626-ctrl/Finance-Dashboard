// Per-person rules for the Habits tab. Pure functions, no DOM (tested by
// tests/web/rules.test.mjs). Saved per user in i_review_decisions
// (kind 'payee_rule', ref = payee key, detail = the rule).
//
//   { type: "mine", n, usual }  Only n payments a month to this person are
//       the user's own: the n closest to [usual]. The rest are marked
//       for_others: still spending, but the "Paid for others" kind and out of
//       habit totals.
//   { type: "loan", above }     Money either way of at least [above] is a
//       loan (friend_loan): out of income and spending, tracked as a balance.
//       Less is a bill share: what the user pays stays spending; what the
//       friend sends back (bill_back) leaves income and comes off spending.

import { EXPENSE_TYPES } from "./insights.js";
import { displayName, isOwnMove, monthOf, personKey } from "./habits.js";
import { kindOf, monthsOf } from "./money.js";
import { splitLoans } from "./splits.js";

const timeOf = (tx) => Date.parse(tx.occurred_at);
const amountOf = (tx) => Number(tx.amount) || 0;
const fmt = (n) => `ETB ${Math.round(n).toLocaleString("en-US")}`;
const median = (xs) => {
  if (!xs.length) return 0;
  const s = [...xs].sort((a, b) => a - b);
  const m = s.length >> 1;
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
};

export const LOAN_ABOVE = 1000;
const isOut = (t) => EXPENSE_TYPES.has(t.type) && !isOwnMove(t) && !t.pass_through;
const isIn = (t) => t.type === "CREDIT" && !isOwnMove(t) && !t.pass_through;

/** A rule as saved, checked; null if it isn't a usable one. */
export function cleanRule(r) {
  if (!r || typeof r !== "object") return null;
  if (r.type === "mine") {
    const n = Math.round(Number(r.n)), usual = Number(r.usual);
    return n >= 1 && n <= 31 && usual > 0 ? { type: "mine", n, usual: Math.round(usual) } : null;
  }
  if (r.type === "loan") {
    const above = Number(r.above);
    return above > 0 ? { type: "loan", above: Math.round(above) } : null;
  }
  return null;
}

/** The rule map (key → rule) and the declined suggestions from saved decisions. */
export function rulesFrom(rows) {
  const rules = new Map(), declined = new Set();
  for (const r of rows || []) {
    if (r.kind !== "payee_rule") continue;
    if (r.decision === "no") { declined.add(r.ref); continue; }
    const rule = cleanRule(r.detail);
    if (r.decision === "yes" && rule) rules.set(r.ref, rule);
  }
  return { rules, declined };
}

/**
 * Marks transactions by the rules (like markPassThroughs): for_others,
 * friend_loan, bill_back. Clears old marks first. Returns how many it marked.
 */
export function markRules(txs, rules, { cal = "gregorian" } = {}) {
  let marked = 0;
  const mine = new Map(); // key|month → payments
  for (const t of txs) {
    delete t.for_others; delete t.friend_loan; delete t.bill_back;
    if (!rules || !rules.size || !Number.isFinite(timeOf(t))) continue;
    const key = personKey(t.counterparty);
    const rule = key && rules.get(key);
    if (!rule) continue;
    if (rule.type === "loan") {
      if (!isOut(t) && !isIn(t)) continue;
      if (amountOf(t) >= rule.above) { t.friend_loan = true; marked += 1; }
      else if (isIn(t)) { t.bill_back = true; marked += 1; }
    } else if (rule.type === "mine" && isOut(t)) {
      const k = `${key}|${monthOf(timeOf(t), cal).key}`;
      if (!mine.has(k)) mine.set(k, { rule, pays: [] });
      mine.get(k).pays.push(t);
    }
  }
  for (const { rule, pays } of mine.values()) {
    const ranked = [...pays].sort((a, b) => Math.abs(amountOf(a) - rule.usual) - Math.abs(amountOf(b) - rule.usual) || timeOf(a) - timeOf(b));
    for (const t of ranked.slice(rule.n)) { t.for_others = true; marked += 1; }
  }
  return marked;
}

/** "Paid for others" per month and per person (needs markRules first). */
export function paidForOthers(txs, { cal }) {
  const months = new Map(), people = new Map();
  for (const t of txs) {
    if (!t.for_others || !isOut(t)) continue;
    const ms = timeOf(t), m = monthOf(ms, cal), key = personKey(t.counterparty);
    if (!months.has(m.key)) months.set(m.key, { key: m.key, label: m.label, total: 0, count: 0, sources: [] });
    const e = months.get(m.key); e.total += amountOf(t); e.count += 1; e.sources.push(t);
    if (!people.has(key)) people.set(key, { key, name: displayName(t.counterparty), total: 0, count: 0 });
    const p = people.get(key); p.total += amountOf(t); p.count += 1;
  }
  const list = [...months.values()].sort((a, b) => (a.key < b.key ? -1 : 1));
  const total = list.reduce((s, m) => s + m.total, 0);
  // Monthly average over full months only (the first, last and Pagume are partial).
  const full = monthsOf(txs, cal).filter((m) => !m.partial);
  const perMonth = full.length ? full.reduce((s, m) => s + (months.get(m.key)?.total || 0), 0) / full.length : 0;
  return { months: list, perMonth, people: [...people.values()].sort((a, b) => b.total - a.total), total, count: list.reduce((s, m) => s + m.count, 0) };
}

/**
 * Loans with each friend under a loan rule (needs markRules first).
 * balance > 0: the user owes them; < 0: they owe the user.
 */
export function loanBook(txs, rules) {
  const by = new Map();
  for (const t of txs) {
    if (!t.friend_loan) continue;
    const key = personKey(t.counterparty);
    if (!by.has(key)) by.set(key, { key, name: displayName(t.counterparty), above: rules.get(key)?.above, got: 0, gave: 0, sources: [] });
    const e = by.get(key);
    if (t.type === "CREDIT") e.got += amountOf(t); else e.gave += amountOf(t);
    e.sources.push(t);
  }
  // Loan parts of split payments (splits.js) count too.
  for (const l of splitLoans(txs)) {
    if (!by.has(l.key)) by.set(l.key, { key: l.key, name: l.name, above: rules.get(l.key)?.above, got: 0, gave: 0, sources: [] });
    const e = by.get(l.key);
    if (l.in) e.got += l.amount; else e.gave += l.amount;
    e.sources.push({ ...l.tx, amount: l.amount, part: true });
  }
  const backs = new Map();
  for (const t of txs) if (t.bill_back) {
    const key = personKey(t.counterparty);
    backs.set(key, (backs.get(key) || 0) + amountOf(t));
  }
  return [...by.values()]
    .map((e) => ({ ...e, balance: e.got - e.gave, billBack: backs.get(e.key) || 0, sources: e.sources.sort((a, b) => timeOf(b) - timeOf(a)) }))
    .sort((a, b) => Math.abs(b.balance) - Math.abs(a.balance));
}

export function explainLoan(e) {
  if (Math.abs(e.balance) < 1) return `You and ${e.name} are even on loans.`;
  return e.balance > 0
    ? `You owe ${e.name} ${fmt(e.balance)} (borrowed ${fmt(e.got)}, paid back ${fmt(e.gave)}).`
    : `${e.name} owes you ${fmt(-e.balance)} (you gave ${fmt(e.gave)}, got back ${fmt(e.got)}).`;
}

export function explainOthers(o) {
  if (!o.count) return "";
  const who = o.people.slice(0, 2).map((p) => p.name).join(" and ");
  return `You've paid ${fmt(o.total)} for other people through ${who}${o.perMonth ? `, about ${fmt(o.perMonth)} in a full month` : ""}. It still counts as your spending, but not as your habit.`;
}

/**
 * People a rule might fit. Loan: someone the user both pays and gets paid
 * by, with amounts above and below 1,000. Mine: a person mapped to a habit
 * whom the user paid twice within an hour in at least 3 months. Skips people with a rule, declined ones
 * and salary payers.
 */
export function suggestRules(txs, { rules = new Map(), declined = new Set(), habitKeys = [], salaryKeys = [], cal = "gregorian" } = {}) {
  const by = new Map();
  for (const t of txs) {
    if (!Number.isFinite(timeOf(t))) continue;
    const key = personKey(t.counterparty);
    if (!key || rules.has(key) || declined.has(key) || salaryKeys.some((s) => personKey(s) === key)) continue;
    const out = isOut(t), inn = isIn(t);
    if (!out && !inn) continue;
    if (!by.has(key)) by.set(key, { key, name: displayName(t.counterparty), person: kindOf(t) === "people", ins: [], outs: [], times: [] });
    const e = by.get(key);
    (out ? e.outs : e.ins).push(amountOf(t));
    if (out) e.times.push(timeOf(t));
  }
  const out = [];
  for (const e of by.values()) {
    const all = [...e.ins, ...e.outs];
    if (e.ins.length >= 2 && e.outs.length >= 2 && all.some((a) => a >= LOAN_ABOVE) && all.some((a) => a < LOAN_ABOVE)) {
      out.push({ key: e.key, name: e.name, rule: { type: "loan", above: LOAN_ABOVE }, weight: all.length,
        why: `You've sent ${e.name} money ${e.outs.length} times and got money back ${e.ins.length} times, some big and some small.` });
      continue;
    }
    // Paying a person twice within the hour (your fee and a friend's) in
    // at least 3 months. A café or a daily habit doesn't count.
    if (!e.person || !habitKeys.some((h) => e.key.startsWith(h) || h.startsWith(e.key))) continue;
    const times = e.times.sort((a, b) => a - b);
    const busy = new Set();
    for (let i = 1; i < times.length; i++) if (times[i] - times[i - 1] <= 3600e3) busy.add(monthOf(times[i], cal).key);
    if (busy.size < 3) continue;
    const usual = median(e.outs);
    // Yours: one payment per day you paid them (a weekly game is about 4 a month).
    const days = new Map();
    for (const ms of times) {
      const m = monthOf(ms, cal).key;
      if (!days.has(m)) days.set(m, new Set());
      days.get(m).add(new Date(ms).toDateString());
    }
    const n = Math.max(1, Math.round(median([...days.values()].map((d) => d.size))));
    const mine = n === 1 ? "one a month is yours" : `${n} a month are yours`;
    out.push({ key: e.key, name: e.name, rule: { type: "mine", n, usual: Math.round(usual) }, weight: e.outs.length,
      why: `In ${busy.size} months you paid ${e.name} twice or more within an hour, as if for you and others. If ${mine} (about ${fmt(usual)}), the rest is for others.` });
  }
  return out.sort((a, b) => b.weight - a.weight).slice(0, 3).map(({ weight, ...s }) => s);
}
