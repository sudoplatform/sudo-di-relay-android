/*
 * Copyright © 2021 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * The platform SDK representation of a relay message.
 *
 * @property messageId Unique identifier of the message.
 * @property connectionId Unique identifier of the postbox/connection this message belongs to.
 * @property cipherText Text content of the message.
 * @property direction An enum representation of the message direction (inbound/outbound).
 * @property timestamp Date object capturing the time the message was sent.
 *
 * @since 2021-07-14
 */
@Parcelize
data class RelayMessage(
    val messageId: String,
    val connectionId: String,
    val cipherText: String,
    val direction: Direction,
    val timestamp: Date
) : Parcelable {

    /** The direction of the message */
    enum class Direction {
        /** Message is inbound to the user - message has been received by the user. */
        INBOUND,
        /** Message is outbound to the user - message has been sent by the user. */
        OUTBOUND,
        /** API Evolution - if this occurs, it may mean you need to update the library. */
        UNKNOWN
    }
}
