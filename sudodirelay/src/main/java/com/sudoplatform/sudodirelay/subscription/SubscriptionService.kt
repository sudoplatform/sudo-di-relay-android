/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay.subscription

import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.mobileconnectors.appsync.AppSyncSubscriptionCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.sudoplatform.sudodirelay.SudoDIRelayClient
import com.sudoplatform.sudodirelay.graphql.OnRelayMessageCreatedSubscription
import com.sudoplatform.sudodirelay.logging.LogConstants
import com.sudoplatform.sudodirelay.types.transformers.MessageTransformer
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Manage the subscriptions of decentralized identity relay event updates.
 */
internal class SubscriptionService(
    private val appSyncClient: AWSAppSyncClient,
    private val userClient: SudoUserClient,
    internal val logger: Logger = Logger(
        LogConstants.SUDOLOG_TAG,
        AndroidUtilsLogDriver(LogLevel.INFO)
    )
) : AutoCloseable {

    companion object {
        private const val ERROR_UNAUTHENTICATED_MSG = "User client does not have subject. Is the user authenticated?"
    }
    private val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            if (throwable !is CancellationException) {
                logger.error("Unhandled scope error $throwable")
                // CancellationException is ignored because they are thrown when cancelling coroutines (not an error)
            }
        }
    )

    private val messageCreatedSubscriptionManager =
        SubscriptionManager<OnRelayMessageCreatedSubscription.Data, MessageSubscriber>()

    suspend fun subscribe(
        id: String,
        subscriber: MessageSubscriber
    ) {

        val userSubject = userClient.getSubject()
            ?: throw SudoDIRelayClient.DIRelayException.AuthenticationException(ERROR_UNAUTHENTICATED_MSG)

        messageCreatedSubscriptionManager.replaceSubscriber(id, subscriber)

        scope.launch {

            if (messageCreatedSubscriptionManager.watcher == null) {
                val watcher = appSyncClient.subscribe(
                    OnRelayMessageCreatedSubscription.builder()
                        .owner(userSubject)
                        .build()
                )
                messageCreatedSubscriptionManager.watcher = watcher
                watcher.execute(
                    MessageCreatedCallback()
                )
            }
        }.join()
    }

    fun unsubscribe(id: String) {
        messageCreatedSubscriptionManager.removeSubscriber(id)
    }

    fun unsubscribeAll() {
        messageCreatedSubscriptionManager.removeAllSubscribers()
    }

    override fun close() {
        unsubscribeAll()
        scope.cancel()
    }

    private inner class MessageCreatedCallback :
        AppSyncSubscriptionCall.Callback<OnRelayMessageCreatedSubscription.Data> {
        override fun onFailure(e: ApolloException) {
            logger.error("OnMessageCreated subscription error $e")
            messageCreatedSubscriptionManager.connectionStatusChanged(
                Subscriber.ConnectionState.DISCONNECTED,
            )
        }

        override fun onResponse(response: Response<OnRelayMessageCreatedSubscription.Data>) {
            scope.launch {
                val message = response.data()?.onRelayMessageCreated()
                    ?: return@launch
                messageCreatedSubscriptionManager.messageCreated(
                    MessageTransformer.toEntityFromMessageCreatedSubscriptionEvent(
                        message
                    )
                )
            }
        }

        override fun onCompleted() {
            messageCreatedSubscriptionManager.connectionStatusChanged(
                Subscriber.ConnectionState.DISCONNECTED,
            )
        }
    }
}
