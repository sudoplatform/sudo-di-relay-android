/*
 * Copyright © 2021 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay.types

/**
 * The resulting data from a deletion of the postbox with id of [connectionId].
 *
 * @property connectionId the connectionId of the postbox deleted.
 * @property remainingMessageIDs a list of Ids of message's that failed to delete.
 *
 * @since 2021-07-14
 */
data class PostboxDeletionResult(val connectionId: String, val remainingMessageIDs: List<String>)
