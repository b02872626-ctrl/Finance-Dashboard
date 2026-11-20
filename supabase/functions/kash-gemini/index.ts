// kash-gemini: sorts a user's payees with Gemini (web dashboard, Habits tab).
//
// Runs as the signed-in user (their JWT, so RLS applies), and only if that
// user turned it on (i_review_decisions kind 'ai_consent', ref 'gemini',
// decision 'yes'). At most DAILY_LIMIT calls per user per day, counted in
// i_events (users can add events but never delete them).
//
// Gets, per payee: an opaque id ("p3"), the name, how many payments, the usual
// amount, the usual hour, the share on weekdays and Kash's guess. Names are
// stripped of digits again here. Nothing the user sent is logged or stored.
//
// Secrets (set by the owner, never in the repo):
//   GEMINI_API_KEY  — Google AI Studio key
//   GEMINI_MODEL    — optional, defaults to DEFAULT_MODEL
// Deploy: supabase functions deploy kash-gemini

import { createClient } from "npm:@supabase/supabase-js@2.119.0";

const DEFAULT_MODEL = "gemini-2.5-flash";
const DAILY_LIMIT = 20;
const MAX_PAYEES = 60;
const KINDS = ["people", "shops", "bills", "airtime", "cash", "loans", "other"];
const HABITS = ["Food", "Coffee", "Transport", "Internet", "Games", "Sport", "Drinks", "Snacks"];

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const reply = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });

type Payee = { id: string; name: string; count: number; usual: number; hour: number; weekdays: number; kind: string };

function cleanPayees(input: unknown): Payee[] | null {
  if (!Array.isArray(input) || input.length === 0 || input.length > MAX_PAYEES) return null;
  const out: Payee[] = [];
  for (const p of input) {
    if (!p || typeof p !== "object") return null;
    const { id, name, count, usual, hour, weekdays, kind } = p as Record<string, unknown>;
    if (typeof id !== "string" || !/^p\d{1,3}$/.test(id)) return null;
    if (typeof name !== "string") return null;
    const safe = name.replace(/[\d*#+]+/g, "").replace(/\s+/g, " ").trim().slice(0, 60);
    const num = (v: unknown, lo: number, hi: number) => typeof v === "number" && Number.isFinite(v) && v >= lo && v <= hi;
    if (!safe || !num(count, 1, 100000) || !num(usual, 0, 1e9) || !num(hour, 0, 23) || !num(weekdays, 0, 100)) return null;
    out.push({ id, name: safe, count: Math.round(count as number), usual: Math.round(usual as number), hour: Math.round(hour as number), weekdays: Math.round(weekdays as number), kind: KINDS.includes(kind as string) ? kind as string : "other" });
  }
  return out;
}

const INSTRUCTIONS = `You help a person in Ethiopia understand where their money goes.
You get the people and places they paid from their bank and mobile-money SMS: a name, how many payments, the usual amount in Ethiopian birr (ETB), the usual hour of day (0-23), the share of payments on weekdays (%), and a first guess from simple name rules.
For each one, say what kind of payee it most likely is:
- people: a person (family, friends, a landlord paid in person, someone they owe)
- shops: a business, restaurant, cafe, shop, hotel, transport or service
- bills: utilities, school fees, insurance, rent paid to a company, bank fees, taxes
- airtime: phone airtime, data or telecom packages
- cash: cash withdrawals, ATMs, agents
- loans: loan apps, credit and repayments
- other: you cannot tell
Ethiopian names are usually a given name and the father's name (for example "Almaz Bekele"); a name that looks like that is people unless the usual amount and hour clearly say otherwise. A name with Plc, S.C., Trading, Hotel, Cafe, Bet or Enterprise is a business.
If the payments look like a daily habit (small, frequent, at a regular hour), also give a short habit name, preferably one of: ${HABITS.join(", ")}. Otherwise habit is null.
"why" is one short plain-English sentence the person will read, about the pattern you saw (no names, no guessing about their private life).
Answer for every id you were given, using exactly that id.`;

const SCHEMA = {
  type: "ARRAY",
  items: {
    type: "OBJECT",
    properties: {
      id: { type: "STRING" },
      kind: { type: "STRING", enum: KINDS },
      habit: { type: "STRING", nullable: true },
      why: { type: "STRING" },
    },
    required: ["id", "kind", "why"],
  },
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return reply(405, { error: "POST only" });

  const key = Deno.env.get("GEMINI_API_KEY");
  if (!key) return reply(503, { error: "not_configured" });

  const auth = req.headers.get("Authorization") || "";
  const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: auth } },
    auth: { persistSession: false },
  });
  const { data: { user } } = await db.auth.getUser();
  if (!user) return reply(401, { error: "signed_out" });

  const consent = await db.from("i_review_decisions").select("decision")
    .eq("user_id", user.id).eq("kind", "ai_consent").eq("ref", "gemini").maybeSingle();
  if (consent.error) return reply(500, { error: "consent_check_failed" });
  if (consent.data?.decision !== "yes") return reply(403, { error: "not_turned_on" });

  const since = new Date(); since.setUTCHours(0, 0, 0, 0);
  const used = await db.from("i_events").select("id", { count: "exact", head: true })
    .eq("user_id", user.id).eq("name", "gemini_sort").gte("created_at", since.toISOString());
  if (used.error) return reply(500, { error: "limit_check_failed" });
  if ((used.count ?? 0) >= DAILY_LIMIT) return reply(429, { error: "daily_limit", limit: DAILY_LIMIT });

  let body: { payees?: unknown };
  try { body = await req.json(); } catch { return reply(400, { error: "bad_json" }); }
  const payees = cleanPayees(body.payees);
  if (!payees) return reply(400, { error: "bad_payees" });

  const logged = await db.from("i_events").insert({ user_id: user.id, name: "gemini_sort", props: { payees: payees.length }, app_version: "web" });
  if (logged.error) return reply(500, { error: "limit_check_failed" });

  const model = Deno.env.get("GEMINI_MODEL") || DEFAULT_MODEL;
  const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": key },
    body: JSON.stringify({
      systemInstruction: { parts: [{ text: INSTRUCTIONS }] },
      contents: [{ role: "user", parts: [{ text: JSON.stringify(payees) }] }],
      generationConfig: { temperature: 0.2, responseMimeType: "application/json", responseSchema: SCHEMA },
    }),
  });
  if (!res.ok) {
    console.warn("Gemini call failed with status", res.status); // status only, never the payload
    return reply(502, { error: "gemini_failed" });
  }
  const answer = await res.json();
  const text = answer?.candidates?.[0]?.content?.parts?.map((p: { text?: string }) => p.text || "").join("") || "";
  let suggestions: unknown;
  try { suggestions = JSON.parse(text); } catch { return reply(502, { error: "gemini_unreadable" }); }
  const ids = new Set(payees.map((p) => p.id));
  const list = Array.isArray(suggestions) ? suggestions.filter((s) => s && ids.has(s.id)) : [];
  return reply(200, { suggestions: list, model });
});
