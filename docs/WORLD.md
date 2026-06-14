# Myriad — World Scale: The Ember Depths

> Source of truth for the **Ember Age** map. Enforced by
> `core/content/.../HundredfoldTest.kt` against the generator in
> `Hundredfold.kt`: the numbers below are checked against the live pack, so the
> doc cannot drift from reality.

## Shape

The Ember Age opens with **4 handcrafted rooms** — the Ashen Cellar (start &
hearth), the Root-Choked Passage, the Collapsed Vault, and the Cellar Stair (the
win condition). A cracked stair in the vault drops into the **Ember Depths**: a
**100-floor** procedural descent that is the Age's endless loot-and-survival zone.

Reaching the Cellar Stair wins the Age — the Depths are optional, the place you go
to grow, not a gate. The descent ends at the **First Ember** capstone on floor 100.

## Every floor is a small branching layout

| room | every floor? | role |
|---|---|---|
| Landing | yes | entry from above; a haven (camp) every **3rd** floor |
| Den | yes | the floor's depth-scaled monster (rat or wisp line) |
| Hoard | yes | a cached weapon, one tier above the floor; dead end + stair down |
| Gallery | even floors | a cache side-room off the landing (tier-true find) |
| Shrine | every **5th** floor | an extra ember-camp off the den — keeps deep delving survivable |

`roomsOnFloor = 3 + (even ? 1 : 0) + (multiple-of-5 ? 1 : 0)`

## The numbers (generated, test-verified)

| quantity | value |
|---|---|
| Floors | 100 |
| Generated depths rooms | 370 |
| Handcrafted rooms (incl. 3 storylet niches) | 7 |
| **Total rooms** | **377** |
| **Total monsters** (the bestiary + 2 handcrafted) | **842** |
| Galleries (even floors) | 50 |
| Shrine-camps (every 5th floor) | 20 |

Each floor's den draws a depth-appropriate creature from the generated bestiary
(see `docs/BESTIARY.md` — 840 species), so the descent shows a real menagerie
rather than one scaling monster. Loot follows the species' tier. Survival pressure
(the warmth meter) is real between camps, and camps are frequent enough — landings
every third floor, shrines every fifth — that attentive delving stays sustainable
while careless delving still kills.

## Raising the ceiling

`FLOORS` is one constant. The room-shape rules (`hasGallery`, `hasShrine`,
`landingIsHaven`) are pure functions of depth. Growing the world toward the §16
lifetime targets is changing those rules or adding Ages — every new room flows
through the same reachability/softlock validators automatically, with no separate
registration. Deep floors are intentionally gated by combat difficulty that future
progression systems (levels, constellations) will unlock.
