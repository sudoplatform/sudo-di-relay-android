/*
 * Copyright © 2021 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay.subscription

import com.sudoplatform.sudodirelay.types.PostboxDeletionResult
import com.sudoplatform.sudodirelay.types.RelayMessage

/**
 * Subscribers implement this interface to receive notifications of incoming RelayMessages and
 *  postbox deletion updates.
 *
 * @sample com.sudoplatform.sudodirelay.samples.Samples.subscriberInterfaceExample
 * @since 2021-07-14
 */
interface DIRelayEventSubscriber {

    /**
     * Connection state of the subscription.
     */
    enum class ConnectionState {

        /**
         * Connected and receiving updates.
         */
        CONNECTED,

        /**
         * Disconnected and won't receive any updates. When disconnected all subscribers will be
         * unsubscribed so the consumer must re-subscribe.
         */
        DISCONNECTED
    }

    /**
     * Notifies the subscriber of a new [RelayMessage]
     *
     * @param message new [RelayMessage]
     */
    fun messageIncoming(message: RelayMessage)

    /**
     * Notifies the subscriber that their postbox deletion request has finished with the update: [update]
     *
     * @param update an update of the deletion
     */
    fun postBoxDeleted(update: PostboxDeletionResult)

    /**
     * Notifies the subscriber that the subscription connection state has changed. The subscriber won't be
     * notified of [RelayMessage] changes until the connection status changes to [ConnectionState.CONNECTED]. The subscriber will
     * stop receiving notifications when the connection state changes to [ConnectionState.DISCONNECTED].
     *
     * @param state connection state.
     */
    fun connectionStatusChanged(state: ConnectionState)
}
