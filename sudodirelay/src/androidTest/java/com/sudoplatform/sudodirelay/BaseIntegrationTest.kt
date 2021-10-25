/*
 * Copyright © 2021 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import timber.log.Timber

/**
 * Test the operation of the [SudoDIRelayClient].
 *
 * @since 2021-06-24
 */
abstract class BaseIntegrationTest {

    protected val context: Context = ApplicationProvider.getApplicationContext()

    protected fun clientConfigFilesPresent(): Boolean {
        val configFiles = context.assets.list("")?.filter { fileName ->
            fileName == "sudoplatformconfig.json"
        } ?: emptyList()
        Timber.d("config files present ${configFiles.size}")
        return configFiles.size == 1
    }
}
