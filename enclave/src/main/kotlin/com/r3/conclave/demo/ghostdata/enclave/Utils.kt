package com.r3.conclave.demo.ghostdata.enclave

import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

/**
 * Exception to be thrown during request handling inside the enclave.
 * These exceptions are to be caught and returned to the user.
 *
 * @param status shows the status of the request when it was thrown
 *
 */
class EnclaveException(val status: Int, override val message: String? = null) : Exception()


/**
 * Login function for inside Enclave
 */
fun logInfo(vararg messages: Any) {
    messages.forEach { println("[ENCLAVE] $it") }
}