# Myriad — Progression

> How a Wanderer grows strong enough to clear the Ember Depths. Five
> constellations on a reusable node engine; abilities, senses, and function-verbs
> all derive from unlocked nodes (no save-format cost). Enforced by
> `ConstellationBreadthTest` against the live generator.

## The spine: levels + use-texture

- **XP** from kills, scaled by threat (`maxHp/3 + attack×4 + defense×2 + 8`), then by
  any **Mind** XP bonus.
- **Levels** (triangular `xpToReach(n) = 18·(n−1)·n`) grant +12 HP, +2 attack, +1
  defense, +1 skill point, and **heal to full**.
- **Weapon mastery**: the family you favour gains +1 attack per 12 blows, capped +3.

## The five constellations

Level gains are **stored**; node bonuses are **derived**, so a respec just clears the
unlocked set (at any camp, `50 × level` gold — the first economy sink). Every
node-effect class is used:

| tree | theme | signature nodes |
|---|---|---|
| **Body** | the flesh | Hardy (HP), Iron Skin (−dmg), Brute Force (atk), Deep Lungs, Unbroken; grants Sunder, Second Wind, Cinderbrand |
| **Mind** | the mover | Quick Study (+XP), Economy of Motion (−stamina cost), Quicken/Overclock (−cooldown), Focus; grants Focused Strike, Mending Trance |
| **Senses** | the perceiver | grants all 6 senses + Sharp Eyes (crit), Evasion (−dmg); grants Pre-emptive Strike, Pinpoint, Execute |
| **Craft** | the provider | Scavenger/Hoarder (+gold), Make Do (−stamina); grants the Forage & Kindle verbs, Bloodletting, Caustic Brand |
| **Voice** | the breaker | Presence (atk), Inspire (+XP), Commanding (crit); grants Intimidate, Dread Howl (rout), Rally Cry, War Chant |

~70 prereq-chained nodes, ≥9 per tree (binding floor `NODE_FLOOR`).

## Abilities (14)

Four kinds, all resolved in the tick-ATB engine with stamina + per-fight cooldown:

- **Strikes**: Sunder, Cinderbrand, Focused Strike, Pre-emptive Strike, Pinpoint,
  Caustic Brand, War Chant, Execute (power, defence-ignore, and crit riders vary).
- **Heals**: Second Wind, Mending Trance, Rally Cry.
- **Life-strike**: Bloodletting (heals for a share of damage dealt).
- **Rout**: Intimidate (35%), Dread Howl (60%) — end the fight outright; the foe flees.

## Senses (6) — text-native power

Each owned sense adds an intel line to combat narration (a sense literally unlocks
sentences): **Deathsight** (exact HP), **Foresight** (blow severity), **Aurasight**
(where the guard is thin), **Greedsense** (is the kill worth it), **Tremorsense**
(how fast it acts), **Soulsight** (the insight a death yields).

## Function-verbs (2)

**Forage** and **Kindle** (Craft) restore warmth without a full camp — exploration
sustain between hearths.

## Proven winnable

The Gauntlet `delver` — equips upgrades, levels, builds durability + a heal first
then offense, camps, fights with abilities — clears all 100 floors and claims the
First Ember in ~80% of runs (~level 23), with zero crashes and zero softlocks; the
random fuzzer (2,000 runs) is also clean. Balance is a living dial tracked by the
Gauntlet, not frozen here.
