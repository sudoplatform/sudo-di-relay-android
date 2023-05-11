/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay

import com.sudoplatform.sudoprofiles.Sudo

/**
 * Data used in tests.
 */
object TestData {

    val sudo = buildTestSudo("shopping")

    const val TWO_MINUTE_MS = 2 * 60 * 1000L
    fun buildTestSudo(label: String = "Shopping"): Sudo {
        return Sudo("Mr", "Theodore", "Bear", label, null, null)
    }
}
