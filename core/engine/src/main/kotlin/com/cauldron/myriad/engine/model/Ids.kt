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
