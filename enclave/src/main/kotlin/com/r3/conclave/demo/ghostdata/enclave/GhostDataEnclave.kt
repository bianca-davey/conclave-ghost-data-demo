@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.r3.conclave.demo.ghostdata.enclave

import com.r3.conclave.apps.orderbook.types.*
import com.r3.conclave.apps.orderbook.types.Message.Companion.deserialize
import com.r3.conclave.demo.ghostdata.enclave.AuthenticationPlugin.Account
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.mail.EnclaveMail
import java.security.PublicKey

/**
 * Implements a dark pool enclave, with basic user sign-in support.
 */
class GhostDataEnclave : Enclave() {

    private val database = EntityDatabase()

    private val authPlugin = AuthenticationPlugin { publicKey, message, routingHint ->
        postMail(publicKey, message, routingHint)
    }

    init {
        println("Enclave has started!")
    }

    @Synchronized
    override fun receiveMail(id: Long, mail: EnclaveMail, routingHint: String?) {

        val message = mail.bodyAsBytes.deserialize()

        val account = authPlugin.authenticate(mail.authenticatedSender)

        try {
            logInfo("Received ${message::class.simpleName} from ${account?.username?.identifier ?: "Unknown"}")

            when (message) {
                is SignInRequest -> authPlugin.createSessionOrAccount(message, mail.authenticatedSender, routingHint)
                is Response -> processResponse(message, account)
                else -> processMessage(message, account)
            }

        } catch (e: EnclaveException) {
            // If an EnclaveException happened, we send the Status Response with it to the sender.
            val response = StatusResponse(e.status, e.message)

            // To avoid infinite messaging loops we do not reply with error to the [Response] messages
            if (message is Response)
                return

            response.correlate(message)
            postMessage(response, mail.authenticatedSender, routingHint)
        }
    }


    private fun processMessage(request: Message, account: Account?) {

        if (account == null) throw EnclaveException(StatusCode.Unauthorized.code, "All requests must originate from an authenticated entity.")

        val response = when(request) {
            is EntitySubmission -> handleEntitySubmission(request, account)
            else -> StatusResponse(StatusCode.BadRequest.code, "Unsupported request type.")
        }

        account.sendResponse(response, request)
    }

    private fun processResponse(response: Response, account: Account?) {
        // This is a place holder for processing responses
        // We drop them here without any response
    }

    /**
     * Handle entry submission
     * Return a [StatusResponse] with the status.
     */
    private fun handleEntitySubmission(message: Message, account: Account) = try {
        StatusResponse(StatusCode.OK.code, "Entity submission accepted")
    } catch (e: EnclaveException) {
        StatusResponse(e.status, e.message)
    }


    private fun postMail(
        publicKey: PublicKey,
        message: Message,
        routingHint: String?
    ) {
        val encryptedMessage = postOffice(publicKey).encryptMail(message.serialize())
        postMail(encryptedMessage, routingHint)
    }

    /**
     * Function that abstract sending a Message out of the Enclave
     */
    private fun postMessage(message: Message, publicKey: PublicKey, routingHint: String?) {
        val bytes = message.serialize()
        val encryptedBytes = postOffice(publicKey).encryptMail(bytes)
        postMail(encryptedBytes, routingHint)
    }
}

