/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudodirelay.logging.LogConstants
import com.sudoplatform.sudodirelay.subscription.MessageSubscriber
import com.sudoplatform.sudodirelay.subscription.Subscriber
import com.sudoplatform.sudodirelay.types.ListOutput
import com.sudoplatform.sudodirelay.types.Message
import com.sudoplatform.sudodirelay.types.Postbox
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient
import java.util.Objects

/**
 * Interface encapsulating a library for interacting with the Sudo Platform Decentralized Identity Relay service.
 * @sample com.sudoplatform.sudodirelay.samples.Samples.sudoDIRelayClient
 */
interface SudoDIRelayClient {

    companion object {
        /** Create a [Builder] for [SudoDIRelayClient]. */
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder internal constructor() {
        private var context: Context? = null
        private var sudoUserClient: SudoUserClient? = null
        private var appSyncClient: AWSAppSyncClient? = null
        private var logger: Logger =
            Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO))

        /**
         * Provide the application context (required input).
         */
        fun setContext(context: Context) = also {
            this.context = context
        }

        /**
         * Provide the implementation of the [SudoUserClient] used to perform
         * sign in and ownership operations (required input).
         */
        fun setSudoUserClient(sudoUserClient: SudoUserClient) = also {
            this.sudoUserClient = sudoUserClient
        }

        /**
         * Provide an [AWSAppSyncClient] for the [SudoDIRelayClient] to use
         * (optional input). If this is not supplied, an [AWSAppSyncClient] will
         * be constructed and used.
         */
        fun setAppSyncClient(appSyncClient: AWSAppSyncClient) = also {
            this.appSyncClient = appSyncClient
        }

        /**
         * Provide the implementation of the [Logger] used for logging errors (optional input).
         * If a value is not supplied a default implementation will be used.
         */
        fun setLogger(logger: Logger) = also {
            this.logger = logger
        }

        /**
         * Construct the [SudoDIRelayClient]. Will throw a [NullPointerException] if
         * the [context] has not been provided.
         */
        @Throws(NullPointerException::class)
        fun build(): SudoDIRelayClient {
            Objects.requireNonNull(context, "Context must be provided.")
            Objects.requireNonNull(sudoUserClient, "SudoUserClient must be provided.")

            val appSyncClient = appSyncClient ?: ApiClientManager.getClient(
                this@Builder.context!!,
                this@Builder.sudoUserClient!!
            )

            return DefaultSudoDIRelayClient(
                context = context!!,
                appSyncClient = appSyncClient,
                sudoUserClient = sudoUserClient!!,
                logger = logger
            )
        }
    }

    /**
     * Defines the exceptions for the decentralized identity relay methods.
     *
     * @property message [String] Accompanying message for the exception.
     * @property cause [Throwable] The cause for the exception.
     */
    sealed class DIRelayException(
        message: String? = null,
        cause: Throwable? = null
    ) : RuntimeException(message, cause) {
        class FailedException(message: String? = null, cause: Throwable? = null) :
            DIRelayException(message = message, cause = cause)

        class AuthenticationException(message: String? = null, cause: Throwable? = null) :
            DIRelayException(message = message, cause = cause)

        class InvalidPostboxInputException(message: String? = null, cause: Throwable? = null) :
            DIRelayException(message = message, cause = cause)

        class InvalidTokenException(message: String? = null, cause: Throwable? = null) :
            DIRelayException(message = message, cause = cause)

        class UnauthorizedPostboxException(message: String? = null, cause: Throwable? = null) :
            DIRelayException(message = message, cause = cause)

        class UnknownException(cause: Throwable) :
            DIRelayException(cause = cause)
    }

    /**
     * Initializes a relay postbox with the given [connectionId].
     *
     * @param connectionId [String] A valid v4 UUID String to identify the postbox.
     * @param ownershipProofToken [String] Proof of sudo ownership for creating postboxes. The ownership
     *  proof must contain an audience of "sudoplatform.relay.postbox".
     */
    @Throws(DIRelayException::class)
    suspend fun createPostbox(connectionId: String, ownershipProofToken: String, isEnabled: Boolean? = true): Postbox

    /**
     * Request update of the relay postbox with ID of [postboxId].
     *
     * @param postboxId [String] the postbox identifier to be updated.
     * @param isEnabled [Boolean?] the new value for postbox enabled status
     * @return the identifier of the deleted postbox
     */
    @Throws(DIRelayException::class)
    suspend fun updatePostbox(postboxId: String, isEnabled: Boolean? = null): Postbox

    /**
     * Request deletion of the relay postbox with ID of [postboxId].
     *
     * @param postboxId [String] the postbox identifier to be deleted.
     * @return the identifier of the deleted postbox
     */
    @Throws(DIRelayException::class)
    suspend fun deletePostbox(postboxId: String): String

    /**
     * Gets a list of postboxes for the current user.
     *
     * @param limit: [Int?] the maximum number of postboxes to be returned
     * @param nextToken: [String?] pagination result from previous calls, if any
     * @return ListOutput<Postbox> a list of [Postbox]s owned by the current user, if any,
     * along with a pagination token if more are available.
     */
    @Throws(DIRelayException::class)
    suspend fun listPostboxes(limit: Int? = null, nextToken: String? = null): ListOutput<Postbox>

    /**
     * Request deletion of the message with ID of [messageId].
     *
     * @param messageId [String] the message identifier to be deleted.
     */
    @Throws(DIRelayException::class)
    suspend fun deleteMessage(messageId: String)

    /**
     * Gets a list of at most limit [Message]s for the current user.
     *
     * @param limit [Int?] the maximum number of messages to retrieve.
     * @param nextToken: [String?] pagination result from previous calls, if any
     * @return OutputList<Message> list of [Message]s for the current user, and a pagination token.
     */
    @Throws(DIRelayException::class)
    suspend fun listMessages(limit: Int? = null, nextToken: String? = null): ListOutput<Message>

    /**
     * Subscribes to notifications of incoming messages for the current user.
     * Resubscribing with the same identifier will replace the existing subscription.
     *
     * @param subscriberId [String] A unique subscription identifier.
     * @param subscriber The [MessageSubscriber] to notify.
     */
    suspend fun subscribeToRelayEvents(
        subscriberId: String,
        subscriber: MessageSubscriber
    )

    /**
     * Unsubscribe from relay events with the subscription identifier [subscriberId] so that the subscriber
     *  is no longer notified about incoming messages.
     *
     * @param subscriberId [String] The postbox identifier to unsubscribe to events from.
     */
    suspend fun unsubscribeToRelayEvents(subscriberId: String)

    /**
     * Unsubscribe all subscribers from being notified about relay events.
     */
    suspend fun unsubscribeAll()
}

/**
 * Subscribes to be notified of incoming messages.
 *
 * @param subscriberId A unique subscription identifier.
 * @param onConnectionChange Lambda that is invoked when the subscription connection state changes.
 * @param messageCreated Lambda that is invoked on an incoming [Message] and is passed
 *  that [Message].
 */
suspend fun SudoDIRelayClient.subscribeToRelayEvents(
    subscriberId: String,
    onConnectionChange: (status: Subscriber.ConnectionState) -> Unit = {},
    messageCreated: (relayMessage: Message) -> Unit,
) =
    subscribeToRelayEvents(
        subscriberId,
        object : MessageSubscriber {
            override fun messageCreated(message: Message) {
                messageCreated.invoke(message)
            }

            override fun connectionStatusChanged(state: Subscriber.ConnectionState) {
                onConnectionChange.invoke(state)
            }
        }
    )
