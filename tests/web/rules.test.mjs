// Tests for Web/rules.js. Run: TZ=Africa/Addis_Ababa node --test tests/web/rules.test.mjs
// Made-up names and amounts only (the repo is public).
import test from "node:test";
import assert from "node:assert/strict";
import {
  cleanRule, explainLoan, explainOthers, loanBook, markRules, paidForOthers, rulesFrom, suggestRules,
} from "../../Web/rules.js";
import { isIncome, isSpending } from "../../Web/insights.js";
import { habitPayments } from "../../Web/habits.js";
import { spendingByKind, spendingRows, topPayees } from "../../Web/money.js";

const at = (y, m, d, h = 12, min = 0) => new Date(y, m - 1, d, h, min).getTime();
let id = 1200;
const out = (ms, amount, who, extra = {}) => ({
  id: ++id, occurred_at: new Date(ms).toISOString(), amount, type: "TRANSFER_OUT", counterparty: who, ...extra,
});
const inn = (ms, amount, who, extra = {}) => out(ms, amount, who, { type: "CREDIT", ...extra });

test("saved rules are checked; declined ones are remembered", () => {
  assert.deepEqual(cleanRule({ type: "mine", n: "2", usual: 1299.6 }), { type: "mine", n: 2, usual: 1300 });
  assert.equal(cleanRule({ type: "mine", n: 0, usual: 100 }), null);
  assert.equal(cleanRule({ type: "loan", above: -5 }), null);
  assert.equal(cleanRule({ type: "rocket" }), null);
  const { rules, declined } = rulesFrom([
    { kind: "payee_rule", ref: "girma tad", decision: "yes", detail: { type: "loan", above: 1000 } },
    { kind: "payee_rule", ref: "abel wor", decision: "no", detail: {} },
    { kind: "payee_rule", ref: "bad one", decision: "yes", detail: { type: "nope" } },
    { kind: "salary", ref: "acme", decision: "no", detail: {} },
  ]);
  assert.deepEqual([...rules.keys()], ["girma tad"]);
  assert.deepEqual([...declined], ["abel wor"]);
});

// Football: one payment a month to Abel Worku is the user's own (~1,300).
const FOOTBALL = () => [
  out(at(2026, 10, 3), 1300, "Abel Worku"),
  out(at(2026, 10, 3, 13), 1300, "Abel Worku"),
  out(at(2026, 10, 10), 650, "Abel Worku"),
  out(at(2026, 11, 2), 1250, "Abel Worku"),
  out(at(2026, 11, 9), 2600, "Abel Worku"),
];

test("only N a month are mine: the closest to the usual amount, the rest is paid for others", () => {
  const txs = FOOTBALL();
  const rules = new Map([["abel wor", { type: "mine", n: 1, usual: 1300 }]]);
  assert.equal(markRules(txs, rules, { cal: "gregorian" }), 3);
  assert.deepEqual(txs.map((t) => !!t.for_others), [false, true, true, false, true]); // a tie goes to the earlier one
  assert.ok(txs.every(isSpending)); // the money still left
  const habit = habitPayments(txs, [{ match: "abel wor", habit: "Sport" }]);
  assert.deepEqual(habit.map((p) => p.amount), [1300, 1250]);
  const rows = spendingRows(txs);
  assert.deepEqual(rows.map((r) => r.kind), ["people", "others", "others", "people", "others"]);
  assert.equal(topPayees(rows, { cal: "gregorian", now: at(2026, 11, 20) }).rows[0].kind, "people");
  const o = paidForOthers(txs, { cal: "gregorian" });
  assert.equal(o.total, 4550);
  assert.deepEqual(o.months.map((m) => [m.key, m.total]), [["2026-10", 1950], ["2026-11", 2600]]);
  assert.match(explainOthers(o), /^You've paid ETB 4,550 for other people through Abel Worku/);
  // Taking the rule away clears the marks.
  assert.equal(markRules(txs, new Map()), 0);
  assert.ok(txs.every((t) => !t.for_others));
});

test("loan or bill share: big amounts are loans, small ones bill shares", () => {
  const txs = [
    inn(at(2026, 10, 1), 5000, "Girma Tadesse"),   // borrowed
    out(at(2026, 10, 20), 3000, "Girma Tadesse"),  // repaid
    out(at(2026, 10, 21), 400, "Girma Tadesse"),   // my bill share: spending
    inn(at(2026, 10, 22), 300, "Girma Tadesse"),   // his share back: off spending
    inn(at(2026, 10, 23), 900, "Sara Bekele"),     // no rule: income
  ];
  const rules = new Map([["girma tad", { type: "loan", above: 1000 }]]);
  markRules(txs, rules);
  assert.deepEqual(txs.map((t) => [!!t.friend_loan, !!t.bill_back]), [[true, false], [true, false], [false, false], [false, true], [false, false]]);
  assert.deepEqual(txs.map(isIncome), [false, false, false, false, true]);
  assert.equal(isSpending(txs[1]), false);
  const rows = spendingRows(txs);
  assert.deepEqual(rows.map((r) => r.amount), [400, -300]);
  assert.equal(spendingByKind(rows, txs, { cal: "gregorian" }).all, 100);
  const [g] = loanBook(txs, rules);
  assert.equal(g.balance, 2000);
  assert.equal(g.billBack, 300);
  assert.equal(explainLoan(g), "You owe Girma Tadesse ETB 2,000 (borrowed ETB 5,000, paid back ETB 3,000).");
  assert.equal(explainLoan({ ...g, balance: -1500, got: 500, gave: 2000 }), "Girma Tadesse owes you ETB 1,500 (you gave ETB 2,000, got back ETB 500).");
});

test("suggestions: two-way money for loans, a busy habit payee for 'mine'", () => {
  const txs = [
    ...FOOTBALL(), out(at(2026, 9, 5), 1300, "Abel Worku"), out(at(2026, 9, 5, 12, 30), 1300, "Abel Worku"),
    out(at(2026, 11, 9, 12, 20), 1300, "Abel Worku"),
    ...[1, 2, 3, 4].flatMap((d) => [out(at(2026, 9 + (d % 3), d), 80, "Sunrise Cafe Plc"), out(at(2026, 9 + (d % 3), d, 12, 10), 80, "Sunrise Cafe Plc")]), // café: never
    inn(at(2026, 9, 1), 5000, "Girma Tadesse"), out(at(2026, 9, 20), 5000, "Girma Tadesse"),
    out(at(2026, 9, 21), 300, "Girma Tadesse"), inn(at(2026, 9, 22), 250, "Girma Tadesse"),
    inn(at(2026, 9, 1), 30000, "Acme Trading Plc"), inn(at(2026, 10, 1), 30000, "Acme Trading Plc"),
    out(at(2026, 9, 2), 500, "Acme Trading Plc"), out(at(2026, 10, 2), 1500, "Acme Trading Plc"),
  ];
  const s = suggestRules(txs, { habitKeys: ["abel wor", "sunrise caf"], salaryKeys: ["acme trading plc"], cal: "gregorian" });
  assert.deepEqual(s.map((x) => [x.name, x.rule]), [
    ["Abel Worku", { type: "mine", n: 2, usual: 1300 }], // paid on 2 days in most months
    ["Girma Tadesse", { type: "loan", above: 1000 }],
  ]);
  assert.equal(suggestRules(txs, { habitKeys: ["abel wor"], salaryKeys: ["acme trading plc"], declined: new Set(["girma tad"]), rules: new Map([["abel wor", {}]]) }).length, 0);
});
