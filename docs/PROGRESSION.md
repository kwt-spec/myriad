# Myriad — Progression

> How a Wanderer grows strong enough to clear the Ember Depths. The Body
> constellation is the first of five trees (MASTER_PLAN §16.4); breadth scales
> next, the way items, the world, and the bestiary did.

## The spine: levels + use-texture

- **XP** comes from kills, scaled by the foe's threat (`maxHp/3 + attack×4 +
  defense×2 + 8`), so deep monsters are worth far more.
- **Levels** follow a triangular curve (`xpToReach(n) = 18·(n−1)·n`). Each level
  grants **+12 max HP, +2 attack, +1 defense, +1 skill point**, and **heals you to
  full** — a real moment of relief mid-delve.
- **Weapon mastery** (use-texture): the weapon family you favour sharpens with use
  — +1 attack per 12 landed blows, capped at +3. Small on purpose; the spine is levels.

## Active abilities (combat)

Learnable in the Body tree, costing stamina with a per-fight cooldown:

| ability | effect | stamina | cooldown |
|---|---|---|---|
| **Sunder** | guard-break strike (×1.8, ignores 6 defence, +10% crit) | 35 | 2 turns |
| **Second Wind** | heal 35% of max HP | 45 | 4 turns |
| **Cinderbrand** | furnace finisher (×3.0, ignores 2 defence, +15% crit) | 55 | 3 turns |

## The Body constellation

14 prereq-chained nodes exercising every node-effect class — stat ranks, ability
grants, and a sense grant:

- **Hardy I/II/III** → +8/+12/+18 max HP · **Unbroken** (capstone) → +30 max HP
- **Iron Skin I/II** → −1/−2 damage taken
- **Deep Lungs** → +40 max stamina · **Brute Force I/II** → +2/+3 attack ·
  **Bloodied Edge** → +12% crit
- **Sunder · Second Wind · Cinderbrand** → grant the abilities above
- **Deathsight** (sense) → combat narration reveals the foe's exact HP and the
  severity of its telegraphed move (a sense literally unlocks sentences, §16.4)

Level gains are **stored**; node bonuses are **derived**, so a respec just changes
the unlocked set and every number follows. **Respec** is available at any camp for
`50 × level` gold — the economy's first real sink (sinks before faucets, §4).

## Proven winnable

The Gauntlet's `delver` strategy — a competent bot that equips upgrades, levels,
spends points on offense and abilities, camps to heal, fights with Sunder/Second
Wind, and descends — **clears all 100 floors and claims the First Ember in 100% of
runs, reaching ~level 25**, with zero crashes and zero softlocks. Deep floors went
from an unwinnable wall to a beatable climb. (Balance is a living dial tracked by
the Gauntlet, not frozen here.)
