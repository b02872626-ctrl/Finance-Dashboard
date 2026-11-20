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
//   decidePassThrough(item, "yes" | "no" | null), addSalary(candidate), rejectSalary(key)
// }

import {
  SUGGESTED_HABITS, byHour, dateLabel, explainChains, explainGroups, explainHours,
  explainMonthly, explainPayday, groupPayments, habitChains, habitPayments, habitTotals,
  monthlyHabits, payees, paydayEffect, paydaysFrom, ruleFor,
  monthOf, displayName,
} from "./habits.js";
import { passThroughReview, salaryByMonth, salaryCandidates, salaryPayments } from "./flows.js";

// Categorical slots, fixed order (blue, orange, aqua, yellow, magenta, green,
// violet, red); colour follows the habit, by the order it was first mapped.
const SLOTS = 8;
const SHARED_GUESS = /food|lunch|breakfast|dinner|coffee|tea|drink|snack|macchiato|juice/i;

const ui = { filter: "", showAll: false, month: null, hour: null, editing: null, salaryAll: false };

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

function stackedBar(byHabit, total, max, order, attrs, label, title) {
  const segs = order.filter((h) => byHabit[h] > 0).reverse()
    .map((h) => `<span style="height:${(100 * byHabit[h]) / (total || 1)}%;background:${colorOf(order, h)}"></span>`).join("");
  return `<button type="button" class="hb-col" ${attrs} title="${esc(title)}">
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
}
