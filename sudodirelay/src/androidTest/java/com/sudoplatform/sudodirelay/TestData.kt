/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay

import com.sudoplatform.sudoprofiles.Sudo

/**
 * Data used in tests.
 */
object TestData {

    val sudo = Sudo("Mr", "Theodore", "Bear", "Shopping", null, null)

    const val TWO_MINUTE_MS = 2 * 60 * 1000L
}
