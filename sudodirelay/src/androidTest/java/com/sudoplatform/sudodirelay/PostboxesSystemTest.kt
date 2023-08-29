/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudodirelay.TestData.TWO_MINUTE_MS
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.util.Date
import java.util.UUID

/**
 * Test the operation of the [SudoDIRelayClient].
 */
@RunWith(AndroidJUnit4::class)
class PostboxesSystemTest : BaseSystemTest() {

    private val verbose = false
    private val logLevel = if (verbose) LogLevel.VERBOSE else LogLevel.INFO
    private val logger = Logger("di-relay-test", AndroidUtilsLogDriver(logLevel))

    private lateinit var sudoDIRelayClient: SudoDIRelayClient

    private val sudo: Sudo by lazy {
        runBlocking {
            sudoClient.createSudo(TestData.buildTestSudo("postboxes"))
        }
    }

    private val ownershipProof: String by lazy {
        runBlocking {
            sudoClient.getOwnershipProof(sudo, "sudoplatform.relay.postbox")
        }
    }

    @Before
    fun init() = runBlocking<Unit> {
        Timber.plant(Timber.DebugTree())

        if (verbose) {
            java.util.logging.Logger.getLogger("com.amazonaws").level =
                java.util.logging.Level.FINEST
            java.util.logging.Logger.getLogger("org.apache.http").level =
                java.util.logging.Level.FINEST
        }

        sudoDIRelayClient = SudoDIRelayClient.builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .setLogger(logger)
            .build()

        // clear data from previously failed runs
        userClient.reset()
    }

    @After
    fun fini() = runBlocking {
        if (userClient.isRegistered() && userClient.isSignedIn()) {
            deregister()
        }
        sudoClient.reset()
        userClient.reset()

        Timber.uprootAll()
    }

    @Test
    fun createPostboxShouldNotThrowWithValidConnectionId() = runBlocking<Unit> {
        registerSignInAndEntitle()

        val connectionId = UUID.randomUUID().toString()
        sudoDIRelayClient.createPostbox(connectionId, ownershipProof)
    }

    @Test
    fun createPostboxShouldThrowWithInvalidConnectionID() = runBlocking<Unit> {
        registerSignInAndEntitle()

        val connectionId = UUID.randomUUID().toString()
        sudoDIRelayClient.createPostbox(connectionId, ownershipProof)

        shouldThrow<SudoDIRelayClient.DIRelayException.InvalidPostboxInputException> {
            sudoDIRelayClient.createPostbox(connectionId, ownershipProof)
        }
    }

    @Test
    fun createPostboxShouldThrowWithInvalidJWT() = runBlocking<Unit> {
        registerSignInAndEntitle()

        val connectionId = UUID.randomUUID().toString()
        val invalidJWT =
            "eyJhbGciOiJIUzI1NiJ9.eyJVc2VybmFtZSI6IkJydWgiLCJCcnVoIjoiSW52YWxpZCIsImV4cCI6MTY1MTAyMzU4OCwiaWF0IjoxNjUxMDIzNTg4fQ.xwRQJdHhhROwRoTI0HnHzG6lJ0kz6rC5-fxjv4dZajI"

        shouldThrow<SudoDIRelayClient.DIRelayException.InvalidTokenException> {
            sudoDIRelayClient.createPostbox(connectionId, invalidJWT)
        }
    }

    @Test
    fun deletePostboxShouldSucceedAndDeletePostbox() = runBlocking<Unit> {
        registerSignInAndEntitle()

        val connectionId = UUID.randomUUID().toString()
        val postbox = sudoDIRelayClient.createPostbox(connectionId, ownershipProof)

        sudoDIRelayClient.deletePostbox(postbox.id)

        shouldThrow<SudoDIRelayClient.DIRelayException.UnauthorizedPostboxException> {
            sudoDIRelayClient.updatePostbox(postbox.id)
        }
    }

    @Test
    fun listPostboxesShouldListInOrderOfTimestamp() = runBlocking<Unit> {
        registerSignInAndEntitle()

        val connectionId1 = UUID.randomUUID().toString()
        val postbox1 = sudoDIRelayClient.createPostbox(connectionId1, ownershipProof)

        val connectionId2 = UUID.randomUUID().toString()
        val postbox2 = sudoDIRelayClient.createPostbox(connectionId2, ownershipProof)

        val connectionId3 = UUID.randomUUID().toString()
        val postbox3 = sudoDIRelayClient.createPostbox(connectionId3, ownershipProof)

        val postboxes = sudoDIRelayClient.listPostboxes()

        postboxes.items.size shouldBe 3

        with(postboxes.items[0]) {
            id shouldBe postbox1.id
            connectionId shouldBe connectionId1
            sudoId shouldBe sudo.id!!
            createdAt.after(Date(Date().time - TWO_MINUTE_MS)) shouldBe true
        }

        with(postboxes.items[1]) {
            id shouldBe postbox2.id
            connectionId shouldBe connectionId2
            sudoId shouldBe sudo.id!!
            createdAt.after(Date(Date().time - TWO_MINUTE_MS)) shouldBe true
        }

        with(postboxes.items[2]) {
            id shouldBe postbox3.id
            connectionId shouldBe connectionId3
            sudoId shouldBe sudo.id!!
            createdAt.after(Date(Date().time - TWO_MINUTE_MS)) shouldBe true
        }
    }

    @Test
    fun updatePostboxShouldHonourIsEnabled() = runBlocking {
        registerSignInAndEntitle()
        val createdPostbox = sudoDIRelayClient.createPostbox(UUID.randomUUID().toString(), ownershipProof, false)

        createdPostbox.isEnabled shouldBe false

        val unchangedPostbox = sudoDIRelayClient.updatePostbox(createdPostbox.id)
        with(unchangedPostbox) {
            id shouldBe createdPostbox.id
            createdAt shouldBe createdPostbox.createdAt
            connectionId shouldBe createdPostbox.connectionId
            isEnabled shouldBe false
        }

        val enabledPostbox = sudoDIRelayClient.updatePostbox(createdPostbox.id, true)
        with(enabledPostbox) {
            id shouldBe createdPostbox.id
            createdAt shouldBe createdPostbox.createdAt
            connectionId shouldBe createdPostbox.connectionId
            isEnabled shouldBe true
        }

        val unchangedEnabledPostbox = sudoDIRelayClient.updatePostbox(createdPostbox.id, true)
        with(unchangedEnabledPostbox) {
            id shouldBe createdPostbox.id
            createdAt shouldBe createdPostbox.createdAt
            connectionId shouldBe createdPostbox.connectionId
            isEnabled shouldBe true
        }
    }
}
