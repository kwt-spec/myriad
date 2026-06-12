# Myriad — Claude Code notes

Text-based RPG for the B2 Ultra. `MASTER_PLAN.md` is the source of truth for
architecture, scope tiers, and the risk register — update it when decisions change.

## Commands
- `./gradlew :core:engine:test :core:content:test` — the test gate (run before any commit)
- `./gradlew :tools:gauntlet:run --args="--runs 1000"` — headless bot runs; fails on crash/softlock, prints repro seed
- `./gradlew :app:assembleDebug` — APK at `app/build/outputs/apk/debug/`
- `adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Modules
- `core/engine` — pure Kotlin/JVM, **zero Android deps**. Rules, RNG, save codec/store.
- `core/content` — content packs (currently `EmberCellar`). Depends only on engine.
- `tools/gauntlet` — headless simulator (random bot).
- `app` — Compose UI shell. Game logic stays out of here.

## Binding invariants (MASTER_PLAN §3, §8, §9)
- Engine is deterministic: same state + action ⇒ same events. No wall-clock, no
  `kotlin.random` (in-repo PCG32 only), no floats in rules math, Long + clamps everywhere.
- RNG streams are named per system (`RngStream`); **append new streams, never reorder**.
- All state changes flow `Action → resolve → Event → reduce`. `reduce` never rolls dice.
- Saves: CBOR + SHA-256 + magic, atomic write + backup rotation. Bump
  `SaveCodec.FORMAT_VERSION` on any `GameState` shape change and add a migration +
  golden save fixture (corpus starts at M2).
- `legalActions` empty ⇔ terminal mode — the Gauntlet's softlock oracle depends on it.
  In combat, Brace and Flee are always legal (no stamina cost) — keep it that way.
- Combat is tick-ATB (M1a): constants in `Engine` companion (GAUGE_MAX 1000, player
  speed 100, recoveries/stamina costs). Monster intents are telegraphed one move
  ahead and serialized in `Mode.Combat`. A final `CombatTicked` event syncs all ATB
  bookkeeping — reduce alone must reconstruct state. `moveFor` resolves missing/
  sentinel move ids to the monster's first move (tombstone-safe).
- `roomStateFor` (not `rooms.getValue`) for room state — old saves lack rooms added
  by newer content packs; it seeds them on demand.
- Content IDs are never deleted, only tombstoned.
- Golden saves live in `src/test/resources/golden/` — never regenerate or edit
  committed fixtures; add new ones per released format version.

## Device target
B2 Ultra: Android 15, OXO OS (aggressive process killer — autosave every turn,
flush in `onStop`), 1080×2460 portrait, AMOLED true-black theme, 24 GB device RAM
but normal app heap (~512 MB) — stream from disk, cache in RAM.
