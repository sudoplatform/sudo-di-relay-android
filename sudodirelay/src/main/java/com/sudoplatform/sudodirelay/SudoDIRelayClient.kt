/*
 * Copyright © 2021 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.sudoplatform.sudodirelay.appsync.AWSAppSyncClientFactory
import com.sudoplatform.sudodirelay.logging.LogConstants
import com.sudoplatform.sudodirelay.subscription.DIRelayEventSubscriber
import com.sudoplatform.sudodirelay.types.PostboxDeletionResult
import com.sudoplatform.sudodirelay.types.RelayMessage
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import java.util.Objects

/**
 * Interface encapsulating a library for interacting with the Sudo Platform Decentralized Identity Relay service.
 * @sample com.sudoplatform.sudodirelay.samples.Samples.sudoDIRelayClient
 * @since 2021-07-14
 */
interface SudoDIRelayClient {

    companion object {
        /** Create a [Builder] for [SudoDIRelayClient]. */
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder internal constructor() {
        private var context: Context? = null
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

            val appSyncClient = appSyncClient
                ?: AWSAppSyncClientFactory.getClientWithAPIKeyAuth(this@Builder.context!!)

            return DefaultSudoDIRelayClient(
                context = context!!,
                appSyncClient = appSyncClient,
                logger = logger
            )
        }
    }

    /**
     * Defines the exceptions for the decentralized identity relay methods.
     *
     * @property message Accompanying message for the exception.
     * @property cause The cause for the exception.
     */
    sealed class DIRelayException(
        message: String? = null,
        cause: Throwable? = null
    ) : RuntimeException(message, cause) {
        class FailedException(message: String? = null, cause: Throwable? = null) :
            DIRelayException(message = message, cause = cause)

        class InvalidPostboxException(message: String? = null, cause: Throwable? = null) :
            DIRelayException(message = message, cause = cause)

        class InvalidConnectionIDException(message: String? = null, cause: Throwable? = null) :
            DIRelayException(message = message, cause = cause)

        class UnknownException(cause: Throwable) :
            DIRelayException(cause = cause)
    }

    /**
     * Initializes a relay postbox with the given [connectionId].
     *
     * @param connectionId A valid v4 UUID String to identify the postbox.
     */
    @Throws(DIRelayException::class)
    suspend fun createPostbox(connectionId: String)

    /**
     * Stores a message with the text contents of [cipherText] in the relay postbox with ID of [connectionId].
     * The message stored in the postbox will have a timestamp of the current system time, a random
     *  v4 UUID string as messageID, and a direction of OUTBOUND.
     *
     * @param connectionId the postbox identifier to store a message in.
     * @param cipherText the string of text to store.
     * @return the [RelayMessage] that was stored in the postbox.
     */
    @Throws(DIRelayException::class)
    suspend fun storeMessage(connectionId: String, cipherText: String): RelayMessage

    /**
     * Begins an async task to delete the relay postbox with ID of [connectionId].
     *  The [subscriber] of [subscribeToRelayEvents] is notified when this task finishes.
     *
     * @param connectionId the postbox identifier to be deleted.
     */
    @Throws(DIRelayException::class)
    suspend fun deletePostbox(connectionId: String)

    /**
     * Gets a list of all [RelayMessage]s at the postbox with ID of [connectionId].
     *
     * @param connectionId the postbox identifier to get messages from.
     * @return list of [RelayMessage]s at the given postbox, or empty list of nothing found.
     */
    @Throws(DIRelayException::class)
    suspend fun getMessages(connectionId: String): List<RelayMessage>

    /**
     * Subscribes to notifications: of incoming messages and when the postbox has finished
     *  being deleted.
     *
     * @param connectionId The postbox identifier to subscribe to events for.
     * @param subscriber The [DIRelayEventSubscriber] to notify.
     */
    suspend fun subscribeToRelayEvents(
        connectionId: String,
        subscriber: DIRelayEventSubscriber
    )

    /**
     * Unsubscribe from relay events for the postbox with ID [connectionId] so that the subscriber
     *  is no longer notified about incoming messages or postbox deletion events.
     *
     * @param connectionId The postbox identifier to unsubscribe to events from.
     */
    suspend fun unsubscribeToRelayEvents(connectionId: String)

    /**
     * Unsubscribe all subscribers from being notified about relay events.
     */
    suspend fun unsubscribeAll()
}

/**
 * Subscribes to be notified of incoming messages, and/or if the postbox has finished deleting,
 *  at the postbox with ID of [connectionId].
 *
 * @param connectionId The postbox identifier to subscribe to events for.
 * @param onConnectionChange Lambda that is invoked when the subscription connection state changes.
 * @param onMessageIncoming Lambda that is invoked on an incoming [RelayMessage] and is passed
 *  that [RelayMessage].
 * @param onPostboxDeleted Lambda that is invoked when the postbox deletion finishes.
 */
suspend fun SudoDIRelayClient.subscribeToRelayEvents(
    connectionId: String,
    onConnectionChange: (status: DIRelayEventSubscriber.ConnectionState) -> Unit = {},
    onMessageIncoming: (relayMessage: RelayMessage) -> Unit,
    onPostboxDeleted: (postBoxUpdate: PostboxDeletionResult) -> Unit
) =
    subscribeToRelayEvents(
        connectionId,
        object : DIRelayEventSubscriber {
            override fun messageIncoming(message: RelayMessage) {
                onMessageIncoming.invoke(message)
            }

            override fun postBoxDeleted(update: PostboxDeletionResult) {
                onPostboxDeleted.invoke(update)
            }

            override fun connectionStatusChanged(state: DIRelayEventSubscriber.ConnectionState) {
                onConnectionChange.invoke(state)
            }
        }
    )
