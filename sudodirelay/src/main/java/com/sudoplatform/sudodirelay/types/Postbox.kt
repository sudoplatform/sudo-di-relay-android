/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * The platform SDK representation of a postbox.
 *
 * @property connectionId Unique identifier of the postbox/connection.
 * @property userId The unique identifier of the signed in user this postbox belongs to.
 * @property sudoId The unique identifier of the sudo this postbox belongs to.
 * @property timestamp Date object capturing the time the postbox was created.
 */
@Parcelize
data class Postbox(
    val connectionId: String,
    val userId: String,
    val sudoId: String,
    val timestamp: Date
) : Parcelable
