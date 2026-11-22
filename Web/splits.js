// Commitments and split payments for the Habits tab. Pure functions, no DOM
// (tested by tests/web/splits.test.mjs). Saved per user in i_review_decisions:
//   kind 'commitment' — ref = id, detail = { name, key (payee), total|null }
//   kind 'tx_split'   — ref = transaction id, detail = { parts: [...] }
//
// A commitment is something paid off in parts to one person (a laptop in
// instalments). Every payment to or from that person waits in "Label these"
// until the user says what it was. A label is one or more parts that add up
// to the payment:
//   instalment — towards a commitment (money out): spending, kind "commitments"
//   normal     — ordinary spending (money out), the payee's usual kind
//   loan       — borrowed or repaid (either way): out of income and spending,
//                counted in "Loans with friends"
//   refund     — money given back (money in): out of income, off spending
//   passed     — money passed on for someone else (either way): out of totals

import { displayName, monthOf, personKey } from "./habits.js";

const timeOf = (tx) => Date.parse(tx.occurred_at);
const amountOf = (tx) => Number(tx.amount) || 0;
const fmt = (n) => `ETB ${Math.round(n).toLocaleString("en-US")}`;
const round2 = (n) => Math.round(n * 100) / 100;
const isCredit = (t) => t.type === "CREDIT";

export const MAX_PARTS = 4;
export const PART_LABELS = {
  instalment: "Instalment",
  normal: "Normal spending",
  loan: "Loan",
  refund: "Refund",
  passed: "Passed on",
};
/** Which meanings fit money out and money in. */
export const PARTS_OUT = ["instalment", "normal", "loan", "passed"];
export const PARTS_IN = ["loan", "refund", "passed"];
const OUT_OF_TOTALS = new Set(["loan", "passed", "refund"]);

export function cleanCommitment(id, d) {
  const name = String(d?.name || "").trim().slice(0, 40);
  const key = String(d?.key || "").trim().slice(0, 80);
  const total = Number(d?.total);
  if (!id || !name || !key) return null;
  return { id: String(id), name, key, total: total > 0 ? Math.round(total) : null };
}

/**
 * Checks parts against the payment: 1 to 4, each a known meaning for this
 * direction with an amount above 0, instalments tied to a commitment, and
 * all together equal to the payment (to the cent). Returns the parts or null.
 */
export function cleanParts(parts, tx, commitmentIds = []) {
  if (!Array.isArray(parts) || !parts.length || parts.length > MAX_PARTS) return null;
  const allowed = isCredit(tx) ? PARTS_IN : PARTS_OUT;
  const out = [];
  for (const p of parts) {
    const amount = round2(Number(p?.amount));
    if (!allowed.includes(p?.what) || !(amount > 0)) return null;
    const part = { what: p.what, amount };
    if (p.what === "instalment") {
      if (!commitmentIds.includes(p.commitment)) return null;
      part.commitment = p.commitment;
    }
    out.push(part);
  }
  const sum = round2(out.reduce((s, p) => s + p.amount, 0));
  return Math.abs(sum - round2(amountOf(tx))) < 0.01 ? out : null;
}

/** Commitments and labels from saved decisions. */
export function splitsFrom(rows) {
  const commitments = [];
  for (const r of rows || []) {
    if (r.kind === "commitment" && r.decision === "yes") {
      const c = cleanCommitment(r.ref, r.detail);
      if (c) commitments.push(c);
    }
  }
  const labels = new Map();
  for (const r of rows || []) {
    if (r.kind === "tx_split" && r.decision === "yes" && Array.isArray(r.detail?.parts)) labels.set(String(r.ref), r.detail.parts);
  }
  return { commitments, labels };
}

/**
 * Marks labelled payments (after markRules, whose marks a label replaces):
 * t.split = the parts; t.split_out when every part is out of the totals, so
 * the Dashboard leaves the whole payment out. Returns how many it marked.
 */
export function markSplits(txs, labels, commitments = []) {
  const ids = commitments.map((c) => c.id);
  let marked = 0;
  for (const t of txs) {
    delete t.split; delete t.split_out;
    const raw = labels && labels.get(String(t.id));
    if (!raw) continue;
    const parts = cleanParts(raw, t, ids);
    if (!parts) continue;
    delete t.for_others; delete t.friend_loan; delete t.bill_back;
    t.split = parts;
    if (parts.every((p) => OUT_OF_TOTALS.has(p.what))) t.split_out = true;
    marked += 1;
  }
  return marked;
}

/** Payments to or from a commitment's person that have no label yet, newest first. */
export function toLabel(txs, commitments) {
  const keys = new Set(commitments.map((c) => c.key));
  return txs
    .filter((t) => !t.split && !t.pass_through && Number.isFinite(timeOf(t)) && keys.has(personKey(t.counterparty))
      && (isCredit(t) || ["DEBIT", "TRANSFER_OUT", "PAYMENT"].includes(t.type)))
    .sort((a, b) => timeOf(b) - timeOf(a));
}

/** What a commitment has had so far: total, by month, the instalments, what's left. */
export function commitmentSummary(txs, c, { cal }) {
  const pays = [];
  for (const t of txs) {
    for (const p of t.split || []) if (p.what === "instalment" && p.commitment === c.id) pays.push({ tx: t, ms: timeOf(t), amount: p.amount });
  }
  pays.sort((a, b) => a.ms - b.ms);
  const months = new Map();
  for (const p of pays) {
    const m = monthOf(p.ms, cal);
    if (!months.has(m.key)) months.set(m.key, { key: m.key, label: m.label, total: 0 });
    months.get(m.key).total += p.amount;
  }
  const paid = round2(pays.reduce((s, p) => s + p.amount, 0));
  return {
    ...c, paid, pays, count: pays.length,
    months: [...months.values()],
    left: c.total ? Math.max(0, round2(c.total - paid)) : null,
    share: c.total ? Math.min(100, Math.round((100 * paid) / c.total)) : null,
  };
}

export function explainCommitment(s) {
  if (!s.count) return `No instalments labelled yet for ${s.name}.`;
  const base = `You've paid ${fmt(s.paid)} towards ${s.name} in ${s.count} ${s.count === 1 ? "instalment" : "instalments"}`;
  if (!s.total) return `${base}.`;
  return s.left > 0 ? `${base}: ${s.share}% of ${fmt(s.total)}, ${fmt(s.left)} to go.` : `${base}. It's fully paid.`;
}

/** Loan parts of split payments, for "Loans with friends". */
export function splitLoans(txs) {
  const out = [];
  for (const t of txs) {
    for (const p of t.split || []) if (p.what === "loan") out.push({ tx: t, key: personKey(t.counterparty), name: displayName(t.counterparty), amount: p.amount, in: isCredit(t) });
  }
  return out;
}

/** A one-part label for the quick buttons. */
export function wholeLabel(tx, what, commitment = null) {
  const part = { what, amount: round2(amountOf(tx)) };
  if (what === "instalment") part.commitment = commitment;
  return [part];
}

/** A short id for a new commitment. */
export function newCommitmentId(name, existing = []) {
  const base = String(name).toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 30) || "commitment";
  let id = base, i = 2;
  while (existing.includes(id)) id = `${base}-${i++}`;
  return id;
}
