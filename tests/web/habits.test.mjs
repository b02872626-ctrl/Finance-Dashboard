// Tests for Web/habits.js. Run: TZ=Africa/Addis_Ababa node --test tests/web/
// Made-up names and amounts only (the repo is public).
import test from "node:test";
import assert from "node:assert/strict";
import {
  busiestHours, byHour, dateLabel, displayName, explainGroups, explainHours, explainPayday,
  groupPayments, habitChains, habitPayments, habitTotals, isOwnMove, monthOf, monthlyHabits,
  nameKey, payees, paydayEffect, paydaysFrom, personKey, ruleFor,
} from "../../Web/habits.js";

const at = (y, m, d, h = 12, min = 0) => new Date(y, m - 1, d, h, min).getTime();
let id = 0;
const out = (ms, amount, who, extra = {}) => ({
  id: ++id, occurred_at: new Date(ms).toISOString(), amount, type: "TRANSFER_OUT", counterparty: who, ...extra,
});
const inn = (ms, amount, who, extra = {}) => out(ms, amount, who, { type: "CREDIT", ...extra });

const RULES = [
  { match: "almaz bek", habit: "Food", shared: true },
  { match: "sunrise caf", habit: "Coffee", shared: true },
  { match: "telecom pac", habit: "Internet", shared: false },
  { match: "yonas gir", habit: "Games", shared: false },
];

// ── Names ───────────────────────────────────────────────────────────────────
test("one person under several spellings is one key", () => {
  assert.equal(personKey("Almaz Bekele"), "almaz bek");
  assert.equal(personKey("almaz bekelle"), "almaz bek");
  assert.equal(personKey("ALMAZ BEKELE TADESSE"), "almaz bek");
  assert.equal(personKey("Almaz Bekele (2519****1234)"), "almaz bek");
  assert.equal(personKey("Telecom package: Daily Internet 1GB"), "telecom pac");
  assert.equal(personKey("Sunrise"), "sunrise");
  assert.equal(nameKey("Sunrise Cafe (Abc Trading)."), "sunrise cafe");
  assert.equal(displayName("Almaz Bekele (2519****1234)"), "Almaz Bekele");
  assert.equal(displayName("almaz bekelle (0911****00)"), "almaz bekelle");
});

test("rules match by name start; the longest match wins", () => {
  const rules = [{ match: "almaz", habit: "Other" }, { match: "almaz bek", habit: "Food" }];
  assert.equal(ruleFor("Almaz Bekele Tadesse", rules).habit, "Food");
  assert.equal(ruleFor("Almaz Tesfaye", rules).habit, "Other");
  assert.equal(ruleFor("Mr Almaz Bekele", rules), null);
  assert.equal(ruleFor("", rules), null);
});

test("moves between your own accounts never count", () => {
  assert.equal(isOwnMove(out(0, 1, "to Commercial Bank of Ethiopia 1****0000")), true);
  assert.equal(isOwnMove(inn(0, 1, "from CBE")), true);
  assert.equal(isOwnMove(out(0, 1, "CBE Birr")), true);
  assert.equal(isOwnMove(out(0, 1, "CBE ATM Bole")), true);
  assert.equal(isOwnMove(out(0, 1, "Anyone", { type: "INTERNAL_TRANSFER" })), true);
  assert.equal(isOwnMove(out(0, 1, "Anyone", { category: "Own accounts" })), true);
  assert.equal(isOwnMove(out(0, 1, "Tomas Haile")), false);
});

test("payees groups spellings and gives the usual amount and hour", () => {
  const txs = [
    out(at(2026, 11, 2, 13), 300, "Almaz Bekele"),
    out(at(2026, 11, 3, 13), 320, "almaz bekelle (0911****00)"),
    out(at(2026, 11, 4, 14), 340, "Almaz Bekele"),
    out(at(2026, 11, 4, 9), 50, "Telecom package: Daily"),
    inn(at(2026, 11, 4, 9), 5000, "Almaz Bekele"),
    out(at(2026, 11, 4, 9), 900, "to Commercial Bank of Ethiopia 1****0000"),
  ];
  const [first, second] = payees(txs);
  assert.equal(first.key, "almaz bek");
  assert.equal(first.name, "Almaz Bekele");
  assert.deepEqual(first.spellings.sort(), ["Almaz Bekele", "almaz bekelle"]);
  assert.equal(first.count, 3);
  assert.equal(first.usual, 320);
  assert.equal(first.usualHour, 13);
  assert.equal(second.key, "telecom pac");
  assert.equal(payees(txs).length, 2); // no credits, no own moves
});

// ── Paybacks ────────────────────────────────────────────────────────────────
test("a group lunch with three friends paying back is the user's share", () => {
  const txs = [
    out(at(2026, 10, 6, 13, 10), 1200, "Almaz Bekele"),
    inn(at(2026, 10, 6, 13, 20), 300, "Kebede Alemu"),
    inn(at(2026, 10, 6, 13, 45), 260, "Sara Tesfaye"),
    inn(at(2026, 10, 6, 14, 30), 240, "Lulit Desta"),
  ];
  const [p] = habitPayments(txs, RULES);
  assert.equal(p.habit, "Food");
  assert.equal(p.back, 800);
  assert.equal(p.mine, 400);
  assert.deepEqual(p.paybacks.map((b) => b.from), ["Kebede Alemu", "Sara Tesfaye", "Lulit Desta"]);
});

test("paybacks: 2.5 hours, same day, under 1,000, from a person, to the latest shared payment", () => {
  const txs = [
    out(at(2026, 11, 13, 12), 600, "Almaz Bekele"),
    out(at(2026, 11, 13, 15), 200, "Sunrise Cafe"),
    inn(at(2026, 11, 13, 12, 30), 150, "Kebede Alemu"),   // → lunch
    inn(at(2026, 11, 13, 15, 10), 100, "Sara Tesfaye"),   // → coffee (latest)
    inn(at(2026, 11, 13, 17, 31), 100, "Sara Tesfaye"),   // 2h31 after coffee: too late
    inn(at(2026, 11, 13, 15, 20), 1000, "Sara Tesfaye"),  // 1,000: a loan, not a share
    inn(at(2026, 11, 13, 15, 25), 100, "Mela loan"),      // not a person
    inn(at(2026, 11, 13, 15, 26), 100, "from CBE"),       // own move
    inn(at(2026, 11, 13, 15, 27), 100, "Acme Plc", { category: "Salary" }),
    inn(at(2026, 11, 13, 15, 28), 100, "Boss Person"),    // a salary payer
    out(at(2026, 11, 13, 23, 0), 300, "Almaz Bekele"),
    inn(at(2026, 11, 14, 0, 30), 100, "Kebede Alemu"),    // next day
    out(at(2026, 11, 13, 16), 100, "Yonas Girma"),        // not shared
  ];
  const pays = habitPayments(txs, RULES, { salaryKeys: ["Boss Person"] });
  const lunch = pays.find((p) => p.amount === 600);
  const coffee = pays.find((p) => p.amount === 200);
  const late = pays.find((p) => p.amount === 300);
  assert.equal(lunch.back, 150);
  assert.equal(coffee.back, 100);
  assert.equal(late.back, 0);
  assert.equal(pays.find((p) => p.habit === "Games").back, 0);
});

test("a payback before any payment, or with no shared place, goes nowhere", () => {
  const txs = [inn(at(2026, 11, 13, 11), 100, "Kebede Alemu"), out(at(2026, 11, 13, 12), 600, "Almaz Bekele")];
  assert.equal(habitPayments(txs, RULES)[0].back, 0);
  assert.equal(habitPayments(txs, RULES.map((r) => ({ ...r, shared: false })))[0].back, 0);
});

test("totals and group payments add up", () => {
  const txs = [
    out(at(2026, 11, 13, 12), 600, "Almaz Bekele"),
    inn(at(2026, 11, 13, 12, 5), 200, "Kebede Alemu"),
    out(at(2026, 11, 14, 12), 500, "Almaz Bekele"),
    out(at(2026, 11, 14, 8), 50, "Telecom package: Daily"),
  ];
  const pays = habitPayments(txs, RULES);
  const t = habitTotals(pays);
  assert.deepEqual(t.map((x) => [x.habit, x.mine, x.paid, x.back, x.count]), [["Food", 900, 1100, 200, 2], ["Internet", 50, 50, 0, 1]]);
  const g = groupPayments(pays);
  assert.equal(g.rows.length, 1);
  assert.equal(g.paid, 600);
  assert.equal(g.back, 200);
  assert.match(explainGroups(g), /1 times you paid for a group.*ETB 600.*ETB 200 \(33%\).*ETB 400/);
});

// ── Months ──────────────────────────────────────────────────────────────────
test("Ethiopian months, with partial first, last and Pagume months", () => {
  assert.equal(monthOf(at(2026, 9, 11), "ethiopian").label, "Meskerem 2019");
  assert.equal(monthOf(at(2026, 7, 8), "ethiopian").label, "Hamle 2018");
  assert.equal(monthOf(at(2026, 9, 7), "ethiopian").label, "Pagume 2018");
  assert.equal(monthOf(at(2026, 11, 4), "gregorian").label, "November 2026");
  assert.equal(dateLabel(at(2026, 10, 6, 13, 10), "ethiopian", { time: true }), "Meskerem 26, 2019 13:10");
  assert.equal(dateLabel(at(2026, 11, 13), "gregorian"), "13 Nov 2026");

  const txs = [
    out(at(2026, 7, 20), 100, "Almaz Bekele"),   // Hamle 2018 (first: partial)
    out(at(2026, 8, 10), 200, "Almaz Bekele"),   // Nehase 2018
    out(at(2026, 8, 20), 50, "Sunrise Cafe"),    // Nehase 2018
    out(at(2026, 9, 8), 300, "Almaz Bekele"),    // Pagume 2018 (partial)
    out(at(2026, 9, 20), 400, "Almaz Bekele"),   // Meskerem 2019
    inn(at(2026, 10, 15), 9000, "Someone Else"), // Tikimt 2019: no habits, still a month
    out(at(2026, 11, 13), 999, "Almaz Bekele"),  // Hidar 2019 (last: partial)
  ];
  const m = monthlyHabits(habitPayments(txs, RULES), txs, { cal: "ethiopian" });
  assert.deepEqual(m.months.map((x) => [x.label, x.partial, x.total]), [
    ["Hamle 2018", true, 100], ["Nehase 2018", false, 250], ["Pagume 2018", true, 300],
    ["Meskerem 2019", false, 400], ["Tikimt 2019", false, 0], ["Hidar 2019", true, 999],
  ]);
  assert.deepEqual(m.habits, ["Food", "Coffee"]);
  assert.equal(m.average.Food, 200);   // (200 + 400 + 0) / 3 full months
  assert.equal(m.average.Coffee, 17);  // 50 / 3
  assert.equal(m.months[1].sources.length, 2);
});

// ── When ────────────────────────────────────────────────────────────────────
test("habit money by hour, and the busiest stretch", () => {
  const txs = [
    out(at(2026, 11, 13, 13), 600, "Almaz Bekele"),
    out(at(2026, 11, 13, 16), 300, "Sunrise Cafe"),
    out(at(2026, 11, 13, 8), 100, "Telecom package: Daily"),
  ];
  const h = byHour(habitPayments(txs, RULES));
  assert.equal(h[13].byHabit.Food, 600);
  assert.equal(h[16].byHabit.Coffee, 300);
  assert.equal(h[8].total, 100);
  assert.deepEqual(busiestHours(h), { from: 12, to: 17, share: 90 });
  assert.equal(explainHours(h), "90% of your habit money goes out between noon and 5pm.");
  assert.equal(busiestHours(byHour([])), null);
});

test("habit chains compare days with and without the first habit", () => {
  const txs = [];
  // 10 lunch days: coffee on 8 of them. 10 coffee-only... days without lunch: coffee on 2 of 10.
  for (let d = 1; d <= 10; d++) {
    txs.push(out(at(2026, 10, d, 12), 300, "Almaz Bekele"));
    if (d <= 8) txs.push(out(at(2026, 10, d, 15), 100, "Sunrise Cafe"));
  }
  for (let d = 11; d <= 20; d++) {
    txs.push(out(at(2026, 10, d, 9), 50, "Telecom package: Daily"));
    if (d <= 12) txs.push(out(at(2026, 10, d, 15), 100, "Sunrise Cafe"));
  }
  const chains = habitChains(habitPayments(txs, RULES));
  const c = chains.find((x) => x.first === "Food" && x.then === "Coffee");
  assert.deepEqual(c, { first: "Food", then: "Coffee", with: 80, without: 20, daysWith: 10, daysWithout: 10 });
  assert.ok(chains.every((x) => x.with > x.without));
});

test("payday effect: the 7 days from each payday against the other days", () => {
  const txs = [
    inn(at(2026, 10, 1, 9), 20000, "Acme Plc"),
    inn(at(2026, 10, 15, 9), 2000, "Acme Plc"),  // under a quarter of the usual: not a payday
    inn(at(2026, 10, 20, 9), 20000, "Acme Plc"),
  ];
  // Every day 1 Oct – 30 Oct: 200 a day in payday weeks, 100 otherwise.
  const pd = paydaysFrom(txs, ["acme plc"]);
  assert.equal(pd.length, 2);
  for (let d = 1; d <= 30; d++) {
    const inWeek = d <= 7 || (d >= 20 && d <= 26);
    txs.push(out(at(2026, 10, d, 13), inWeek ? 200 : 100, "Almaz Bekele"));
  }
  const p = paydayEffect(habitPayments(txs, RULES), pd);
  assert.deepEqual(p.after, { perDay: 200, days: 14 });
  assert.deepEqual(p.other, { perDay: 100, days: 16 });
  assert.match(explainPayday(p), /ETB 200 a day on habits, 100% more than the ETB 100/);
  assert.equal(paydayEffect([], pd), null);
  assert.equal(paydayEffect(habitPayments(txs, RULES), []), null);
});
