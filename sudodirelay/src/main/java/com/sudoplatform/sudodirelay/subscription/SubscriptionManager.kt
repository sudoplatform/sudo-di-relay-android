/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay.subscription

import com.amazonaws.mobileconnectors.appsync.AppSyncSubscriptionCall
import com.sudoplatform.sudodirelay.types.Message

/**
 * Manages subscriptions for a specific GraphQL subscription.
 */
internal open class SubscriptionManager<T, S : MessageSubscriber> {

    /**
     * Subscribers.
     */
    private val subscribers: MutableMap<String, S> = mutableMapOf()

    /**
     * AppSync subscription watcher.
     */
    internal var watcher: AppSyncSubscriptionCall<T>? = null

    /**
     * Watcher that has not been fully initialized yet. We need to make this
     * distinction because there's a bug in AWSAppSync SDK that causes a crash
     * when a partially initialized watcher is used. This can happen if the
     * subscription creation fails due to a network error. Although the watcher
     * is valid in this situation, it's possible that some internal state is
     * yet to be set by the time the control is returned to the consumer via a
     * callback. We will remove this once AWS has fixed the issue. We are using
     * a separate variable to make the removal easier in the future.
     */
    internal var pendingWatcher: AppSyncSubscriptionCall<T>? = null

    /**
     * Adds or replaces a subscriber with the specified ID.
     *
     * @param id subscriber ID.
     * @param subscriber subscriber to subscribe.
     */
    internal fun replaceSubscriber(id: String, subscriber: S) {
        synchronized(this) {
            subscribers[id] = subscriber
        }
    }

    /**
     * Removes the subscriber with the specified ID.
     *
     * @param id subscriber ID.
     */
    internal fun removeSubscriber(id: String) {
        synchronized(this) {
            subscribers.remove(id)

            if (subscribers.isEmpty()) {
                watcher?.cancel()
                watcher = null
            }
        }
    }

    /**
     * Removes all subscribers.
     */
    internal fun removeAllSubscribers() {
        synchronized(this) {
            subscribers.clear()
            watcher?.cancel()
            watcher = null
        }
    }

    /**
     * Processes AppSync subscription connection status change.
     *
     * @param state connection state.
     */
    internal fun connectionStatusChanged(state: Subscriber.ConnectionState) {
        var subscribersToNotify: ArrayList<S>
        synchronized(this) {
            // Take a copy of the subscribers to notify in synchronized block
            // but notify outside the block to avoid deadlock.
            subscribersToNotify = ArrayList(subscribers.values)

            // If the subscription was disconnected then remove all subscribers.
            if (state == Subscriber.ConnectionState.DISCONNECTED) {
                subscribers.clear()
                if (watcher?.isCanceled == false) {
                    watcher?.cancel()
                }
                watcher = null
                pendingWatcher = null
            }
        }

        // Notify subscribers.
        for (subscriber in subscribersToNotify) {
            runCatching {
                subscriber.connectionStatusChanged(state)
            }
        }
    }

    /**
     * Notifies subscribers of a new [Message].
     *
     * @param message new [Message].
     */
    internal fun messageCreated(message: Message) {
        var subscribersToNotify: ArrayList<MessageSubscriber>
        synchronized(this) {
            // Take a copy of the subscribers to notify in synchronized block
            // but notify outside the block to avoid deadlock.
            subscribersToNotify = ArrayList(subscribers.values)
        }

        // Notify subscribers.
        for (subscriber in subscribersToNotify) {
            runCatching {
                subscriber.messageCreated(message)
            }
        }
    }
}
