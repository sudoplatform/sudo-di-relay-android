/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay.types

import java.util.Date

/**
 * The platform SDK representation of a postbox.
 *
 * @property id Unique identifier of the postbox
 * @property createdAt Date object capturing the time the postbox was created.
 * @property updatedAt Date object capturing the time the postbox was last updated
 * @property ownerId The unique identifier of the signed in user this postbox belongs to.
 * @property sudoId The unique identifier of the sudo this postbox belongs to.
 * @property connectionId Client-provided connection identifier. Unique across all postboxes for an owner.
 * @property isEnabled Indicates whether the postbox is enabled and messages may be written to it.
 * @property serviceEndpoint The endpoint which should be used to send messages to the postbox.
 */

data class Postbox(
    val id: String,
    val createdAt: Date,
    val updatedAt: Date,
    val ownerId: String,
    val sudoId: String,
    val connectionId: String,
    val isEnabled: Boolean,
    val serviceEndpoint: String,
)
