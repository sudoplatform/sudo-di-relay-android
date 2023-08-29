/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay.types

import java.util.Date

/**
 * The platform SDK representation of a relay message.
 *
 * @property id Unique identifier of the message
 * @property createdAt Date object capturing the time the message was received.
 * @property updatedAt Date object capturing the time the message was last updated
 * @property ownerId The unique identifier of the signed in user this message belongs to.
 * @property sudoId The unique identifier of the sudo this message belongs to.
 * @property postboxId Unique identifier of the postbox this message belongs to.
 * @property message Text content of the message.
 */

data class Message(
    val id: String,
    val createdAt: Date,
    val updatedAt: Date,
    val ownerId: String,
    val sudoId: String,
    val postboxId: String,
    val message: String
)
