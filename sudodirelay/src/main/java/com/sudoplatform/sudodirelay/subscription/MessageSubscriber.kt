/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay.subscription

import com.sudoplatform.sudodirelay.types.Message

/**
 * Subscribers implement this interface to receive notifications of newly created Messages
 *
 * @sample com.sudoplatform.sudodirelay.samples.Samples.subscriberInterfaceExample
 */
interface MessageSubscriber : Subscriber {

    /**
     * Notifies the subscriber of a new [Message]
     *
     * @param message new [Message]
     */
    fun messageCreated(message: Message)
}
