package com.cauldron.myriad.content

import com.cauldron.myriad.engine.model.Attribute
import com.cauldron.myriad.engine.model.ChoiceDef
import com.cauldron.myriad.engine.model.ChoiceId
import com.cauldron.myriad.engine.model.ExitDef
import com.cauldron.myriad.engine.model.ItemDef
import com.cauldron.myriad.engine.model.ItemId
import com.cauldron.myriad.engine.model.OutcomeEffect
import com.cauldron.myriad.engine.model.Requirement
import com.cauldron.myriad.engine.model.RoomDef
import com.cauldron.myriad.engine.model.RoomId
import com.cauldron.myriad.engine.model.SkillCheck
import com.cauldron.myriad.engine.model.StoryletDef
import com.cauldron.myriad.engine.model.StoryletId

/**
 * M2 — the storylets that make the cellar feel lived-in: tap-a-choice scenes with
 * skill-checks, gated options, and earned weapon caches (so finds feel lucky, and
 * the constellations/attributes matter outside combat).
 */
object CellarStorylets {

    val SEALED_DOOR = StoryletId("sealed_door")
    val EMBER_SHRINE = StoryletId("ember_shrine")
    val GRAVEDIGGER = StoryletId("gravedigger")

    val DOOR_NICHE = RoomId("door_niche")
    val SHRINE_NOOK = RoomId("shrine_nook")
    val GRAVE_NICHE = RoomId("grave_niche")

    private fun choice(
        id: String, label: String,
        requirement: Requirement? = null, check: SkillCheck? = null,
        success: List<OutcomeEffect>, failure: List<OutcomeEffect>? = null,
    ) = ChoiceDef(ChoiceId(id), label, requirement, check, success, failure)

    fun storylets(items: Map<ItemId, ItemDef>): Map<StoryletId, StoryletDef> {
        fun pick(family: String, tier: Int): ItemId =
            items.values.first { it.family == family && it.tier == tier }.id
        val doorPrize = pick("dagger", 2)
        val shrinePrize = pick("staff", 2)
        val graveGift = pick("mace", 2)

        return listOf(
            StoryletDef(
                SEALED_DOOR,
                "A door of black iron fills the alcove, its lock long rusted but unyielding. " +
                    "Something shifts on the far side — or perhaps the dark only wants you to think so.",
                listOf(
                    choice(
                        "door_pick", "Pick the lock", check = SkillCheck(Attribute.CUNNING, 6),
                        success = listOf(
                            OutcomeEffect.Narrate("The lock yields with a tired click. Beyond, a cache no one came back for."),
                            OutcomeEffect.SetFlag("opened_door", 1), OutcomeEffect.GiveItem(doorPrize),
                        ),
                        failure = listOf(OutcomeEffect.Narrate("A needle snaps from the keyhole — you jerk away, but not quite fast enough."), OutcomeEffect.Hurt(5)),
                    ),
                    choice(
                        "door_force", "Force it open", requirement = Requirement.AttributeAtLeast(Attribute.MIGHT, 6),
                        success = listOf(
                            OutcomeEffect.Narrate("The hinges scream and the door folds inward. You take what the dark was keeping."),
                            OutcomeEffect.SetFlag("opened_door", 1), OutcomeEffect.GiveItem(doorPrize),
                        ),
                    ),
                    choice(
                        "door_search", "Search the rubble for a key", check = SkillCheck(Attribute.PERCEPTION, 4),
                        success = listOf(OutcomeEffect.Narrate("A blackened key, under a fallen brick. The door opens to you."), OutcomeEffect.SetFlag("opened_door", 1), OutcomeEffect.GiveItem(doorPrize)),
                        failure = listOf(OutcomeEffect.Narrate("Only ash, and broken stone, and your own breath.")),
                    ),
                    choice("door_leave", "Leave it be", success = listOf(OutcomeEffect.EndStory)),
                ),
            ),
            StoryletDef(
                EMBER_SHRINE,
                "A low shrine of fire-blackened bricks. A bowl at its heart holds a single coal " +
                    "that has not gone out in a very long time. The air around it is almost warm.",
                listOf(
                    choice("shrine_warm", "Warm yourself at the coal",
                        success = listOf(OutcomeEffect.Narrate("You hold your hands to the old ember. The ache of the cold loosens its grip."), OutcomeEffect.Heal(15))),
                    choice("shrine_offer", "Offer it your own warmth",
                        success = listOf(
                            OutcomeEffect.Narrate("You breathe your own heat into the bowl. Something ancient takes notice, and approves."),
                            OutcomeEffect.Hurt(4), OutcomeEffect.GainXp(40), OutcomeEffect.SetFlag("shrine_blessed", 1),
                        )),
                    choice("shrine_pray", "Pray for fortune", check = SkillCheck(Attribute.RESOLVE, 5),
                        success = listOf(OutcomeEffect.Narrate("The coal flares, and in its sudden light you see what you had walked past."), OutcomeEffect.GiveItem(shrinePrize)),
                        failure = listOf(OutcomeEffect.Narrate("The coal only smokes. The dark keeps its counsel."))),
                    choice("shrine_leave", "Bow, and leave it", success = listOf(OutcomeEffect.EndStory)),
                ),
            ),
            StoryletDef(
                GRAVEDIGGER,
                "A woman sits against the wall, a long spade across her knees, ember-light caught in " +
                    "her eyes. \"Not from the cellars, are you,\" she says. It is not a question.",
                listOf(
                    choice("grave_ask", "Ask the way down",
                        success = listOf(OutcomeEffect.Narrate("\"Down and down,\" she says. \"A hundred floors, and a thing at the bottom worth the walk. Mind the fast ones.\""), OutcomeEffect.SetFlag("knows_depths", 1))),
                    choice("grave_fire", "Ask to share her fire",
                        success = listOf(OutcomeEffect.Narrate("She shifts to make room. For a while the dark is only dark, and not so cold."), OutcomeEffect.Heal(12))),
                    choice("grave_intimidate", "Intimidate her", check = SkillCheck(Attribute.MIGHT, 6),
                        success = listOf(OutcomeEffect.Narrate("Something in your face decides her. She slides a heavy thing across the stone and will not meet your eye."), OutcomeEffect.SetFlag("feared", 1), OutcomeEffect.GiveItem(graveGift)),
                        failure = listOf(OutcomeEffect.Narrate("She moves faster than you'd credit — the flat of the spade rings off your skull."), OutcomeEffect.Hurt(6))),
                    choice("grave_leave", "Leave her to her digging", success = listOf(OutcomeEffect.EndStory)),
                ),
            ),
        ).associateBy { it.id }
    }

    fun rooms(): Map<RoomId, RoomDef> = mapOf(
        DOOR_NICHE to RoomDef(
            id = DOOR_NICHE, name = "Sealed Alcove",
            description = "A dead-end alcove, all rust and old iron. The black door waits.",
            exits = listOf(ExitDef("Back — to the passage", EmberCellar.ROOT_PASSAGE)),
            storylet = SEALED_DOOR,
        ),
        SHRINE_NOOK to RoomDef(
            id = SHRINE_NOOK, name = "Shrine Nook",
            description = "A still nook, oddly warm, where someone long ago kept a small fire alive.",
            exits = listOf(ExitDef("Back — to the vault", EmberCellar.COLLAPSED_VAULT)),
            storylet = EMBER_SHRINE,
            haven = true,
            campText = "You rest a while in the shrine's borrowed warmth.",
        ),
        GRAVE_NICHE to RoomDef(
            id = GRAVE_NICHE, name = "Side Niche",
            description = "A cramped niche off the cellar, faint with someone else's firelight.",
            exits = listOf(ExitDef("Back — to the cellar", EmberCellar.ASHEN_CELLAR)),
            storylet = GRAVEDIGGER,
        ),
    )
}
