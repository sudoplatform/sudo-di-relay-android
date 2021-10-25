/*
 * Copyright © 2021 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay.subscription

import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.mobileconnectors.appsync.AppSyncSubscriptionCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.sudoplatform.sudodirelay.graphql.OnMessageCreatedSubscription
import com.sudoplatform.sudodirelay.graphql.OnPostBoxDeletedSubscription
import com.sudoplatform.sudodirelay.graphql.type.Direction
import com.sudoplatform.sudodirelay.logging.LogConstants
import com.sudoplatform.sudodirelay.types.transformers.RelayMessageTransformer
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Manage the subscriptions of decentralized identity relay event updates.
 *
 * @since 2021-06-23
 */
internal class DIRelayEventSubscriptionService(
    private val appSyncClient: AWSAppSyncClient,
    internal val logger: Logger = Logger(
        LogConstants.SUDOLOG_TAG,
        AndroidUtilsLogDriver(LogLevel.INFO)
    )
) : AutoCloseable {

    internal val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    internal val messageCreatedSubscriptionManager =
        SubscriptionManager<OnMessageCreatedSubscription.Data>()
    internal val postboxDeletedSubscriptionManager =
        SubscriptionManager<OnPostBoxDeletedSubscription.Data>()

    suspend fun subscribe(
        connectionId: String,
        subscriber: DIRelayEventSubscriber
    ) {

        messageCreatedSubscriptionManager.replaceSubscriber(connectionId, subscriber)
        postboxDeletedSubscriptionManager.replaceSubscriber(connectionId, subscriber)

        scope.launch {
            if (!messageCreatedSubscriptionManager.watchers.contains(connectionId)) {
                val messageCreatedWatcher = appSyncClient.subscribe(
                    OnMessageCreatedSubscription.builder()
                        .direction(Direction.INBOUND)
                        .connectionId(connectionId)
                        .build()
                )
                messageCreatedSubscriptionManager.addWatcherForConnection(
                    connectionId,
                    messageCreatedWatcher
                )

                messageCreatedWatcher.execute(
                    MessageCreatedCallback(
                        connectionId,
                        this@DIRelayEventSubscriptionService
                    )
                )
            }

            if (!postboxDeletedSubscriptionManager.watchers.contains(connectionId)) {
                val postboxDeletedWatcher = appSyncClient.subscribe(
                    OnPostBoxDeletedSubscription.builder()
                        .connectionId(connectionId)
                        .build()
                )
                postboxDeletedSubscriptionManager.addWatcherForConnection(
                    connectionId,
                    postboxDeletedWatcher
                )

                postboxDeletedWatcher.execute(
                    PostboxDeletedCallback(
                        connectionId,
                        this@DIRelayEventSubscriptionService
                    )
                )
            }
        }.join()
    }

    fun unsubscribe(id: String) {
        messageCreatedSubscriptionManager.removeSubscriber(id)
        postboxDeletedSubscriptionManager.removeSubscriber(id)
    }

    fun unsubscribeAll() {
        messageCreatedSubscriptionManager.removeAllSubscribers()
        postboxDeletedSubscriptionManager.removeAllSubscribers()
    }

    override fun close() {
        unsubscribeAll()
        scope.cancel()
    }

    private class MessageCreatedCallback(
        private val connectionId: String,
        private val subscriptionService: DIRelayEventSubscriptionService
    ) :
        AppSyncSubscriptionCall.Callback<OnMessageCreatedSubscription.Data> {
        override fun onFailure(e: ApolloException) {
            subscriptionService.logger.error("RelayMessage created subscription error $e")
            subscriptionService.messageCreatedSubscriptionManager.connectionStatusChanged(
                connectionId,
                DIRelayEventSubscriber.ConnectionState.DISCONNECTED,
                expectedChange = false
            )
        }

        override fun onResponse(response: Response<OnMessageCreatedSubscription.Data>) {
            subscriptionService.scope.launch {
                val newRelayMessage = response.data()?.onMessageCreated()
                    ?: return@launch
                subscriptionService.messageCreatedSubscriptionManager.relayMessageIncoming(
                    connectionId,
                    RelayMessageTransformer.toEntityFromMessageCreatedSubscriptionEvent(
                        newRelayMessage
                    )
                )
            }
        }

        override fun onCompleted() {
            subscriptionService.messageCreatedSubscriptionManager.connectionStatusChanged(
                connectionId,
                DIRelayEventSubscriber.ConnectionState.DISCONNECTED,
                expectedChange = true
            )
        }
    }

    private class PostboxDeletedCallback(
        private val connectionId: String,
        private val subscriptionService: DIRelayEventSubscriptionService
    ) : AppSyncSubscriptionCall.Callback<OnPostBoxDeletedSubscription.Data> {
        override fun onFailure(e: ApolloException) {
            subscriptionService.logger.error("PostBox delete subscription error $e")
            subscriptionService.postboxDeletedSubscriptionManager.connectionStatusChanged(
                connectionId,
                DIRelayEventSubscriber.ConnectionState.DISCONNECTED,
                expectedChange = false
            )
        }

        override fun onResponse(response: Response<OnPostBoxDeletedSubscription.Data>) {
            subscriptionService.scope.launch {
                val deletedPostboxUpdate = response.data()?.onPostBoxDeleted()
                    ?: return@launch
                subscriptionService.postboxDeletedSubscriptionManager.postBoxDeleted(
                    connectionId,
                    RelayMessageTransformer.toEntityFromPostboxDeleted(deletedPostboxUpdate)
                )
            }
        }

        override fun onCompleted() {
            subscriptionService.messageCreatedSubscriptionManager.connectionStatusChanged(
                connectionId,
                DIRelayEventSubscriber.ConnectionState.DISCONNECTED,
                expectedChange = true
            )
        }
    }
}
