@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.r3.conclave.demo.ghostdata.enclave

import com.r3.conclave.apps.orderbook.types.*
import com.r3.conclave.common.SHA256Hash
import java.security.PublicKey

/**
 * This is a test plugin we have developed for Conclave applications.
 * It handles user authentication.
 * There are two levels of authentication:
 * Username & Password -> [AccountInfo]
 * Public Key -> [Session] -> [AccountInfo]
 *
 * When a new user connects, he will create a new account and a new session.
 * An account can have 0 to many sessions.
 * Session can be linked to only one account.
 */
class AuthenticationPlugin(private val postMailCallback: (PublicKey, Message, String?) -> Unit) {

    init {
        logInfo("Login Plugin for Enclave is up and running")
    }

    /**
     * Database of user accounts.
     * Accounts ([AccountInfo]) from this map are referenced in the [Session]
     */
    private val registeredAccounts = object : LinkedHashMap<AccountId, AccountInfo>() {}

    /**
     * Hashmap between public keys and user sessions. We use it as a way to store current active sessions.
     * All sessions for the same User are linked to the same [AccountInfo].
     * This allows us to terminate them simultaneously and update [AccountInfo] among all sessions.
     */
    private val userSessions = object : LinkedHashMap<PublicKey?, Session>() {}

    /**
     * Users "signs in" by presenting a username/password pair that was previously known.
     * If a new pair is encountered, we create a new account for this user.
     */
    internal inner class AccountInfo(
        val accountId: AccountId,
        private val password: String,
        val accountData: ByteArray,
    ) {
        fun passwordMatch(password: String): Boolean {
            var bytes1 = password.encodeToByteArray()
            var bytes2 = this.password.encodeToByteArray()

            // Grind a bit to slow down the host if it tries to brute force a login.
            for (i in 0..1024*1024) {
                bytes1 = SHA256Hash.hash(bytes1).bytes
                bytes2 = SHA256Hash.hash(bytes2).bytes
            }

            return compare(bytes1, bytes2)
        }

        /**
         * Compares two byte arrays such that the timing doesn't leak anything about the contents of the arrays. Even if the
         * very first byte do not match, all bytes will be compared regardless. When buffer sizes don't match the number of
         * comparisons is always equal to the largest size.
         */
        private fun compare(first: ByteArray, second: ByteArray): Boolean {
            var answer = true
            for (i in 0 until maxOf(first.size, second.size)) {
                val a: Byte = first[minOf(i, first.size - 1)]
                val b: Byte = second[minOf(i, second.size - 1)]
                answer = answer and (a == b)
            }
            return answer
        }
    }

    class Account internal constructor(private val session: Session) {

        val username = session.accountReference.accountId
        val userData = session.accountReference.accountData

        fun sendMessage(request: Message) = session.sendMessage(request)
        fun sendResponse(response: Response, requestToRespond: Message) = session.sendResponse(response, requestToRespond)

    }

    /** Information relevant to the current session of the user. */
    internal inner class Session(
        val accountReference: AccountInfo,
        private val publicKey: PublicKey,
        /**
         * We use a dynamic routing hint that is allowed to change with the public key for the same user.
         */
        private val routingHint: String?
    ) {
        fun sendMessage(message: Message) {
            postMailCallback(publicKey, message, routingHint)
        }
        fun sendResponse(response: Response, messageToRespond: Message) { response.correlate(messageToRespond); this.sendMessage(response)
        }
    }

    /**
     * If successfully authenticated, we return [StatusResponse] with [StatusCode.OK]
     *
     * If the username already exists and the password match, we say the user has signed in.
     * If the username already exists but the password does not match we through an error.
     * If the username does not exist, we create a new account and new session. We say the user has registered.
     */
    @Synchronized
    fun createSessionOrAccount(loginRequest: SignInRequest, senderKey: PublicKey, routingHint: String?) {
        // Variable that indicates if a user has already been registered in the system
        var previouslyRegistered = true

        // We try to find the account with that [AccountId]
        // In case it is absent we create and add it to the database
        val userAccount = registeredAccounts[loginRequest.accountId] ?:
        AccountInfo(loginRequest.accountId, loginRequest.password, loginRequest.data).also { registeredAccounts[it.accountId] = it; previouslyRegistered = false }


        if (!userAccount.passwordMatch(loginRequest.password))
            throw EnclaveException(StatusCode.Unauthorized.code, "The provided password does not match one in the database.")

        if (previouslyRegistered) {
            // Clear all previous sessions
            userSessions.filter { it.value.accountReference.accountId == userAccount.accountId }
                .map { userSessions.remove(it.key) }
        }
        // Create a new session
        val session = Session(userAccount, senderKey, routingHint)
        // Add the new session to the session list
        userSessions[senderKey] = session

        //Send a message to the requester

        val signedInResponse = if (previouslyRegistered) StatusResponse(StatusCode.OK.code, "Successfully authorised a new public key.")
        else StatusResponse(StatusCode.OK.code, "Successfully registered.")

        session.sendResponse(signedInResponse, loginRequest)

    }

    /**
     * Checks if we have already registered this public key for any user.
     * If we have, we return this users data.
     * We assume that the ability to present the same public key is enough to authenticate one as the user with this registered public key.
     */
    fun authenticate(publicKey: PublicKey): Account? {


        // If we've seen this public key before and they're authenticated already, return the user data.
        // We also check that this user is present in username to userdata database.
        // Response status will be changed during the execution of the following block
        return userSessions[publicKey]?.let { Account(it) }
    }

    /**
     * Looks up user in the account database with the provided [AccountId].
     * Returns user information with the way to send this user messages if the account has been found.
     * Otherwise returns `null`.
     */
    fun getAccount(accountId: AccountId): Account? {
        // Find first session for this account
        val sessionForAccount = userSessions.values.firstOrNull { it.accountReference.accountId == accountId }
        //
        return sessionForAccount?.let { Account(it) }
    }




}