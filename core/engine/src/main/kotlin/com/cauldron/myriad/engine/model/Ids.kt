package com.cauldron.myriad.engine.model

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class RoomId(val value: String)

@Serializable
@JvmInline
value class ItemId(val value: String)

@Serializable
@JvmInline
value class MonsterId(val value: String)

@Serializable
@JvmInline
value class MoveId(val value: String)

@Serializable
@JvmInline
value class MeterId(val value: String)

@Serializable
@JvmInline
value class NodeId(val value: String)

@Serializable
@JvmInline
value class AbilityId(val value: String)

@Serializable
@JvmInline
value class SenseId(val value: String)

@Serializable
@JvmInline
value class VerbId(val value: String)

@Serializable
@JvmInline
value class StoryletId(val value: String)

@Serializable
@JvmInline
value class ChoiceId(val value: String)
