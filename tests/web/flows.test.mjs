// Tests for Web/flows.js. Run: TZ=Africa/Addis_Ababa node --test tests/web/flows.test.mjs
// Made-up names and amounts only (the repo is public).
import test from "node:test";
import assert from "node:assert/strict";
import {
  findPassThroughs, isPayer, markPassThroughs, passThroughReview, salaryByMonth,
  salaryCandidates, salaryPayments,
} from "../../Web/flows.js";
import { isIncome, isSpending, spendOverview } from "../../Web/insights.js";
import { habitPayments, monthOf } from "../../Web/habits.js";

const at = (y, m, d, h = 12, min = 0) => new Date(y, m - 1, d, h, min).getTime();
let id = 100;
const out = (ms, amount, who, extra = {}) => ({
  id: ++id, occurred_at: new Date(ms).toISOString(), amount, type: "TRANSFER_OUT", counterparty: who, ...extra,
});
const inn = (ms, amount, who, extra = {}) => out(ms, amount, who, { type: "CREDIT", ...extra });

// ── Pass-throughs ───────────────────────────────────────────────────────────
test("money in that goes straight out again is a pass-through", () => {
  const txs = [
    inn(at(2026, 9, 2, 15), 40000, "Girma Tadesse"),
    out(at(2026, 9, 2, 18), 2000, "Small Shop"),          // under 5,000: not part of it
    out(at(2026, 9, 2, 19), 38500, "Mulu Haile"),          // 96%: enough
  ];
  const [p] = findPassThroughs(txs);
  assert.equal(p.key, String(txs[0].id));
  assert.deepEqual(p.outs.map((o) => o.amount), [38500]);
  assert.equal(p.out, 38500);
});

test("pass-through: several payments add up, nothing past 36 hours or over 110%", () => {
  const txs = [
    inn(at(2026, 9, 2, 9), 20000, "Girma Tadesse"),
    out(at(2026, 9, 2, 10), 12000, "Mulu Haile"),
    out(at(2026, 9, 2, 11), 15000, "Abel Worku"),          // 27,000 > 22,000: skipped
    out(at(2026, 9, 2, 12), 7000, "Abel Worku"),           // 19,000: 95%
    inn(at(2026, 9, 10, 9), 10000, "Girma Tadesse"),
    out(at(2026, 9, 11, 22), 10000, "Mulu Haile"),         // 37h later: too late
    inn(at(2026, 9, 20, 9), 10000, "Girma Tadesse"),
    out(at(2026, 9, 20, 10), 8000, "Mulu Haile"),          // 80%: not enough
  ];
  const found = findPassThroughs(txs);
  assert.equal(found.length, 1);
  assert.deepEqual(found[0].outs.map((o) => o.amount), [12000, 7000]);
});

test("pass-through: salary, own moves and small amounts are never one; outs are used once", () => {
  const txs = [
    inn(at(2026, 9, 1, 9), 30000, "Acme Plc"),                         // salary payer
    inn(at(2026, 9, 1, 9), 30000, "Other Co", { category: "Salary" }), // tagged salary
    inn(at(2026, 9, 1, 9), 30000, "from CBE"),                         // own move
    inn(at(2026, 9, 1, 9), 4000, "Girma Tadesse"),                     // under 5,000
    out(at(2026, 9, 1, 10), 30000, "Mulu Haile"),
    out(at(2026, 9, 1, 10), 4000, "Mulu Haile"),
    inn(at(2026, 9, 5, 9), 10000, "Girma Tadesse"),
    inn(at(2026, 9, 5, 10), 10000, "Sara Bekele"),
    out(at(2026, 9, 5, 11), 10000, "Mulu Haile"),                      // only one of the two
  ];
  const found = findPassThroughs(txs, { salaryKeys: ["acme plc"] });
  assert.equal(found.length, 1);
  assert.equal(found[0].in.counterparty, "Girma Tadesse");
});

test("confirmed pass-throughs leave income and spending; rejected ones stay", () => {
  const txs = [
    inn(at(2026, 11, 2, 9), 50000, "Girma Tadesse"),
    out(at(2026, 11, 2, 10), 50000, "Mulu Haile"),
    inn(at(2026, 11, 3, 9), 9000, "Sara Bekele"),
    out(at(2026, 11, 3, 10), 9000, "Abel Worku"),
    out(at(2026, 11, 4, 10), 300, "Cafe"),
  ];
  const [a, b] = findPassThroughs(txs).sort((x, y) => x.in.amount - y.in.amount).reverse();
  const decisions = new Map([
    [a.key, { decision: "yes", detail: { out_ids: a.outs.map((o) => o.id) } }],
    [b.key, { decision: "no", detail: {} }],
  ]);
  assert.equal(markPassThroughs(txs, decisions), 2);
  assert.equal(isIncome(txs[0]), false);
  assert.equal(isSpending(txs[1]), false);
  assert.equal(isIncome(txs[2]), true);
  const o = spendOverview(txs, { cal: "gregorian", now: at(2026, 11, 5), preset: "month" });
  assert.equal(o.total, 9300);

  const r = passThroughReview(txs, decisions);
  assert.equal(r.toReview.length, 0);
  assert.equal(r.confirmed.length, 1);
  assert.equal(r.rejected.length, 1);

  // Undo: nothing confirmed any more, the marks come off.
  assert.equal(markPassThroughs(txs, new Map()), 0);
  assert.equal(isIncome(txs[0]), true);
});

test("a confirmed pass-through keeps its saved payments even if detection changes", () => {
  const txs = [inn(at(2026, 11, 2, 9), 50000, "Girma Tadesse"), out(at(2026, 11, 5, 10), 50000, "Mulu Haile")];
  const decisions = new Map([[String(txs[0].id), { decision: "yes", detail: { out_ids: [txs[1].id] } }]]);
  assert.equal(findPassThroughs(txs).length, 0); // 3 days apart: not found today
  const r = passThroughReview(txs, decisions);
  assert.equal(r.confirmed.length, 1);
  assert.equal(r.confirmed[0].out, 50000);
  markPassThroughs(txs, decisions);
  assert.equal(isSpending(txs[1]), false);
});

test("habits skip confirmed pass-throughs", () => {
  const txs = [inn(at(2026, 11, 2, 9), 9000, "Girma Tadesse"), out(at(2026, 11, 2, 10), 9000, "Almaz Bekele")];
  const rules = [{ match: "almaz bek", habit: "Food" }];
  assert.equal(habitPayments(txs, rules).length, 1);
  markPassThroughs(txs, new Map([[String(txs[0].id), { decision: "yes", detail: { out_ids: [txs[1].id] } }]]));
  assert.equal(habitPayments(txs, rules).length, 0);
});

// ── Salary ──────────────────────────────────────────────────────────────────
test("a picked payer matches longer and shorter spellings, never a lone first name", () => {
  assert.equal(isPayer("Abebe Kebede Alemu", ["abebe kebede"]), true);
  assert.equal(isPayer("Abebe Kebede", ["abebe kebede alemu"]), true);
  assert.equal(isPayer("Abebe Kebede (2519****1234)", ["abebe kebede"]), true);
  assert.equal(isPayer("Abebe", ["abebe kebede"]), false);
  assert.equal(isPayer("Abebe Tesfaye", ["abebe kebede"]), false);
  assert.equal(isPayer("Abebe Kebede", ["abebe"]), false);
});

test("salary: every payment from the payers, money sent back taken off, odd small credits left out", () => {
  const txs = [
    inn(at(2026, 9, 12, 9), 30000, "Abebe Kebede"),
    inn(at(2026, 10, 12, 9), 30000, "Abebe Kebede Alemu"),
    out(at(2026, 10, 13, 9), 5000, "Abebe Kebede Alemu"),   // sent back
    inn(at(2026, 10, 20, 9), 2000, "Abebe Kebede"),         // a personal transfer, not salary
    inn(at(2026, 11, 12, 9), 30000, "Abebe Kebede"),
    inn(at(2026, 11, 12, 9), 30000, "Someone Else"),
  ];
  const pays = salaryPayments(txs, ["Abebe Kebede"]);
  assert.deepEqual(pays.map((p) => p.amount), [30000, 30000, -5000, 30000]);
  assert.equal(pays[2].back, true);
  const months = salaryByMonth(pays, (ms) => monthOf(ms, "ethiopian"));
  assert.deepEqual(months.map((m) => [m.label, m.total]), [["Meskerem 2019", 30000], ["Tikimt 2019", 25000], ["Hidar 2019", 30000]]);
  assert.deepEqual(salaryPayments(txs, []), []);
});

test("salary candidates: steady amount on a steady day, or every two weeks", () => {
  const txs = [];
  for (let m = 6; m <= 10; m++) txs.push(inn(at(2026, m, 10 + (m % 2)), 25000 + m * 100, "Acme Trading Plc"));
  for (let i = 0; i < 6; i++) txs.push(inn(at(2026, 6, 3) + i * 14 * 864e5, 8000, "Lulit Desta"));
  for (let m = 6; m <= 10; m++) txs.push(inn(at(2026, m, (m * 7) % 28 + 1), 5000 * (m - 4), "Random Person")); // no pattern
  txs.push(inn(at(2026, 6, 1), 3000, "Top-up wallet"));
  const c = salaryCandidates(txs, { cal: "gregorian" });
  assert.deepEqual(c.map((x) => [x.name, x.cadence]).sort(), [["Acme Trading Plc", "monthly"], ["Lulit Desta", "every 2 weeks"]]);
  assert.equal(salaryCandidates(txs, { cal: "gregorian", picked: ["acme trading plc"], rejected: ["Lulit Desta"] }).length, 0);
});
