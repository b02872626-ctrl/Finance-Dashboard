// Tests for Web/ai.js and picked payee kinds in Web/money.js.
// Run: TZ=Africa/Addis_Ababa node --test tests/web/ai.test.mjs
// Made-up names and amounts only (the repo is public).
import test from "node:test";
import assert from "node:assert/strict";
import { aiPayload, aiRequest, kindsFrom, readSuggestions, safeName } from "../../Web/ai.js";
import { kindOf, spendingRows } from "../../Web/money.js";

const at = (y, m, d, h = 12) => new Date(y, m - 1, d, h, 0).getTime();
let id = 900;
const out = (ms, amount, who, extra = {}) => ({
  id: ++id, occurred_at: new Date(ms).toISOString(), amount, type: "TRANSFER_OUT", counterparty: who, ...extra,
});

test("names lose phone, account and till numbers before they leave", () => {
  assert.equal(safeName("Almaz Bekele (2519****1111)"), "Almaz Bekele");
  assert.equal(safeName("456 - KALDI'S COFFEE PLC"), "KALDI'S COFFEE PLC");
  assert.equal(safeName("Sara Tesfaye 0911223344"), "Sara Tesfaye");
  assert.equal(safeName("Acct 1000****5678 Kebede"), "Acct Kebede");
  assert.equal(safeName("1234"), "");
});

const TXS = () => [
  // Weekday lunches at Sunset Kitchen (Mon 2 Nov 2026 ...), one on a Saturday.
  out(at(2026, 11, 2, 13), 300, "Sunset Kitchen"),
  out(at(2026, 11, 3, 13), 280, "Sunset Kitchen"),
  out(at(2026, 11, 4, 12), 320, "Sunset Kitchen"),
  out(at(2026, 11, 7, 19), 300, "Sunset Kitchen"),
  out(at(2026, 11, 5, 18), 5000, "Almaz Bekele (2519****1111)"),
  out(at(2026, 11, 6, 9), 800, "Sunrise Cafe Plc"),                    // mapped to a habit: not sent
  out(at(2026, 11, 6, 10), 1500, "Kebede Alemu"),                       // kind already picked: not sent
  out(at(2026, 11, 6, 11), 9000, "to Commercial Bank of Ethiopia 1****0000"), // own move: not sent
  out(at(2026, 11, 6, 12), 2000, "Acme Trading Plc"),                   // salary payer: not sent
  out(at(2026, 11, 6, 12), 50, "127"),                                  // no name left: not sent
  out(at(2026, 11, 6, 14), 3000, "CBE ATM"),                            // cash: the name settles it, not sent
  out(at(2026, 11, 6, 15), 700, "123 - Ethiopian Electric Utility"),    // bills: not sent
];

test("payload: biggest spenders first, sorted payees left out, only safe fields sent", () => {
  const items = aiPayload(TXS(), {
    rules: [{ match: "sunrise caf", habit: "Coffee" }],
    kinds: new Map([["kebede ale", "people"]]),
    salaryKeys: ["acme trading plc"],
  });
  assert.deepEqual(items.map((p) => p.name), ["Almaz Bekele", "Sunset Kitchen"]);
  assert.deepEqual(items[1], { id: "p2", key: "sunset kit", name: "Sunset Kitchen", count: 4, usual: 300, hour: 13, weekdays: 75, kind: "people" }); // looks like a person: the case Gemini is for
  const wire = aiRequest(items);
  assert.deepEqual(Object.keys(wire[0]).sort(), ["count", "hour", "id", "kind", "name", "usual", "weekdays"]);
  for (const p of wire) assert.doesNotMatch(p.name, /\d|\*/);
  assert.equal(aiPayload(TXS(), { limit: 1 }).length, 1);
});

test("Gemini's answer is checked against what was sent", () => {
  const sent = aiPayload(TXS());
  const idOf = (name) => sent.find((p) => p.name === name).id;
  const s = readSuggestions([
    { id: idOf("Sunset Kitchen"), kind: "shops", habit: "food", why: "Lunch time on weekdays" },
    { id: idOf("Sunset Kitchen"), kind: "bills" },  // second answer for the same payee: ignored
    { id: idOf("Almaz Bekele"), kind: "people", habit: "" }, // no change: dropped
    { id: "p9", kind: "shops" },                    // never sent: dropped
    { id: idOf("Kebede Alemu"), kind: "rockets" },  // unknown kind, no habit: dropped
    { id: idOf("Kebede Alemu"), kind: "habits" },                   // habits isn't a pickable kind: dropped
    "nonsense", null,
  ], sent);
  const lunch = s.find((x) => x.name === "Sunset Kitchen");
  assert.deepEqual(lunch, { key: "sunset kit", name: "Sunset Kitchen", count: 4, from: "people", kind: "shops", habit: "Food", why: "Lunch time on weekdays" });
  assert.equal(s.length, 1);
  assert.deepEqual(readSuggestions({ not: "a list" }, sent), []);
});

test("a picked kind beats the built-in guess, but not a habit", () => {
  const kinds = kindsFrom([
    { kind: "payee_kind", ref: "almaz bek", decision: "yes", detail: { kind: "loans", from: "you" } },
    { kind: "payee_kind", ref: "sunset kit", decision: "yes", detail: { kind: "bogus" } },
    { kind: "salary", ref: "acme trading plc", decision: "no", detail: {} },
  ]);
  assert.deepEqual([...kinds], [["almaz bek", "loans"]]);
  const tx = out(0, 1, "almaz bekelle (2519****1111)");
  assert.equal(kindOf(tx), "people");
  assert.equal(kindOf(tx, [], kinds), "loans");
  assert.equal(kindOf(tx, [{ match: "almaz bek", habit: "Family" }], kinds), "habits");
  const rows = spendingRows(TXS(), { kinds });
  assert.equal(rows.find((r) => r.key === "almaz bek").kind, "loans");
});
