// Where the user's money goes. Pure functions, no DOM (tested by
// tests/web/money.test.mjs); habits-view.js draws them.
//
// Most people never label anything, so every payment gets one built-in
// "kind", first match wins:
//   habits  — a payee the user mapped to a habit (i_habit_rules)
//   bills   — electricity, water, TV, insurance, school, levies, bank fees
//   airtime — telecom packages, airtime, data
//   cash    — ATM and agent withdrawals
//   loans   — loan apps and repayments
//   shops   — businesses (Plc, Trading, Hotel, Cafe…) and merchant payments
//   people  — names that look like a person
//   others  — "Paid for others": past the user's own N a month (rules.js)
//   other   — the rest
// A kind the user picked for a payee (or accepted from Gemini) beats every
// built-in rule except a habit. Own moves, confirmed pass-throughs (isSpending) and money sent back to a
// salary payer (already taken off the salary) are never spending here.

import { isIncome, isSpending } from "./insights.js";
import { isOwnMove, monthOf, personKey, displayName, ruleFor } from "./habits.js";
import { isPayer, salaryPayments } from "./flows.js";

const timeOf = (tx) => Date.parse(tx.occurred_at);
const amountOf = (tx) => Number(tx.amount) || 0;
const pct = (a, b) => (b > 0 ? Math.round((100 * a) / b) : 0);
const fmt = (n) => `ETB ${Math.round(n).toLocaleString("en-US")}`;

/** The kinds, in a fixed order; the order is also the colour slot. */
export const KINDS = [
  { id: "habits", label: "Your habits" },
  { id: "people", label: "People" },
  { id: "shops", label: "Shops & places" },
  { id: "bills", label: "Bills & fees" },
  { id: "airtime", label: "Airtime & data" },
  { id: "cash", label: "Cash out" },
  { id: "loans", label: "Loans" },
  { id: "others", label: "Paid for others" },
  { id: "other", label: "Other" },
];
/** Kinds a user can put a payee in by hand (habits come from habit rules). */
export const PICKABLE_KINDS = KINDS.filter((k) => k.id !== "habits" && k.id !== "others").map((k) => k.id);
const KIND_IDS = new Set(PICKABLE_KINDS);
export const kindLabel = (id) => KINDS.find((k) => k.id === id)?.label || id;

const BILLS = /\b(eeu|electric|electricity|power|water|dstv|gotv|canal|insurance|disaster risk|tax|vat|service charge|commission|fees?|penalt\w*|tuition|school fee|rent|municipal|levy)\b/i;
const AIRTIME = /telecom package|airtime|air time|safaricom|ethio ?tel|data package|recharge|top ?-?up/i;
const CASH = /\batm\b|withdraw|cash ?out|agent/i;
const LOANS = /loan|\bmela\b|repay|michu|endekise|dube|kifiya|overdraft/i;
const SHOPS = /\b(plc|p\.?l\.?c|s\.?c|trading|hotel|caf[eé]|coffee|restaurant|super ?market|pharmac\w*|enterprise|share company|general|import|export|bank|bakery|pastry|shop|store|market|mall|boutique|clinic|hospital|academy|university|college|transport|taxi|ride|fuel|oil|station|lounge|bar|pizza|burger|juice|fitness|gym|salon|spa|cinema|club|institute|agency|services|solutions|technolog\w*|printing|stationery|electronics|furniture|garage)\b/i;
const MERCHANT = /^\s*\d+\s*-\s*/; // "123 - KALDIS COFFEE"
const PERSON = /^[a-z][a-z'’.\/-]*(?: [a-z][a-z'’.\/-]*){1,4}$/i;

/** The kind of one outgoing payment. */
export function kindOf(tx, rules = [], kinds = null) {
  const name = String(tx.counterparty || "");
  if (rules.length && ruleFor(name, rules)) return "habits";
  // A kind the user picked (or accepted from Gemini) for this payee.
  const picked = kinds && kinds.get(personKey(name));
  if (picked && KIND_IDS.has(picked)) return picked;
  const text = `${name} ${tx.category || ""}`;
  if (AIRTIME.test(name)) return "airtime";
  if (CASH.test(name)) return "cash";
  if (LOANS.test(text)) return "loans";
  if (BILLS.test(name)) return "bills";
  if (MERCHANT.test(name) || SHOPS.test(name)) return "shops";
  const clean = displayName(name).replace(/\s*\(.*$/, "").trim();
  if (PERSON.test(clean)) return "people";
  if (tx.type === "PAYMENT") return "shops";
  return "other";
}

/**
 * Every payment that counts as spending, with its kind:
 * [{ tx, ms, amount, kind, name, key }]. [salaryKeys]: money sent back to
 * a salary payer is left out (it's taken off the salary instead). Moves to
 * the user's own accounts are left out too, except cash taken out and loan
 * repayments.
 */
export function spendingRows(txs, { rules = [], salaryKeys = [], kinds = null } = {}) {
  const payers = salaryKeys.filter(Boolean);
  const rows = [];
  for (const t of txs) {
    if (!isSpending(t) || !Number.isFinite(timeOf(t))) continue;
    if (payers.length && isPayer(t.counterparty, payers)) continue;
    const kind = t.for_others ? "others" : kindOf(t, rules, kinds);
    // Moves to the user's own accounts aren't spending; cash taken out and
    // loan repayments are kept, since that's where the money went.
    if (isOwnMove(t) && kind !== "cash" && kind !== "loans") continue;
    rows.push({ tx: t, ms: timeOf(t), amount: amountOf(t), kind, name: displayName(t.counterparty) || "Unnamed", key: personKey(t.counterparty) || "unnamed" });
  }
  // A friend's share of a bill sent back (rules.js) comes off spending.
  for (const t of txs) {
    if (!t.bill_back || !Number.isFinite(timeOf(t))) continue;
    rows.push({ tx: t, ms: timeOf(t), amount: -amountOf(t), back: true, kind: kindOf(t, rules, kinds), name: displayName(t.counterparty) || "Unnamed", key: personKey(t.counterparty) || "unnamed" });
  }
  return rows.sort((a, b) => a.ms - b.ms);
}

/** Every month in the data, oldest first, partial ones marked. */
export function monthsOf(txs, cal) {
  const times = txs.map(timeOf).filter(Number.isFinite);
  if (!times.length) return [];
  const first = monthOf(times.reduce((a, b) => Math.min(a, b)), cal).key;
  const last = monthOf(times.reduce((a, b) => Math.max(a, b)), cal).key;
  const by = new Map();
  for (const ms of times) {
    const m = monthOf(ms, cal);
    if (!by.has(m.key)) by.set(m.key, { ...m, partial: m.key === first || m.key === last || (cal === "ethiopian" && m.month === 13) });
  }
  return [...by.values()].sort((a, b) => a.key.localeCompare(b.key));
}

/** Spending per month, split by kind. */
export function spendingByKind(rows, txs, { cal }) {
  const months = monthsOf(txs, cal).map((m) => ({ ...m, byKind: {}, total: 0, sources: [] }));
  const by = new Map(months.map((m) => [m.key, m]));
  for (const r of rows) {
    const m = by.get(monthOf(r.ms, cal).key);
    if (!m) continue;
    m.byKind[r.kind] = (m.byKind[r.kind] || 0) + r.amount;
    m.total += r.amount;
    m.sources.push(r);
  }
  const totals = {};
  for (const r of rows) totals[r.kind] = (totals[r.kind] || 0) + r.amount;
  const full = months.filter((m) => !m.partial);
  const average = {};
  for (const k of KINDS) average[k.id] = full.length ? Math.round(full.reduce((s, m) => s + (m.byKind[k.id] || 0), 0) / full.length) : null;
  return { months, totals, average, all: rows.reduce((s, r) => s + r.amount, 0) };
}

/**
 * One month's money flow: what came in (salary, other) and where it went
 * (by kind), plus what was kept, or what came from savings when more went
 * out than came in.
 */
export function moneyFlow(txs, rows, { cal, key, salaryKeys = [] }) {
  const inMonth = (ms) => monthOf(ms, cal).key === key;
  const sal = salaryPayments(txs, salaryKeys).filter((p) => inMonth(p.ms));
  const salary = sal.reduce((s, p) => s + p.amount, 0);
  const otherRows = txs.filter((t) => isIncome(t) && inMonth(timeOf(t)) && !(salaryKeys.length && isPayer(t.counterparty, salaryKeys)));
  const other = otherRows.reduce((s, t) => s + amountOf(t), 0);
  const out = rows.filter((r) => inMonth(r.ms));
  const byKind = {};
  for (const r of out) byKind[r.kind] = (byKind[r.kind] || 0) + r.amount;
  const spent = out.reduce((s, r) => s + r.amount, 0);
  const moneyIn = Math.max(0, salary) + other;
  return {
    key, salary: Math.max(0, salary), other, moneyIn, spent, byKind,
    kept: Math.max(0, moneyIn - spent), fromSavings: Math.max(0, spent - moneyIn),
    sources: { salary: sal, other: otherRows, out },
  };
}

/** Per month: salary against spending by kind. Months with no salary are left out. */
export function salaryVsSpending(txs, byKind, { cal, salaryKeys = [] }) {
  if (!salaryKeys.length) return [];
  const sal = new Map();
  for (const p of salaryPayments(txs, salaryKeys)) {
    const k = monthOf(p.ms, cal).key;
    sal.set(k, (sal.get(k) || 0) + p.amount);
  }
  return byKind.months.filter((m) => sal.has(m.key)).map((m) => ({ ...m, salary: sal.get(m.key) }));
}

/**
 * The [n] people and places the user pays most, with their share of all
 * spending and the last [months] months' totals for a trend line.
 */
export function topPayees(rows, { cal, now, n = 10, months = 6 }) {
  const keys = [];
  let cursor = monthOf(now, cal);
  let ms = now;
  for (let i = 0; i < months; i++) {
    keys.unshift(cursor.key);
    // Step back into the previous month: 5 days before its first day is safe in both calendars.
    do { ms -= 5 * 864e5; } while (monthOf(ms, cal).key === cursor.key);
    cursor = monthOf(ms, cal);
  }
  const total = rows.reduce((s, r) => s + r.amount, 0);
  const by = new Map();
  for (const r of rows) {
    if (!by.has(r.key)) by.set(r.key, { key: r.key, names: new Map(), kinds: new Map(), total: 0, count: 0, trend: Object.fromEntries(keys.map((k) => [k, 0])), sources: [] });
    const e = by.get(r.key);
    e.names.set(r.name, (e.names.get(r.name) || 0) + 1);
    if (r.kind !== "others") e.kinds.set(r.kind, (e.kinds.get(r.kind) || 0) + 1);
    e.total += r.amount; if (!r.back) e.count += 1; e.sources.push(r);
    const mk = monthOf(r.ms, cal).key;
    if (mk in e.trend) e.trend[mk] += r.amount;
  }
  return {
    months: keys,
    rows: [...by.values()].sort((a, b) => b.total - a.total).slice(0, n).map(({ names, kinds, trend, ...e }) => ({
      ...e,
      kind: [...kinds.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] || "others",
      name: [...names.entries()].sort((a, b) => b[1] - a[1])[0][0],
      share: pct(e.total, total),
      trend: keys.map((k) => trend[k]),
    })),
  };
}

/** Payments under [limit]: how many, how much, and where most of them go. */
export function smallPayments(rows, txs, { cal, limit = 200 }) {
  const pays = rows.filter((r) => !r.back);
  const small = pays.filter((r) => r.amount < limit);
  const total = small.reduce((s, r) => s + r.amount, 0);
  const all = rows.reduce((s, r) => s + r.amount, 0);
  const full = monthsOf(txs, cal).filter((m) => !m.partial).map((m) => m.key);
  const inFull = small.filter((r) => full.includes(monthOf(r.ms, cal).key));
  const by = new Map();
  for (const r of small) {
    if (!by.has(r.key)) by.set(r.key, { key: r.key, name: r.name, count: 0, total: 0 });
    const e = by.get(r.key); e.count += 1; e.total += r.amount;
  }
  return {
    limit, count: small.length, total, share: pct(total, all), countShare: pct(small.length, pays.length),
    perMonth: full.length ? { count: Math.round(inFull.length / full.length), total: Math.round(inFull.reduce((s, r) => s + r.amount, 0) / full.length) } : null,
    top: [...by.values()].sort((a, b) => b.total - a.total).slice(0, 3),
    sources: small,
  };
}

// ── Plain words ─────────────────────────────────────────────────────────────
export function explainFlow(f, label) {
  if (!f.spent && !f.moneyIn) return `Nothing came in or went out in ${label}.`;
  const kinds = Object.entries(f.byKind).sort((a, b) => b[1] - a[1]).slice(0, 2);
  const where = kinds.map(([k, v]) => `${pct(v, f.spent)}% to ${kindLabel(k).toLowerCase()}`).join(" and ");
  const end = f.fromSavings > 0
    ? `${fmt(f.fromSavings)} more went out than came in, so it came from money you already had.`
    : `You kept ${fmt(f.kept)} (${pct(f.kept, f.moneyIn)}% of what came in).`;
  return `In ${label}, ${fmt(f.spent)} went out: ${where}. ${end}`;
}

export function explainByKind(b) {
  const top = KINDS.map((k) => [k.id, b.totals[k.id] || 0]).sort((x, y) => y[1] - x[1]).filter(([, v]) => v > 0);
  if (!top.length) return "";
  const [k, v] = top[0];
  const avg = b.average[k];
  return `${kindLabel(k)} take the biggest share of your spending: ${pct(v, b.all)}% overall${avg ? `, about ${fmt(avg)} in a full month` : ""}.`;
}

export function explainSalary(rows) {
  if (!rows.length) return "";
  const over = rows.filter((m) => m.total > m.salary);
  const last = [...rows].reverse().find((m) => !m.partial) || rows[rows.length - 1];
  const share = pct(last.total, last.salary);
  if (over.length === rows.length) return `You spent more than your salary in every month it came in. In ${last.label}: ${share}% of it.`;
  if (!over.length) return `Your spending stayed under your salary every month. In ${last.label} you spent ${share}% of it.`;
  return `You spent more than your salary in ${over.length} of ${rows.length} months (${over.map((m) => m.label).join(", ")}). In ${last.label} you spent ${share}% of it.`;
}

export function explainTop(t) {
  if (!t.rows.length) return "";
  const top3 = t.rows.slice(0, 3);
  const share = top3.reduce((s, r) => s + r.share, 0);
  return `${top3.map((r) => r.name).join(", ")} together take ${share}% of everything you spend.`;
}

export function explainSmall(s) {
  if (!s.count) return "";
  return `${s.countShare}% of your payments are under ${fmt(s.limit)}. They look small, but together they come to ${fmt(s.total)}, ${s.share}% of your spending`
    + (s.perMonth ? `: about ${s.perMonth.count} payments and ${fmt(s.perMonth.total)} a month.` : ".");
}
