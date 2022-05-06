/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * The resulting data from a deletion of the postbox with id of [connectionId].
 *
 * @property connectionId the connectionId of the postbox deleted.
 * @property remainingMessageIDs a list of Ids of message's that failed to delete.
 */
@Parcelize
data class PostboxDeletionResult(
    val connectionId: String,
    val remainingMessageIDs: List<String>
) : Parcelable
