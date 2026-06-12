# MYRIAD — Master Plan

> Working title (rename freely). The biggest text-based RPG ever, built native for the B2 Ultra.
> Status: planning complete, pre-M0. This document is the single source of truth; update it when reality wins an argument.

---

## 0. Vision & Pillars

**One line:** An endless, systemic text RPG where a deterministic engine owns the truth and (optionally) AI narrates it beautifully.

Pillars — every feature argument gets settled against these, in order:

1. **The engine never lies.** Every outcome comes from deterministic rules. Prose decorates; it never decides. (This is what makes AI integration safe and saves replayable.)
2. **Depth over graphics.** Every byte and every hour goes into systems and words, not pixels.
3. **Respect the phone.** Instant resume, zero data loss ever, battery-sipping, fully one-handed.
4. **Infinite but authored.** Handcrafted spine, procedural flesh, AI skin. Generated content passes the same validators as written content.

**Non-goals (v1):** graphics beyond Unicode glyphs, multiplayer, real-money anything, real-time mechanics.

---

## 1. What "Biggest Ever" Means — Measurable Targets

"Biggest" is a number or it's a vibe. These are the numbers:

| Dimension | v1 ship | Lifetime target (binding minimums) |
|---|---|---|
| Handcrafted words | 150,000 | 2,000,000+ |
| Template/procedural words | unbounded (seed-stable) | unbounded |
| Handcrafted playtime | 30–40 h | **300+ h slow burn** |
| Procedural playtime | unbounded | unbounded |
| **Ages (eras)** | 1 (Ember Age shard) | 10 — primordial → far-future (§16) |
| **Playable Vessels (characters)** | 3 | **100**, each with kit + personal quest + skins |
| Skills/perks/tree nodes | 60 | **600+** across constellations (§16) |
| Items | 800 | **8,000+** — incl. ≥100 daggers, ≥120 swords, ≥150 staffs per §16 family floors |
| **Skins** (vessel/weapon/mount) | a handful | **500+** narration-layer cosmetics |
| **Mounts** | 0 | 40+, each with skins |
| Monsters | 150 | 1,000+ across all Ages |
| Zones | 1 region, ~30 areas, deep | 10 Ages × 2–4 regions |
| Quests | 80 (12 branching) | 1,000+ (100 vessel-personal alone) |
| **Menu tabs** | 6 screens | dozens, via TabRegistry (§16) |
| Endings | 4 | 12 per-age + 1 grand |

**Reality check that kills a false blindside:** text is never the storage problem. 1M words ≈ 6–7 MB raw, ~2 MB compressed. War & Peace is ~3 MB. The bottlenecks for "biggest ever" are **authoring throughput, validation tooling, and save-compatibility over years** — that's where this plan spends its effort.

---

## 2. B2 Ultra Device Profile — Facts and Traps

| Fact | Design consequence |
|---|---|
| 1080×2460, 20.5:9 | Portrait-locked. ~390–410 dp usable width → 60–75 char text measure at 16–17 sp. Bottom 40% of screen owns all primary actions (reachability on a 2460 px panel). |
| **24 GB RAM — THE TRAP** | Device RAM ≠ app heap. Android caps per-app heap (query `ActivityManager.memoryClass` / `largeMemoryClass`; expect ~256–512 MB). Design streams from disk; RAM is a cache, never home. Big RAM means generous LRU caches, not "load the world." |
| OXO OS (aggressive OEM skin) | Assume the process is killed the moment you background. Autosave every turn, flush in `onStop`, never rely on `onDestroy`. Resume-from-death must be invisible. |
| High refresh display | Feed scroll must hold frame budget (8.3 ms at 120 Hz). Otherwise the UI should be idle — zero always-running animations. |
| AMOLED | True-black theme as a first-class option (battery + looks). |
| Android 15 / target SDK 35 | Edge-to-edge is enforced — design with `WindowInsets` from day 1. Adopt predictive back. Gesture nav: no critical swipe targets at screen edges. |
| It's "just text" — complacency trap | Text *shaping* is the actual render cost. Cache `AnnotatedString`s, stable LazyColumn keys, windowed history (§7). |

minSdk 29 / targetSdk 35. Pure Kotlin, no native libs (also sidesteps the Play 16 KB page-size requirement entirely if we ever ship there).

---

## 3. Architecture (decided — object now or hold peace until M1 retro)

### Module layout

```
myriad/
├── core/engine/      Pure Kotlin/JVM. Zero Android deps. Rules, state, RNG,
│                     save format, content runtime. Runs on desktop for tests/tools.
├── core/content/     Compiled content packs + schemas.
├── app/              Compose UI shell (single activity).
├── tools/forge/      JVM CLI: content compiler + linters (§10).
├── tools/gauntlet/   Headless simulator that *plays the game* (§11).
└── ai/               (M4) GM narration layer, cleanly severable.
```

### Core decisions, with the why

- **Event-sourced state.** `GameState` changes only via `Action → Event(s) → reduce`. A save is snapshot + journal tail. Buys us: undo, replay, time-travel debugging, perfect bug repro (seed + journal = exact reproduction), and AI-narration caching keyed by event hash. This single decision pays for half the "no errors" promise.
- **Single-threaded game actor.** All mutations on one dispatcher; UI observes `StateFlow`. Concurrency bugs in game state become impossible by construction, not by discipline.
- **Engine-owned RNG.** Implement PCG32/xoroshiro *in the repo* — never `kotlin.random` (algorithm stability across Kotlin versions is not guaranteed, and saves outlive Kotlin versions). **Named split streams** (combat / loot / worldgen / ambient) so adding a feature never shifts existing rolls — the classic roguelike save-breaker. Full RNG state serialized in saves.
- **Integer math only in rules.** No floats in damage/economy (float nondeterminism + rounding drift). `Long` with explicit clamps; overflow property-tested (§11). Endgame multiplicative stacking is where level-99 saves go to die.
- **Storage:** Room/SQLite (WAL) for the world DB + codex search (`@Fts4`); saves as atomic files (§8). Settings in DataStore.
- **Serialization:** kotlinx-serialization (CBOR/protobuf for saves) — with R8 keep rules, and the *minified* build's save roundtrip in the release gate, because R8-stripped serializers are a classic ship-week ambush.
- **DI:** Koin or manual — no Hilt ceremony for a solo project.
- **Navigation:** single-activity Compose, no fragments.

---

## 4. Game Design Catalog — Every Idea, Tiered

Tiers: **[CORE]** in v1 · **[NEXT]** first expansions · **[SOMEDAY]** parked · **[REJECT]** decided against, with reason (so we don't re-litigate in six months).

### Setting & tone (pick one, default first)
- **Dark ember-age fantasy** — a world cooling after the gods burned out; magic is scavenged heat. (Recommended: flexible, fits "cauldron" energy, supports both grim and wry prose.)
- Alternatives considered: sword-&-sorcery pulp, mythic Norse, weird-west, post-magic apocalypse, nautical cosmic horror. All viable; pick by which you can write 1,000 words about tonight without effort.

### Character
- [CORE] 6 attributes; derived stats; races with mechanical + reactivity hooks; classes as starting packages not cages; backgrounds with story hooks; traits/flaws chosen at creation (CK-style, with drawbacks worth taking).
- [CORE] Reputation **vectors** per faction instead of one karma meter (karma meters flatten every interesting choice).
- [NEXT] Scars/injuries with narrative weight; legacy system (your descendant inherits the world state after permadeath).
- [REJECT] Alignment grid — replaced by faction reputation.

### Progression
- [CORE] XP levels + use-based skill micro-progression (both: levels for fanfare, use-skill for texture); perk trees per class + general trees; visible breakpoints.
- [NEXT] Mastery challenges (weapon/school-specific feats); prestige/NG+ with world remix flags.
- [SOMEDAY] Seasonal challenge seeds shared as text strings.

### Combat
- [CORE — SHIPPED M1a, replaces the original turn-based design after playtest feedback] **Tick-ATB, pause-on-ready**: deterministic integer gauges (player 100/tick, monster speed from content) on a visible timeline; the world waits when your gauge fills; monsters **telegraph their next move** (⚠ line) and execute it when their gauge wraps — fast foes act multiple times against heavy swings. Strike menu: Quick (light, fast return, cheap stamina) / Heavy (1.6×, crits on 5–6, slow return, dear stamina) / Brace (halves the next hit, persists until consumed, restores stamina — always legal, preserving the softlock oracle) / Flee. All bookkeeping syncs via a `CombatTicked` event so reduce alone reconstructs state.
- [CORE later] front/back ranks; status-effect matrix with stated stacking rules; elemental interactions beyond rock-paper-scissors (wet+lightning, oil+fire); armor as typed mitigation; **negotiate/intimidate as combat verbs**; morale (enemies rout, beg, betray); boss phases; visible hit/damage odds.
- [NEXT] Weapon movesets/combos; environmental hazards from encounter templates; companion combat AI orders.
- [REJECT] Real-time anything; QTEs (pillar 3 — one-handed, interruptible).

### Loot & economy
- [CORE] Rarity tiers; affix system (prefix/suffix pools per base type); cursed items with informed-risk tells; generated item lore lines; **economy sinks** (repairs-as-gold-not-durability, taxes, tolls, housing) — sinks designed before faucets; regional price variation.
- [CORE-tooling] Inflation tripwire: the Gauntlet (§11) charts gold-per-hour across 10k simulated runs every night; out-of-envelope = red build.
- [NEXT] Set items; trade routes you can invest in; item "memories" (procedural provenance: *"taken from the hand of…"*).
- [REJECT] Durability decay — it's a chore tax, replaced by gold-sink repairs on death/rout only.

### Crafting
- [CORE] Gathering (mining/herbalism/fishing — fishing is [CORE]; it's tradition); recipe discovery by experimentation with a visible "untried combination" matrix (alchemy as actual play, not menus); cooking buffs; enchanting with stated risk.
- [NEXT] Transmutation; workshop upgrades; commissioning NPCs.
- [REJECT] Crafting minigames — friction in text; the *discovery matrix* is the minigame.

### Exploration & world
- [CORE] Zone graph with travel events; Unicode glyph map + fog of reveal; fast-travel unlocks; day/night + weather + seasons affecting encounters and prose; hidden areas behind perception/skills; camping/rest with risk; **dungeon generator** (themed room grammars, key/lock cycles, secrets, one guaranteed "story room" per floor).
- [CORE] World ticks on **game time** (advanced by actions), never wall-clock — kills the clock-cheat/timezone/DST blindside class entirely in v1.
- [NEXT] Faction simulation ticking per game-day (goals, territory, war/peace); rumor propagation that's *true* (rumors reference real sim events); regional events (plague, festival, comet).
- [SOMEDAY] Player housing/stronghold; mounts; naval layer.

### Narrative & quests
- [CORE] 5-act handcrafted spine; hub-and-spoke chapters; branch-and-merge with **flag discipline** (every flag write/read validated by the Forge, §10); choices with delayed consequences that the journal tracks; multiple endings from a major-flags matrix; a **journal that writes itself in prose** (event log → readable saga — event sourcing gives this nearly free).
- [CORE] Companions: recruit, loyalty, banter triggered by world state, one personal quest each; can leave/betray with warning signs.
- [CORE-lite] Radiant side-quest generator from templates, constrained by the same validators as handcrafted content.
- [NEXT] Romance arcs; companion-vs-companion conflicts; epilogue generator (AI-assisted saga summary, §5).
- [REJECT] Voiced/cinematic anything.

### Social systems
- [CORE] Skill checks with **visible odds and stakes before you commit**; persuade/deceive/intimidate as distinct verbs with different consequence textures; theft + heat/jail consequences; religion as faction reputation with favors.
- [NEXT] Disguise; festivals (game-time, not calendar-time).

### Meta & modes
- [CORE] Difficulty settings (separate knobs: combat math / economy harshness / information visibility); permadeath **toggle at creation** (default off); seeded runs (enter a seed string, share it); local achievements; bestiary/codex with completion %; statistics page (event sourcing = free stats).
- [NEXT] Daily/weekly challenge seeds [gated on solving wall-clock integrity — parked deliberately]; "saga card" image export of your run summary.
- [REJECT] Cloud leaderboards (multiplayer non-goal, anti-cheat tarpit).

### Minigames
- [CORE] Dice gambling (Knucklebones-class depth); riddles only as *optional* flavor (translation/ambiguity hazard — never gate progress).
- [NEXT] Collectible card game against NPCs; arena ladder with bet-on-yourself.
- [SOMEDAY] Player-run shop.

**Cut philosophy:** v1 = one region done *deep* with all CORE systems (25–40 h). Breadth ships as expansions. "Biggest ever" is reached by sustained cadence on proven tooling, not a five-year v1.

---

## 5. AI Game-Master Layer (your edge — and optional at runtime)

You've already shipped Claude vision integration in Rogue; this is the same muscle pointed at narration. The architecture makes AI **additive, never load-bearing**:

**Prime directive: the engine owns truth.** AI may only (a) narrate events the engine already computed, (b) translate freeform player input into the *same verbs* a player could tap — which the engine then validates like any input. AI can never mint items, XP, or flags. A hallucination is, at worst, a weird sentence — never a corrupted save.

**Graceful-degradation ladder** (every tier fully playable):
- **T0 — Templates (offline, always works):** Tracery-style grammar prose, seed-stable. This is the floor and it ships first.
- **T1 — Cached AI prose:** narration cached by event-hash; replays and repeated states cost zero tokens and work offline.
- **T2 — Live Haiku:** NPC barks, flavor, minor scene dressing. Cheap, fast.
- **T3 — Live Fable/Opus:** chapter beats, freeform "say/do anything" scenes, the end-of-run epilogue that retells *your* saga.

**Engineering the ladder:**
- Freeform input → tool-use schema → engine verbs; unknown intent gets an in-fiction redirect ("The innkeeper squints, not following.").
- **Cost:** prompt caching; campaign memory = rolling summary + pinned facts (compaction); model routing by beat importance; per-session token budget with a visible in-game meter; hard monthly cap in settings.
- **Latency:** stream tokens straight into the feed (you know this dance from Rogue); optionally precompute likely-next narrations during idle, capped.
- **Hostile input:** player text is untrusted. Hardened system prompt + structured output validation + engine validation of every proposed verb. Prompt injection's blast radius is "odd prose," by design.
- **Outage/airplane mode:** silent fallback to T1 cache → T0 templates. Release gate includes a full airplane-mode session.
- **Key management:** BYO key for the personal build, stored Keystore-wrapped (note: Jetpack `EncryptedSharedPreferences` is deprecated — wrap an AES key in Android Keystore yourself, store ciphertext in DataStore). Key never in the APK, never in logs, redacted from bug-report bundles. If this ever goes public: proxy backend with per-user budgets — non-negotiable.
- **Determinism interplay:** AI prose is decoration cached by event hash, so replays are byte-identical and saves stay deterministic.

**Ship order: offline-first.** T0 at M1; the ladder lands at M4. The game must be excellent with AI off, or the AI is lipstick on a softlock.

---

## 6. UX Specification

### Screen inventory
Title/continue · Character forge · **Main feed** (the game) · Status strip (HP/MP/gold/game-time, pinned) · Verb chips + context actions · Optional command line · Character sheet · Inventory (sort/filter/compare) · Equipment · Map · Quest journal · Codex/bestiary · Crafting bench · Shop · Dialogue view · Save manager · Settings · Achievements · Statistics · Death/epilogue · Seeded-run setup.

### The Living Dark (M1a — supersedes "pure text page")
Playtest feedback ("a dead text page in your face") overrode §14 default 6. The
presentation is now **text + procedural life**: an `EmberField` of ~36 drifting
particles behind everything (draw-phase-only invalidation, parks with the
choreographer when backgrounded); a `CombatScene` HUD in fights (enemy panel with
animated HP bar, pulsing ⚠ telegraph, you/foe ATB timeline lanes, flying damage
popups, crit screen-shake, hit flashes, haptics); kinetic feed (entries breathe in
over 250 ms); animated status strip (HP counts down, low-HP heartbeat, gold rolls).
Chips sleep ~620 ms during the timeline sweep — the pause-on-ready feel.
Bundled illustration packs (curated per Age) land at M3+ as content, not code.

### The feed (where the game lives)
- `LazyColumn`, **windowed history**: in-memory ring of ~500 entries; older history pages in from DB on scroll. An unbounded scrollback is the text-game OOM/jank blindside — capped by design.
- Cached `AnnotatedString` per entry, stable keys, immutable models. Zero recomposition in steady state (verify with Layout Inspector recomposition counts).
- Typewriter effect: **optional, off by default, always tap-to-skip, never gates input.**

### Input
- **Verb-chips-first** (thumb-friendly, discoverable, localizable); context actions on entities (long-press to examine).
- Optional command bar for purists: fuzzy parser, synonyms, typo suggestions (Levenshtein ≤2 → "did you mean"). Parser is additive sugar, never the only path (parsers + localization + emoji graphemes = misery as a sole input).
- `imePadding()`/insets so the keyboard never covers the input — test with keyboard up + font scale 2.0 together.

### Reading & themes
- 16–17 sp default, user scale 0.8–1.6×, line-height 1.5, ~65-char measure, paragraph spacing (not indents).
- Themes: AMOLED true black (default), parchment/sepia, light. No color-only information anywhere.
- Keep-screen-on toggle for long reads.

### Comfort & feel
- One-handed: every primary action reachable in the bottom 40%.
- Haptics on crits/level-ups (subtle, respects system settings). Ambient audio + SFX via SoundPool — all optional, duck on notifications, respect DND/silent.
- Predictive back; back-out-of-game gets a confirm (autosave fires regardless, so rage-quit loses nothing).

### Accessibility (a text RPG can be the best-in-class a11y game — cheap win, real differentiator)
- Full TalkBack semantics on feed entries; **TTS auto-narration mode** (the game reads itself); 48 dp touch targets; AAA contrast on all themes; layouts survive font scale 2.0 without clipping (screenshot-diff test in the release gate).

---

## 7. Performance Budgets (measured, not vibed)

| Metric | Budget | Enforced by |
|---|---|---|
| Cold start → title | < 600 ms | Macrobenchmark + Baseline Profiles |
| Resume from background | < 200 ms | Macrobenchmark |
| Feed scroll | 0 dropped frames steady-state @ high refresh | JankStats in internal builds |
| Engine turn resolution | < 5 ms p99 | JVM microbench in CI |
| New-game worldgen | < 2 s (with progress) | instrumented test |
| Save write | < 50 ms, **never on main thread** | StrictMode debug + trace |
| Base APK / v1 with content | < 30 MB / < 60 MB | CI size check |
| Steady heap | < 200 MB (caches tunable down) | LeakCanary + heap dumps in soak |
| Battery | < 4%/h reading, < 8%/h AI streaming | on-device soak ritual |

Techniques: R8 full mode, Baseline Profiles, cached text layout, stable keys, `derivedStateOf` discipline, no always-on animations, DB indices + FTS, compressed content blobs, bitmap-free UI.

**Ambient exception (M1a):** the EmberField and combat-scene pulses are deliberate, measured always-on animations — single Canvas, time-pure particles, zero per-frame recomposition or allocation, suspended off-screen. Settings kill-switch ships at M1b; the battery budget line above is the tripwire.

---

## 8. Persistence — Zero Data Loss, Ever

- **Autosave:** every event batch (debounced ~500 ms) + synchronous-ish flush in `onStop` + manual slots + **3 rotating backups per slot** + a panic snapshot on uncaught exception (a crash resumes exactly where it happened).
- **Atomic write protocol:** serialize → temp file → `fsync` → atomic rename → `fsync` dir. On load: verify SHA-256; on corruption walk the backup chain and *tell the user which save was used*.
- **Format:** versioned header `{formatVer, contentVer, engineVer, rngState}`; CBOR via kotlinx-serialization. Migrations are pure functions with a **golden-save corpus** — a save file from every released version lives in test fixtures and must load forever. This is the single most important habit for a game meant to run for years.
- **Compaction:** snapshot every N events, truncate journal; target save < 5 MB.
- **Android Auto Backup:** include saves, exclude caches — the **25 MB quota silently truncates** otherwise; compaction keeps us under. Test restore on a wiped emulator before each release.
- **Export/import:** SAF + share sheet, human-readable manifest, import validates `contentVer` and warns, never crashes.
- **Storage-full:** save failure surfaces a calm in-game warning and retries; never silently drops a save (release-gate test fills the disk).
- Save format is device-agnostic from day 1 (no paths, no platform types) so cloud sync stays a [SOMEDAY], not a rewrite.

---

## 9. Determinism & Correctness Discipline

The quiet bug classes that ambush long-lived RPGs, each with its kill:

| Trap | Kill |
|---|---|
| RNG algorithm drift across language versions | Own PRNG in-repo; RNG state in saves |
| New feature shifts existing RNG streams | Named split streams per system |
| Float nondeterminism / rounding drift | Integer-only rules math |
| `Long` overflow at endgame stacking | Explicit clamps + property tests |
| HashMap iteration order leaking into rules | Ordered collections only in engine; property test runs with shuffled hash seeds |
| Turkish-i / locale-sensitive case ops on internal keys | `Locale.ROOT` everywhere internal; lint ban on bare `lowercase()` |
| Emoji/grapheme splitting (player names: "👨‍👩‍👧‍👦") | `BreakIterator` for user text; grapheme-safe truncation; it's in the test corpus |
| Player name into FTS/SQL | Parameterized queries only; name fuzzing test |
| Catastrophic regex in parser | No backtracking-prone patterns; input length caps |
| Wall-clock in rules (DST, timezone, clock cheats) | Game time advances only by actions (v1 has zero wall-clock gameplay) |

---

## 10. Content Pipeline — "The Forge"

Authoring throughput is the real "biggest ever" bottleneck, so the pipeline is a first-class product:

- **Format:** YAML + Markdown hybrid, one file per node/quest/item-table; stable slug IDs. (Ink/Twine considered and rejected: weak hooks into deep systemic state; we own a minimal DSL instead.)
- **Forge compiler:** parse → validate → link → compress → binary pack + symbol report. Content packs carry `contentVer`; **IDs are never deleted, only tombstoned with aliases**, so old saves always resolve.
- **Linters (each one is a blindside, pre-killed):** reference integrity (no dangling item/flag/node), reachability (no orphan nodes — graph analysis), flag write-before-read, loot-table weight sums, skill-check odds within stated bounds, declared-exit symmetry, spellcheck + banned-word list, per-node word counts, `TODO` markers fail release builds.
- **Authoring aids:** hot-reload content into a running debug build; Mermaid/Graphviz quest-graph export; word-count dashboard; Tracery-style template grammar with seed-stable expansion for procedural prose.
- The radiant-quest generator emits through the **same validators** as handcrafted content — generated ≠ exempt.

---

## 11. Testing & QA — "No Errors" Operationalized

**Pyramid:**
1. Engine unit tests — pure JVM, thousands, milliseconds.
2. **Property-based tests** (kotest): save/load roundtrip identity, migration identity across corpus, overflow clamps, RNG stream independence, reducer totality (every event handled from every state).
3. Golden-file tests: `(state, action) → exact events` for story-critical beats.
4. Compose UI tests: creation, save/load, combat, death, process-death-resume.
5. Macrobenchmark perf CI against §7 budgets.
6. On-device soak: 8-hour bot session on the actual B2 Ultra.

**The Gauntlet (the crown jewel):** headless JVM bots play *complete runs* — strategies: random, greedy, speedrun, hoarder, pacifist. Nightly 10k runs assert:
- zero crashes; **zero softlocks** (≥1 legal action in every reachable state — the silent killer of branching RPGs);
- economy inside the inflation envelope; level-curve within bounds; every ending reachable; quest completion rates sane.
- Any failure outputs `seed + strategy + version` → event sourcing replays it **exactly**. Bugs stop being anecdotes.

**Release gate (every release, no exceptions):**
- Minified-build save roundtrip (the R8 ambush) · fresh-install AND upgrade-install with old saves (migration) · Auto Backup restore on wiped emulator · font-scale 2.0 screenshot diff · TalkBack smoke · full airplane-mode session (AI fallback ladder) · storage-full save attempt · `adb shell am kill` mid-combat → perfect resume · clock-change mid-session · 8 h soak on the B2 Ultra with LeakCanary clean.

**In-app bug report:** one tap zips save + journal + logs (key redacted). Event sourcing makes every report perfectly reproducible.

---

## 12. Risk Register — Project-Level Blindsides

| Risk | Likelihood | Impact | Mitigation / tripwire |
|---|---|---|---|
| **Scope spiral** (the actual #1 killer of "biggest ever") | High | Fatal | Pillars + tier tags are binding; M1 vertical-slice gate before any content scale-out; new ideas enter §4 as [NEXT], not the sprint |
| Solo burnout | High | Fatal | Tooling before content; sustainable words/week cadence set at M3, measured not hoped; weekly on-device play ritual keeps it fun |
| Save-compat break ships | Med | Severe | Golden-save corpus + release gate; format versioned from commit 1 |
| Content softlock ships | Med | Severe | Gauntlet nightly + reachability linting |
| OEM kill corrupts state | Med | Severe | Atomic writes + journal + backup chain (§8) |
| Balance collapse at scale (economy/levels) | Med | High | Gauntlet envelopes graphed nightly; red build on drift |
| AI cost runaway | Med | Med | Hard budget caps + visible meter + T0 parity |
| AI dependency creep (game un-fun offline) | Med | High | Offline-first ship order; airplane-mode release gate |
| Compose perf decay as feed grows | Med | Med | JankStats + windowing + budget CI |
| Play-Store-later surprises (target-API ratchet, AI-content disclosure policies, data-safety form) | Low (sideload default) | Med | Decisions logged in §14; revisit only if distribution changes |
| **No version control / single device** | — | Fatal | `git init` is literally step 1 of Day 1 (§15); push to a private remote for off-machine backup |

---

## 13. Roadmap

| Milestone | Scope | Exit criteria (binding) |
|---|---|---|
| **M0 — Skeleton** (1–2 wk) | Modules, engine core (state/events/RNG/save), 1 room, 1 fight, feed UI, autosave | `adb shell am kill` mid-combat on the B2 Ultra → invisible resume. Save roundtrip property test green. |
| **M1a — Living Combat** ✅ 2026-06-12 | Tick-ATB engine (gauges, telegraphs, stamina, multi-act foes), save format v2 + migrations + golden corpus (incl. real device save), CombatScene HUD, EmberField, kinetic feed, ember wisp + Collapsed Vault | All suites green; Gauntlet 1k: 0 crashes/softlocks (88.2% random-bot wins — accepted above the 30–70 band: tutorial room should be survivable; rat speed is the tuning lever); device proof of mid-combat process-death resume |
| **M1b — Vertical slice** (3–4 wk) | One zone deep, 3 classes, 20 items, 5 quests (1 branching), **Forge v1 (storylet/quality format)** + 3 linters, Meter framework (per-Age clocks), settings screen (ambient/haptics toggles), unfolding-tab seed | 2 hours of *actual fun* on device; 1k bot runs zero crash/softlock; all §7 startup budgets green |
| **M2 — Systems complete** (4–6 wk) | All [CORE] systems, migrations live, TTS mode, full release gate passing | Release-gate checklist 100%; first golden save archived |
| **M3 — Content scale-out** (ongoing) | Region-by-region; radiant generator; cadence | Weekly Gauntlet balance report; word-count dashboard trending to targets |
| **M4 — AI-GM alpha** | T0→T3 ladder, budget meter, caching | Airplane-mode parity; cost within budget over a 10 h campaign |
| **M5 — RC & release** | Polish, distribution decision executed | Full gate + 8 h soak clean |

Weekly ritual regardless of milestone: Sunday play session on the B2 Ultra, issues filed, one is fixed first thing Monday.

---

## 14. Open Decisions (defaults chosen so work starts now)

1. **Title** — using *Myriad* until you rename.
2. **Setting** — default: dark ember-age fantasy (§4). Override by writing 1,000 words of any other setting tonight; whichever flows wins.
3. **Ship order** — default: offline-first, AI at M4.
4. **Distribution** — default: personal sideload; Play Store re-evaluated at M5.
5. **Permadeath** — default off, toggle at creation.
6. ~~**Presentation** — pure text + Unicode glyphs, zero images~~ **OVERRIDDEN by playtest (2026-06-12):** text + procedural visual life (the Living Dark, §6) now; curated illustration packs per Age at M3+. Cosmetics/skins remain narration-layer (§16).

---

## 15. Day-1 Checklist — ✅ DONE 2026-06-11

(M0 skeleton built and unit-verified; see git history.)

## 16. The Hundredfold Expansion — Ages, Vessels, Wardrobe, Constellations

> Added 2026-06-11 after M0 verified. This section raises the lifetime scope to
> "hundreds of everything, 300+ hours, every era" and defines how that stays
> shippable. v1 scope (§13 M1–M2) is unchanged — these systems land era by era.

### 16.1 The Ages — world structure for "every era and style of life"

The world of Myriad is a stack of **Ages** — strata of one world's deep time, not
separate planets. You descend/ascend between them through Gates (the slow-burn spine).
Each Age is a content-pack cluster with its own item families, monsters, factions,
idioms, and *tech-as-magic register* — the engine is era-agnostic by design.

**Each Age is a different RPG** (Kaelen's directive, 2026-06-12): eras differ in
*kind*, not costume — its own dominant loop, meters, verbs, tabs, and way to live.
The unifier: *every Age teaches a new way to live, and nothing you master is
wasted* — verbs, skills, and contacts carry forward.

| # | Age | Register | **Plays as** |
|---|---|---|---|
| I | **Ember Age** | dark fantasy (current start) | **survival RPG** — hunger/warmth/light meters, scavenge, camp, ration |
| II | Primordial | myth-time | hunt-and-tribe — beast taming, totems, migrations |
| III | Bronze | ancient empires | warband & tribute — raids, oaths, levies |
| IV | Feudal | high medieval | classic questing RPG — guilds, dungeons, chivalry |
| V | Gilded | renaissance / age of sail | merchant explorer — routes, cargo, compounding trade |
| VI | Soot | industrial / steam | **labor life-sim: the factory era** — shifts, quotas, wages, rent, foreman ladder, union-vs-baron politics |
| VII | Neon | modern / noir-arcane | crime RPG — nerve meter, crime ladder, heat, jail/hospital as timed states |
| VIII | Chrome | near-future | **the Manager era** — run a company/crew of NPCs and unlocked Vessels: hire, assign, contracts, payroll |
| IX | Static | far-future / post-human | automation & ascension — delegate everything you once did by hand |
| X | The Hush | outside time (endgame) | roguelike anthology — runs remixing every verb set you've learned |

A genre = a **systems package** (verb set + meter set + tab set + content
register) behind the unchanged Action→Event→reduce core. One generic **Meter
framework** (id, cap, regen source, sinks) powers hunger (I), shift-stamina (VI),
nerve (VII) — see 16.11 for its clocks.

Rules: every Age ships as its own pack cluster (validated by the Forge like any content);
items/monsters/factions are Age-tagged; Gates unlock by spine progress, making pacing a
content dial, not a code change. **Anachronism is a feature** (a plasma katana in the
Feudal Age is a legendary artifact) but always *curated*, never random slop.

### 16.2 Vessels — 100 playable characters

You are the Wanderer — a nameless ember that can inhabit **Vessels**: distinct
characters found, earned, or freed across the Ages. The roster IS the character system:

- Each Vessel = identity (name, epithet, prose voice), base kit (stats, starting verbs),
  **one unique mechanic** (e.g., the Gravedigger hears the dead; the Clockwright rewinds
  her own last turn — capstones ride on event sourcing), a personal questline (~1 h),
  a constellation branch (16.4), and **3+ skins** (16.3).
- Unlocks are *earned in fiction* — quests, secrets, achievements, era milestones. Never
  purchased, never randomized. 100 Vessels × ~1 h personal quests = 100 h of content
  by itself; that's the slow-burn backbone.
- One save, one Wanderer: Vessels share world flags and collections; switching Vessels
  is an in-fiction act at sanctums. Save format: roster bitset + per-vessel progression
  blocks (compact; designed into the M2 migration).
- Engineering floor: Vessel = data (a `VesselDef` in content packs), not code. The
  unique mechanic is the only per-Vessel engineering; budget one new engine verb or
  modifier per Vessel, reused across Vessels where honest.

### 16.3 The Wardrobe — skins in a text game (cosmetics = narration packs)

A skin is a **narration overlay**: it never touches rules math (pillar: the engine never
lies; cosmetics never lie either).

What a skin changes: epithet and description lines · attack/cast verb flavor ("your
blade" → "your smoldering edge weeps sparks") · feed accent color · status-strip glyph ·
victory/death banner art (Unicode) · later, TTS voice parameters and AI style directives
(T2/T3 inherit skin voice automatically — one system, every tier).

- **Vessel skins** (3+ each), **weapon skins** (named weapons get 2+), **mount skins**,
  feed themes, title wordmarks. Target: 500+ total.
- Architecture: a skin is a keyed override table consumed by the Narrator at T0 and
  passed as style context to AI tiers. Forge-validated like all content (no dangling
  keys, register-appropriate word lists per Age).
- All skins are earned unlocks. No monetization, ever (this is a single-player love letter).

### 16.4 Constellations — skill status, upgrades, and trees

Three layers, visible in a full **Skill Status** tab (current values, sources, next breakpoints):

1. **Wanderer constellations** (universal trees, persist across Vessels): Body / Mind /
   **Senses** / Craft / Voice. Nodes do one of four things, each mechanically distinct:
   - **Functions** — unlock *verbs*: Lockpicking → "Pick lock" appears as a chip;
     Tracking → trails appear; Haggling → new shop actions.
   - **Abilities** — combat actives with costs/cooldowns (engine-resolved like any action).
   - **Increases** — numeric ranks with visible breakpoints (never invisible +1%s).
   - **Senses** — text-native perception layers: Keen Eyes reveals hidden exits *as new
     prose in the same room*; Deathsight shows enemy intent lines; Resonance exposes
     item auras. In a text game, a sense literally unlocks sentences — cheapest, most
     extraordinary upgrade class we have. Each sense = a Narrator layer + a legality filter.
2. **Vessel branches** — each of the 100 Vessels has a compact branch (8–15 nodes)
   deepening its unique mechanic.
3. **Age attunements** — per-era tech/magic trees (Soot-Age boilercraft, Chrome-Age
   augment slots) gated by era reputation.

Target: 600+ nodes lifetime. Engineering rule: every node is data referencing a finite
set of engine effects (verb-unlock, ability-def, stat-rank, sense-layer) — new *effect
types* need engine work; new *nodes* never do. Forge lints: orphan nodes, unreachable
tiers, cost curves, duplicate verb grants.

### 16.5 Item families at the hundreds scale

Their floors are binding: **≥100 daggers, ≥120 swords, ≥150 staffs**, and comparable
floors per family (axes, bows, guns (Neon+), implements, armor sets, trinkets, consumables).

- Composition: Age-tagged **bases** (bronze khopesh, arming sword, plasma katana) ×
  curated **affix pools** × hand-named **uniques with lore lines**. Counted honestly:
  a count target is met by *distinct named entries that read differently*, not stat reskins —
  the Forge word-diff lint enforces minimum prose distance between entries.
- "Items that enhance": enchant/augment sockets as separate systems from cosmetic skins —
  power and appearance never share a slot.
- Scale engineering: defs are a few KB each; 8,000 items ≈ small data. The real costs are
  curation throughput (Forge authoring aids, §10) and balance (Gauntlet stat-envelope
  sims per family per Age, §11).

### 16.6 The Stable — mounts

40+ mounts across Ages (ash-mare, root-strider, brass velocipede, grav-skiff, the
Hush-whale). Mechanics: travel-time/encounter-table modifiers, one mount-verb each
(charge, leap, phase), saddlebag capacity, and skins. A mount is a `MountDef` +
narration pack — same data discipline as everything else.

### 16.7 Dozens of tabs — the TabRegistry

UI architecture for "dozens of tabs of things to do" without dozens of bespoke screens:
a **TabRegistry** — tabs are data-registered (id, glyph, title, badge-count provider,
composable factory), lazily composed, searchable from a hub, and gated by progression
(tabs *unlock* — the UI itself is a collection).

Planned roster: Character · Skill Status · Constellations · Inventory · Equipment ·
Wardrobe · Vessels · Stable · Map · Atlas (Ages) · Quests · Journal · Codex · Bestiary ·
Factions · Crafting · Recipes · Alchemy · Enchanting · Collections · Achievements ·
Statistics · Challenges · Seeds · Titles · Relationships · Ledger · Settings · Saves —
and the registry makes the next dozen cheap.

### 16.8 Slow-burn pacing — the honest 300-hour math

Stacked progression loops, each independently satisfying: spine per Age (10 × 12–20 h)
≈ 150 h · Vessel personal quests ≈ 100 h · constellations/attunements maxing ≈ 50 h ·
collections (bestiary %, wardrobe %, stable %, codex %) ≈ 50–100 h · seeded challenge
runs and NG+ remix flags beyond that. Pacing dials live in content (XP curves, gate
costs), tuned by Gauntlet long-run sims — never by invisible grind multipliers.

### 16.9 Genre research — what the Torn family teaches Myriad

| Game | Steal | Skip |
|---|---|---|
| **Torn** | meter-driven check-in cadence; jobs/promotion ladders; crime ladder; multi-day education courses granting permanent perks; jail/hospital as timed states; dozens of small interlocking loops (stocks, properties, races) as tab content | MMO/PvP, player economy, paid refills |
| **Fallen London** | **storylets + qualities as the Forge's native format** — every player-fact a "quality," every piece of content a self-contained storylet gated by and changing qualities; proven past a million words | real-money candle refills |
| **Kingdom of Loathing** | **ascension** — prestige runs with permanent skill carryover (formalizes the Hush age); familiars≈pets; crafting combinatorics as an item-count engine; seasonal content | daily-turn caps as the only pacing |
| **A Dark Room** | **the genre-shift proof** (survival → village manager → map roguelike); the **unfolding UI** — tabs appear as systems unlock, withholding as drama | terminal-minimalism (we chose the Living Dark) |
| **Melvor Idle** | mobile-proven **skill-grid**: 20+ skills, each a tab with its own loop + mastery; offline progression done right — commercial proof of the tab roster | pure-idle combat |
| **King of Dragon Pass** | **management by event-cards with advisors** — opinionated (sometimes wrong) counsel on dilemmas: the Chrome Manager / Soot union-politics pattern; advisors = Vessel cameos | — |
| **NationStates / BitLife / Urban Dead / LoGD** | daily dilemma generator (templates + qualities = cadence without authoring burnout); life-ledger biography; AP-scarcity planning (Ember); daily rhythm | nation-sim scope; real-person content |

Architectural adoptions: (1) storylets/qualities become the Forge format — our
flags were qualities in embryo, the validators map 1:1; (2) the TabRegistry
follows the **unfolding-UI principle** — the game starts tiny and grows in your
hands; (3) **ascension** is the Hush age's formal mechanic.

### 16.10 Meter clocks — DECIDED (per-Age mix, confirmed twice 2026-06-12)

Each Age declares its own clock in the Meter framework: survival **Ember =
game-time** (meters burn per action; zero pressure while the app is closed);
life-sim Ages (**Soot, Neon**, similar) = **real-time** — wall-clock regen
anchored to `elapsedRealtime`, ~16 h offline accrual cap so absence never
trivializes, clock-change forgiveness, refills journaled as events at app-open
so determinism and replay hold. Combat and story stay action-driven game-time
in every Age. Engineering lands with the Meter framework at M1b+.

### 16.11 What this expansion does NOT change

The pillars (§0), the v1 cut (one Age, deep), the engine invariants (§3, §9), the save
discipline (§8), and the tier system: every idea above enters §4's tiers as [NEXT] or
[SOMEDAY] and ships era by era through the M3+ cadence. The skeleton we verified today
is the same skeleton that carries all of it — that was the point of building it first.

## 17. Day-1 Checklist (original, completed)

1. `git init` + commit this plan. Add private remote (GitHub) — off-device backup is non-negotiable for a multi-year project.
2. Gradle skeleton: version catalog, modules per §3, Kotlin-only.
3. `core/engine`: `GameState` / `Action` / `Event` / `reduce()` + PCG32 with named streams + CBOR save roundtrip, all under property test from the first hour.
4. `app`: feed LazyColumn + verb chips + status strip, edge-to-edge insets, true-black theme.
5. Wire autosave + process-death resume. **M0's exit test is the first thing built, not the last.**
6. Keep §4 open in a tab; every "ooh what if" gets a tier tag, not a detour.
