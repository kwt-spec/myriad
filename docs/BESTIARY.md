# Myriad — The Bestiary

> Source of truth for the Ember Age's creatures. Enforced by
> `core/content/.../BestiaryTest.kt` against the generator in `Bestiary.kt`: the
> numbers below are checked against the live pack, so the doc cannot drift.

## The binding floor

**The bestiary ships at least `SPECIES_FLOOR = 700` distinct species.**

Counted honestly (MASTER_PLAN §1): every species has a unique id, a unique name,
and unique prose — never a stat reskin. The test asserts all three, plus
mechanical validity (positive HP/attack, in-range speed, telegraphed moves, real
loot) on every entry.

## How species are generated

Each creature is `condition × tier × archetype`, assembled at pack construction
(pure, deterministic). Stats scale by tier; identity comes from all three layers.

- **14 archetypes** — cinder rat · wisp · ash hound · ember crawler · ash wraith ·
  slag golem · cinder serpent · ember swarm · soot brute · dark stalker · cinder
  knight · gaping maw · pyre shade · ember drake. Each carries a body plan
  (HP/attack/defense/speed baseline), a weapon-family affinity for its loot, and
  its own telegraphed moveset.
- **6 conditions** — ashen · sooty · cindered · molten · void-singed · hush-touched
  (HP and attack riders, plus flavor).
- **10 tiers** — lesser · common · hale · grown · elder · dire · great · dread ·
  ancient · apex (rising HP multipliers from 100% to 1200%).

So the roster is `14 × 6 × 10` = **840 species**, comfortably past the 700 floor.

## Where they live

The Ember Depths (100 floors) draw each floor's den-dweller from the bestiary:
floor depth picks a tier (`ceil(depth/10)`, so floors 1–10 → tier 1 … 91–100 →
tier 10) and rotates archetype and condition for variety. The dozens of species
not yet placed are the catalogued bestiary that deeper floors, special encounters,
and future Ages pull from. Loot follows the species' tier (mapped to item tiers
1–5), so a dire drake drops dire-worthy gear wherever it is met.

With the bestiary placed, the pack holds **842 monsters** (840 generated + the two
handcrafted tutorial creatures, the cinder rat and ember wisp of the cellar).

## Raising the ceiling

Add an archetype (+60 species), a condition (+140), or a tier (+84) in
`Bestiary.kt`; the floor, the table test, and the Forge validators all apply
automatically. New Ages bring their own bestiaries the same way — this is the lever
toward the §1 lifetime target of 1,000+.
