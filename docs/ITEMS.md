# Myriad — Item Floors & Generation

> Source of truth for the **Hundredfold** item system. The numbers here are a
> contract, enforced by `core/content/.../HundredfoldTest.kt` against the live
> generator in `Hundredfold.kt` — if this doc and the code disagree, the test
> goes red. Do not hand-edit counts; change the pools and let the table follow.

## The binding floor

**Every weapon family ships at least `FAMILY_FLOOR = 100` distinct named entries.**

"Distinct" is counted honestly (MASTER_PLAN §16.5): each entry has a unique id,
a unique name, and unique prose — never a stat reskin. The test asserts all three
plus the per-family floor, so the count can only be met by real content.

## How entries are generated

Each weapon is `prefix × base × suffix`, assembled at pack-construction time
(pure, deterministic, no RNG). Stats add up; tier is derived from total attack so
the power envelope rises monotonically by tier (also test-enforced).

- **5 prefixes** — `ashen` (+0) · `soot-bound` (+1) · `cindered` (+2) · `ember-wrought` (+3) · `molten-hearted` (+4); the upper two also grant +1 defense.
- **5 suffixes** — `of the Drift` · `of Cold Hours` · `of the Root-Choked Dark` · `of Banked Fire` · `of the First Light`; attack/defense riders from +0 to +2.
- **bases** — per family (below), each carrying base attack and its own prose fragment.

So each family yields `bases × 5 × 5` = `bases × 25` entries.

## The table

| family | bases | prefixes | suffixes | count |
|---|---|---|---|---|
| dagger | 5 | 5 | 5 | 125 |
| sword | 6 | 5 | 5 | 150 |
| staff | 6 | 5 | 5 | 150 |
| axe | 5 | 5 | 5 | 125 |
| mace | 5 | 5 | 5 | 125 |
| spear | 5 | 5 | 5 | 125 |
| bow | 5 | 5 | 5 | 125 |
| warhammer | 5 | 5 | 5 | 125 |
| flail | 5 | 5 | 5 | 125 |
| scythe | 5 | 5 | 5 | 125 |
| cleaver | 5 | 5 | 5 | 125 |
| whip | 5 | 5 | 5 | 125 |

**12 families · 1550 generated weapons · +1 capstone (The First Ember) = 1551.**

Every family clears the 100 floor; the smallest is 125.

## Tiers & where they drop

Tier `= clamp(((attack+1)/2), 1..5)`. The Ember Depths (10 floors below the
Collapsed Vault) theme each floor to a different family and drop tier-appropriate
loot; hoard caches run one tier above the floor. The deepest hoard holds the
capstone, **The First Ember** (atk +7 / def +2).

## Raising the ceiling

To grow the system, add bases or affixes in `Hundredfold.kt`:
- +1 base to a family → +25 entries to that family.
- +1 prefix or suffix → +`(that family's bases × 5)` entries to **every** family at once.

New families follow the same shape (a `Family` with a `bases` list). The floor,
the table test, and the Forge validators all apply automatically — there is no
separate registration step. This is the lever that takes the roster from 1,550
toward the §1 lifetime ceiling.
