// =============================================================================
// Finance Lore — Web companion
//
// Single-page app that signs the user into the same Supabase project as the
// Android app and renders their finance data (accounts, balance, transactions,
// category breakdown, daily/monthly trends, top counterparties, recurring
// spend, day-of-week patterns). All requests go directly to Supabase;
// Row-Level Security in secure_schema.sql restricts each user to their rows.
// =============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// ----------------------------------------------------------------------------
// Config — same project as the Android app (local.properties).
// The anon key is safe in client because the database uses RLS on auth.uid().
// ----------------------------------------------------------------------------
const SUPABASE_URL  = "https://sadbxjnmcgzwtjbqqhbk.supabase.co";
const SUPABASE_ANON = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNhZGJ4am5tY2d6d3RqYnFxaGJrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQ2Mzg1NzcsImV4cCI6MjA5MDIxNDU3N30.aXA5I5CD--DjpnsbrHPZLebAmITNSDVmYDo1mkj3Gz4";

const supabase = createClient(SUPABASE_URL, SUPABASE_ANON, {
  auth: { persistSession: true, autoRefreshToken: true },
});

// ----------------------------------------------------------------------------
// DOM refs
// ----------------------------------------------------------------------------
const $ = (id) => document.getElementById(id);

const loginView      = $("login-view");
const dashboardView  = $("dashboard-view");
const loginForm      = $("login-form");
const loginBtn       = $("login-btn");
const retryBtn       = $("retry-btn");
const errorBox       = $("login-error");
const emailInput     = $("email");
const passwordInput  = $("password");
const userNameEl     = $("user-name");
const greetingEl     = $("greeting");
const logoutBtn      = $("logout-btn");
const refreshBtn     = $("refresh-btn");

// Stat cards
const statBalance       = $("stat-balance");
const statBalanceSub    = $("stat-balance-sub");
const statMonthSpend    = $("stat-month-spend");
const statMonthSpendSub = $("stat-month-spend-sub");
const statMonthIncome   = $("stat-month-income");
const statMonthIncomeSub= $("stat-month-income-sub");
const statNet           = $("stat-net");
const statNetSub        = $("stat-net-sub");
const statAvgDaily      = $("stat-avg-daily");
const statAvgDailySub   = $("stat-avg-daily-sub");
const statLargest       = $("stat-largest");
const statLargestSub    = $("stat-largest-sub");
const statPeriodLabel1  = $("stat-period-label-1");
const statPeriodLabel2  = $("stat-period-label-2");

// Lists / charts
const periodBar      = $("period-bar");
const accountsList   = $("accounts-list");
const accountsCount  = $("accounts-count");
const txTbody        = $("tx-tbody");
const txCount        = $("tx-count");
const txSearch       = $("tx-search");
const txTypeFilter   = $("tx-type-filter");
const csvBtn         = $("csv-btn");
const categoryList   = $("category-list");
const counterpartyList = $("counterparty-list");
const recurringList  = $("recurring-list");
const dailyChart     = $("daily-chart");
const dailyChartWin  = $("daily-chart-window");
const monthlyChart   = $("monthly-chart");
const weekdayStrip   = $("weekday-strip");
const projectRef     = $("project-ref");
const batchBar       = $("batch-bar");
const batchCount     = $("batch-count");
const batchPills     = $("batch-pills");
const batchClear     = $("batch-clear");
const batchApply     = $("batch-apply");
const toastEl        = $("toast");

// Auth shell + view tabs (dashboardView is already declared above)
const authShell      = $("auth-shell");
const viewTabs       = $("view-tabs");
const categoriesView = $("categories-view");
const ledgerView     = $("ledger-view");

// Ledger refs
const ledgerBadge       = $("ledger-badge");
const ledgerIOwe        = $("ledger-i-owe");
const ledgerIOweSub     = $("ledger-i-owe-sub");
const ledgerOwedToMe    = $("ledger-owed-to-me");
const ledgerOwedToMeSub = $("ledger-owed-to-me-sub");
const ledgerNet         = $("ledger-net");
const ledgerNetSub      = $("ledger-net-sub");
const ledgerFormToggle  = $("ledger-form-toggle");
const ledgerForm        = $("ledger-form");
const ledgerType        = $("ledger-type");
const ledgerDirection   = $("ledger-direction");
const ledgerCounterparty= $("ledger-counterparty");
const ledgerAmount      = $("ledger-amount");
const ledgerDue         = $("ledger-due");
const ledgerCadence     = $("ledger-cadence");
const ledgerCadenceField= $("ledger-cadence-field");
const ledgerNote        = $("ledger-note");
const ledgerCancel      = $("ledger-cancel");
const ledgerSave        = $("ledger-save");
const ledgerFormError   = $("ledger-form-error");
const ledgerOweList     = $("ledger-owe-list");
const ledgerLentList    = $("ledger-lent-list");
const ledgerOweCount    = $("ledger-owe-count");
const ledgerLentCount   = $("ledger-lent-count");
const ledgerClosedList  = $("ledger-closed-list");
const ledgerClosedToggle= $("ledger-closed-toggle");

// Category analysis refs
const catTotal        = $("cat-total");
const catTotalSub     = $("cat-total-sub");
const catTopName      = $("cat-top-name");
const catTopSub       = $("cat-top-sub");
const catUncategorised    = $("cat-uncategorised");
const catUncategorisedSub = $("cat-uncategorised-sub");
const catDonut        = $("cat-donut");
const catDonutLegend  = $("cat-donut-legend");
const catTrend        = $("cat-trend");
const catTrendLegend  = $("cat-trend-legend");
const catCards        = $("cat-cards");
const catDetail       = $("cat-detail");
const catDetailTitle  = $("cat-detail-title");
const catDetailClose  = $("cat-detail-close");
const catDetailTotal  = $("cat-detail-total");
const catDetailTotalSub = $("cat-detail-total-sub");
const catDetailTrend  = $("cat-detail-trend");
const catDetailTrendSub = $("cat-detail-trend-sub");
const catDetailAvg    = $("cat-detail-avg");
const catDetailAvgSub = $("cat-detail-avg-sub");
const catDetailTbody  = $("cat-detail-tbody");

// Palette for category colours (donut, legend, stack, card border)
const CAT_PALETTE = [
  "#4FB99F", "#E56B5C", "#3C6BC9", "#59C792",
  "#8A8F39", "#7BD0E5", "#D4A24C", "#B59BE0",
  "#E08AB6", "#6FAF82", "#C97A4B", "#8DA0CA",
];

projectRef.textContent = new URL(SUPABASE_URL).hostname.split(".")[0];

// ----------------------------------------------------------------------------
// State
// ----------------------------------------------------------------------------
const EXPENSE_TYPES = new Set(["DEBIT", "TRANSFER_OUT", "PAYMENT"]);

const state = {
  allTxs: [],     // raw transactions for the signed-in user (most recent first)
  period: "month",
  search: "",
  typeFilter: "",
  userId: null,
  selectedCps: new Set(),  // counterparties picked for batch categorization
  pickedCategory: null,    // chosen category for the next Apply
  view: "dashboard",       // "dashboard" | "categories" | "ledger"
  activeCatDetail: null,   // category name currently drilled into
  ledger: [],              // active + settled ledger entries
  editingId: null,         // id of the entry the form is editing, or null = new
  payingId: null,          // id of the entry whose inline pay form is open
  showClosed: false,       // whether the "Closed entries" section is expanded
};

// Defaults match TransactionCategoryCatalog.kt on Android.
const DEFAULT_CATEGORIES = [
  "Food",
  "Coffee and refreshments",
  "Bills",
  "Loan",
  "Drinks and fun",
  "Transport",
];

const PERIOD_LABELS = {
  month: "this month",
  "last-month": "last month",
  "30d": "last 30 days",
  "90d": "last 90 days",
  year: "this year",
  all: "all time",
};

// ----------------------------------------------------------------------------
// Boot
// ----------------------------------------------------------------------------
(async function init() {
  const { data: { session } } = await supabase.auth.getSession();
  if (session) await showDashboard(session);
  else showLogin();

  supabase.auth.onAuthStateChange(async (_event, session) => {
    if (session) await showDashboard(session);
    else showLogin();
  });
})();

// ----------------------------------------------------------------------------
// Login / Logout
// ----------------------------------------------------------------------------
loginForm.addEventListener("submit", async (e) => { e.preventDefault(); await attemptLogin(); });
retryBtn.addEventListener("click", () => { hideError(); retryBtn.classList.add("hidden"); passwordInput.value = ""; passwordInput.focus(); });
logoutBtn.addEventListener("click", async () => { await supabase.auth.signOut(); });
refreshBtn.addEventListener("click", async () => {
  const { data: { session } } = await supabase.auth.getSession();
  if (session) await showDashboard(session);
});

async function attemptLogin() {
  hideError();
  setLoginBusy(true);
  const email = emailInput.value.trim();
  const password = passwordInput.value;
  const { error } = await supabase.auth.signInWithPassword({ email, password });
  setLoginBusy(false);
  if (error) {
    showError(prettyAuthError(error.message));
    retryBtn.classList.remove("hidden");
  }
}

function showError(msg) { errorBox.textContent = msg; errorBox.classList.remove("hidden"); }
function hideError() { errorBox.classList.add("hidden"); }
function setLoginBusy(busy) {
  loginBtn.disabled = busy;
  loginBtn.textContent = busy ? "Signing in…" : "Sign in";
}

function prettyAuthError(raw) {
  if (!raw) return "Sign-in failed.";
  if (/invalid login credentials/i.test(raw)) return "Wrong email or password.";
  if (/email not confirmed/i.test(raw)) return "Confirm your email before signing in.";
  if (/network/i.test(raw)) return "Network error — check your connection and retry.";
  return raw;
}

// ----------------------------------------------------------------------------
// Period selector
// ----------------------------------------------------------------------------
periodBar.addEventListener("click", (e) => {
  const btn = e.target.closest(".period-pill");
  if (!btn) return;
  periodBar.querySelectorAll(".period-pill").forEach((b) => b.classList.toggle("active", b === btn));
  state.period = btn.dataset.period;
  rerender();
});

// ----------------------------------------------------------------------------
// View tabs (Dashboard ↔ Category Analysis)
// ----------------------------------------------------------------------------
viewTabs.addEventListener("click", (e) => {
  const btn = e.target.closest(".view-tab");
  if (!btn) return;
  switchView(btn.dataset.view);
});

function switchView(name) {
  state.view = name;
  viewTabs.querySelectorAll(".view-tab").forEach((t) => t.classList.toggle("active", t.dataset.view === name));
  dashboardView.classList.toggle("hidden", name !== "dashboard");
  categoriesView.classList.toggle("hidden", name !== "categories");
  ledgerView.classList.toggle("hidden", name !== "ledger");
  if (name === "categories") renderCategoriesView();
  if (name === "ledger") renderLedgerView();
  // Scroll to top so the user lands above-the-fold on the new view.
  window.scrollTo({ top: 0, behavior: "smooth" });
}

if (catDetailClose) {
  catDetailClose.addEventListener("click", () => {
    state.activeCatDetail = null;
    catDetail.classList.add("hidden");
    catCards.querySelectorAll(".cat-card").forEach((c) => c.classList.remove("active"));
  });
}

// ----------------------------------------------------------------------------
// Tx search + type filter
// ----------------------------------------------------------------------------
txSearch.addEventListener("input", () => { state.search = txSearch.value.trim().toLowerCase(); renderTxTable(); });
txTypeFilter.addEventListener("change", () => { state.typeFilter = txTypeFilter.value; renderTxTable(); });
csvBtn.addEventListener("click", exportCsv);

// ----------------------------------------------------------------------------
// Dashboard rendering
// ----------------------------------------------------------------------------
function showLogin() {
  authShell.classList.add("hidden");
  loginView.classList.remove("hidden");
}

async function showDashboard(session) {
  loginView.classList.add("hidden");
  authShell.classList.remove("hidden");
  setupGreeting(session.user);

  const userId = session.user.id;
  const [profileResult, txResult] = await Promise.all([
    supabase.from("i_users").select("name, email").eq("id", userId).maybeSingle(),
    supabase.from("i_transactions")
      .select("id, occurred_at, bank_name, sender, type, category, amount, balance, counterparty, ref_num")
      .eq("user_id", userId)
      .order("occurred_at", { ascending: false })
      .limit(2000),
  ]);

  if (profileResult.data?.name) userNameEl.textContent = profileResult.data.name;

  if (txResult.error) {
    txTbody.innerHTML = `<tr><td colspan="7" class="muted">Couldn't load transactions: ${escapeHtml(txResult.error.message)}</td></tr>`;
    return;
  }

  state.allTxs = txResult.data || [];
  state.userId = userId;
  renderBatchPills();
  rerender();
  // Fire-and-forget ledger fetch so the dashboard renders immediately.
  fetchLedger().catch((e) => console.warn("Ledger fetch failed:", e));
}

// ----------------------------------------------------------------------------
// Ledger — fetch + render + CRUD
// ----------------------------------------------------------------------------
async function fetchLedger() {
  if (!state.userId) return;
  const { data, error } = await supabase
    .from("i_ledger_entries")
    .select("*")
    .eq("user_id", state.userId)
    .order("created_at", { ascending: false });
  if (error) {
    // The migration may not be applied yet — surface a friendly hint, not a crash.
    if (/relation .* does not exist/i.test(error.message)) {
      console.warn("i_ledger_entries table missing — run supabase/add_ledger.sql in your Supabase project.");
    } else {
      console.warn("Ledger fetch error:", error.message);
    }
    state.ledger = [];
  } else {
    state.ledger = data || [];
  }
  renderLedgerBadge();
  if (state.view === "ledger") renderLedgerView();
}

function renderLedgerBadge() {
  const overdue = state.ledger.filter((e) => e.status === "ACTIVE" && isOverdue(e.due_date)).length;
  if (overdue > 0) {
    ledgerBadge.textContent = String(overdue);
    ledgerBadge.classList.remove("hidden");
  } else {
    ledgerBadge.classList.add("hidden");
  }
}

function renderLedgerView() {
  const entries = state.ledger;
  const active  = entries.filter((e) => e.status === "ACTIVE");
  const closed  = entries.filter((e) => e.status !== "ACTIVE");

  const iOwe       = active.filter((e) => e.direction === "I_OWE");
  const owedToMe   = active.filter((e) => e.direction === "OWED_TO_ME");
  const iOweTotal     = iOwe.reduce((s, e) => s + Number(e.balance), 0);
  const owedToMeTotal = owedToMe.reduce((s, e) => s + Number(e.balance), 0);
  const net           = owedToMeTotal - iOweTotal;
  const overdueCount  = active.filter((e) => isOverdue(e.due_date)).length;

  ledgerIOwe.textContent = formatETB(iOweTotal);
  ledgerIOweSub.textContent = iOwe.length === 0
    ? "no debts recorded"
    : `${iOwe.length} entr${iOwe.length === 1 ? "y" : "ies"} · ${overdueOf(iOwe)} overdue`;

  ledgerOwedToMe.textContent = formatETB(owedToMeTotal);
  ledgerOwedToMeSub.textContent = owedToMe.length === 0
    ? "nothing outstanding"
    : `${owedToMe.length} entr${owedToMe.length === 1 ? "y" : "ies"} · ${overdueOf(owedToMe)} overdue`;

  ledgerNet.textContent = (net >= 0 ? "+ " : "- ") + formatNumberAbs(net);
  ledgerNet.classList.remove("coral", "income");
  ledgerNet.classList.add(net >= 0 ? "income" : "coral");
  ledgerNetSub.textContent = net >= 0 ? "people owe you more than you owe" : "you owe more than is owed to you";
  if (overdueCount > 0) ledgerNetSub.textContent += ` · ${overdueCount} overdue total`;

  // Lists.
  ledgerOweCount.textContent = `${iOwe.length} active`;
  ledgerLentCount.textContent = `${owedToMe.length} active`;
  ledgerOweList.innerHTML  = iOwe.length === 0
    ? `<p class="muted">Nothing here. Tap + Add when you take a loan or rent comes up.</p>`
    : iOwe.sort(sortByDue).map(renderLedgerEntry).join("");
  ledgerLentList.innerHTML = owedToMe.length === 0
    ? `<p class="muted">No outstanding loans out yet.</p>`
    : owedToMe.sort(sortByDue).map(renderLedgerEntry).join("");

  // Closed entries.
  ledgerClosedList.innerHTML = closed.length === 0
    ? `<p class="muted">Nothing settled yet.</p>`
    : closed.map(renderLedgerEntry).join("");

  attachLedgerEntryHandlers();
  renderLedgerBadge();
}

function sortByDue(a, b) {
  const ax = a.due_date ? Date.parse(a.due_date) : Number.POSITIVE_INFINITY;
  const bx = b.due_date ? Date.parse(b.due_date) : Number.POSITIVE_INFINITY;
  return ax - bx;
}

function overdueOf(entries) {
  return entries.filter((e) => isOverdue(e.due_date)).length;
}

function isOverdue(dueIso) {
  if (!dueIso) return false;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return Date.parse(dueIso) < today.getTime();
}

function renderLedgerEntry(e) {
  const overdue   = e.status === "ACTIVE" && isOverdue(e.due_date);
  const isSettled = e.status !== "ACTIVE";
  const klass = [
    "ledger-entry",
    e.direction === "I_OWE" ? "i-owe" : "owed-to-me",
    overdue ? "overdue" : "",
    isSettled ? "settled" : "",
  ].filter(Boolean).join(" ");
  const typePill = e.type === "RECURRING"
    ? `RECURRING · ${(e.cadence || "monthly").toLowerCase()}`
    : "IOU";
  const principal = Number(e.principal || 0);
  const balance   = Number(e.balance || 0);
  const partial   = balance > 0 && balance < principal;
  const dueLabel = e.due_date
    ? (overdue ? `Overdue · ${formatDueDate(e.due_date)}` : `Due ${formatDueDate(e.due_date)}`)
    : "No due date";

  const payInline = state.payingId === e.id ? `
    <div class="ledger-pay-form">
      <input type="number" min="0.01" step="0.01" max="${balance}" value="${balance.toFixed(2)}"
             class="pay-amount" data-id="${e.id}" />
      <button class="confirm-pay" data-id="${e.id}">Pay</button>
      <button class="cancel-pay" data-id="${e.id}">Cancel</button>
    </div>
  ` : "";

  const actions = isSettled
    ? `<button data-action="restore" data-id="${e.id}">Reopen</button>
       <button data-action="delete"  data-id="${e.id}">Delete</button>`
    : `<button class="primary" data-action="pay" data-id="${e.id}">Mark Paid</button>
       <button data-action="edit"    data-id="${e.id}">Edit</button>
       <button data-action="archive" data-id="${e.id}">Archive</button>`;

  return `
    <div class="${klass}" data-id="${e.id}">
      <div class="ledger-head">
        <span class="ledger-cp" title="${escapeHtml(e.counterparty)}">${escapeHtml(e.counterparty)}</span>
        <span class="ledger-type-pill">${escapeHtml(typePill)}</span>
      </div>
      <div class="ledger-amounts">
        <span class="ledger-balance">${formatETB(balance)}</span>
        ${partial ? `<span class="ledger-of-principal">of ${formatETB(principal)}</span>` : ""}
      </div>
      <div class="ledger-due">${escapeHtml(dueLabel)}</div>
      ${e.note ? `<div class="ledger-note">${escapeHtml(e.note)}</div>` : ""}
      ${payInline}
      <div class="ledger-actions">${actions}</div>
    </div>
  `;
}

function formatDueDate(iso) {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });
}

function attachLedgerEntryHandlers() {
  // Action buttons
  ledgerView.querySelectorAll(".ledger-actions button[data-action]").forEach((btn) => {
    btn.addEventListener("click", async (e) => {
      e.stopPropagation();
      const id = btn.dataset.id;
      const action = btn.dataset.action;
      const entry = state.ledger.find((x) => x.id === id);
      if (!entry) return;

      if (action === "pay") {
        state.payingId = (state.payingId === id) ? null : id;
        renderLedgerView();
      } else if (action === "edit") {
        openEditForm(entry);
      } else if (action === "archive") {
        await updateEntry(id, { status: "ARCHIVED" });
      } else if (action === "restore") {
        await updateEntry(id, { status: "ACTIVE", settled_at: null });
      } else if (action === "delete") {
        if (!confirm("Delete this entry permanently?")) return;
        await deleteEntry(id);
      }
    });
  });

  // Confirm / cancel pay
  ledgerView.querySelectorAll(".confirm-pay").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const id = btn.dataset.id;
      const input = ledgerView.querySelector(`.pay-amount[data-id="${id}"]`);
      const amount = parseFloat(input.value);
      if (!Number.isFinite(amount) || amount <= 0) return;
      await logRepayment(id, amount);
    });
  });
  ledgerView.querySelectorAll(".cancel-pay").forEach((btn) => {
    btn.addEventListener("click", () => {
      state.payingId = null;
      renderLedgerView();
    });
  });
}

// ----------------------------------------------------------------------------
// Form handlers
// ----------------------------------------------------------------------------
function showFormForCadence() {
  const isRecurring = ledgerType.value === "RECURRING";
  ledgerCadenceField.style.display = isRecurring ? "" : "none";
}
ledgerType.addEventListener("change", showFormForCadence);

ledgerFormToggle.addEventListener("click", () => {
  if (state.editingId) {
    resetLedgerForm();
  } else {
    ledgerForm.classList.toggle("hidden");
  }
  showFormForCadence();
});

ledgerCancel.addEventListener("click", () => resetLedgerForm());

ledgerForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  ledgerFormError.classList.add("hidden");
  const payload = {
    user_id:     state.userId,
    type:        ledgerType.value,
    direction:   ledgerDirection.value,
    counterparty: ledgerCounterparty.value.trim(),
    principal:   parseFloat(ledgerAmount.value),
    balance:     parseFloat(ledgerAmount.value),  // overridden below if editing
    due_date:    ledgerDue.value || null,
    cadence:     ledgerType.value === "RECURRING" ? ledgerCadence.value : null,
    note:        ledgerNote.value.trim() || null,
  };
  if (!payload.counterparty || !Number.isFinite(payload.principal) || payload.principal < 0) {
    showFormError("Counterparty and a non-negative amount are required.");
    return;
  }

  ledgerSave.disabled = true;
  let res;
  if (state.editingId) {
    // Keep the existing balance ratio if the user changed the principal.
    const existing = state.ledger.find((x) => x.id === state.editingId);
    const ratio = existing && existing.principal > 0 ? Number(existing.balance) / Number(existing.principal) : 1;
    payload.balance = payload.principal * ratio;
    res = await supabase.from("i_ledger_entries").update(payload).eq("id", state.editingId).select().maybeSingle();
  } else {
    res = await supabase.from("i_ledger_entries").insert(payload).select().maybeSingle();
  }
  ledgerSave.disabled = false;
  if (res.error) {
    showFormError(prettyLedgerError(res.error.message));
    return;
  }
  await fetchLedger();
  resetLedgerForm();
  showToast(state.editingId ? "Entry updated" : "Entry saved", "success");
});

function showFormError(msg) {
  ledgerFormError.textContent = msg;
  ledgerFormError.classList.remove("hidden");
}

function prettyLedgerError(raw) {
  if (/relation .* does not exist/i.test(raw)) {
    return "Database isn't set up yet. Run supabase/add_ledger.sql in your Supabase SQL editor.";
  }
  return raw || "Save failed.";
}

function openEditForm(entry) {
  state.editingId = entry.id;
  ledgerType.value        = entry.type;
  ledgerDirection.value   = entry.direction;
  ledgerCounterparty.value= entry.counterparty;
  ledgerAmount.value      = Number(entry.principal).toFixed(2);
  ledgerDue.value         = entry.due_date || "";
  ledgerCadence.value     = entry.cadence || "MONTHLY";
  ledgerNote.value        = entry.note || "";
  ledgerSave.textContent  = "Update entry";
  showFormForCadence();
  ledgerForm.classList.remove("hidden");
  ledgerForm.scrollIntoView({ behavior: "smooth", block: "start" });
}

function resetLedgerForm() {
  state.editingId = null;
  ledgerForm.reset();
  ledgerForm.classList.add("hidden");
  ledgerSave.textContent = "Save entry";
  ledgerFormError.classList.add("hidden");
  showFormForCadence();
}

// ----------------------------------------------------------------------------
// Repayments + recurring auto-roll
// ----------------------------------------------------------------------------
async function logRepayment(entryId, amount) {
  const entry = state.ledger.find((x) => x.id === entryId);
  if (!entry) return;
  const currentBalance = Number(entry.balance);
  const newBalance = Math.max(0, currentBalance - amount);
  const fullyPaid = newBalance === 0;

  const { error: repError } = await supabase.from("i_ledger_repayments").insert({
    entry_id: entryId,
    user_id:  state.userId,
    amount:   Math.min(amount, currentBalance),
  });
  if (repError) { showToast(`Couldn't record payment: ${repError.message}`, "error"); return; }

  const updates = { balance: newBalance };
  if (fullyPaid) {
    updates.status = "SETTLED";
    updates.settled_at = new Date().toISOString();
  }
  const { error: upError } = await supabase.from("i_ledger_entries").update(updates).eq("id", entryId);
  if (upError) { showToast(`Couldn't update entry: ${upError.message}`, "error"); return; }

  // Auto-roll a recurring entry on full payment.
  if (fullyPaid && entry.type === "RECURRING") {
    const nextDue = nextDueDate(entry.due_date, entry.cadence);
    await supabase.from("i_ledger_entries").insert({
      user_id:     state.userId,
      type:        "RECURRING",
      direction:   entry.direction,
      counterparty: entry.counterparty,
      principal:   entry.principal,
      balance:     entry.principal,
      due_date:    nextDue,
      cadence:     entry.cadence,
      parent_id:   entry.id,
      note:        entry.note,
    });
    showToast(`Paid. Next ${(entry.cadence || "").toLowerCase()} entry queued for ${formatDueDate(nextDue)}.`, "success");
  } else {
    showToast(fullyPaid ? "Settled in full" : "Payment recorded", "success");
  }

  state.payingId = null;
  await fetchLedger();
}

function nextDueDate(currentIso, cadence) {
  const base = currentIso ? new Date(currentIso) : new Date();
  switch ((cadence || "MONTHLY").toUpperCase()) {
    case "WEEKLY":    base.setDate(base.getDate() + 7); break;
    case "QUARTERLY": base.setMonth(base.getMonth() + 3); break;
    case "YEARLY":    base.setFullYear(base.getFullYear() + 1); break;
    case "MONTHLY":
    default:          base.setMonth(base.getMonth() + 1); break;
  }
  return base.toISOString().slice(0, 10);
}

async function updateEntry(id, patch) {
  const { error } = await supabase.from("i_ledger_entries").update(patch).eq("id", id);
  if (error) { showToast(`Update failed: ${error.message}`, "error"); return; }
  await fetchLedger();
}

async function deleteEntry(id) {
  const { error } = await supabase.from("i_ledger_entries").delete().eq("id", id);
  if (error) { showToast(`Delete failed: ${error.message}`, "error"); return; }
  await fetchLedger();
}

ledgerClosedToggle.addEventListener("click", () => {
  state.showClosed = !state.showClosed;
  ledgerClosedList.classList.toggle("hidden", !state.showClosed);
  ledgerClosedToggle.textContent = state.showClosed ? "Hide" : "Show";
});

function setupGreeting(user) {
  const hour = new Date().getHours();
  const greet = hour < 12 ? "Good morning," : hour < 17 ? "Good afternoon," : "Good evening,";
  greetingEl.textContent = greet;
  userNameEl.textContent =
    user.user_metadata?.name ||
    user.user_metadata?.full_name ||
    (user.email ? user.email.split("@")[0] : "There");
}

// ----------------------------------------------------------------------------
// Period helpers
// ----------------------------------------------------------------------------
function periodRange(period, now = new Date()) {
  const start = new Date(now);
  switch (period) {
    case "month": {
      start.setDate(1); start.setHours(0, 0, 0, 0);
      return { start: start.getTime(), end: now.getTime() };
    }
    case "last-month": {
      const s = new Date(now.getFullYear(), now.getMonth() - 1, 1);
      const e = new Date(now.getFullYear(), now.getMonth(), 1) - 1;
      return { start: s.getTime(), end: e };
    }
    case "30d":  return { start: now.getTime() - 30 * 864e5, end: now.getTime() };
    case "90d":  return { start: now.getTime() - 90 * 864e5, end: now.getTime() };
    case "year": {
      const s = new Date(now.getFullYear(), 0, 1);
      return { start: s.getTime(), end: now.getTime() };
    }
    case "all":
    default:     return { start: 0, end: now.getTime() };
  }
}

function periodDays(period, range) {
  if (period === "all") return Math.max(1, Math.ceil((range.end - (state.allTxs.at(-1)?.occurred_at ? Date.parse(state.allTxs.at(-1).occurred_at) : range.end)) / 864e5));
  return Math.max(1, Math.ceil((range.end - range.start) / 864e5));
}

// ----------------------------------------------------------------------------
// Render
// ----------------------------------------------------------------------------
function rerender() {
  if (!state.allTxs) return;
  const range = periodRange(state.period);
  const txs = state.allTxs;
  const inRange = txs.filter((t) => {
    const ts = Date.parse(t.occurred_at);
    return Number.isFinite(ts) && ts >= range.start && ts <= range.end;
  });

  const label = PERIOD_LABELS[state.period] || "";
  statPeriodLabel1.textContent = label;
  statPeriodLabel2.textContent = label;

  renderHeaderStats(txs, inRange, range);
  renderAccounts(txs);
  renderCategoryList(inRange);
  renderCounterparties(inRange);
  renderRecurring(inRange);
  renderDailyChart(txs);
  renderMonthlyChart(txs);
  renderWeekdayStrip(inRange);
  renderTxTable();
  if (state.view === "categories") renderCategoriesView();
}

function renderHeaderStats(allTxs, inRange, range) {
  // Latest balance per bank → total balance (independent of period filter).
  const byBank = groupBy(allTxs, (t) => t.bank_name || "Unknown");
  let totalBalance = 0;
  let accountCount = 0;
  for (const [, list] of byBank) {
    const latestWithBalance = list.find((t) => typeof t.balance === "number" && t.balance >= 0);
    if (latestWithBalance) { totalBalance += latestWithBalance.balance; accountCount++; }
    else accountCount++;
  }

  let spend = 0, spendCount = 0;
  let income = 0, incomeCount = 0;
  let largestExpense = null;

  for (const tx of inRange) {
    if (EXPENSE_TYPES.has(tx.type)) {
      spend += tx.amount;
      spendCount++;
      if (!largestExpense || tx.amount > largestExpense.amount) largestExpense = tx;
    } else if (tx.type === "CREDIT") {
      income += tx.amount;
      incomeCount++;
    }
  }

  const net = income - spend;
  const days = periodDays(state.period, range);
  const avgDaily = spend / days;
  const savingsRate = income > 0 ? ((income - spend) / income) * 100 : null;

  statBalance.textContent       = formatETB(totalBalance);
  statBalanceSub.textContent    = `${accountCount} account${accountCount === 1 ? "" : "s"}`;

  statMonthSpend.textContent    = formatETB(spend);
  statMonthSpendSub.textContent = `${spendCount} transaction${spendCount === 1 ? "" : "s"}`;

  statMonthIncome.textContent   = formatETB(income);
  statMonthIncomeSub.textContent = `${incomeCount} credit${incomeCount === 1 ? "" : "s"}`;

  statNet.textContent = (net >= 0 ? "+ " : "- ") + formatNumberAbs(net);
  statNet.classList.remove("teal", "coral", "income");
  statNet.classList.add(net >= 0 ? "income" : "coral");
  statNetSub.textContent = savingsRate == null
    ? "no income recorded"
    : `${savingsRate >= 0 ? "saving" : "overspending"} ${formatPercent(savingsRate)}`;

  statAvgDaily.textContent = formatETB(avgDaily);
  statAvgDailySub.textContent = `over ${days} day${days === 1 ? "" : "s"}`;

  if (largestExpense) {
    statLargest.textContent = formatETB(largestExpense.amount);
    statLargestSub.textContent = (largestExpense.counterparty || largestExpense.bank_name || "—").slice(0, 36);
  } else {
    statLargest.textContent = "—";
    statLargestSub.textContent = "no expenses yet";
  }
}

function renderAccounts(allTxs) {
  const byBank = groupBy(allTxs, (t) => t.bank_name || "Unknown");
  const accounts = [...byBank.entries()].map(([name, list]) => {
    const latestWithBalance = list.find((t) => typeof t.balance === "number" && t.balance >= 0);
    return {
      name,
      balance: latestWithBalance?.balance ?? 0,
      count: list.length,
    };
  }).sort((a, b) => b.balance - a.balance);

  accountsCount.textContent = `${accounts.length} bank${accounts.length === 1 ? "" : "s"}`;
  accountsList.innerHTML = accounts.length === 0
    ? `<p class="muted">No accounts synced yet. Pull-to-refresh in the Android app to push your data.</p>`
    : accounts.map(renderAccountCard).join("");
}

function renderAccountCard(account) {
  return `
    <div class="bank-card">
      <span class="rail"></span>
      <div class="info">
        <p class="name">${escapeHtml(account.name)}</p>
        <p class="balance">${formatNumber(account.balance)}<span class="unit">ETB</span></p>
      </div>
    </div>
  `;
}

function renderCategoryList(inRange) {
  const byCategory = new Map();
  for (const tx of inRange) {
    if (!EXPENSE_TYPES.has(tx.type)) continue;
    const cat = (tx.category || "").trim();
    if (!cat) continue;
    byCategory.set(cat, (byCategory.get(cat) || 0) + tx.amount);
  }
  const rows = [...byCategory.entries()].sort((a, b) => b[1] - a[1]);
  categoryList.innerHTML = rows.length === 0
    ? `<p class="muted">No categorised expenses for this period.</p>`
    : rows.map(([name, total]) => `
        <div class="category-row">
          <span class="name">${escapeHtml(name)}</span>
          <span class="amount">${formatETB(total)}</span>
        </div>
      `).join("");
}

function renderCounterparties(inRange) {
  const byCp = new Map();
  for (const tx of inRange) {
    if (!EXPENSE_TYPES.has(tx.type)) continue;
    const cp = (tx.counterparty || "").trim();
    if (!cp) continue;
    const entry = byCp.get(cp) || { total: 0, count: 0 };
    entry.total += tx.amount;
    entry.count += 1;
    byCp.set(cp, entry);
  }
  const rows = [...byCp.entries()]
    .sort((a, b) => b[1].total - a[1].total)
    .slice(0, 10);
  counterpartyList.innerHTML = rows.length === 0
    ? `<p class="muted">No counterparty data yet.</p>`
    : rows.map(([name, { total, count }]) => renderSelectableRow({
        name, total, count, type: "counterparty",
        meta: `${count}×`,
      })).join("");
  attachSelectionHandlers(counterpartyList);
}

function renderRecurring(inRange) {
  const byCp = new Map();
  for (const tx of inRange) {
    if (!EXPENSE_TYPES.has(tx.type)) continue;
    const cp = (tx.counterparty || "").trim();
    if (!cp) continue;
    const entry = byCp.get(cp) || { total: 0, count: 0, last: 0 };
    entry.total += tx.amount;
    entry.count += 1;
    const ts = Date.parse(tx.occurred_at) || 0;
    if (ts > entry.last) entry.last = ts;
    byCp.set(cp, entry);
  }
  const rows = [...byCp.entries()]
    .filter(([, v]) => v.count >= 3)
    .sort((a, b) => b[1].count - a[1].count)
    .slice(0, 12);
  recurringList.innerHTML = rows.length === 0
    ? `<p class="muted">No recurring patterns detected yet (need 3+ payments to the same counterparty).</p>`
    : rows.map(([name, { total, count, last }]) => renderSelectableRow({
        name, total, count, type: "recurring",
        meta: `${count} payments · last ${formatRelativeDate(last)}`,
      })).join("");
  attachSelectionHandlers(recurringList);
}

// ----------------------------------------------------------------------------
// Selectable row (used by Top Counterparties and Recurring Spend)
// ----------------------------------------------------------------------------
function renderSelectableRow({ name, total, meta, type }) {
  const isSelected = state.selectedCps.has(name);
  const currentCategory = currentCategoryFor(name);
  const rowClass = type === "recurring" ? "recurring-row" : "counterparty-row";
  return `
    <div class="${rowClass}${isSelected ? " selected" : ""}" data-cp="${escapeHtml(name)}">
      <span class="row-check" aria-hidden="true"></span>
      <span class="name" title="${escapeHtml(name)}">${escapeHtml(name)}</span>
      ${currentCategory ? `<span class="category-currently">${escapeHtml(currentCategory)}</span>` : ""}
      <span class="meta">${escapeHtml(meta)}</span>
      <span class="amount">${formatETB(total)}</span>
    </div>
  `;
}

function currentCategoryFor(counterparty) {
  // Look up the most common existing category for this counterparty.
  const counts = new Map();
  for (const tx of state.allTxs) {
    if ((tx.counterparty || "").trim() !== counterparty) continue;
    const c = (tx.category || "").trim();
    if (!c) continue;
    counts.set(c, (counts.get(c) || 0) + 1);
  }
  let best = null, bestCount = 0;
  for (const [c, n] of counts) if (n > bestCount) { best = c; bestCount = n; }
  return best;
}

function attachSelectionHandlers(container) {
  container.querySelectorAll("[data-cp]").forEach((row) => {
    row.addEventListener("click", () => {
      const cp = row.dataset.cp;
      if (state.selectedCps.has(cp)) state.selectedCps.delete(cp);
      else state.selectedCps.add(cp);
      // Re-render both lists so a selection in one mirrors to the other.
      const range = periodRange(state.period);
      const inRange = state.allTxs.filter((t) => {
        const ts = Date.parse(t.occurred_at);
        return Number.isFinite(ts) && ts >= range.start && ts <= range.end;
      });
      renderCounterparties(inRange);
      renderRecurring(inRange);
      updateBatchBar();
    });
  });
}

// ----------------------------------------------------------------------------
// Batch action bar
// ----------------------------------------------------------------------------
function renderBatchPills() {
  // Build pills: defaults + any custom categories already used in the data.
  const used = new Set();
  for (const tx of state.allTxs) {
    const c = (tx.category || "").trim();
    if (c) used.add(c);
  }
  const customs = [...used].filter((c) => !DEFAULT_CATEGORIES.includes(c)).sort();
  const all = [...DEFAULT_CATEGORIES, ...customs];

  batchPills.innerHTML = all.map((c) => `
    <button type="button" class="category-pill" data-category="${escapeHtml(c)}">${escapeHtml(c)}</button>
  `).join("") + `
    <button type="button" class="category-pill" data-category="__custom__">+ Custom…</button>
  `;
  batchPills.querySelectorAll(".category-pill").forEach((p) => {
    p.addEventListener("click", () => {
      if (p.dataset.category === "__custom__") {
        const custom = window.prompt("New category name:");
        if (!custom || !custom.trim()) return;
        state.pickedCategory = custom.trim();
      } else {
        state.pickedCategory = p.dataset.category;
      }
      // Highlight the chosen pill.
      batchPills.querySelectorAll(".category-pill").forEach((el) => {
        el.classList.toggle("selected", el.dataset.category === state.pickedCategory);
      });
      batchApply.disabled = state.selectedCps.size === 0 || !state.pickedCategory;
    });
  });
}

function updateBatchBar() {
  const n = state.selectedCps.size;
  if (n === 0) {
    batchBar.classList.remove("visible");
    state.pickedCategory = null;
    batchPills.querySelectorAll(".category-pill").forEach((el) => el.classList.remove("selected"));
    batchApply.disabled = true;
    return;
  }
  batchCount.textContent = `${n} counterpart${n === 1 ? "y" : "ies"} selected`;
  batchBar.classList.add("visible");
  batchApply.disabled = !state.pickedCategory;
}

batchClear.addEventListener("click", () => {
  state.selectedCps.clear();
  // Force re-render of selection visuals.
  const range = periodRange(state.period);
  const inRange = state.allTxs.filter((t) => {
    const ts = Date.parse(t.occurred_at);
    return Number.isFinite(ts) && ts >= range.start && ts <= range.end;
  });
  renderCounterparties(inRange);
  renderRecurring(inRange);
  updateBatchBar();
});

batchApply.addEventListener("click", applyBatchCategory);

async function applyBatchCategory() {
  if (state.selectedCps.size === 0 || !state.pickedCategory || !state.userId) return;
  const counterparties = [...state.selectedCps];
  const category = state.pickedCategory;

  batchApply.disabled = true;
  batchApply.textContent = "Applying…";

  const { error, count } = await supabase
    .from("i_transactions")
    .update({ category }, { count: "exact" })
    .eq("user_id", state.userId)
    .in("counterparty", counterparties);

  batchApply.textContent = "Apply";

  if (error) {
    showToast(`Update failed: ${error.message}`, "error");
    batchApply.disabled = false;
    return;
  }

  // Optimistically update local state so the UI reflects the change immediately.
  for (const tx of state.allTxs) {
    if (counterparties.includes((tx.counterparty || "").trim())) tx.category = category;
  }
  state.selectedCps.clear();
  state.pickedCategory = null;
  showToast(`Categorised ${count ?? "—"} transactions as ${category}`, "success");
  rerender();
  updateBatchBar();
}

function showToast(message, kind = "success") {
  toastEl.textContent = message;
  toastEl.className = `toast ${kind} visible`;
  clearTimeout(showToast._t);
  showToast._t = setTimeout(() => { toastEl.className = "toast"; }, 3500);
}

// ----------------------------------------------------------------------------
// Charts — inline SVG bar charts (no external libraries)
// ----------------------------------------------------------------------------
function renderDailyChart(allTxs) {
  // Last 30 days of expenses, one bar per day.
  const days = 30;
  const today = new Date();
  today.setHours(23, 59, 59, 999);
  const start = today.getTime() - (days - 1) * 864e5;
  const buckets = Array.from({ length: days }, (_, i) => ({
    day: new Date(start + i * 864e5),
    total: 0,
  }));

  for (const tx of allTxs) {
    if (!EXPENSE_TYPES.has(tx.type)) continue;
    const ts = Date.parse(tx.occurred_at);
    if (!Number.isFinite(ts) || ts < start) continue;
    const idx = Math.floor((ts - start) / 864e5);
    if (idx >= 0 && idx < days) buckets[idx].total += tx.amount;
  }

  const max = Math.max(1, ...buckets.map((b) => b.total));
  dailyChartWin.textContent = `last ${days} days · max ${formatNumber(max)} ETB`;
  dailyChart.innerHTML = renderBarChartSvg(
    buckets.map((b) => ({ value: b.total, label: b.day.getDate().toString() })),
    { color: "var(--coral)" }
  );
}

function renderMonthlyChart(allTxs) {
  // Last 12 months, side-by-side income + expense bars.
  const months = 12;
  const now = new Date();
  const buckets = Array.from({ length: months }, (_, i) => {
    const d = new Date(now.getFullYear(), now.getMonth() - (months - 1 - i), 1);
    return { d, income: 0, expense: 0 };
  });

  const monthIndex = (date) => {
    const monthsBack = (now.getFullYear() - date.getFullYear()) * 12 + (now.getMonth() - date.getMonth());
    return (months - 1) - monthsBack;
  };
  for (const tx of allTxs) {
    const ts = Date.parse(tx.occurred_at);
    if (!Number.isFinite(ts)) continue;
    const idx = monthIndex(new Date(ts));
    if (idx < 0 || idx >= months) continue;
    if (EXPENSE_TYPES.has(tx.type)) buckets[idx].expense += tx.amount;
    else if (tx.type === "CREDIT") buckets[idx].income += tx.amount;
  }

  const max = Math.max(1, ...buckets.flatMap((b) => [b.income, b.expense]));
  monthlyChart.innerHTML = renderGroupedBarChartSvg(
    buckets.map((b) => ({
      label: b.d.toLocaleDateString(undefined, { month: "short" }),
      values: [b.income, b.expense],
    })),
    { colors: ["var(--green-income)", "var(--coral)"], max }
  );
}

function renderBarChartSvg(points, { color }) {
  if (points.length === 0) return `<p class="muted">No data.</p>`;
  const W = 600, H = 220, pad = { l: 8, r: 8, t: 12, b: 26 };
  const innerW = W - pad.l - pad.r;
  const innerH = H - pad.t - pad.b;
  const max = Math.max(1, ...points.map((p) => p.value));
  const gap = 2;
  const barW = (innerW - gap * (points.length - 1)) / points.length;

  const bars = points.map((p, i) => {
    const h = (p.value / max) * innerH;
    const x = pad.l + i * (barW + gap);
    const y = pad.t + innerH - h;
    return `<rect class="chart-bar" x="${x.toFixed(2)}" y="${y.toFixed(2)}" width="${barW.toFixed(2)}" height="${Math.max(0, h).toFixed(2)}" rx="3" fill="${color}">
      <title>${escapeHtml(p.label)}: ${formatETB(p.value)}</title>
    </rect>`;
  }).join("");

  // Sparse x-axis labels (~6 evenly spaced).
  const labelStride = Math.max(1, Math.floor(points.length / 6));
  const labels = points.map((p, i) => {
    if (i % labelStride !== 0) return "";
    const x = pad.l + i * (barW + gap) + barW / 2;
    return `<text x="${x.toFixed(2)}" y="${(H - 6).toFixed(2)}" class="chart-axis-label" text-anchor="middle">${escapeHtml(p.label)}</text>`;
  }).join("");

  return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none">${bars}${labels}</svg>`;
}

function renderGroupedBarChartSvg(groups, { colors, max }) {
  if (groups.length === 0) return `<p class="muted">No data.</p>`;
  const W = 600, H = 220, pad = { l: 8, r: 8, t: 12, b: 26 };
  const innerW = W - pad.l - pad.r;
  const innerH = H - pad.t - pad.b;
  const groupCount = groups.length;
  const valuesPerGroup = groups[0].values.length;
  const groupGap = 6;
  const groupW = (innerW - groupGap * (groupCount - 1)) / groupCount;
  const barW = Math.max(2, (groupW - 2 * (valuesPerGroup - 1)) / valuesPerGroup);

  const bars = groups.flatMap((g, gi) => g.values.map((v, vi) => {
    const h = (v / max) * innerH;
    const groupX = pad.l + gi * (groupW + groupGap);
    const x = groupX + vi * (barW + 2);
    const y = pad.t + innerH - h;
    return `<rect class="chart-bar" x="${x.toFixed(2)}" y="${y.toFixed(2)}" width="${barW.toFixed(2)}" height="${Math.max(0, h).toFixed(2)}" rx="2" fill="${colors[vi]}">
      <title>${escapeHtml(g.label)} · ${vi === 0 ? "Income" : "Expense"}: ${formatETB(v)}</title>
    </rect>`;
  })).join("");

  const labels = groups.map((g, gi) => {
    const x = pad.l + gi * (groupW + groupGap) + groupW / 2;
    return `<text x="${x.toFixed(2)}" y="${(H - 6).toFixed(2)}" class="chart-axis-label" text-anchor="middle">${escapeHtml(g.label)}</text>`;
  }).join("");

  return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none">${bars}${labels}</svg>`;
}

function renderWeekdayStrip(inRange) {
  // Average spend per weekday (Sun..Sat).
  const sums = [0, 0, 0, 0, 0, 0, 0];
  const counts = [0, 0, 0, 0, 0, 0, 0];
  const seenDays = [new Set(), new Set(), new Set(), new Set(), new Set(), new Set(), new Set()];
  for (const tx of inRange) {
    if (!EXPENSE_TYPES.has(tx.type)) continue;
    const ts = Date.parse(tx.occurred_at);
    if (!Number.isFinite(ts)) continue;
    const d = new Date(ts);
    const dow = d.getDay();
    sums[dow] += tx.amount;
    counts[dow] += 1;
    seenDays[dow].add(`${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`);
  }
  const avgs = sums.map((s, i) => seenDays[i].size > 0 ? s / seenDays[i].size : 0);
  const max = Math.max(1, ...avgs);
  const labels = ["S", "M", "T", "W", "T", "F", "S"];
  weekdayStrip.innerHTML = labels.map((day, i) => {
    const pct = Math.round((avgs[i] / max) * 100);
    return `
      <div class="weekday-cell">
        <div class="day">${day}</div>
        <div class="amount">${avgs[i] > 0 ? formatNumber(avgs[i]) : "—"}</div>
        <div class="meter"><span style="width: ${pct}%"></span></div>
      </div>
    `;
  }).join("");
}

// ----------------------------------------------------------------------------
// Transactions table (search + type filter + CSV export)
// ----------------------------------------------------------------------------
function renderTxTable() {
  const filtered = applyTxFilters(state.allTxs);
  txCount.textContent = `${state.allTxs.length} loaded · ${filtered.length} shown`;
  const recent = filtered.slice(0, 100);
  txTbody.innerHTML = recent.length === 0
    ? `<tr><td colspan="7" class="muted">No transactions match this filter.</td></tr>`
    : recent.map(renderTxRow).join("");
}

function applyTxFilters(txs) {
  const q = state.search;
  const t = state.typeFilter;
  return txs.filter((tx) => {
    if (t && tx.type !== t) return false;
    if (q) {
      const blob = `${tx.counterparty || ""} ${tx.bank_name || ""} ${tx.sender || ""} ${tx.ref_num || ""} ${tx.category || ""}`.toLowerCase();
      if (!blob.includes(q)) return false;
    }
    return true;
  });
}

function renderTxRow(tx) {
  const outgoing = EXPENSE_TYPES.has(tx.type);
  const amount = `${outgoing ? "-" : "+"} ${formatNumber(tx.amount)}`;
  const amountClass = outgoing ? "amount-out" : "amount-in";
  return `
    <tr>
      <td>${formatDate(tx.occurred_at)}</td>
      <td>${escapeHtml(tx.counterparty || tx.sender || "—")}</td>
      <td>${escapeHtml(tx.bank_name || "—")}</td>
      <td><span class="tag">${escapeHtml(prettyType(tx.type))}</span></td>
      <td>${tx.category ? escapeHtml(tx.category) : '<span class="muted">—</span>'}</td>
      <td class="right ${amountClass}">${amount}</td>
      <td class="right">${tx.balance != null ? formatNumber(tx.balance) : '<span class="muted">—</span>'}</td>
    </tr>
  `;
}

function exportCsv() {
  const rows = applyTxFilters(state.allTxs);
  if (rows.length === 0) return;
  const header = ["Date", "Counterparty", "Bank", "Type", "Category", "Amount", "Balance", "Reference"];
  const escape = (v) => {
    const s = v == null ? "" : String(v);
    return /[",\n]/.test(s) ? `"${s.replaceAll('"', '""')}"` : s;
  };
  const lines = [header.join(",")];
  for (const tx of rows) {
    lines.push([
      tx.occurred_at || "",
      tx.counterparty || tx.sender || "",
      tx.bank_name || "",
      tx.type || "",
      tx.category || "",
      tx.amount ?? "",
      tx.balance ?? "",
      tx.ref_num || "",
    ].map(escape).join(","));
  }
  const blob = new Blob(["﻿" + lines.join("\n")], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `finance-lore-${new Date().toISOString().slice(0, 10)}.csv`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

// ----------------------------------------------------------------------------
// Category analysis view
// ----------------------------------------------------------------------------
function renderCategoriesView() {
  const range = periodRange(state.period);
  const inRange = state.allTxs.filter((t) => {
    const ts = Date.parse(t.occurred_at);
    return Number.isFinite(ts) && ts >= range.start && ts <= range.end;
  });

  // Aggregate by category (only expenses).
  const byCategory = new Map();
  let totalCategorised = 0;
  let uncategorisedCount = 0;
  let totalExpense = 0;
  for (const tx of inRange) {
    if (!EXPENSE_TYPES.has(tx.type)) continue;
    totalExpense += tx.amount;
    const cat = (tx.category || "").trim();
    if (!cat) { uncategorisedCount++; continue; }
    if (!byCategory.has(cat)) byCategory.set(cat, { total: 0, count: 0, txs: [] });
    const e = byCategory.get(cat);
    e.total += tx.amount;
    e.count += 1;
    e.txs.push(tx);
    totalCategorised += tx.amount;
  }

  const sortedCats = [...byCategory.entries()].sort((a, b) => b[1].total - a[1].total);

  // Hero stats.
  catTotal.textContent = formatETB(totalCategorised);
  catTotalSub.textContent =
    totalExpense > 0 ? `${((totalCategorised / totalExpense) * 100).toFixed(0)}% of spend categorised` : "no spend recorded";
  if (sortedCats.length > 0) {
    const [topName, top] = sortedCats[0];
    catTopName.textContent = topName;
    const pct = totalCategorised > 0 ? (top.total / totalCategorised) * 100 : 0;
    catTopSub.textContent = `${formatETB(top.total)} · ${pct.toFixed(0)}% of categorised spend`;
  } else {
    catTopName.textContent = "—";
    catTopSub.textContent = "no categorised data yet";
  }
  catUncategorised.textContent = String(uncategorisedCount);
  catUncategorisedSub.textContent = uncategorisedCount === 0
    ? "all expenses tagged"
    : `${uncategorisedCount} expense${uncategorisedCount === 1 ? "" : "s"} missing a tag`;

  // Donut + legend.
  renderCategoryDonut(sortedCats, totalCategorised);

  // 12-month trend stacked by category.
  renderCategoryTrend(sortedCats.map(([n]) => n));

  // Per-category cards with trend vs the prior equivalent period.
  const priorRange = priorPeriodRange(state.period);
  const priorByCategory = priorRange ? aggregateCategoryTotals(state.allTxs, priorRange) : new Map();

  if (sortedCats.length === 0) {
    catCards.innerHTML = `<p class="muted">No categorised expenses for this period. Tag some transactions first.</p>`;
    if (state.activeCatDetail) {
      state.activeCatDetail = null;
      catDetail.classList.add("hidden");
    }
    return;
  }

  catCards.innerHTML = sortedCats.map(([name, data], i) => {
    const color = CAT_PALETTE[i % CAT_PALETTE.length];
    const pct = totalCategorised > 0 ? (data.total / totalCategorised) * 100 : 0;
    const prior = priorByCategory.get(name)?.total ?? 0;
    const trend = prior > 0 ? ((data.total - prior) / prior) * 100 : null;
    const trendClass = trend == null ? "flat" : trend > 5 ? "up" : trend < -5 ? "down" : "flat";
    const trendLabel = trend == null
      ? "no prior data"
      : `${trend >= 0 ? "↑" : "↓"} ${formatPercent(trend)} vs prior`;
    const isActive = state.activeCatDetail === name ? " active" : "";
    return `
      <div class="cat-card${isActive}" data-cat="${escapeHtml(name)}" style="border-top: 3px solid ${color}">
        <div class="cat-head">
          <div class="cat-emoji">${emojiFor(name)}</div>
          <span class="cat-name">${escapeHtml(name)}</span>
          <span class="cat-pct">${pct.toFixed(0)}%</span>
        </div>
        <p class="cat-total">${formatETB(data.total)}</p>
        <div class="cat-bar"><span style="width: ${pct.toFixed(0)}%; background: ${color}"></span></div>
        <div class="cat-meta">
          <span>${data.count} tx</span>
          <span class="cat-trend ${trendClass}">${trendLabel}</span>
        </div>
      </div>
    `;
  }).join("");

  // Wire click → drill-in
  catCards.querySelectorAll(".cat-card").forEach((card) => {
    card.addEventListener("click", () => {
      const name = card.dataset.cat;
      openCategoryDetail(name);
    });
  });

  // Refresh open detail if any.
  if (state.activeCatDetail && byCategory.has(state.activeCatDetail)) {
    openCategoryDetail(state.activeCatDetail);
  } else if (state.activeCatDetail) {
    state.activeCatDetail = null;
    catDetail.classList.add("hidden");
  }
}

function aggregateCategoryTotals(allTxs, range) {
  const m = new Map();
  for (const tx of allTxs) {
    if (!EXPENSE_TYPES.has(tx.type)) continue;
    const ts = Date.parse(tx.occurred_at);
    if (!Number.isFinite(ts) || ts < range.start || ts > range.end) continue;
    const cat = (tx.category || "").trim();
    if (!cat) continue;
    if (!m.has(cat)) m.set(cat, { total: 0, count: 0 });
    const e = m.get(cat);
    e.total += tx.amount;
    e.count += 1;
  }
  return m;
}

function priorPeriodRange(period, now = new Date()) {
  switch (period) {
    case "month":      return periodRange("last-month", now);
    case "last-month": {
      const s = new Date(now.getFullYear(), now.getMonth() - 2, 1);
      const e = new Date(now.getFullYear(), now.getMonth() - 1, 1) - 1;
      return { start: s.getTime(), end: e };
    }
    case "30d":        return { start: Date.now() - 60 * 864e5, end: Date.now() - 30 * 864e5 };
    case "90d":        return { start: Date.now() - 180 * 864e5, end: Date.now() - 90 * 864e5 };
    case "year": {
      const s = new Date(now.getFullYear() - 1, 0, 1);
      const e = new Date(now.getFullYear(), 0, 1) - 1;
      return { start: s.getTime(), end: e };
    }
    default: return null;
  }
}

function openCategoryDetail(name) {
  state.activeCatDetail = name;
  catCards.querySelectorAll(".cat-card").forEach((c) => c.classList.toggle("active", c.dataset.cat === name));

  const range = periodRange(state.period);
  const txs = state.allTxs.filter((t) => {
    if (!EXPENSE_TYPES.has(t.type)) return false;
    if ((t.category || "").trim() !== name) return false;
    const ts = Date.parse(t.occurred_at);
    return Number.isFinite(ts) && ts >= range.start && ts <= range.end;
  });

  const total = txs.reduce((s, t) => s + t.amount, 0);
  const avg = txs.length > 0 ? total / txs.length : 0;

  // Prior-period comparison
  const prior = priorPeriodRange(state.period);
  let trendStr = "no prior data", trendColor = "muted";
  if (prior) {
    const priorTotal = state.allTxs
      .filter((t) => EXPENSE_TYPES.has(t.type) && (t.category || "").trim() === name)
      .filter((t) => {
        const ts = Date.parse(t.occurred_at);
        return Number.isFinite(ts) && ts >= prior.start && ts <= prior.end;
      })
      .reduce((s, t) => s + t.amount, 0);
    if (priorTotal > 0) {
      const pct = ((total - priorTotal) / priorTotal) * 100;
      trendStr = `${pct >= 0 ? "↑" : "↓"} ${formatPercent(pct)}`;
      trendColor = pct > 0 ? "coral" : pct < 0 ? "income" : "teal";
    } else if (total > 0) {
      trendStr = "first-time spend";
      trendColor = "teal";
    }
  }

  catDetailTitle.textContent = `${emojiFor(name)}  ${name}`;
  catDetailTotal.textContent = formatETB(total);
  catDetailTotalSub.textContent = `${txs.length} transaction${txs.length === 1 ? "" : "s"}`;
  catDetailTrend.textContent = trendStr;
  catDetailTrend.classList.remove("coral", "income", "teal", "muted");
  catDetailTrend.classList.add(trendColor);
  catDetailTrendSub.textContent = "compared to prior equivalent window";
  catDetailAvg.textContent = formatETB(avg);
  catDetailAvgSub.textContent = `over ${txs.length} payment${txs.length === 1 ? "" : "s"}`;

  catDetailTbody.innerHTML = txs.length === 0
    ? `<tr><td colspan="4" class="muted">No transactions tagged ${escapeHtml(name)} in this period.</td></tr>`
    : txs.slice(0, 100).map((tx) => `
        <tr>
          <td>${formatDate(tx.occurred_at)}</td>
          <td>${escapeHtml(tx.counterparty || tx.sender || "—")}</td>
          <td>${escapeHtml(tx.bank_name || "—")}</td>
          <td class="right amount-out">- ${formatNumber(tx.amount)}</td>
        </tr>
      `).join("");

  catDetail.classList.remove("hidden");
}

function renderCategoryDonut(sortedCats, total) {
  if (sortedCats.length === 0 || total === 0) {
    catDonut.innerHTML = `<p class="muted">No categorised expenses yet.</p>`;
    catDonutLegend.innerHTML = "";
    return;
  }
  const W = 240, H = 240, cx = W / 2, cy = H / 2, r = 96, strokeW = 28;
  let angle = -Math.PI / 2;  // start at 12 o'clock
  const arcs = sortedCats.map(([name, data], i) => {
    const slicePct = data.total / total;
    const sweep = slicePct * Math.PI * 2;
    const a0 = angle;
    const a1 = angle + sweep;
    angle = a1;
    const x0 = cx + Math.cos(a0) * r;
    const y0 = cy + Math.sin(a0) * r;
    const x1 = cx + Math.cos(a1) * r;
    const y1 = cy + Math.sin(a1) * r;
    const largeArc = sweep > Math.PI ? 1 : 0;
    const color = CAT_PALETTE[i % CAT_PALETTE.length];
    // Stroke-arc path so we get a donut without filling the centre.
    return `<path d="M ${x0.toFixed(2)} ${y0.toFixed(2)} A ${r} ${r} 0 ${largeArc} 1 ${x1.toFixed(2)} ${y1.toFixed(2)}"
      fill="none" stroke="${color}" stroke-width="${strokeW}" stroke-linecap="butt">
      <title>${escapeHtml(name)}: ${formatETB(data.total)} (${(slicePct * 100).toFixed(0)}%)</title>
    </path>`;
  }).join("");

  catDonut.innerHTML = `
    <svg viewBox="0 0 ${W} ${H}">
      ${arcs}
      <text x="${cx}" y="${cy - 6}" text-anchor="middle" fill="var(--body-muted)" font-size="11">total</text>
      <text x="${cx}" y="${cy + 16}" text-anchor="middle" fill="var(--headline)" font-size="18" font-weight="700">${escapeHtml(formatNumber(total))}</text>
    </svg>
  `;
  catDonutLegend.innerHTML = sortedCats.map(([name, data], i) => {
    const color = CAT_PALETTE[i % CAT_PALETTE.length];
    const pct = ((data.total / total) * 100).toFixed(0);
    return `<div class="legend-row"><span class="swatch" style="background: ${color}"></span>${escapeHtml(name)} <span class="muted small" style="margin-left:auto">${pct}%</span></div>`;
  }).join("");
}

function renderCategoryTrend(orderedCategoryNames) {
  // Build 12-month per-category totals.
  const months = 12;
  const now = new Date();
  const buckets = Array.from({ length: months }, (_, i) => {
    const d = new Date(now.getFullYear(), now.getMonth() - (months - 1 - i), 1);
    return { d, totals: new Map() };
  });

  for (const tx of state.allTxs) {
    if (!EXPENSE_TYPES.has(tx.type)) continue;
    const cat = (tx.category || "").trim();
    if (!cat) continue;
    const ts = Date.parse(tx.occurred_at);
    if (!Number.isFinite(ts)) continue;
    const d = new Date(ts);
    const monthsBack = (now.getFullYear() - d.getFullYear()) * 12 + (now.getMonth() - d.getMonth());
    const idx = (months - 1) - monthsBack;
    if (idx < 0 || idx >= months) continue;
    buckets[idx].totals.set(cat, (buckets[idx].totals.get(cat) || 0) + tx.amount);
  }

  // Categories present in either the current period or anywhere in the last 12 months.
  const categorySet = new Set(orderedCategoryNames);
  for (const b of buckets) for (const k of b.totals.keys()) categorySet.add(k);
  const categories = [...categorySet];
  // Largest current-period categories first (matches donut/legend ordering).
  categories.sort((a, b) => {
    const ai = orderedCategoryNames.indexOf(a);
    const bi = orderedCategoryNames.indexOf(b);
    if (ai === -1 && bi === -1) return a.localeCompare(b);
    if (ai === -1) return 1;
    if (bi === -1) return -1;
    return ai - bi;
  });

  if (categories.length === 0) {
    catTrend.innerHTML = `<p class="muted">No data.</p>`;
    catTrendLegend.innerHTML = "";
    return;
  }

  const max = Math.max(1, ...buckets.map((b) => [...b.totals.values()].reduce((a, c) => a + c, 0)));
  const W = 600, H = 220, pad = { l: 8, r: 8, t: 12, b: 26 };
  const innerW = W - pad.l - pad.r;
  const innerH = H - pad.t - pad.b;
  const gap = 4;
  const barW = (innerW - gap * (months - 1)) / months;

  const segs = buckets.map((b, gi) => {
    const x = pad.l + gi * (barW + gap);
    let yCursor = pad.t + innerH;
    return categories.map((c, ci) => {
      const v = b.totals.get(c) || 0;
      if (v <= 0) return "";
      const h = (v / max) * innerH;
      yCursor -= h;
      const color = CAT_PALETTE[ci % CAT_PALETTE.length];
      return `<rect class="chart-bar" x="${x.toFixed(2)}" y="${yCursor.toFixed(2)}" width="${barW.toFixed(2)}" height="${h.toFixed(2)}" fill="${color}">
        <title>${escapeHtml(b.d.toLocaleDateString(undefined, { month: "short", year: "numeric" }))} · ${escapeHtml(c)}: ${formatETB(v)}</title>
      </rect>`;
    }).join("");
  }).join("");

  const labels = buckets.map((b, gi) => {
    const x = pad.l + gi * (barW + gap) + barW / 2;
    return `<text x="${x.toFixed(2)}" y="${(H - 6).toFixed(2)}" class="chart-axis-label" text-anchor="middle">${escapeHtml(b.d.toLocaleDateString(undefined, { month: "short" }))}</text>`;
  }).join("");

  catTrend.innerHTML = `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none">${segs}${labels}</svg>`;
  catTrendLegend.innerHTML = categories.map((name, i) => {
    const color = CAT_PALETTE[i % CAT_PALETTE.length];
    return `<div class="legend-row"><span class="swatch" style="background: ${color}"></span>${escapeHtml(name)}</div>`;
  }).join("");
}

function emojiFor(category) {
  const lc = (category || "").toLowerCase();
  if (lc.includes("food"))      return "🍩";
  if (lc.includes("coffee"))    return "☕";
  if (lc.includes("bill"))      return "💡";
  if (lc.includes("loan"))      return "🔄";
  if (lc.includes("drink") || lc.includes("fun")) return "🔥";
  if (lc.includes("transport") || lc.includes("fuel") || lc.includes("car")) return "🛵";
  if (lc.includes("internet") || lc.includes("airtime") || lc.includes("telecom")) return "📶";
  if (lc.includes("rent") || lc.includes("home")) return "🏠";
  if (lc.includes("health") || lc.includes("medic")) return "💊";
  if (lc.includes("shop") || lc.includes("cloth")) return "🛍️";
  return "💳";
}

// ----------------------------------------------------------------------------
// Helpers
// ----------------------------------------------------------------------------
const ETB_FMT = new Intl.NumberFormat("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
function formatNumber(n) { return ETB_FMT.format(Number.isFinite(n) ? n : 0); }
function formatNumberAbs(n) { return formatNumber(Math.abs(n || 0)); }
function formatETB(n) { return `ETB ${formatNumber(n)}`; }
function formatPercent(n) { return `${(Math.abs(n)).toFixed(0)}%`; }

function formatDate(iso) {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString(undefined, {
    day: "numeric", month: "short", year: "numeric",
    hour: "2-digit", minute: "2-digit"
  });
}

function formatRelativeDate(ts) {
  if (!ts) return "—";
  const days = Math.floor((Date.now() - ts) / 864e5);
  if (days <= 0) return "today";
  if (days === 1) return "yesterday";
  if (days < 7) return `${days} days ago`;
  if (days < 31) return `${Math.floor(days / 7)} week${Math.floor(days / 7) === 1 ? "" : "s"} ago`;
  return new Date(ts).toLocaleDateString(undefined, { day: "numeric", month: "short" });
}

function prettyType(type) {
  switch (type) {
    case "CREDIT":       return "Credit";
    case "DEBIT":        return "Debit";
    case "PAYMENT":      return "Payment";
    case "TRANSFER_OUT": return "Transfer";
    case "UNKNOWN":      return "Unknown";
    default:             return type || "—";
  }
}

function groupBy(items, keyFn) {
  const m = new Map();
  for (const it of items) {
    const k = keyFn(it);
    if (!m.has(k)) m.set(k, []);
    m.get(k).push(it);
  }
  return m;
}

function escapeHtml(value) {
  if (value == null) return "";
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}
