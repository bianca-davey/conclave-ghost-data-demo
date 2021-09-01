@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.r3.conclave.apps.orderbook.types

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.protobuf.ProtoNumber
import java.util.*

@Serializable
enum class BuySell { BUY, SELL }

@Serializable
sealed class OrderOrTrade

@Serializable
data class Order(
        @ProtoNumber(1) val id: Int,
        @ProtoNumber(2) val party: String,
        @ProtoNumber(3) val side: BuySell,
        @ProtoNumber(4) val instrument: String,
        @ProtoNumber(5) val price: Long,
        @ProtoNumber(6) val quantity: Long
) : OrderOrTrade()

@Serializable
data class Trade(
        @ProtoNumber(1) val orderId: Int,
        @ProtoNumber(2) val instrument: String,
        @ProtoNumber(3) val matchingOrder: Order
) : OrderOrTrade()

/**
 * Represents users identifier withing Enclave.
 * Users are clients that connect to the Enclave: Insurer, Data Provider, etc.
 */
@Serializable
data class AccountId(
        val identifier: String // Could be replaced by UUID or another unique identifier.
)

class UUIDSerializer : KSerializer<UUID> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)


        override fun serialize(encoder: Encoder, value: UUID) =
                encoder.encodeString(value.toString())

        override fun deserialize(decoder: Decoder): UUID =
                UUID.fromString(decoder.decodeString())
}

/**
 * Payload sent from Client to Enclave
 */
/*
@Serializable
class Entity {
        // TODO - implement entity type
}
*/

@Serializable
sealed class EntityType

// TODO: Person Entity, testing input table row.
@Serializable
data class Person(
        @ProtoNumber(1) val personId: Int,
        @ProtoNumber(2) val firstName: String,
        @ProtoNumber(3) val lastName: String,
        @ProtoNumber(4) val dOB: String,
        @ProtoNumber(5) val address: String,
        @ProtoNumber(6) val passport: String,
) : EntityType()

/**
 * Payload sent from Enclave to Client
 */
@Serializable
class Warning {
        // TODO - implement warning type
}