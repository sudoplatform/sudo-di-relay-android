/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay.subscription

/**
 * Subscribers implement this interface to receive notifications of incoming RelayMessages and
 *  postbox deletion updates.
 *
 * @sample com.sudoplatform.sudodirelay.samples.Samples.subscriberInterfaceExample
 */
interface Subscriber {

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
     * Notifies the subscriber that the subscription connection state has changed. The subscriber won't be
     * notified of changes until the connection status changes to [ConnectionState.CONNECTED]. The subscriber will
     * stop receiving notifications when the connection state changes to [ConnectionState.DISCONNECTED].
     *
     * @param state connection state.
     */
    fun connectionStatusChanged(state: ConnectionState)
}
