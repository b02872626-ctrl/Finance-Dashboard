// The Habits tab: draws what Web/habits.js works out. No numbers are made
// here; every figure can be opened to the transactions behind it.
// app.js calls renderHabits(root, ctx) whenever the tab is shown, the data
// changes or the calendar switches.
//
// ctx = {
//   txs, cal, salaryKeys,
//   rules: [{ match, habit, shared }],
//   status: "ready" | "loading" | "missing" | "error",
//   setRule(key, habit, shared), removeRule(key),
//   decisions: Map(ref → { decision, detail }) for pass-throughs,
//   salaryNo: [payer keys the user said aren't salary],
//   reviewStatus: "ready" | "loading" | "missing" | "error",
//   decidePassThrough(item, "yes" | "no" | null), addSalary(candidate), rejectSalary(key),
//   kinds: Map(payee key → kind the user picked), setKind(key, kind | null, from),
//   ai: { consent: true | false | null, status: "idle" | "asking" | "done" | "error", error, suggestions },
//   setAiConsent(on), askGemini(items), aiAccept(suggestion, shared), aiSkip(suggestion)
// }

import {
  SUGGESTED_HABITS, byHour, dateLabel, explainChains, explainGroups, explainHours,
  explainMonthly, explainPayday, groupPayments, habitChains, habitPayments, habitTotals,
  monthlyHabits, payees, paydayEffect, paydaysFrom, ruleFor,
  monthOf, displayName,
} from "./habits.js";
import { passThroughReview, salaryByMonth, salaryCandidates, salaryPayments } from "./flows.js";
import {
  KINDS, explainByKind, explainFlow, explainSalary, explainSmall, explainTop, kindLabel, moneyFlow,
  PICKABLE_KINDS, salaryVsSpending, smallPayments, spendingByKind, spendingRows, topPayees,
} from "./money.js";
import { AI_LIMIT, aiPayload } from "./ai.js";

// Categorical slots, fixed order (blue, orange, aqua, yellow, magenta, green,
// violet, red); colour follows the habit, by the order it was first mapped.
const SLOTS = 8;
const SHARED_GUESS = /food|lunch|breakfast|dinner|coffee|tea|drink|snack|macchiato|juice/i;

const ui = {
  filter: "", showAll: false, month: null, hour: null, editing: null, salaryAll: false,
  flowMonth: null, kindMonth: null, salaryMonth: null, payee: null, smallOpen: false, flowOpen: false,
  aiList: false,
};
// Kinds have their own fixed colours; "Other" is grey.
const KIND_IDS = KINDS.map((k) => k.id);
const kindColor = (id) => (id === "other" ? "var(--hb-other)" : `var(--hb-${KIND_IDS.indexOf(id) + 1})`);
let flowDraw = null; // the money flow drawn after the HTML is in place (it needs the card's width)
let lastRoot = null;
if (typeof window !== "undefined") {
  let t = null;
  window.addEventListener("resize", () => { clearTimeout(t); t = setTimeout(() => lastRoot && drawFlow(lastRoot), 150); });
}

const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const etb = (n) => `ETB ${Math.round(n).toLocaleString("en-US")}`;
const hourLabel = (h) => (h === 0 ? "12am" : h === 12 ? "12pm" : h < 12 ? `${h}am` : `${h - 12}pm`);

function habitOrder(rules) {
  const seen = [];
  for (const r of rules) if (!seen.includes(r.habit)) seen.push(r.habit);
  return seen;
}
const colorOf = (order, habit) => {
  const i = order.indexOf(habit);
  return i >= 0 && i < SLOTS ? `var(--hb-${i + 1})` : "var(--hb-other)";
};

export function renderHabits(root, ctx) {
  if (!root) return;
  if (ctx.status === "missing") {
    root.innerHTML = `<div class="card"><h3>Habits</h3>
      <p class="muted">Habits aren't switched on for this account yet. (Owner: run <code>supabase/add_habit_rules.sql</code> on Supabase.)</p></div>`;
    return;
  }
  const rules = ctx.rules || [];
  const order = habitOrder(rules);
  const pays = habitPayments(ctx.txs, rules, { salaryKeys: ctx.salaryKeys });

  root.innerHTML = `
    <div class="card hb-intro">
      <h3>Your habits</h3>
      <p class="muted">Tell Kash which people and places are which habit (lunch, coffee, games…). Kash then shows how much each habit costs you, when it happens, what comes with it, and how much friends pay you back. Every number opens to the transactions behind it.</p>
    </div>
    ${renderPassThroughs(ctx)}
    ${renderMoney(ctx)}
    ${renderSalary(ctx)}
    ${renderPicker(ctx, rules, order)}
    ${pays.length ? renderInsights(ctx, pays, order) : `<div class="card"><p class="muted">Map a few people or places above to see your habits.</p></div>`}`;
  wire(root, ctx, rules);
}

// ── Money that passed through you ───────────────────────────────────────────
function passItem(item, cal, actions) {
  return `<div class="hb-pass">
    <div class="hb-pass-lines">
      <p><strong>${esc(dateLabel(Date.parse(item.in.occurred_at), cal, { time: true }))}</strong></p>
      <p class="hb-in">+<span class="hb-amt">${etb(Number(item.in.amount))}</span> from ${esc(displayName(item.in.counterparty))}</p>
      ${item.outs.map((o) => `<p class="hb-out">−<span class="hb-amt">${etb(Number(o.amount))}</span> to ${esc(displayName(o.counterparty))} <span class="muted small">${esc(dateLabel(Date.parse(o.occurred_at), cal, { time: true }))}</span></p>`).join("")}
    </div>
    <div class="hb-pass-actions">${actions}</div>
  </div>`;
}

function renderPassThroughs(ctx) {
  if (ctx.reviewStatus === "missing") {
    return `<div class="card"><h3>Money that passed through you</h3>
      <p class="muted">Not switched on for this account yet. (Owner: run <code>supabase/add_review_decisions.sql</code> on Supabase.)</p></div>`;
  }
  const r = passThroughReview(ctx.txs, ctx.decisions || new Map(), { salaryKeys: ctx.salaryKeys });
  if (!r.toReview.length && !r.confirmed.length && !r.rejected.length) return "";
  const sum = (xs) => xs.reduce((s, x) => s + Number(x.in.amount), 0);
  const btn = (key, d, label, cls = "") => `<button type="button" class="btn-ghost hb-btn ${cls}" data-hb="pass" data-key="${esc(key)}" data-d="${d}">${label}</button>`;
  return `<div class="card">
    <header class="section-header"><h3>Money that passed through you</h3><span class="muted small">${r.toReview.length ? `${r.toReview.length} to check` : "All checked"}</span></header>
    <p class="hb-explain">Money that came in and went straight out again, usually for someone else. It isn't your income or your spending. ${r.confirmed.length ? `You confirmed ${r.confirmed.length} (<span class="hb-amt">${etb(sum(r.confirmed))}</span>); they're left out of every total on the dashboard.` : "Confirm the ones that weren't yours and Kash leaves them out of every total on the dashboard."}</p>
    <p class="muted small">Kash looks for ETB 5,000 or more coming in, then payments of ETB 5,000 or more going out within 36 hours that add up to 90–110% of it.</p>
    ${r.toReview.map((item) => passItem(item, ctx.cal, btn(item.key, "yes", "Not mine, passed through", "primary") + btn(item.key, "no", "It was mine"))).join("")}
    ${r.confirmed.length ? `<details class="hb-details"><summary>Confirmed (${r.confirmed.length})</summary>
      ${r.confirmed.map((item) => passItem(item, ctx.cal, btn(item.key, "undo", "Undo"))).join("")}</details>` : ""}
    ${r.rejected.length ? `<details class="hb-details"><summary>Yours, counted as normal (${r.rejected.length})</summary>
      ${r.rejected.map((item) => passItem(item, ctx.cal, btn(item.key, "undo", "Undo"))).join("")}</details>` : ""}
  </div>`;
}

// ── Salary ──────────────────────────────────────────────────────────────────
function renderSalary(ctx) {
  const cal = ctx.cal;
  const pays = salaryPayments(ctx.txs, ctx.salaryKeys);
  const candidates = ctx.reviewStatus === "missing" ? [] : salaryCandidates(ctx.txs, { cal, picked: ctx.salaryKeys, rejected: ctx.salaryNo || [] });
  if (!pays.length && !candidates.length) return "";
  const months = salaryByMonth(pays, (ms) => monthOf(ms, cal));
  const max = Math.max(...months.map((m) => m.total), 1);
  const total = pays.reduce((s, p) => s + p.amount, 0);
  const shown = ui.salaryAll ? [...pays].reverse() : [...pays].reverse().slice(0, 8);
  const dayWord = (d) => (cal === "ethiopian" ? `around day ${d} of the month` : `around the ${d}${["th", "st", "nd", "rd"][(d % 100 > 10 && d % 100 < 14) || d % 10 > 3 ? 0 : d % 10]}`);
  return `<div class="card">
    <header class="section-header"><h3>Salary</h3><span class="muted small">Payers are picked on the Dashboard's Payday card</span></header>
    ${pays.length ? `
      <p class="hb-explain">${pays.filter((p) => !p.back).length} salary payments came in, <span class="hb-amt">${etb(total)}</span> in all${pays.some((p) => p.back) ? ", after taking off what you sent back to your payers" : ""}.</p>
      <div class="hb-salary-months">${months.map((m) => `
        <div class="hb-salary-month"><span class="muted small">${esc(m.label)}</span>
          <div class="hb-hbar"><span style="width:${Math.max(0, (100 * m.total) / max)}%;background:var(--hb-6)"></span><em class="hb-amt">${etb(m.total)}</em></div></div>`).join("")}
      </div>
      <div class="hb-table-wrap"><table class="tx-table hb-table">
        <thead><tr><th>Date</th><th>From</th><th class="right">Amount</th><th>Note</th></tr></thead>
        <tbody>${shown.map((p) => `<tr><td>${esc(dateLabel(p.ms, cal))}</td><td>${esc(p.from)}</td>
          <td class="right">${p.amount < 0 ? "−" : "+"}${etb(Math.abs(p.amount))}</td><td class="muted small">${p.back ? "Sent back" : ""}</td></tr>`).join("")}</tbody>
      </table></div>
      ${pays.length > 8 ? `<button type="button" class="ghost-btn hb-more" data-hb="salary-all">${ui.salaryAll ? "Show fewer" : `Show all ${pays.length}`}</button>` : ""}`
    : `<p class="muted">You haven't picked who pays your salary yet.</p>`}
    ${candidates.length ? `
      <p class="hb-sub"><strong>${pays.length ? "Anyone else paying you a salary?" : "Is one of these your salary?"}</strong></p>
      ${candidates.slice(0, 5).map((c) => `<div class="hb-payee">
        <div class="hb-payee-name"><div><strong>${esc(c.name)}</strong>
          <p class="muted small">${c.count} payments · usually <span class="hb-amt">${etb(c.usual)}</span> · ${c.cadence === "monthly" ? dayWord(c.usualDay) : "every 2 weeks"}</p></div></div>
        <div class="hb-pass-actions">
          <button type="button" class="btn-ghost hb-btn primary" data-hb="salary-yes" data-key="${esc(c.key)}">Yes, salary</button>
          <button type="button" class="btn-ghost hb-btn" data-hb="salary-no" data-key="${esc(c.key)}">No</button>
        </div></div>`).join("")}` : ""}
  </div>`;
}

// ── Where your money goes ───────────────────────────────────────────────────
const shortMonth = (label) => label.replace(/ (\d{4})$/, (_, y) => ` ’${y.slice(2)}`);
const kindLegend = (present) => `<div class="hb-legend">${KINDS.filter((k) => present.includes(k.id)).map((k) =>
  `<span><i style="background:${kindColor(k.id)}"></i>${esc(k.label)}</span>`).join("")}</div>`;

function moneyTable(title, rows, cal) {
  const sorted = [...rows].sort((a, b) => b.ms - a.ms);
  return `<div class="hb-sources">
    <p><strong>${esc(title)}</strong></p>
    <div class="hb-table-wrap"><table class="tx-table hb-table">
      <thead><tr><th>Date</th><th>To</th><th>Kind</th><th class="right">Amount</th></tr></thead>
      <tbody>${sorted.slice(0, 200).map((r) => `<tr><td>${esc(dateLabel(r.ms, cal, { time: true }))}</td><td>${esc(r.name)}</td>
        <td><i class="hb-key" style="background:${kindColor(r.kind)}"></i>${esc(kindLabel(r.kind))}</td><td class="right">${etb(r.amount)}</td></tr>`).join("")}</tbody>
    </table></div>
    ${sorted.length > 200 ? `<p class="muted small">Showing the latest 200 of ${sorted.length}.</p>` : ""}
  </div>`;
}

function renderMoney(ctx) {
  const cal = ctx.cal;
  const rows = spendingRows(ctx.txs, { rules: ctx.rules || [], salaryKeys: ctx.salaryKeys, kinds: ctx.kinds });
  if (!rows.length) return "";
  const byKind = spendingByKind(rows, ctx.txs, { cal });
  const months = byKind.months;
  const full = months.filter((m) => !m.partial);
  if (!ui.flowMonth || !months.some((m) => m.key === ui.flowMonth)) ui.flowMonth = (full[full.length - 1] || months[months.length - 1]).key;
  const fm = months.find((m) => m.key === ui.flowMonth);
  const flow = moneyFlow(ctx.txs, rows, { cal, key: fm.key, salaryKeys: ctx.salaryKeys });
  flowDraw = { flow };
  const sal = salaryVsSpending(ctx.txs, byKind, { cal, salaryKeys: ctx.salaryKeys });
  const top = topPayees(rows, { cal, now: Date.now(), n: 10 });
  const small = smallPayments(rows, ctx.txs, { cal });
  return `
    <div class="card">
      <header class="section-header"><h3>Where your money went</h3>
        <select class="hb-select" data-hb="flow-month" aria-label="Month">${[...months].reverse().map((m) =>
          `<option value="${m.key}" ${m.key === fm.key ? "selected" : ""}>${esc(m.label)}${m.partial ? " (partial)" : ""}</option>`).join("")}</select></header>
      <p class="hb-explain">${esc(explainFlow(flow, fm.label))}</p>
      <div class="hb-flow" data-hb="flow"></div>
      <p class="muted small">Left: what came in. Right: where it went. Moves between your own accounts and money that passed through you aren't counted.</p>
      <button type="button" class="ghost-btn hb-more" data-hb="flow-open">${ui.flowOpen ? "Hide the payments" : `See all ${flow.sources.out.length} payments`}</button>
      ${ui.flowOpen ? moneyTable(`${fm.label}: everything that went out`, flow.sources.out, cal) : ""}
    </div>
    ${kindCard(byKind, cal)}
    ${salaryGoesCard(sal, ctx, cal)}
    ${topCard(top, cal, ctx)}
    ${aiCard(ctx)}
    ${smallCard(small, cal)}`;
}

function kindCard(b, cal) {
  const present = KIND_IDS.filter((k) => b.totals[k] > 0);
  const max = Math.max(...b.months.map((m) => m.total), 1);
  const sel = b.months.find((m) => m.key === ui.kindMonth);
  return `<div class="card">
    <header class="section-header"><h3>Spending by kind</h3><span class="muted small">Each month</span></header>
    <p class="hb-explain">${esc(explainByKind(b))}</p>
    ${kindLegend(present)}
    <div class="hb-bars hb-months">${b.months.map((m) => stackedBar(m.byKind, m.total, max, KIND_IDS,
      `data-hb="kind-month" data-key="${m.key}" ${m.partial ? 'data-partial="1"' : ""} aria-pressed="${m.key === ui.kindMonth}"`,
      esc(shortMonth(m.label)) + (m.partial ? "*" : ""),
      `${m.label}${m.partial ? " (partial month)" : ""}: ${etb(m.total)}\n` + present.filter((k) => m.byKind[k]).map((k) => `${kindLabel(k)} ${etb(m.byKind[k])}`).join("\n"),
      kindColor)).join("")}
    </div>
    <div class="hb-kind-totals">${present.sort((x, y) => b.totals[y] - b.totals[x]).map((k) => `
      <div><i class="hb-key" style="background:${kindColor(k)}"></i>${esc(kindLabel(k))} <span class="muted small">${Math.round((100 * b.totals[k]) / b.all)}%${b.average[k] ? ` · <span class="hb-amt">${etb(b.average[k])}</span> a month` : ""}</span></div>`).join("")}
    </div>
    <p class="muted small">* Partial month: left out of the monthly figures. Kash sorts each payment by the name on it: habits you mapped first, then bills, airtime, cash, loans, shops and people. Tap a month to check.</p>
    ${sel ? moneyTable(`${sel.label}: ${sel.sources.length} payments`, sel.sources, cal) : ""}
  </div>`;
}

function salaryGoesCard(sal, ctx, cal) {
  if (!ctx.salaryKeys.length) {
    return `<div class="card"><header class="section-header"><h3>Where your salary goes</h3></header>
      <p class="muted">Pick who pays your salary (below, or on the Dashboard's Payday card) to compare each month's spending with it.</p></div>`;
  }
  if (!sal.length) return "";
  const present = KIND_IDS.filter((k) => sal.some((m) => m.byKind[k]));
  const max = Math.max(...sal.map((m) => Math.max(m.total, m.salary)), 1);
  const sel = sal.find((m) => m.key === ui.salaryMonth);
  return `<div class="card">
    <header class="section-header"><h3>Where your salary goes</h3><span class="muted small">Spending by kind, line = salary</span></header>
    <p class="hb-explain">${esc(explainSalary(sal))}</p>
    ${kindLegend(present)}<div class="hb-legend"><span><i class="hb-marker-key"></i>Salary that month</span></div>
    <div class="hb-bars hb-months hb-with-marker">${sal.map((m) => stackedBar(m.byKind, m.total, max, KIND_IDS,
      `data-hb="salary-month" data-key="${m.key}" ${m.partial ? 'data-partial="1"' : ""} aria-pressed="${m.key === ui.salaryMonth}"`,
      esc(shortMonth(m.label)) + (m.partial ? "*" : ""),
      `${m.label}: salary ${etb(m.salary)}, spent ${etb(m.total)} (${Math.round((100 * m.total) / (m.salary || 1))}%)`,
      kindColor, m.salary)).join("")}
    </div>
    ${sel ? moneyTable(`${sel.label}: salary ${etb(sel.salary)}, ${sel.sources.length} payments`, sel.sources, cal) : ""}
  </div>`;
}

function sparkline(values) {
  const w = 84, h = 26, max = Math.max(...values, 1);
  const pts = values.map((v, i) => `${(i * w) / (values.length - 1 || 1)},${h - 2 - (v / max) * (h - 4)}`).join(" ");
  return `<svg class="hb-spark" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}" aria-hidden="true"><polyline points="${pts}" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/></svg>`;
}

function topCard(t, cal, ctx) {
  if (!t.rows.length) return "";
  const max = t.rows[0].share || 1;
  const sel = t.rows.find((r) => r.key === ui.payee);
  return `<div class="card">
    <header class="section-header"><h3>Who you pay most</h3><span class="muted small">Share of all spending · last 6 months</span></header>
    <p class="hb-explain">${esc(explainTop(t))}</p>
    <div class="hb-top">${t.rows.map((r) => `
      <button type="button" class="hb-top-row" data-hb="payee" data-key="${esc(r.key)}" aria-pressed="${r.key === ui.payee}">
        <span class="hb-top-name"><strong><i class="hb-key" style="background:${kindColor(r.kind)}"></i>${esc(r.name)}</strong>
          <span class="muted small">${esc(kindLabel(r.kind))} · ${r.count} payments · <span class="hb-amt">${etb(r.total)}</span></span></span>
        <span class="hb-top-share"><span class="hb-hbar"><span style="width:${(100 * r.share) / max}%;background:${kindColor(r.kind)}"></span><em>${r.share}%</em></span></span>
        <span class="hb-top-trend" title="Last 6 months: ${r.trend.map((v) => etb(v)).join(", ")}">${sparkline(r.trend)}</span>
      </button>`).join("")}
    </div>
    ${sel ? kindPicker(sel, ctx) + moneyTable(`${sel.name}: ${sel.count} payments`, sel.sources, cal) : ""}
  </div>`;
}

// Put a payee in another kind by hand (saved per user, i_review_decisions).
function kindPicker(row, ctx) {
  if (row.kind === "habits") return `<p class="hb-kind-pick muted small">Mapped to a habit. Change it under "Who is which habit".</p>`;
  if (!ctx.setKind || ctx.reviewStatus !== "ready") return "";
  const picked = ctx.kinds?.has(row.key);
  return `<div class="hb-kind-pick">
    <label for="hb-kind-${esc(row.key)}">${esc(row.name)} is</label>
    <select class="hb-select" id="hb-kind-${esc(row.key)}" data-hb="set-kind" data-key="${esc(row.key)}">
      ${PICKABLE_KINDS.map((k) => `<option value="${k}" ${k === row.kind ? "selected" : ""}>${esc(kindLabel(k))}</option>`).join("")}
    </select>
    ${picked ? `<button type="button" class="btn-ghost hb-btn" data-hb="reset-kind" data-key="${esc(row.key)}">Use Kash's guess</button>` : `<span class="muted small">Kash's guess</span>`}
  </div>`;
}

// Opt-in: Gemini suggests a kind (and maybe a habit) for payees Kash can't
// tell apart by name. Shows exactly what would be sent before anything is.
function aiCard(ctx) {
  if (!ctx.ai || ctx.reviewStatus !== "ready") return "";
  const ai = ctx.ai;
  const items = aiPayload(ctx.txs, { rules: ctx.rules || [], kinds: ctx.kinds, salaryKeys: ctx.salaryKeys });
  const sentList = `<div class="hb-table-wrap"><table class="tx-table hb-table">
      <thead><tr><th>Name sent</th><th class="right">Payments</th><th class="right">Usual</th><th class="right">Usual time</th><th class="right">Weekdays</th></tr></thead>
      <tbody>${items.map((p) => `<tr><td>${esc(p.name)}</td><td class="right">${p.count}</td><td class="right"><span class="hb-amt">${etb(p.usual)}</span></td><td class="right">${hourLabel(p.hour)}</td><td class="right">${p.weekdays}%</td></tr>`).join("")}</tbody>
    </table></div>`;
  const what = `<ul class="hb-ai-what">
      <li><strong>Sent to Google's Gemini:</strong> the names of up to ${AI_LIMIT} people and places Kash isn't sure about (digits removed), how many times you paid each, the usual amount, the usual time of day and how often it's on a weekday.</li>
      <li><strong>Never sent:</strong> account or phone numbers, balances, dates, your SMS, or who you are.</li>
      <li>Nothing changes until you accept a suggestion. You can turn this off any time.</li>
    </ul>
    <button type="button" class="ghost-btn hb-more" data-hb="ai-list">${ui.aiList ? "Hide the list" : `See exactly what would be sent (${items.length})`}</button>
    ${ui.aiList ? sentList : ""}`;
  const head = `<header class="section-header"><h3>Sort my payees with Gemini</h3><span class="muted small">Optional</span></header>`;

  if (ai.consent !== true) {
    return `<div class="card hb-ai">${head}
      <p class="hb-explain">Kash sorts payees by their name, so a café with a person's name lands in People. Gemini can look at how you pay each one (amount, time, how often) and suggest a better kind, and a habit when it looks like one.</p>
      ${what}
      <div class="hb-pass-actions"><button type="button" class="btn-ghost hb-btn primary" data-hb="ai-on" ${items.length ? "" : "disabled"}>Turn on and sort ${items.length} payees</button></div>
    </div>`;
  }

  const pending = ai.suggestions || [];
  const status = ai.status === "asking" ? `<p class="muted">Asking Gemini about ${items.length} payees…</p>`
    : ai.status === "error" ? `<p class="hb-error">${esc(ai.error)}</p>` : "";
  const list = ai.status === "done" && !pending.length
    ? `<p class="muted">Nothing left to look at. ${items.length ? "Ask again after you've paid new people or places." : ""}</p>`
    : pending.length ? `<div class="hb-ai-list">${pending.map((x, i) => `
      <div class="hb-ai-row">
        <div><strong>${esc(x.name)}</strong> <span class="muted small">${x.count} payments</span>
          <p class="small">${x.kind !== x.from ? `${esc(kindLabel(x.from))} → <i class="hb-key" style="background:${kindColor(x.kind)}"></i><strong>${esc(kindLabel(x.kind))}</strong>` : esc(kindLabel(x.kind))}${x.habit ? ` · habit: <strong>${esc(x.habit)}</strong>` : ""}</p>
          ${x.why ? `<p class="muted small">${esc(x.why)}</p>` : ""}</div>
        <div class="hb-pass-actions"><button type="button" class="btn-ghost hb-btn primary" data-hb="ai-accept" data-i="${i}">Accept</button>
          <button type="button" class="btn-ghost hb-btn" data-hb="ai-skip" data-i="${i}">Skip</button></div>
      </div>`).join("")}</div>
      <div class="hb-pass-actions"><button type="button" class="btn-ghost hb-btn" data-hb="ai-accept-all">Accept all ${pending.length}</button></div>` : "";
  return `<div class="card hb-ai">${head}
    <p class="hb-explain">${items.length ? `${items.length} payees you haven't sorted yet. Gemini suggests; you decide.` : "Every payee you pay is sorted."}</p>
    ${status}${list}
    <div class="hb-pass-actions">
      ${items.length && ai.status !== "asking" ? `<button type="button" class="btn-ghost hb-btn${pending.length ? "" : " primary"}" data-hb="ai-ask">${ai.status === "done" ? "Ask again" : `Sort ${items.length} payees`}</button>` : ""}
      <button type="button" class="btn-ghost hb-btn" data-hb="ai-off">Turn off</button>
    </div>
    <details class="hb-ai-details"><summary class="small">What Gemini sees</summary>${what}</details>
  </div>`;
}

function smallCard(s, cal) {
  if (!s.count) return "";
  return `<div class="card">
    <header class="section-header"><h3>Small things add up</h3><span class="muted small">Payments under ${etb(s.limit)}</span></header>
    <p class="hb-explain">${esc(explainSmall(s))}</p>
    <div class="hb-stats">
      ${s.perMonth ? `<div><p class="big-number">${s.perMonth.count}</p><p class="muted small">small payments a month</p></div>
      <div><p class="big-number">${etb(s.perMonth.total)}</p><p class="muted small">on them a month</p></div>` : ""}
      <div><p class="big-number">${s.share}%</p><p class="muted small">of your spending, ${s.countShare}% of your payments</p></div>
    </div>
    ${s.top.length ? `<p class="hb-sub"><strong>Where most of them go</strong></p>
      ${s.top.map((x) => `<p>${esc(x.name)} <span class="muted small">${x.count} payments · <span class="hb-amt">${etb(x.total)}</span></span></p>`).join("")}` : ""}
    <button type="button" class="ghost-btn hb-more" data-hb="small-open">${ui.smallOpen ? "Hide them" : `See all ${s.count}`}</button>
    ${ui.smallOpen ? moneyTable(`Payments under ${etb(s.limit)}`, s.sources, cal) : ""}
  </div>`;
}

// The money flow: what came in on the left, where it went on the right,
// bands as wide as the money. Drawn at the card's real width so the labels
// stay readable on a phone.
function drawFlow(root) {
  const box = root.querySelector('[data-hb="flow"]');
  if (!box || !flowDraw) return;
  const f = flowDraw.flow;
  const W = Math.max(280, box.clientWidth || 600);
  const narrow = W < 480; // a phone: shorter labels, no % on the right
  const left = [
    { id: "salary", label: "Salary", value: f.salary },
    { id: "other-in", label: narrow ? "Other in" : "Other money in", value: f.other },
    { id: "savings", label: narrow ? "Savings" : "From savings", value: f.fromSavings },
  ].filter((n) => n.value > 0);
  const right = [
    ...KINDS.map((k) => ({ id: k.id, label: k.label, value: f.byKind[k.id] || 0 })).filter((n) => n.value > 0).sort((a, b) => b.value - a.value),
    ...(f.kept > 0 ? [{ id: "kept", label: "Kept", value: f.kept }] : []),
  ];
  const total = left.reduce((s, n) => s + n.value, 0);
  if (!total) { box.innerHTML = `<p class="muted">Nothing came in or went out this month.</p>`; return; }
  const gap = 10, nodeW = 10, rowMin = 34;
  const H = Math.max(200, right.length * rowMin + 16, left.length * 60);
  const scale = (H - gap * (Math.max(left.length, right.length) - 1)) / total;
  const lw = narrow ? W * 0.26 : Math.min(120, W * 0.27), rw = narrow ? W * 0.38 : Math.min(150, W * 0.36);
  const x0 = lw, x1 = W - rw - nodeW;
  const place = (nodes) => {
    const used = nodes.reduce((s, n) => s + n.value * scale, 0) + gap * (nodes.length - 1);
    let y = (H - used) / 2;
    for (const n of nodes) { n.y = y; n.h = Math.max(1.5, n.value * scale); y += n.h + gap; n.off = 0; }
  };
  place(left); place(right);
  // Labels sit by their node but never closer than 32px; pushed down, then
  // back up if they run past the bottom.
  const spread = (nodes) => {
    let prev = -Infinity;
    for (const n of nodes) { n.ly = Math.max(n.y + n.h / 2, prev + 32); prev = n.ly; }
    const over = prev + 16 - (H + 8);
    if (over > 0) for (const n of nodes) n.ly -= over;
    for (let i = nodes.length - 2; i >= 0; i--) nodes[i].ly = Math.min(nodes[i].ly, nodes[i + 1].ly - 32);
  };
  spread(left); spread(right);
  const all = [...left, ...right];
  const pad = Math.max(0, 16 - Math.min(...all.map((n) => n.ly)));
  const bottom = Math.max(H + 8, ...all.map((n) => n.ly + 20)) + pad;
  const color = (id) => (id === "kept" ? "var(--hb-compare)" : kindColor(id));
  let bands = "";
  for (const a of left) for (const b of right) {
    const v = (a.value * b.value) / total;
    const t = v * scale;
    if (t < 0.3) continue;
    const ya = a.y + a.off, yb = b.y + b.off;
    a.off += t; b.off += t;
    const xa = x0 + nodeW, mid = (xa + x1) / 2;
    bands += `<path d="M${xa},${ya} C${mid},${ya} ${mid},${yb} ${x1},${yb} L${x1},${yb + t} C${mid},${yb + t} ${mid},${ya + t} ${xa},${ya + t} Z" fill="${color(b.id)}" opacity="0.35"><title>${esc(a.label)} → ${esc(b.label)}: ${etb(v)}</title></path>`;
  }
  const lNodes = left.map((n) => `<rect x="${x0}" y="${n.y}" width="${nodeW}" height="${n.h}" rx="2" fill="var(--k-ink-muted)"/>
    <text x="${x0 - 8}" y="${n.ly - 2}" text-anchor="end" class="hb-flow-label">${esc(n.label)}</text>
    <text x="${x0 - 8}" y="${n.ly + 13}" text-anchor="end" class="hb-flow-value hb-amt">${etb(n.value)}</text>`).join("");
  const rNodes = right.map((n) => `<rect x="${x1}" y="${n.y}" width="${nodeW}" height="${n.h}" rx="2" fill="${color(n.id)}"/>
    <text x="${x1 + nodeW + 8}" y="${n.ly - 2}" class="hb-flow-label">${esc(n.label)}</text>
    <text x="${x1 + nodeW + 8}" y="${n.ly + 13}" class="hb-flow-value hb-amt">${etb(n.value)}${narrow ? "" : ` · ${Math.round((100 * n.value) / total)}%`}</text>`).join("");
  box.innerHTML = `<svg viewBox="0 0 ${W} ${bottom}" width="${W}" height="${bottom}" role="img" aria-label="Money in and where it went"><g transform="translate(0 ${pad})">${bands}${lNodes}${rNodes}</g></svg>`;
}

// ── Who is which habit ──────────────────────────────────────────────────────
function renderPicker(ctx, rules, order) {
  const list = payees(ctx.txs, { limit: 300 });
  const mapped = new Map(rules.map((r) => [r.match, r]));
  const q = ui.filter.trim().toLowerCase();
  const rows = list
    .map((p) => ({ ...p, rule: mapped.get(p.key) || ruleFor(p.name, rules) }))
    .filter((p) => !q || p.key.includes(q) || p.spellings.some((s) => s.toLowerCase().includes(q)) || (p.rule && p.rule.habit.toLowerCase().includes(q)));
  const shown = ui.showAll || q ? rows : rows.slice(0, 12);
  const habits = [...new Set([...order, ...SUGGESTED_HABITS])];
  const summary = order.length
    ? order.map((h) => {
      const n = rules.filter((r) => r.habit === h).length;
      return `<span class="hb-chip"><i style="background:${colorOf(order, h)}"></i>${esc(h)} <span class="muted">${n}</span></span>`;
    }).join("")
    : `<span class="muted small">Nothing mapped yet.</span>`;
  return `<div class="card">
    <header class="section-header"><h3>Who is which habit</h3><span class="muted small">${rules.length} mapped</span></header>
    <div class="hb-chips">${summary}</div>
    <input class="hb-search" type="search" placeholder="Search people, places or habits" value="${esc(ui.filter)}" data-hb="filter" />
    <datalist id="hb-habits">${habits.map((h) => `<option value="${esc(h)}"></option>`).join("")}</datalist>
    <div class="hb-payees">
      ${shown.map((p) => payeeRow(p, order)).join("") || `<p class="muted small">No one matches.</p>`}
    </div>
    ${!q && rows.length > 12 ? `<button type="button" class="ghost-btn hb-more" data-hb="more">${ui.showAll ? "Show fewer" : `Show all ${rows.length}`}</button>` : ""}
    <p class="muted small">Different spellings of one name are grouped. A habit you type is saved as you leave the box; clear it to unmap.</p>
  </div>`;
}

function payeeRow(p, order) {
  const r = p.rule;
  const viaOther = r && r.match !== p.key;
  return `<div class="hb-payee${r ? " mapped" : ""}">
    <div class="hb-payee-name">
      <span class="hb-dot" style="background:${r ? colorOf(order, r.habit) : "transparent"}"></span>
      <div><strong>${esc(p.name)}</strong>
      <p class="muted small">${p.count} payments · usually <span class="hb-amt">${etb(p.usual)}</span> · around ${hourLabel(p.usualHour)}${p.spellings.length > 1 ? ` · ${p.spellings.length} spellings` : ""}</p></div>
    </div>
    <div class="hb-payee-ctl">
      ${viaOther ? `<span class="muted small">${esc(r.habit)} (via “${esc(r.match)}”)</span>` : `
      <input class="hb-habit-input" list="hb-habits" placeholder="Not a habit" value="${esc(r?.habit || "")}" data-hb="habit" data-key="${esc(p.key)}" aria-label="Habit for ${esc(p.name)}" />
      ${r ? `<label class="hb-shared"><input type="checkbox" data-hb="shared" data-key="${esc(p.key)}" ${r.shared ? "checked" : ""}/> Friends pay me back here</label>` : ""}`}
    </div>
  </div>`;
}

// ── Insights ────────────────────────────────────────────────────────────────
function renderInsights(ctx, pays, order) {
  const cal = ctx.cal;
  const m = monthlyHabits(pays, ctx.txs, { cal });
  const totals = habitTotals(pays);
  const g = groupPayments(pays);
  const hours = byHour(pays);
  const chains = habitChains(pays);
  const pd = paydayEffect(pays, paydaysFrom(ctx.txs, ctx.salaryKeys));
  return `
    <section class="hb-tiles">${totals.map((t) => `
      <div class="card hb-tile">
        <p class="label"><i class="hb-key" style="background:${colorOf(order, t.habit)}"></i>${esc(t.habit)}</p>
        <p class="big-number">${m.average[t.habit] != null ? etb(m.average[t.habit]) : etb(t.mine)}</p>
        <p class="muted small">${m.average[t.habit] != null ? "a month, your share" : "so far, your share"} · ${t.count} payments${t.back ? ` · friends paid back <span class="hb-amt">${etb(t.back)}</span>` : ""}</p>
      </div>`).join("")}
    </section>
    ${monthlyCard(m, order, cal)}
    ${groupsCard(g, cal)}
    ${hoursCard(hours, order, cal)}
    ${chainsCard(chains, order)}
    ${paydayCard(pd, ctx, cal)}`;
}

function legend(order, present) {
  return `<div class="hb-legend">${order.filter((h) => present.includes(h)).map((h) =>
    `<span><i style="background:${colorOf(order, h)}"></i>${esc(h)}</span>`).join("")}</div>`;
}

function stackedBar(byHabit, total, max, order, attrs, label, title, color = (h) => colorOf(order, h), marker = null) {
  const segs = order.filter((h) => byHabit[h] > 0).reverse()
    .map((h) => `<span style="height:${(100 * byHabit[h]) / (total || 1)}%;background:${color(h)}"></span>`).join("");
  return `<button type="button" class="hb-col" ${attrs} title="${esc(title)}">
    ${marker != null ? `<span class="hb-marker" style="bottom:calc(34px + (100% - 34px) * ${max ? Math.min(1, marker / max) : 0})"></span>` : ""}
    <span class="hb-stack" style="height:${max ? (100 * total) / max : 0}%">${segs}</span>
    <span class="hb-col-label">${label}</span></button>`;
}

function monthlyCard(m, order, cal) {
  const max = Math.max(...m.months.map((x) => x.total), 1);
  const sel = m.months.find((x) => x.key === ui.month);
  return `<div class="card">
    <header class="section-header"><h3>Habits by month</h3><span class="muted small">Your share, after paybacks</span></header>
    <p class="hb-explain">${esc(explainMonthly(m))}</p>
    ${legend(order, m.habits)}
    <div class="hb-bars hb-months">${m.months.map((x) => stackedBar(x.byHabit, x.total, max, order,
      `data-hb="month" data-key="${x.key}" ${x.partial ? 'data-partial="1"' : ""} aria-pressed="${x.key === ui.month}"`,
      esc(x.label.replace(/ (\d{4})$/, (_, y) => ` ’${y.slice(2)}`)) + (x.partial ? "*" : ""),
      `${x.label}${x.partial ? " (partial month)" : ""}: ${etb(x.total)}\n` + m.habits.filter((h) => x.byHabit[h]).map((h) => `${h} ${etb(x.byHabit[h])}`).join("\n"))).join("")}
    </div>
    <p class="muted small">* Partial month (first or last in your data${cal === "ethiopian" ? ", or Pagume" : ""}): left out of the monthly averages. Tap a month to see its payments.</p>
    ${sel ? sourcesTable(`${sel.label}: ${sel.sources.length} payments`, sel.sources, cal) : ""}
  </div>`;
}

function groupsCard(g, cal) {
  if (!g.rows.length) {
    return `<div class="card"><header class="section-header"><h3>Paying for the group</h3></header>
      <p class="muted">No paybacks found yet. Tick “Friends pay me back here” on the places where you pay for everyone.</p></div>`;
  }
  return `<div class="card">
    <header class="section-header"><h3>Paying for the group</h3><span class="muted small">${g.rows.length} payments</span></header>
    <p class="hb-explain">${esc(explainGroups(g))}</p>
    <p class="muted small">Kash matches by timing only: money under ETB 1,000 from a person, the same day and within 2.5 hours after you paid. Open a row to check.</p>
    <div class="hb-table-wrap"><table class="tx-table hb-table">
      <thead><tr><th>Date</th><th>Place</th><th class="right">Paid</th><th class="right">Paid back</th><th class="right">Your share</th></tr></thead>
      <tbody>${g.rows.slice(0, 150).map((p) => `
        <tr class="hb-group-row" data-hb="toggle">
          <td>${esc(dateLabel(p.ms, cal, { time: true }))}</td><td>${esc(p.place)}</td>
          <td class="right">${etb(p.amount)}</td><td class="right">${etb(p.back)}</td>
          <td class="right">${p.mine < 0 ? `<span class="hb-flag" title="More came back than you paid: one of these may be for something else">check</span> ` : ""}${etb(p.mine)}</td>
        </tr>
        <tr class="hb-paybacks hidden"><td colspan="5">
          <span>${esc(dateLabel(p.ms, cal, { time: true }).slice(-5))}: −${etb(p.amount)} to ${esc(p.place)}</span>
          ${p.paybacks.map((b) => `<span>${esc(dateLabel(b.ms, cal, { time: true }).slice(-5))}: +${etb(b.amount)} from ${esc(b.from)}</span>`).join("")}
        </td></tr>`).join("")}
      </tbody></table></div>
    ${g.rows.length > 150 ? `<p class="muted small">Showing the latest 150.</p>` : ""}
  </div>`;
}

function hoursCard(hours, order, cal) {
  const max = Math.max(...hours.map((h) => h.total), 1);
  const present = order.filter((h) => hours.some((x) => x.byHabit[h]));
  const sel = ui.hour != null ? hours[ui.hour] : null;
  return `<div class="card">
    <header class="section-header"><h3>Time of day</h3><span class="muted small">Your share, whole period</span></header>
    <p class="hb-explain">${esc(explainHours(hours))}</p>
    ${legend(order, present)}
    <div class="hb-bars hb-hours">${hours.map((h) => stackedBar(h.byHabit, h.total, max, order,
      `data-hb="hour" data-key="${h.hour}" aria-pressed="${h.hour === ui.hour}"`,
      h.hour % 3 === 0 ? hourLabel(h.hour) : "",
      `${hourLabel(h.hour)}–${hourLabel((h.hour + 1) % 24)}: ${etb(h.total)}\n` + present.filter((x) => h.byHabit[x]).map((x) => `${x} ${etb(h.byHabit[x])}`).join("\n"))).join("")}
    </div>
    <p class="muted small">Tap an hour to see its payments.</p>
    ${sel ? sourcesTable(`${hourLabel(sel.hour)}–${hourLabel((sel.hour + 1) % 24)}: ${sel.sources.length} payments`, sel.sources, cal) : ""}
  </div>`;
}

function chainsCard(chains, order) {
  return `<div class="card">
    <header class="section-header"><h3>Habits that come together</h3><span class="muted small">Share of days</span></header>
    <p class="hb-explain">${esc(explainChains(chains))}</p>
    ${chains.map((c) => `
      <div class="hb-chain">
        <p><strong>${esc(c.then)}</strong> on days with ${esc(c.first)}</p>
        <div class="hb-hbar"><span style="width:${c.with}%;background:${colorOf(order, c.then)}"></span><em>${c.with}%</em></div>
        <p class="muted small">vs other days</p>
        <div class="hb-hbar"><span style="width:${c.without}%;background:var(--hb-compare)"></span><em>${c.without}%</em></div>
        <p class="muted small">${c.daysWith} days with ${esc(c.first)}, ${c.daysWithout} days with other habits but not ${esc(c.first)}.</p>
      </div>`).join("")}
  </div>`;
}

function paydayCard(pd, ctx, cal) {
  if (!ctx.salaryKeys.length) {
    return `<div class="card"><header class="section-header"><h3>Payday effect</h3></header>
      <p class="muted">Pick who pays your salary on the Dashboard's Payday card, and Kash compares your habits in the week after payday with the rest of the month.</p></div>`;
  }
  if (!pd) return "";
  const max = Math.max(pd.after.perDay, pd.other.perDay, 1);
  return `<div class="card">
    <header class="section-header"><h3>Payday effect</h3><span class="muted small">Habit money paid out a day, before paybacks</span></header>
    <p class="hb-explain">${esc(explainPayday(pd))}</p>
    <div class="hb-chain">
      <p>7 days from each payday <span class="muted small">(${pd.after.days} days)</span></p>
      <div class="hb-hbar"><span style="width:${(100 * pd.after.perDay) / max}%;background:var(--hb-1)"></span><em class="hb-amt">${etb(pd.after.perDay)}</em></div>
      <p>Other days <span class="muted small">(${pd.other.days} days)</span></p>
      <div class="hb-hbar"><span style="width:${(100 * pd.other.perDay) / max}%;background:var(--hb-compare)"></span><em class="hb-amt">${etb(pd.other.perDay)}</em></div>
    </div>
    <details class="hb-details"><summary class="muted small">Paydays used (${pd.paydays.length})</summary>
      <p class="muted small">${pd.paydays.map((d) => esc(dateLabel(d, cal))).join(" · ")}</p></details>
  </div>`;
}

function sourcesTable(title, rows, cal) {
  const sorted = [...rows].sort((a, b) => b.ms - a.ms);
  return `<div class="hb-sources">
    <p><strong>${esc(title)}</strong></p>
    <div class="hb-table-wrap"><table class="tx-table hb-table">
      <thead><tr><th>Date</th><th>Place</th><th>Habit</th><th class="right">Paid</th><th class="right">Paid back</th><th class="right">Your share</th></tr></thead>
      <tbody>${sorted.slice(0, 200).map((p) => `<tr>
        <td>${esc(dateLabel(p.ms, cal, { time: true }))}</td><td>${esc(p.place)}</td><td>${esc(p.habit)}</td>
        <td class="right">${etb(p.amount)}</td><td class="right">${p.back ? etb(p.back) : "—"}</td><td class="right">${etb(p.mine)}</td></tr>`).join("")}
      </tbody></table></div>
    ${sorted.length > 200 ? `<p class="muted small">Showing the latest 200.</p>` : ""}
  </div>`;
}

// ── Events ──────────────────────────────────────────────────────────────────
function wire(root, ctx, rules) {
  const rerender = () => renderHabits(root, ctx);
  lastRoot = root;
  drawFlow(root);
  root.querySelector('[data-hb="flow-month"]')?.addEventListener("change", (e) => { ui.flowMonth = e.target.value; ui.flowOpen = false; rerender(); });
  root.querySelector('[data-hb="flow-open"]')?.addEventListener("click", () => { ui.flowOpen = !ui.flowOpen; rerender(); });
  root.querySelector('[data-hb="small-open"]')?.addEventListener("click", () => { ui.smallOpen = !ui.smallOpen; rerender(); });
  const toggle = (sel, field, num = false) => root.querySelectorAll(`[data-hb="${sel}"]`).forEach((b) => b.addEventListener("click", () => {
    const v = num ? Number(b.dataset.key) : b.dataset.key;
    ui[field] = ui[field] === v ? null : v; rerender();
  }));
  toggle("kind-month", "kindMonth");
  toggle("salary-month", "salaryMonth");
  toggle("payee", "payee");
  const filter = root.querySelector('[data-hb="filter"]');
  filter?.addEventListener("input", () => {
    ui.filter = filter.value;
    const at = filter.selectionStart;
    rerender();
    const again = root.querySelector('[data-hb="filter"]');
    again.focus();
    again.setSelectionRange(at, at);
  });
  root.querySelectorAll('[data-hb="habit"]').forEach((input) => {
    input.addEventListener("change", () => {
      const key = input.dataset.key;
      const habit = input.value.trim().slice(0, 40);
      const existing = rules.find((r) => r.match === key);
      if (!habit) { if (existing) ctx.removeRule(key); return; }
      if (existing && existing.habit === habit) return;
      // A habit already in use keeps its "pay me back" choice; a new one guesses from its name.
      const same = rules.find((r) => r.habit === habit);
      ctx.setRule(key, habit, same ? same.shared : SHARED_GUESS.test(habit));
    });
  });
  root.querySelectorAll('[data-hb="shared"]').forEach((box) => {
    box.addEventListener("change", () => {
      const r = rules.find((x) => x.match === box.dataset.key);
      if (r) ctx.setRule(r.match, r.habit, box.checked);
    });
  });
  root.querySelectorAll('[data-hb="pass"]').forEach((b) => b.addEventListener("click", () => {
    const r = passThroughReview(ctx.txs, ctx.decisions || new Map(), { salaryKeys: ctx.salaryKeys });
    const item = [...r.toReview, ...r.confirmed, ...r.rejected].find((x) => x.key === b.dataset.key);
    if (item) ctx.decidePassThrough(item, b.dataset.d === "undo" ? null : b.dataset.d);
  }));
  root.querySelectorAll('[data-hb="salary-yes"]').forEach((b) => b.addEventListener("click", () => {
    const c = salaryCandidates(ctx.txs, { cal: ctx.cal, picked: ctx.salaryKeys, rejected: ctx.salaryNo || [] }).find((x) => x.key === b.dataset.key);
    if (c) ctx.addSalary(c);
  }));
  root.querySelectorAll('[data-hb="salary-no"]').forEach((b) => b.addEventListener("click", () => ctx.rejectSalary(b.dataset.key)));
  root.querySelector('[data-hb="salary-all"]')?.addEventListener("click", () => { ui.salaryAll = !ui.salaryAll; rerender(); });
  root.querySelector('[data-hb="more"]')?.addEventListener("click", () => { ui.showAll = !ui.showAll; rerender(); });
  root.querySelectorAll('[data-hb="month"]').forEach((b) => b.addEventListener("click", () => {
    ui.month = ui.month === b.dataset.key ? null : b.dataset.key; rerender();
  }));
  root.querySelectorAll('[data-hb="hour"]').forEach((b) => b.addEventListener("click", () => {
    const h = Number(b.dataset.key);
    ui.hour = ui.hour === h ? null : h; rerender();
  }));
  root.querySelectorAll('[data-hb="toggle"]').forEach((tr) => tr.addEventListener("click", () => {
    tr.nextElementSibling?.classList.toggle("hidden");
  }));
  // Payee kinds and Gemini.
  root.querySelector('[data-hb="set-kind"]')?.addEventListener("change", (e) => ctx.setKind(e.target.dataset.key, e.target.value, "you"));
  root.querySelector('[data-hb="reset-kind"]')?.addEventListener("click", (e) => ctx.setKind(e.currentTarget.dataset.key, null));
  root.querySelector('[data-hb="ai-list"]')?.addEventListener("click", () => { ui.aiList = !ui.aiList; rerender(); });
  const items = () => aiPayload(ctx.txs, { rules: ctx.rules || [], kinds: ctx.kinds, salaryKeys: ctx.salaryKeys });
  root.querySelector('[data-hb="ai-on"]')?.addEventListener("click", async () => {
    ui.aiList = false;
    if (await ctx.setAiConsent(true)) ctx.askGemini(items());
  });
  root.querySelector('[data-hb="ai-off"]')?.addEventListener("click", () => ctx.setAiConsent(false));
  root.querySelector('[data-hb="ai-ask"]')?.addEventListener("click", () => ctx.askGemini(items()));
  // A habit already in use keeps its "pay me back" choice; a new one guesses from its name.
  const sharedFor = (habit) => { const same = rules.find((r) => r.habit === habit); return same ? same.shared : SHARED_GUESS.test(habit || ""); };
  const pending = () => ctx.ai?.suggestions || [];
  root.querySelectorAll('[data-hb="ai-accept"]').forEach((b) => b.addEventListener("click", () => {
    const x = pending()[Number(b.dataset.i)];
    if (x) ctx.aiAccept([x], sharedFor);
  }));
  root.querySelectorAll('[data-hb="ai-skip"]').forEach((b) => b.addEventListener("click", () => {
    const x = pending()[Number(b.dataset.i)];
    if (x) ctx.aiSkip(x);
  }));
  root.querySelector('[data-hb="ai-accept-all"]')?.addEventListener("click", () => ctx.aiAccept([...pending()], sharedFor));
}
