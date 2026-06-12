package com.cauldron.myriad.engine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Player intents. Everything that can happen, happens through one of these. */
@Serializable
sealed interface Action {
    @Serializable
    @SerialName("look")
    data object Look : Action

    @Serializable
    @SerialName("move")
    data class Move(val to: RoomId) : Action

    @Serializable
    @SerialName("search")
    data object Search : Action

    @Serializable
    @SerialName("take")
    data class Take(val item: ItemId) : Action

    @Serializable
    @SerialName("equip")
    data class Equip(val item: ItemId) : Action

    @Serializable
    @SerialName("attack")
    data object Attack : Action

    @Serializable
    @SerialName("defend")
    data object Defend : Action

    @Serializable
    @SerialName("flee")
    data object Flee : Action
}
