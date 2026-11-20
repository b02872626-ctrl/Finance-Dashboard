// Opt-in Gemini sorting for the Habits tab. Pure functions, no DOM, no network
// (tested by tests/web/ai.test.mjs). app.js sends what aiPayload() builds to the
// kash-gemini Edge Function and runs the answer through readSuggestions().
//
// What leaves the device, per payee: an id like "p3", the name with any digits
// removed, how many payments, the usual amount, the usual hour, the share on
// weekdays, and the kind Kash guessed. Never account or phone numbers,
// balances, dates, or SMS text. Only payees Kash put in People, Shops or Other
// are sent, and not ones the user already sorted (a habit or a picked kind).

import { ruleFor, SUGGESTED_HABITS } from "./habits.js";
import { PICKABLE_KINDS, spendingRows } from "./money.js";

export const AI_LIMIT = 60;
// Only kinds a name can't settle. Bills, airtime, cash and loans come from
// clear words in the name, so they are never sent.
const UNSURE = new Set(["people", "shops", "other"]);
const round = (n) => Math.round(n);
const median = (xs) => {
  const s = [...xs].sort((a, b) => a - b);
  const m = s.length >> 1;
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
};

/** A name safe to send: no runs of digits or masked digits, no till prefix. */
export function safeName(name) {
  return String(name || "")
    .replace(/^\s*\d+\s*-\s*/, "")        // "123 - KALDIS COFFEE"
    .replace(/[\d*#+]{3,}/g, "")          // phones, accounts, masked numbers
    .replace(/\(\s*\)/g, "")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 60);
}

/**
 * The payees to send, biggest spend first. [kinds] is the user's picked
 * kinds (Map key -> kind); those payees and habit-mapped ones are left out.
 */
export function aiPayload(txs, { rules = [], kinds = null, salaryKeys = [], limit = AI_LIMIT } = {}) {
  const rows = spendingRows(txs, { salaryKeys });
  const by = new Map();
  for (const r of rows) {
    if (r.key === "unnamed" || !UNSURE.has(r.kind) || (kinds && kinds.has(r.key))) continue;
    if (rules.length && ruleFor(r.tx.counterparty, rules)) continue;
    if (!by.has(r.key)) by.set(r.key, { key: r.key, names: new Map(), kind: r.kind, total: 0, amounts: [], hours: [], weekdays: 0 });
    const e = by.get(r.key);
    e.names.set(r.name, (e.names.get(r.name) || 0) + 1);
    e.total += r.amount; e.amounts.push(r.amount);
    const d = new Date(r.ms);
    e.hours.push(d.getHours());
    if (d.getDay() !== 0 && d.getDay() !== 6) e.weekdays += 1;
  }
  return [...by.values()]
    .map((e) => ({
      key: e.key,
      name: safeName([...e.names.entries()].sort((a, b) => b[1] - a[1])[0][0]),
      count: e.amounts.length,
      usual: round(median(e.amounts)),
      hour: round(median(e.hours)),
      weekdays: round((100 * e.weekdays) / e.amounts.length),
      kind: e.kind,
      total: e.total,
    }))
    .filter((p) => p.name)
    .sort((a, b) => b.total - a.total)
    .slice(0, limit)
    .map(({ total, ...p }, i) => ({ id: `p${i + 1}`, ...p }));
}

/** Exactly what goes over the wire: an opaque id instead of Kash's key. */
export function aiRequest(items) {
  return items.map(({ id, name, count, usual, hour, weekdays, kind }) => ({ id, name, count, usual, hour, weekdays, kind }));
}

/**
 * Checks Gemini's answer against what was sent (aiPayload items). Keeps one
 * suggestion per sent id, only known kinds, a short habit name (or none) and a short reason.
 * Drops suggestions that change nothing.
 */
export function readSuggestions(answer, sent) {
  const byId = new Map(sent.map((p) => [p.id, p]));
  const seen = new Set();
  const out = [];
  for (const s of Array.isArray(answer) ? answer : []) {
    if (!s || typeof s !== "object") continue;
    const p = byId.get(String(s.id || ""));
    if (!p || seen.has(p.key)) continue;
    const kind = PICKABLE_KINDS.includes(s.kind) ? s.kind : null;
    let habit = typeof s.habit === "string" ? s.habit.trim().replace(/\s+/g, " ").slice(0, 40) : "";
    if (habit) habit = SUGGESTED_HABITS.find((h) => h.toLowerCase() === habit.toLowerCase()) || habit[0].toUpperCase() + habit.slice(1);
    if (!kind && !habit) continue;
    if (kind === p.kind && !habit) continue;
    seen.add(p.key);
    out.push({
      key: p.key,
      name: p.name,
      count: p.count,
      from: p.kind,
      kind: kind || p.kind,
      habit: habit || null,
      why: typeof s.why === "string" ? s.why.trim().slice(0, 160) : "",
    });
  }
  return out;
}

/** The payee-kind map from the user's saved decisions (i_review_decisions rows). */
export function kindsFrom(rows) {
  const m = new Map();
  for (const r of rows || []) {
    if (r.kind === "payee_kind" && r.decision === "yes" && PICKABLE_KINDS.includes(r.detail?.kind)) m.set(r.ref, r.detail.kind);
  }
  return m;
}
