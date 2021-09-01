@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.r3.conclave.apps.orderbook.types

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.*
import kotlin.properties.Delegates

@Serializable
sealed class Message {

    @Serializable(UUIDSerializer::class)
    var correlationId: UUID = UUID.randomUUID()

    /**
     * Serialize message to a byte array
     */
    fun serialize() = ProtoBuf.encodeToByteArray(serializer(), this)


    /**
     * Deserialize message from a byte array
     */
    companion object { fun ByteArray.deserialize() = ProtoBuf.decodeFromByteArray(serializer(), this) }
}

@Serializable
sealed class Response : Message() {

    @Serializable
    var correlated by Delegates.notNull<Boolean>()

    fun correlate(message: Message) = correlate(message.correlationId)

    fun correlate(correlationId: UUID) {
        this.correlationId = correlationId
        correlated = true
    }

}

/**
 * Message sent from the Client to the Host with Entities
 */
@Serializable
data class EntitySubmission(
    val records: MutableList<Person> = mutableListOf()
) : Message()

/**
 * Message sent from the Host to the Client with Warning generated
 */
@Serializable
data class WarningSubmission(
    val warning: Warning
) : Message()


/**
 * Sign in request from the Bank. Used to update a public key, linked to this account.
 * If user has never signed up, this is used to create a new account within the system.
 * Responses: [StatusResponse]
 */
@Serializable
data class SignInRequest (
    val accountId : AccountId,
    val password : String,
    val data : ByteArray = ByteArray(0)
) : Message() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SignInRequest

        if (accountId != other.accountId) return false
        if (password != other.password) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = accountId.hashCode()
        result = 31 * result + password.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}


/**
 * Enclave response status for SignIn request.
 * @param status is HttpStatus that describes the request status.
 * @param message is an optional message, describing what has happened.
 */
@Serializable
data class StatusResponse (
    val status : Int,
    val message : String? = null,
) : Response()


@Suppress("unused")
enum class StatusCode(val code: Int) {
    Continue(100),
    SwitchingProtocols(101),
    Processing(102),

    OK(200),
    Created(201),
    Accepted(202),
    NonAuthoritativeInformation(203),
    NoContent(204),
    ResetContent(205),
    PartialContent(206),
    MultiStatus(207),
    AlreadyReported(208),
    IMUsed(226),

    MultipleChoices(300),
    MovedPermanently(301),
    Found(302),
    SeeOther(303),
    NotModified(304),
    UseProxy(305),
    TemporaryRedirect(307),
    PermanentRedirect(308),

    BadRequest(400),
    Unauthorized(401),
    PaymentRequired(402),
    Forbidden(403),
    NotFound(404),
    MethodNotAllowed(405),
    NotAcceptable(406),
    ProxyAuthenticationRequired(407),
    RequestTimeout(408),
    Conflict(409),
    Gone(410),
    LengthRequired(411),
    PreconditionFailed(412),
    PayloadTooLarge(413),
    URITooLong(414),
    UnsupportedMediaType(415),
    RangeNotSatisfiable(416),
    ExpectationFailed(417),
    IAmATeapot(418),
    MisdirectedRequest(421),
    UnprocessableEntity(422),
    Locked(423),
    FailedDependency(424),
    UpgradeRequired(426),
    PreconditionRequired(428),
    TooManyRequests(429),
    RequestHeaderFieldsTooLarge(431),
    UnavailableForLegalReasons(451),

    InternalServerError(500),
    NotImplemented(501),
    BadGateway(502),
    ServiceUnavailable(503),
    GatewayTimeout(504),
    HTTPVersionNotSupported(505),
    VariantAlsoNegotiates(506),
    InsufficientStorage(507),
    LoopDetected(508),
    NotExtended(510),
    NetworkAuthenticationRequired(511),

    Unknown(0)
}