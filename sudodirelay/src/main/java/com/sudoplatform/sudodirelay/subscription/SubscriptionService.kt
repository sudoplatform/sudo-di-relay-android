/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay.subscription

import com.amplifyframework.api.ApiException
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudodirelay.SudoDIRelayClient
import com.sudoplatform.sudodirelay.graphql.OnRelayMessageCreatedSubscription
import com.sudoplatform.sudodirelay.logging.LogConstants
import com.sudoplatform.sudodirelay.types.transformers.MessageTransformer
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.amplify.GraphQLClient
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
    private val graphQLClient: GraphQLClient,
    private val userClient: SudoUserClient,
    internal val logger: Logger = Logger(
        LogConstants.SUDOLOG_TAG,
        AndroidUtilsLogDriver(LogLevel.INFO),
    ),
) : AutoCloseable {

    companion object {
        private const val ERROR_UNAUTHENTICATED_MSG = "User client does not have subject. Is the user authenticated?"
    }

    private val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            if (throwable !is CancellationException) {
                logger.error("Unhandled scope error $throwable")
            }
        },
    )

    private val messageCreatedSubscriptionManager =
        SubscriptionManager<OnRelayMessageCreatedSubscription.Data, MessageSubscriber>()

    private val messageCreatedCallback = object {
        val onSubscriptionEstablished: (GraphQLResponse<OnRelayMessageCreatedSubscription.Data>) -> Unit = {
            messageCreatedSubscriptionManager.connectionStatusChanged(Subscriber.ConnectionState.CONNECTED)
        }
        val onSubscription: (GraphQLResponse<OnRelayMessageCreatedSubscription.Data>) -> Unit = {
            scope.launch {
                val message = it.data?.onRelayMessageCreated ?: return@launch
                messageCreatedSubscriptionManager.messageCreated(
                    MessageTransformer.toEntityFromMessageCreatedSubscriptionEvent(message),
                )
            }
        }
        val onSubscriptionCompleted = {
            messageCreatedSubscriptionManager.connectionStatusChanged(Subscriber.ConnectionState.DISCONNECTED)
        }
        val onFailure: (ApiException) -> Unit = {
            logger.error("OnMessageCreated subscription error $it")
        }
    }

    suspend fun subscribe(id: String, subscriber: MessageSubscriber) {
        val userSubject = userClient.getSubject()
            ?: throw SudoDIRelayClient.DIRelayException.AuthenticationException(ERROR_UNAUTHENTICATED_MSG)

        messageCreatedSubscriptionManager.replaceSubscriber(id, subscriber)

        scope.launch {
            if (messageCreatedSubscriptionManager.watcher == null) {
                val watcher = graphQLClient.subscribe<OnRelayMessageCreatedSubscription, OnRelayMessageCreatedSubscription.Data>(
                    OnRelayMessageCreatedSubscription.OPERATION_DOCUMENT,
                    mapOf("owner" to userSubject),
                    messageCreatedCallback.onSubscriptionEstablished,
                    messageCreatedCallback.onSubscription,
                    messageCreatedCallback.onSubscriptionCompleted,
                    messageCreatedCallback.onFailure,
                )
                messageCreatedSubscriptionManager.watcher = watcher
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
}
