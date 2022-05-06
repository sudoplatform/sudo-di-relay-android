/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay.samples

import android.content.Context
import com.sudoplatform.sudodirelay.BaseTests
import com.sudoplatform.sudodirelay.SudoDIRelayClient
import com.sudoplatform.sudodirelay.subscription.DIRelayEventSubscriber
import com.sudoplatform.sudodirelay.types.PostboxDeletionResult
import com.sudoplatform.sudodirelay.types.RelayMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

/**
 * These are sample snippets of code that are included in the generated documentation. They are
 * placed here in the test code so that at least we know they will compile.
 */
@RunWith(RobolectricTestRunner::class)
@Suppress("UNUSED_VARIABLE")
class Samples : BaseTests() {

    private val context by before { mock<Context>() }

    @Test
    fun mockTest() {
        // Just to keep junit happy
    }

    fun sudoDIRelayClient() {
        val relayClient = SudoDIRelayClient.builder()
            .setContext(context)
            .build()
    }

    fun subscriberInterfaceExample() {
        val subscriber = object : DIRelayEventSubscriber {
            override fun messageIncoming(message: RelayMessage) {
                println("new message incoming! $message")
            }

            override fun postBoxDeleted(update: PostboxDeletionResult) {
                println("post box has deleted with update: $update")
            }

            override fun connectionStatusChanged(state: DIRelayEventSubscriber.ConnectionState) {
                println("connection has changed to state: $state")
            }
        }
    }
}
