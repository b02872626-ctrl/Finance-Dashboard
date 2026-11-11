// Tests for Web/insights.js. Run: TZ=Africa/Addis_Ababa node --test tests/web/
// (CI runs the same; the time zone pins day boundaries.)
import test from "node:test";
import assert from "node:assert/strict";
import {
  accountSuffix, changePct, dayOfMonth, ethiopianToGregorian, gregorianToEthiopian,
  groupAccounts, inContext, isIncome, isSpending, lowBalanceDays, monthBounds,
  monthlyInOut, paydayPattern, regularIncome, spendOverview, weekStart,
} from "../../Web/insights.js";

const at = (y, m, d, h = 12) => new Date(y, m - 1, d, h, 0).getTime();
let id = 0;
const tx = (ms, amount, extra = {}) => ({
  id: ++id, occurred_at: new Date(ms).toISOString(), amount, type: "DEBIT",
  bank_name: "Commercial Bank of Ethiopia", counterparty: "Shop", ...extra,
});
const credit = (ms, amount, who = "ACME PLC", extra = {}) => tx(ms, amount, { type: "CREDIT", counterparty: who, ...extra });
const NOW = at(2026, 11, 12); // Thursday 12 Nov 2026, noon
const G = { cal: "gregorian", now: NOW };

// ── Rules and calendars ─────────────────────────────────────────────────────
test("own-account moves are neither spending nor income", () => {
  assert.equal(isSpending(tx(NOW, 10, { type: "INTERNAL_TRANSFER" })), false);
  assert.equal(isSpending(tx(NOW, 10, { category: "Own accounts" })), false);
  assert.equal(isIncome(credit(NOW, 10, "Me", { category: "Own accounts" })), false);
  assert.equal(isSpending(tx(NOW, 10, { type: "PAYMENT" })), true);
});

test("Ethiopian conversion round-trips and months have the right length", () => {
  const d = new Date(2026, 10, 12);
  const e = gregorianToEthiopian(d);
  assert.deepEqual(e, { year: 2019, month: 3, day: 3 });                 // 3 Hidar 2019
  assert.equal(ethiopianToGregorian(e.year, e.month, e.day).getTime(), d.getTime());
  const [s, en] = monthBounds(NOW, "ethiopian");
  assert.equal(Math.round((en - s) / 864e5), 30);
  const [ps, pe] = monthBounds(ethiopianToGregorian(2018, 13, 2).getTime(), "ethiopian");
  assert.equal(Math.round((pe - ps) / 864e5), 5);                          // Pagume 2018
  assert.equal(dayOfMonth(NOW, "ethiopian"), 3);
});

test("weeks start on Monday", () => {
  assert.equal(weekStart(NOW), new Date(2026, 10, 9).getTime());
  assert.equal(changePct(150, 100), 50);
  assert.equal(changePct(10, 0), null);
});

// ── Accounts ────────────────────────────────────────────────────────────────
test("Berhan's two masks are one account", () => {
  assert.equal(accountSuffix("103002XXXXX67"), "67");
  assert.equal(accountSuffix("*********9067"), "9067");
  assert.equal(accountSuffix("1000482226926"), "6926");
  const rows = [
    tx(at(2026, 11, 2), 10, { bank_name: "Berhan Bank", account_number: "*********9067", balance: 34.83 }),
    tx(at(2026, 10, 29), 10, { bank_name: "Berhan Bank", account_number: "103002XXXXX67", balance: 16083.24 }),
  ];
  const groups = groupAccounts(rows);
  assert.equal(groups.length, 1);
  assert.equal(groups[0].list[0].balance, 34.83);                         // newest first kept
});

test("two real accounts at one bank stay apart", () => {
  const rows = [
    tx(at(2026, 11, 2), 10, { account_number: "1********6155" }),
    tx(at(2026, 11, 1), 10, { account_number: "1********8506" }),
  ];
  assert.deepEqual(groupAccounts(rows).map((g) => g.name), ["CBE 1", "CBE 2"].reverse().sort());
});

// ── In context ──────────────────────────────────────────────────────────────
test("in context: ties share a rank and the month is the transaction's own", () => {
  const rows = [
    tx(at(2026, 10, 3), 500, { category: "Food" }), tx(at(2026, 10, 4), 500), tx(at(2026, 10, 5), 200, { category: "Food" }),
    tx(at(2026, 11, 3), 9000),
  ];
  const c = inContext(rows[1], rows, "gregorian");
  assert.equal(c.rank, 1);                                                // tie with the other 500
  assert.equal(c.count, 3);                                               // October only
  assert.equal(Math.round(c.pct), 42);                                    // 500 of 1200
  assert.equal(c.monthStart, new Date(2026, 9, 1).getTime());
  const food = inContext(rows[0], rows, "gregorian");
  assert.equal(Math.round(food.catPct), 71);                              // 500 of 700 Food
});

test("in context: money in is measured against money in; own moves get nothing", () => {
  const rows = [credit(at(2026, 10, 1), 3000), credit(at(2026, 10, 15), 1000, "Abebe"), tx(at(2026, 10, 2), 50000)];
  const c = inContext(rows[1], rows, "gregorian");
  assert.equal(c.kind, "in");
  assert.equal(c.rank, 2);
  assert.equal(Math.round(c.pct), 25);
  assert.equal(inContext(tx(NOW, 10, { type: "INTERNAL_TRANSFER" }), rows, "gregorian"), null);
});

test("in context follows the Ethiopian month", () => {
  // 10 Nov 2026 is 1 Hidar; 9 Nov is 30 Tikimt.
  const rows = [tx(at(2026, 11, 9), 100), tx(at(2026, 11, 10), 300)];
  assert.equal(inContext(rows[1], rows, "ethiopian").count, 1);
  assert.equal(inContext(rows[1], rows, "gregorian").count, 2);
});

// ── Overview ────────────────────────────────────────────────────────────────
test("overview: today, week and month against the same point before", () => {
  const rows = [
    tx(at(2026, 11, 12, 9), 100), tx(at(2026, 11, 12, 10), 50),          // today
    tx(at(2026, 11, 10), 200),                                             // this week
    tx(at(2026, 11, 3), 100),                                              // last week, same point
    tx(at(2026, 11, 6), 999),                                              // last week, after the point
    tx(at(2026, 10, 5), 300),                                              // last month, before the point
    tx(at(2026, 11, 14), 7),                                               // future
    credit(at(2026, 11, 11), 80),
  ];
  const o = spendOverview(rows, { ...G, preset: "month" });
  assert.equal(o.todaySpent, 150);
  assert.equal(o.todayCount, 2);
  assert.equal(o.weekSpent, 350);
  assert.equal(o.weekChangePct, 250);
  assert.equal(o.monthSpent, 150 + 200 + 100 + 999);
  assert.equal(o.days.length, 30);
  assert.equal(o.days[12], null);                                         // 13 Nov hasn't happened
  assert.equal(o.total, 1449);
});

test("overview ranges: week, 30 days and picked dates", () => {
  const rows = [tx(at(2026, 11, 9), 10), tx(at(2026, 10, 14), 5), tx(at(2026, 10, 13), 1000)];
  assert.equal(spendOverview(rows, { ...G, preset: "week" }).days.length, 7);
  const d30 = spendOverview(rows, { ...G, preset: "30d" });
  assert.equal(d30.days.length, 30);
  assert.equal(d30.total, 15);
  const custom = spendOverview(
    [tx(at(2026, 11, 3), 300), tx(at(2026, 10, 28), 100)],
    { ...G, preset: "custom", custom: [at(2026, 11, 1, 0), at(2026, 11, 8, 0)] },
  );
  assert.equal(custom.days.length, 7);
  assert.equal(custom.changePct, 200);                                    // vs 25–31 Oct
});

test("overview: payees, accounts and categories follow the range", () => {
  const rows = [
    tx(at(2026, 11, 2), 50, { counterparty: "Abebe" }), tx(at(2026, 11, 3), 70, { counterparty: "Abebe" }),
    tx(at(2026, 11, 4), 90, { counterparty: "Taxi", bank_name: "Telebirr" }), tx(at(2026, 10, 2), 900, { counterparty: "Old" }),
  ];
  const o = spendOverview(rows, { ...G, preset: "month" });
  assert.deepEqual(o.topPayees.map((p) => p.name), ["Abebe", "Taxi"]);
  assert.deepEqual(o.perAccount.map((a) => a.name), ["Commercial Bank of Ethiopia", "Telebirr"]);
  assert.deepEqual(o.categories, []);
});

// ── Money in vs out ─────────────────────────────────────────────────────────
test("in vs out by month with kept and kept %", () => {
  const rows = [credit(at(2026, 10, 1), 32000), tx(at(2026, 10, 9), 27000), tx(at(2026, 9, 9), 5000), credit(at(2026, 9, 1), 4000)];
  const m = monthlyInOut(rows, { ...G, count: 6 });
  assert.equal(m.length, 6);
  const oct = m[4];
  assert.equal(oct.start, new Date(2026, 9, 1).getTime());
  assert.deepEqual([oct.moneyIn, oct.out, oct.kept, oct.keptPct], [32000, 27000, 5000, 16]);
  assert.equal(m[3].kept, -1000);                                         // September overspent
  assert.equal(m[5].keptPct, null);                                       // November: nothing in yet
});

// ── Payday ──────────────────────────────────────────────────────────────────
const salary = [
  credit(at(2026, 8, 28), 20000), credit(at(2026, 9, 28), 21000), credit(at(2026, 10, 28), 20500),
  credit(at(2026, 10, 5), 300, "Abebe"),
];

test("regular income: same sender, similar amount, 2 of the last 3 months", () => {
  const r = regularIncome(salary, G);
  assert.equal(r.source, "ACME PLC");
  assert.equal(r.usualDay, 28);
  assert.equal(r.paydays.length, 3);
  assert.equal(regularIncome([credit(at(2026, 10, 28), 20000)], G), null);  // once isn't a pattern
});

test("payday pattern: day 70% is gone and the burn-down", () => {
  // Each cycle: 15,000 (over 70% of its paycheck) gone by day 10, then a little more.
  const spends = [];
  for (const [m, d] of [[8, 28], [9, 28]]) {
    spends.push(tx(at(2026, m, d) + 9 * 864e5, 15000), tx(at(2026, m, d) + 20 * 864e5, 1000));
  }
  spends.push(tx(at(2026, 11, 2), 5000));                                 // this cycle so far
  const p = paydayPattern([...salary, ...spends], G);
  assert.equal(p.found, true);
  assert.equal(p.usualDay, 28);
  assert.equal(p.daysTo70, 10);
  assert.equal(p.cycleLen, 31);                                          // 28 Aug→28 Sep is 31 days
  assert.equal(p.usual.length, 31);
  assert.ok(p.current.length > 0 && p.current.length <= 31);
  assert.ok(p.current.at(-1) < 1);                                        // some of it spent
  // The chart is in ETB spent: usual pace from past cycles, and this cycle.
  assert.equal(p.chart.which, "this");
  assert.equal(p.usualSpent[9], 15000);                                   // both cycles: 15k by day 10
  assert.equal(p.chart.spent.at(-1), 5000);
});

test("payday chart falls back to the last cycle while this one is new", () => {
  const spends = [tx(at(2026, 10, 30), 4000), tx(at(2026, 11, 5), 2000)];
  // 28 Nov payday, viewed on the 29th: this cycle has 2 days.
  const rows = [...salary, credit(at(2026, 11, 28), 20200), ...spends];
  const p = paydayPattern(rows, { cal: "gregorian", now: at(2026, 11, 29) });
  assert.equal(p.chart.which, "last");
  assert.equal(p.chart.start, at(2026, 10, 28));
  assert.equal(p.chart.spent[2], 4000);                                   // 30 Oct is day 3
});

test("no regular income found", () => {
  assert.deepEqual(paydayPattern([credit(at(2026, 10, 3), 500, "Random")], G), { found: false });
});

// ── Low-balance days ────────────────────────────────────────────────────────
test("low days carry the last balance forward and use the account's own floor", () => {
  // 100 ETB a day spent in November so far → floor 200.
  const rows = [
    tx(at(2026, 10, 30), 0, { balance: 1000, type: "CREDIT" }),
    tx(at(2026, 11, 3), 900, { balance: 150 }),                           // low from the 3rd
    tx(at(2026, 11, 8), 300, { balance: 450, type: "CREDIT" }),          // back up on the 8th
  ];
  const [acc] = lowBalanceDays(rows, { ...G, usualPayday: 9 });
  assert.equal(acc.floor, 2 * 900 / 12);                                  // 150
  // Floor is 150 and the balance is 150 (not under), so: no low days. Lower it:
  const [acc2] = lowBalanceDays(rows, { ...G, userFloor: 200, usualPayday: 9 });
  assert.equal(acc2.days[0].balance, 1000);                               // carried from 30 Oct
  assert.deepEqual(acc2.days.slice(2, 8).map((d) => d.low), [true, true, true, true, true, false]);
  assert.equal(acc2.lowCount, 5);
  assert.equal(acc2.beforePayday, true);                                  // 3rd–7th: the week before the 9th
  assert.equal(acc2.days.length, 12);                                     // up to today
});
