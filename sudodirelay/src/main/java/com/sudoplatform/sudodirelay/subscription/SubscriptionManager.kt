/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay.subscription

import com.amazonaws.mobileconnectors.appsync.AppSyncSubscriptionCall
import com.sudoplatform.sudodirelay.types.PostboxDeletionResult
import com.sudoplatform.sudodirelay.types.RelayMessage

/**
 * Manages subscriptions for a specific GraphQL subscription.
 */
internal class SubscriptionManager<T> {

    /**
     * Subscribers - map of connectionIds to Subscribers
     */
    private val subscribers: MutableMap<String, DIRelayEventSubscriber> =
        mutableMapOf()

    /**
     * AppSync subscription watchers mapped to connectionIds
     */
    internal val watchers: MutableMap<String, AppSyncSubscriptionCall<T>> = mutableMapOf()

    /**
     * Thread-safely adds a new watcher to the managers watchers
     *
     * @param connectionId the connectionId the watcher is watching
     * @param watcher the watcher to add
     */
    internal fun addWatcherForConnection(
        connectionId: String,
        watcher: AppSyncSubscriptionCall<T>
    ) {
        synchronized(this) {
            if (!watchers.contains(connectionId)) {
                watchers[connectionId] = watcher
            }
        }
    }

    /**
     * Adds or replaces a subscriber with the specified ID.
     *
     * @param connectionId subscriber ID.
     * @param subscriber subscriber to subscribe.
     */
    internal fun replaceSubscriber(
        connectionId: String,
        subscriber: DIRelayEventSubscriber
    ) {
        synchronized(this) {
            subscribers[connectionId] = subscriber
        }
    }

    /**
     * Removes the subscriber with the specified ID.
     *
     * @param connectionId subscriber ID.
     */
    internal fun removeSubscriber(connectionId: String) {
        synchronized(this) {
            subscribers.remove(connectionId)

            val watcher = watchers.remove(connectionId)
            watcher?.cancel()
        }
    }

    /**
     * Removes all subscribers.
     */
    internal fun removeAllSubscribers() {
        synchronized(this) {
            subscribers.clear()
            watchers.values.forEach {
                it.cancel()
            }
            watchers.clear()
        }
    }

    /**
     * Notifies subscribers of connectionId of a new [RelayMessage].
     *
     * @param connectionId the connectionId that the incoming message belongs to.
     * @param relayMessage new [RelayMessage].
     */
    internal fun relayMessageIncoming(connectionId: String, relayMessage: RelayMessage) {
        var subscribersToNotify: ArrayList<DIRelayEventSubscriber>
        synchronized(this) {
            // Take a copy of the subscribers to notify in synchronized block
            // but notify outside the block to avoid deadlock.
            subscribersToNotify =
                ArrayList(
                    subscribers.filter { entry ->
                        entry.key == connectionId
                    }.values
                )
        }

        // Notify subscribers.
        for (subscriber in subscribersToNotify) {
            subscriber.messageIncoming(relayMessage)
        }
    }

    /**
     * Notifies subscribers of connectionId of a deleted postbox.
     *
     * @param connectionId the connectionId that the postboxDeleted notification belongs to.
     * @param update status of the deleted postbox.
     */
    internal fun postBoxDeleted(connectionId: String, update: PostboxDeletionResult) {
        var subscribersToNotify: ArrayList<DIRelayEventSubscriber>
        synchronized(this) {
            // Take a copy of the subscribers to notify in synchronized block
            // but notify outside the block to avoid deadlock.
            subscribersToNotify =
                ArrayList(
                    subscribers.filter { entry ->
                        entry.key == connectionId
                    }.values
                )
        }

        // Notify subscribers.
        for (subscriber in subscribersToNotify) {
            subscriber.postBoxDeleted(update)
        }
    }

    /**
     * Notify all subscribers of AppSync subscription connection status change.
     *  If there was an unexpected disconnection, clean up by cancelling watchers and subscribers.
     *  If the disconnection was expected, watchers and subscribers should already be cleaned.
     *
     * @param connectionId the connectionId that's watchers state has changed.
     * @param state connection state.
     * @param expectedChange boolean flag for whether the connection change was expected.
     */
    internal fun connectionStatusChanged(
        connectionId: String,
        state: DIRelayEventSubscriber.ConnectionState,
        expectedChange: Boolean
    ) {
        var subscribersToNotify: ArrayList<DIRelayEventSubscriber>
        synchronized(this) {
            // Take a copy of the subscribers to notify in synchronized block
            // but notify outside the block to avoid deadlock.
            subscribersToNotify =
                ArrayList(
                    subscribers.filter { entry ->
                        entry.key == connectionId
                    }.values
                )

            // If the subscription was disconnected then remove all subscribers.
            if (!expectedChange && state == DIRelayEventSubscriber.ConnectionState.DISCONNECTED) {
                subscribers.remove(connectionId)
                val watcher = watchers.remove(connectionId)
                if (watcher?.isCanceled == false) {
                    watcher.cancel()
                }
            }
        }

        // Notify subscribers.
        for (subscriber in subscribersToNotify) {
            subscriber.connectionStatusChanged(state)
        }
    }
}
