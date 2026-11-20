// Money that isn't the user's own income or spending, and the user's salary.
// Pure functions, no DOM (tested by tests/web/flows.test.mjs); app.js and
// habits-view.js import them.
//
// - Pass-throughs: money that came in for someone else and went straight
//   out again. Kash finds them; the user confirms or rejects each one in
//   i_review_decisions (supabase/add_review_decisions.sql). Confirmed ones
//   are marked `pass_through` and leave every income and spending total
//   (insights.js isSpending / isIncome).
// - Salary: the payers the user picked (Dashboard → Payday, stored in
//   i_income_entries). Money sent back to a payer is taken off. Kash
//   suggests payers who pay a steady amount on a steady day.

import { DAY, EXPENSE_TYPES, gregorianToEthiopian, isOwnAccountTransfer, isPayerName, normaliseSource } from "./insights.js";
import { isOwnMove, displayName } from "./habits.js";

const HOUR = 36e5;
const timeOf = (tx) => Date.parse(tx.occurred_at);
const amountOf = (tx) => Number(tx.amount) || 0;
const median = (xs) => {
  if (!xs.length) return 0;
  const s = [...xs].sort((a, b) => a - b);
  const m = Math.floor(s.length / 2);
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
};

/**
 * Which of the picked payers [keys] (normaliseSource keys) [name] is, or null,
 * allowing a longer or shorter spelling of the same full name: "abebe
 * kebede" matches "Abebe Kebede Alemu" and the other way round, but a lone
 * first name never matches.
 */
export function payerOf(name, keys) {
  const k = normaliseSource(name);
  if (!k) return null;
  for (const key of keys) {
    if (!key) continue;
    if (k === key || (k.startsWith(key + " ") && key.includes(" ")) || (key.startsWith(k + " ") && k.includes(" "))) return key;
  }
  return null;
}
export const isPayer = (name, keys) => payerOf(name, keys) != null;
const payerKeys = (keys) => [...new Set(keys.map(normaliseSource).filter(Boolean))];

// ── Pass-throughs ───────────────────────────────────────────────────────────
export const PASS_MIN = 5000;
export const PASS_WINDOW = 36 * HOUR;

/**
 * Money in of at least [min] (not salary, not an own move) where money out
 * in the next [window] adds up to 90–110% of it. Outgoing payments of at
 * least [min] are taken in time order while they still fit under 110%,
 * until they reach 90%; each one is used once.
 * Returns [{ key, in, outs, out }], key = the incoming transaction's id.
 */
export function findPassThroughs(txs, { salaryKeys = [], min = PASS_MIN, window = PASS_WINDOW } = {}) {
  const salary = payerKeys(salaryKeys);
  const big = txs.filter((t) => amountOf(t) >= min && !isOwnMove(t) && Number.isFinite(timeOf(t))
    && (t.category || "").trim() !== "Salary" && !isPayer(t.counterparty, salary));
  const ins = big.filter((t) => t.type === "CREDIT").sort((a, b) => timeOf(a) - timeOf(b));
  const outs = big.filter((t) => EXPENSE_TYPES.has(t.type)).sort((a, b) => timeOf(a) - timeOf(b));
  const used = new Set();
  const found = [];
  for (const c of ins) {
    const at = timeOf(c), amount = amountOf(c);
    let total = 0;
    const pick = [];
    for (const o of outs) {
      const ms = timeOf(o);
      if (ms < at || used.has(o.id)) continue;
      if (ms > at + window || total >= 0.9 * amount) break;
      if (total + amountOf(o) <= 1.1 * amount) { pick.push(o); total += amountOf(o); }
    }
    if (pick.length && total >= 0.9 * amount) {
      pick.forEach((o) => used.add(o.id));
      found.push({ key: String(c.id), in: c, outs: pick, out: total });
    }
  }
  return found;
}

/**
 * Marks the transactions of confirmed pass-throughs with `pass_through:
 * true` (in place) and returns how many were marked. [decisions] is a Map
 * key → { decision: "yes" | "no", detail: { out_ids } }. A confirmed
 * pass-through keeps the outgoing payments saved with it, even if the
 * detection would pick others today.
 */
export function markPassThroughs(txs, decisions) {
  const yes = [...decisions.entries()].filter(([, d]) => d.decision === "yes");
  const ids = new Set();
  for (const [key, d] of yes) {
    ids.add(String(key));
    for (const id of d.detail?.out_ids || []) ids.add(String(id));
  }
  let n = 0;
  for (const t of txs) {
    const on = ids.has(String(t.id));
    if (on) n++;
    if (on) t.pass_through = true; else delete t.pass_through;
  }
  return n;
}

/** Pass-throughs split by what the user said: to review, confirmed, rejected. */
export function passThroughReview(txs, decisions, opts) {
  const found = findPassThroughs(txs, opts);
  const byId = new Map(txs.map((t) => [String(t.id), t]));
  const seen = new Set(found.map((f) => f.key));
  // A confirmed one the detection no longer finds still shows, from what was saved.
  for (const [key, d] of decisions) {
    if (d.decision !== "yes" || seen.has(key) || !byId.has(key)) continue;
    const outs = (d.detail?.out_ids || []).map((id) => byId.get(String(id))).filter(Boolean);
    found.push({ key, in: byId.get(key), outs, out: outs.reduce((s, o) => s + amountOf(o), 0) });
  }
  found.sort((a, b) => timeOf(b.in) - timeOf(a.in));
  return {
    toReview: found.filter((f) => !decisions.has(f.key)),
    confirmed: found.filter((f) => decisions.get(f.key)?.decision === "yes"),
    rejected: found.filter((f) => decisions.get(f.key)?.decision === "no"),
  };
}

// ── Salary ──────────────────────────────────────────────────────────────────
/**
 * Every payment from the picked salary payers, and every payment back to
 * them (negative), oldest first: [{ tx, ms, from, amount, back }]. A credit
 * under a quarter of that payer's usual amount is something else (a
 * personal transfer from the same account) and is left out.
 */
export function salaryPayments(txs, salaryKeys) {
  const keys = payerKeys(salaryKeys);
  if (!keys.length) return [];
  const rows = [];
  for (const t of txs) {
    if (isOwnAccountTransfer(t) || t.pass_through || !Number.isFinite(timeOf(t))) continue;
    if (t.type !== "CREDIT" && !EXPENSE_TYPES.has(t.type)) continue;
    const payer = payerOf(t.counterparty, keys);
    if (payer) rows.push({ t, payer });
  }
  const usual = new Map();
  for (const key of keys) usual.set(key, median(rows.filter((r) => r.payer === key && r.t.type === "CREDIT").map((r) => amountOf(r.t))));
  return rows
    .filter(({ t, payer }) => t.type !== "CREDIT" || amountOf(t) >= usual.get(payer) / 4)
    .map(({ t }) => {
      const back = t.type !== "CREDIT";
      return { tx: t, ms: timeOf(t), from: displayName(t.counterparty), amount: back ? -amountOf(t) : amountOf(t), back };
    })
    .sort((a, b) => a.ms - b.ms);
}

/** Salary per month (net of money sent back), in the user's calendar. */
export function salaryByMonth(pays, monthOf) {
  const by = new Map();
  for (const p of pays) {
    const m = monthOf(p.ms);
    if (!by.has(m.key)) by.set(m.key, { ...m, total: 0, rows: [] });
    const e = by.get(m.key);
    e.total += p.amount; e.rows.push(p);
  }
  return [...by.values()].sort((a, b) => a.key.localeCompare(b.key));
}

const ecDay = (ms) => gregorianToEthiopian(new Date(ms)).day;
const gcDay = (ms) => new Date(ms).getDate();

/**
 * People who look like they pay a salary: at least [minCount] payments of
 * at least [minAmount], mostly the same amount (most within ±25% of the
 * usual), either monthly on about the same day (in the user's calendar) or
 * every two weeks. Not already picked, not rejected. Steadiest first.
 */
export function salaryCandidates(txs, { cal, picked = [], rejected = [], minCount = 3, minAmount = 1000 } = {}) {
  const skip = payerKeys([...picked, ...rejected]);
  const by = new Map();
  for (const t of txs) {
    if (t.type !== "CREDIT" || isOwnMove(t) || t.pass_through || !isPayerName(t.counterparty) || amountOf(t) < minAmount) continue;
    const key = normaliseSource(t.counterparty);
    if (!key || isPayer(t.counterparty, skip)) continue;
    if (!by.has(key)) by.set(key, { key, name: displayName(t.counterparty), rows: [] });
    by.get(key).rows.push(t);
  }
  const dayOf = cal === "ethiopian" ? ecDay : gcDay;
  const out = [];
  for (const e of by.values()) {
    if (e.rows.length < minCount) continue;
    const amounts = e.rows.map(amountOf);
    const usual = median(amounts);
    const steady = amounts.filter((a) => Math.abs(a - usual) <= 0.25 * usual).length / amounts.length;
    if (steady < 0.75) continue;
    const ms = e.rows.map(timeOf).sort((a, b) => a - b);
    const gaps = ms.slice(1).map((m, i) => (m - ms[i]) / DAY);
    const gap = median(gaps);
    const days = ms.map(dayOf);
    const usualDay = median(days);
    // Distance on a 30-day circle, so day 29 and day 2 are 3 apart.
    const spread = median(days.map((d) => { const x = Math.abs(d - usualDay); return Math.min(x, 30 - x); }));
    let cadence = null;
    if (gap >= 25 && gap <= 35 && spread <= 4) cadence = "monthly";
    else if (gap >= 12 && gap <= 16) cadence = "every 2 weeks";
    if (!cadence) continue;
    out.push({ key: e.key, name: e.name, count: e.rows.length, usual: Math.round(usual), usualDay: Math.round(usualDay), cadence, steady, last: ms[ms.length - 1] });
  }
  return out.sort((a, b) => b.steady - a.steady || b.count - a.count);
}
