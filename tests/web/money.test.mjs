// Tests for Web/money.js. Run: TZ=Africa/Addis_Ababa node --test tests/web/money.test.mjs
// Made-up names and amounts only (the repo is public).
import test from "node:test";
import assert from "node:assert/strict";
import {
  explainByKind, explainFlow, explainSalary, explainSmall, explainTop, kindOf, moneyFlow,
  salaryVsSpending, smallPayments, spendingByKind, spendingRows, topPayees,
} from "../../Web/money.js";

const at = (y, m, d, h = 12) => new Date(y, m - 1, d, h, 0).getTime();
let id = 500;
const out = (ms, amount, who, extra = {}) => ({
  id: ++id, occurred_at: new Date(ms).toISOString(), amount, type: "TRANSFER_OUT", counterparty: who, ...extra,
});
const inn = (ms, amount, who, extra = {}) => out(ms, amount, who, { type: "CREDIT", ...extra });
const tx = (who, extra = {}) => out(0, 1, who, extra);

test("every payment gets one kind, first match wins", () => {
  const rules = [{ match: "sunrise caf", habit: "Coffee" }];
  assert.equal(kindOf(tx("Sunrise Cafe Plc"), rules), "habits");
  assert.equal(kindOf(tx("Sunrise Cafe Plc")), "shops");
  assert.equal(kindOf(tx("Telecom package: Daily Internet 1GB", { type: "PAYMENT" })), "airtime");
  assert.equal(kindOf(tx("Airtime")), "airtime");
  assert.equal(kindOf(tx("CBE ATM")), "cash");
  assert.equal(kindOf(tx("Acme Bank S C ATM")), "cash");
  assert.equal(kindOf(tx("Mela loan repayment")), "loans");
  assert.equal(kindOf(tx("Tomas Haile", { category: "Loan" })), "loans");
  assert.equal(kindOf(tx("123 - Ethiopian Electric Utility")), "bills");
  assert.equal(kindOf(tx("Nile Insurance SC")), "bills");
  assert.equal(kindOf(tx("456 - KALDI'S COFFEE PLC BOLE BRANCH")), "shops");
  assert.equal(kindOf(tx("Getaway Hotel")), "shops");
  assert.equal(kindOf(tx("Almaz Bekele Tadesse")), "people");
  assert.equal(kindOf(tx("almaz bekelle (2519****1111)")), "people");
  assert.equal(kindOf(tx("Abebe G/mariam W/giorgis")), "people");
  assert.equal(kindOf(tx("Somewhere", { type: "PAYMENT" })), "shops");
  assert.equal(kindOf(tx("127")), "other");
});

const MONTH_TXS = () => [
  inn(at(2026, 10, 12, 9), 30000, "Acme Trading Plc"),        // salary, Tikimt 2019
  inn(at(2026, 10, 20, 9), 5000, "Lulit Desta"),               // other income
  out(at(2026, 10, 13), 3000, "Acme Trading Plc"),             // sent back to the payer: not spending
  out(at(2026, 10, 14), 12000, "Almaz Bekele"),                // people
  out(at(2026, 10, 15), 150, "Sunrise Cafe Plc"),              // habit (mapped)
  out(at(2026, 10, 15, 16), 120, "Sunrise Cafe Plc"),
  out(at(2026, 10, 16), 2000, "CBE ATM"),                      // cash
  out(at(2026, 10, 17), 50, "Telecom package: Daily", { type: "PAYMENT" }),
  out(at(2026, 10, 18), 9000, "to Commercial Bank of Ethiopia 1****0000"), // own move: left out
  out(at(2026, 10, 19), 4000, "Kebede Shop", { pass_through: true }),     // confirmed pass-through
  out(at(2026, 9, 8), 100, "Almaz Bekele"),                    // Pagume 2018: partial
  out(at(2026, 11, 13), 100, "Almaz Bekele"),                  // Hidar 2019: last, partial
];
const RULES = [{ match: "sunrise caf", habit: "Coffee" }];
const SAL = ["acme trading plc"];

test("spending rows leave out own moves, pass-throughs and money sent back to a payer", () => {
  const rows = spendingRows(MONTH_TXS(), { rules: RULES, salaryKeys: SAL });
  assert.deepEqual(rows.map((r) => [r.kind, r.amount]), [
    ["people", 100], ["people", 12000], ["habits", 150], ["habits", 120], ["cash", 2000], ["airtime", 50], ["people", 100],
  ]);
});

test("spending by kind per month, partial months out of the averages", () => {
  const txs = MONTH_TXS();
  const rows = spendingRows(txs, { rules: RULES, salaryKeys: SAL });
  const b = spendingByKind(rows, txs, { cal: "ethiopian" });
  assert.deepEqual(b.months.map((m) => [m.label, m.partial, m.total]), [["Pagume 2018", true, 100], ["Tikimt 2019", false, 14320], ["Hidar 2019", true, 100]]);
  assert.equal(b.months[1].byKind.people, 12000);
  assert.equal(b.average.people, 12000);
  assert.equal(b.all, 14520);
  assert.match(explainByKind(b), /^People take the biggest share of your spending: 84% overall, about ETB 12,000 in a full month\.$/);
});

test("money flow: in by source, out by kind, kept or from savings", () => {
  const txs = MONTH_TXS();
  const rows = spendingRows(txs, { rules: RULES, salaryKeys: SAL });
  const f = moneyFlow(txs, rows, { cal: "ethiopian", key: "2019-02", salaryKeys: SAL });
  assert.equal(f.salary, 27000); // 30,000 less 3,000 sent back
  assert.equal(f.other, 5000);
  assert.equal(f.spent, 14320);
  assert.equal(f.kept, 17680);
  assert.equal(f.fromSavings, 0);
  assert.equal(f.sources.out.length, 5);
  assert.match(explainFlow(f, "Tikimt 2019"), /84% to people and 14% to cash out\. You kept ETB 17,680 \(55% of what came in\)\./);

  const short = moneyFlow(txs, rows, { cal: "ethiopian", key: "2018-13", salaryKeys: SAL });
  assert.equal(short.fromSavings, 100);
  assert.match(explainFlow(short, "Pagume 2018"), /ETB 100 more went out than came in/);
});

test("salary against spending; the sentence uses the last full month", () => {
  const txs = [
    inn(at(2026, 8, 12), 10000, "Acme Trading Plc"), out(at(2026, 8, 13), 12000, "Almaz Bekele"),  // Nehase: over
    inn(at(2026, 9, 12), 10000, "Acme Trading Plc"), out(at(2026, 9, 13), 6000, "Almaz Bekele"),   // Meskerem: under
    inn(at(2026, 10, 12), 10000, "Acme Trading Plc"), out(at(2026, 10, 13), 8000, "Almaz Bekele"), // Tikimt
    inn(at(2026, 11, 12), 10000, "Acme Trading Plc"), out(at(2026, 11, 13), 20000, "Almaz Bekele"), // Hidar, last: partial
  ];
  const rows = spendingRows(txs, { salaryKeys: SAL });
  const s = salaryVsSpending(txs, spendingByKind(rows, txs, { cal: "ethiopian" }), { cal: "ethiopian", salaryKeys: SAL });
  assert.deepEqual(s.map((m) => [m.label, m.salary, m.total]), [
    ["Nehase 2018", 10000, 12000], ["Meskerem 2019", 10000, 6000], ["Tikimt 2019", 10000, 8000], ["Hidar 2019", 10000, 20000],
  ]);
  assert.equal(explainSalary(s), "You spent more than your salary in 2 of 4 months (Nehase 2018, Hidar 2019). In Tikimt 2019 you spent 80% of it.");
  assert.deepEqual(salaryVsSpending(txs, spendingByKind(rows, txs, { cal: "ethiopian" }), { cal: "ethiopian", salaryKeys: [] }), []);
});

test("top payees with share and a 6-month trend", () => {
  const txs = [];
  for (let m = 5; m <= 10; m++) txs.push(out(at(2026, m, 20), 1000 * m, "Almaz Bekele"));
  txs.push(out(at(2026, 10, 21), 500, "almaz bekelle (2519****1111)"));
  txs.push(out(at(2026, 10, 22), 2000, "Getaway Hotel"));
  const rows = spendingRows(txs);
  const t = topPayees(rows, { cal: "gregorian", now: at(2026, 10, 25), n: 5 });
  assert.deepEqual(t.months, ["2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10"]);
  assert.equal(t.rows[0].name, "Almaz Bekele");
  assert.equal(t.rows[0].count, 7);
  assert.deepEqual(t.rows[0].trend, [5000, 6000, 7000, 8000, 9000, 10500]);
  assert.equal(t.rows[0].share, 96);
  assert.equal(t.rows[1].kind, "shops");
  assert.match(explainTop(t), /^Almaz Bekele, Getaway Hotel together take 100% of everything you spend\.$/);
  // Ethiopian months step back across Pagume too.
  assert.deepEqual(topPayees(rows, { cal: "ethiopian", now: at(2026, 10, 25), months: 3 }).months, ["2018-13", "2019-01", "2019-02"]);
});

test("small payments add up", () => {
  const txs = [];
  for (let d = 1; d <= 30; d++) txs.push(out(at(2026, 10, d), 50, "Sunrise Cafe Plc"));   // October: full month
  txs.push(out(at(2026, 10, 3), 8500, "Almaz Bekele"));
  txs.push(out(at(2026, 9, 3), 50, "Bunna House"));    // first month: partial
  txs.push(out(at(2026, 11, 3), 50, "Bunna House"));   // last month: partial
  const rows = spendingRows(txs);
  const s = smallPayments(rows, txs, { cal: "gregorian", limit: 200 });
  assert.equal(s.count, 32);
  assert.equal(s.total, 1600);
  assert.equal(s.share, 16);
  assert.equal(s.countShare, 97);
  assert.deepEqual(s.perMonth, { count: 30, total: 1500 });
  assert.equal(s.top[0].name, "Sunrise Cafe Plc");
  assert.equal(explainSmall(s), "97% of your payments are under ETB 200. They look small, but together they come to ETB 1,600, 16% of your spending: about 30 payments and ETB 1,500 a month.");
});
