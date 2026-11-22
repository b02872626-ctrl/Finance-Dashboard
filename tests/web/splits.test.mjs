// Tests for Web/splits.js. Run: TZ=Africa/Addis_Ababa node --test tests/web/splits.test.mjs
// Made-up names and amounts only (the repo is public).
import test from "node:test";
import assert from "node:assert/strict";
import {
  cleanParts, commitmentSummary, explainCommitment, markSplits, newCommitmentId, splitsFrom, toLabel, wholeLabel,
} from "../../Web/splits.js";
import { isIncome, isSpending } from "../../Web/insights.js";
import { habitPayments } from "../../Web/habits.js";
import { spendingByKind, spendingRows, topPayees } from "../../Web/money.js";
import { loanBook, markRules } from "../../Web/rules.js";

const at = (y, m, d, h = 12) => new Date(y, m - 1, d, h, 0).getTime();
let id = 3000;
const out = (ms, amount, who, extra = {}) => ({
  id: ++id, occurred_at: new Date(ms).toISOString(), amount, type: "TRANSFER_OUT", counterparty: who, ...extra,
});
const inn = (ms, amount, who, extra = {}) => out(ms, amount, who, { type: "CREDIT", ...extra });

const LAPTOP = { id: "laptop", name: "Laptop", key: "tomas hai", total: 100000 };

test("saved commitments and labels are read back", () => {
  const { commitments, labels } = splitsFrom([
    { kind: "commitment", ref: "laptop", decision: "yes", detail: { name: "Laptop", key: "tomas hai", total: "100000" } },
    { kind: "commitment", ref: "nameless", decision: "yes", detail: { key: "x" } },
    { kind: "tx_split", ref: "42", decision: "yes", detail: { parts: [{ what: "loan", amount: 10 }] } },
    { kind: "payee_rule", ref: "abel wor", decision: "yes", detail: {} },
  ]);
  assert.deepEqual(commitments, [LAPTOP]);
  assert.deepEqual([...labels.keys()], ["42"]);
  assert.equal(newCommitmentId("My Laptop!", ["my-laptop"]), "my-laptop-2");
});

test("parts must fit the direction and add up to the payment", () => {
  const big = out(at(2026, 10, 1), 38000, "Tomas Haile");
  const ok = [{ what: "instalment", amount: 20000, commitment: "laptop" }, { what: "loan", amount: 18000 }];
  assert.deepEqual(cleanParts(ok, big, ["laptop"]), ok);
  assert.equal(cleanParts([{ what: "instalment", amount: 20000, commitment: "laptop" }, { what: "loan", amount: 17000 }], big, ["laptop"]), null); // short
  assert.equal(cleanParts([{ what: "instalment", amount: 38000, commitment: "gone" }], big, ["laptop"]), null); // no such commitment
  assert.equal(cleanParts([{ what: "refund", amount: 38000 }], big, ["laptop"]), null); // a refund is money in
  assert.equal(cleanParts(Array(5).fill({ what: "normal", amount: 7600 }), big), null); // too many parts
  assert.deepEqual(cleanParts([{ what: "refund", amount: 2500 }], inn(at(2026, 10, 2), 2500, "Tomas Haile")), [{ what: "refund", amount: 2500 }]);
  assert.deepEqual(wholeLabel(big, "instalment", "laptop"), [{ what: "instalment", amount: 38000, commitment: "laptop" }]);
});

const TXS = () => [
  out(at(2026, 8, 12), 20000, "Tomas Haile"),             // instalment
  inn(at(2026, 8, 23), 2500, "Tomas Haile"),              // refund
  inn(at(2026, 9, 3), 18000, "Tomas Haile"),              // loan from him
  out(at(2026, 9, 24), 38000, "Tomas Haile"),             // 20,000 instalment + 18,000 loan repaid
  out(at(2026, 10, 2), 52300, "Tomas Haile"),             // passed on
  out(at(2026, 10, 11), 20000, "Tomas Haile"),            // not labelled yet
  out(at(2026, 10, 12), 300, "Sunset Kitchen"),           // someone else
];

test("labels: commitments are spending, loans and passed-on money leave the totals", () => {
  const txs = TXS();
  const labels = new Map([
    [String(txs[0].id), wholeLabel(txs[0], "instalment", "laptop")],
    [String(txs[1].id), wholeLabel(txs[1], "refund")],
    [String(txs[2].id), wholeLabel(txs[2], "loan")],
    [String(txs[3].id), [{ what: "instalment", amount: 20000, commitment: "laptop" }, { what: "loan", amount: 18000 }]],
    [String(txs[4].id), wholeLabel(txs[4], "passed")],
  ]);
  assert.equal(markSplits(txs, labels, [LAPTOP]), 5);
  // Dashboard: all-loan / passed / refund payments leave; a mixed one stays in full.
  assert.deepEqual(txs.map(isSpending), [true, false, false, true, false, true, true]);
  assert.deepEqual(txs.map(isIncome), [false, false, false, false, false, false, false]);
  // Habits tab: exact parts.
  const rows = spendingRows(txs, { rules: [] });
  assert.deepEqual(rows.map((r) => [r.kind, r.amount]), [
    ["commitments", 20000], ["people", -2500], ["commitments", 20000], ["people", 20000], ["people", 300], // "Sunset Kitchen" reads as a person by name
  ]);
  const byKind = spendingByKind(rows, txs, { cal: "gregorian" });
  assert.equal(byKind.totals.commitments, 40000);
  assert.equal(byKind.all, 40000 - 2500 + 20000 + 300);
  assert.equal(topPayees(rows, { cal: "gregorian", now: at(2026, 10, 20) }).rows[0].count, 3); // the split one counts once
  // Habits never count labelled payments.
  assert.equal(habitPayments(txs, [{ match: "tomas hai", habit: "Food" }]).length, 1);
  // Loans with friends: 18,000 borrowed, 18,000 repaid inside the split.
  const [book] = loanBook(txs, new Map());
  assert.deepEqual([book.got, book.gave, book.balance], [18000, 18000, 0]);
  // What's left to label and how the commitment stands.
  assert.deepEqual(toLabel(txs, [LAPTOP]).map((t) => t.amount), [20000]);
  const s = commitmentSummary(txs, LAPTOP, { cal: "gregorian" });
  assert.deepEqual([s.paid, s.count, s.left, s.share], [40000, 2, 60000, 40]);
  assert.deepEqual(s.months.map((m) => [m.key, m.total]), [["2026-08", 20000], ["2026-09", 20000]]);
  assert.equal(explainCommitment(s), "You've paid ETB 40,000 towards Laptop in 2 instalments: 40% of ETB 100,000, ETB 60,000 to go.");
  // Removing the labels undoes it all.
  assert.equal(markSplits(txs, new Map(), [LAPTOP]), 0);
  assert.ok(txs.every((t) => !t.split && !t.split_out));
});

test("a label replaces a per-person rule's marks", () => {
  const txs = TXS();
  markRules(txs, new Map([["tomas hai", { type: "loan", above: 1000 }]]));
  assert.ok(txs[3].friend_loan);
  markSplits(txs, new Map([[String(txs[3].id), wholeLabel(txs[3], "instalment", "laptop")]]), [LAPTOP]);
  assert.ok(!txs[3].friend_loan);
  assert.equal(isSpending(txs[3]), true);
});
