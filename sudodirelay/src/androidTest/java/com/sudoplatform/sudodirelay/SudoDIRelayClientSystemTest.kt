/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudodirelay.types.Message
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.awaitility.Awaitility
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Test the operation of the [SudoDIRelayClient].
 */
@RunWith(AndroidJUnit4::class)
class SudoDIRelayClientSystemTest : BaseSystemTest() {

    private val verbose = false
    private val logLevel = if (verbose) LogLevel.VERBOSE else LogLevel.INFO
    private val logger = Logger("di-relay-test", AndroidUtilsLogDriver(logLevel))

    private lateinit var sudoDIRelayClient: SudoDIRelayClient

    private val sudo: Sudo by lazy {
        runBlocking {
            sudoClient.createSudo(TestData.buildTestSudo("relay-client"))
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
    fun shouldThrowIfRequiredItemsNotProvidedToBuilder() {
        // All required items not provided
        shouldThrow<NullPointerException> {
            SudoDIRelayClient.builder().build()
        }
    }

    @Test
    fun shouldNotThrowIfTheRequiredItemsAreProvidedToBuilder() {
        SudoDIRelayClient.builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .build()
    }

    @Test
    fun shouldNotThrowIfAllItemsAreProvidedToBuilder() {
        val appSyncClient = ApiClientManager.getClient(context, userClient)

        SudoDIRelayClient.builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .setAppSyncClient(appSyncClient)
            .setLogger(logger)
            .build()
    }

    @Test
    fun shouldBeAbleToRegisterAndDeregister() = runBlocking<Unit> {
        userClient.isRegistered() shouldBe false
        register()
        userClient.isRegistered() shouldBe true
        signIn()
        userClient.isSignedIn() shouldBe true
        deregister()
        userClient.isRegistered() shouldBe false
    }

    @Test
    fun deleteSudoShouldCleanUp() = runBlocking<Unit> {
        registerSignInAndEntitle()

        val localSudo = sudoClient.createSudo(TestData.buildTestSudo("delete-tester"))
        val localOwnershipProof = sudoClient.getOwnershipProof(localSudo, "sudoplatform.relay.postbox")

        // set up postbox1
        val connectionId1 = UUID.randomUUID().toString()
        val postbox1 = sudoDIRelayClient.createPostbox(connectionId1, localOwnershipProof)

        // set up postbox2
        val connectionId2 = UUID.randomUUID().toString()
        val postbox2 = sudoDIRelayClient.createPostbox(connectionId2, localOwnershipProof)

        delay(2000)

        // post message from postbox1 to postbox2
        val messageFromPostbox1to2 = "hello postbox 2"
        if (!postMessageToEndpoint(messageFromPostbox1to2, postbox2.serviceEndpoint)) {
            fail("http post response with code other than 200..")
        }
        // post message from postbox2 to postbox1
        val messageFromPostbox2to1 = "hello postbox 1"
        if (!postMessageToEndpoint(messageFromPostbox2to1, postbox1.serviceEndpoint)) {
            fail("http post response with code other than 200..")
        }

        // check the listMessages for both postboxes are correct
        val postbox1Messages = getAllMessagesForPostbox(sudoDIRelayClient, postbox1.id)
        val postbox2Messages = getAllMessagesForPostbox(sudoDIRelayClient, postbox2.id)

        postbox1Messages.size shouldBe 1
        postbox2Messages.size shouldBe 1

        // delete sudo
        sudoClient.deleteSudo(localSudo)

        sudoDIRelayClient.deletePostbox(postbox1.id)
        sudoDIRelayClient.deletePostbox(postbox2.id)

        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    val postboxesAfterDeletion = sudoDIRelayClient.listPostboxes(100)
                    val messages1AfterDeletion = getAllMessagesForPostbox(sudoDIRelayClient, postbox1.id)
                    val messages2AfterDeletion = getAllMessagesForPostbox(sudoDIRelayClient, postbox2.id)
                    postboxesAfterDeletion.items.isEmpty()
                    messages1AfterDeletion.isEmpty()
                    messages2AfterDeletion.isEmpty()
                }
            }
    }

    @Test
    fun completeFlowShouldSucceed() = runBlocking<Unit> {
        registerSignInAndEntitle()

        // set up postbox1
        val connectionId1 = UUID.randomUUID().toString()
        val postbox1 = sudoDIRelayClient.createPostbox(connectionId1, ownershipProof)
        val incomingPostbox1Messages = mutableListOf<Message>()

        // set up postbox2
        val connectionId2 = UUID.randomUUID().toString()
        val postbox2 = sudoDIRelayClient.createPostbox(connectionId2, ownershipProof)
        val incomingPostbox2Messages = mutableListOf<Message>()

        // check both postboxes have no messages
        sudoDIRelayClient.listMessages(100).items shouldBe emptyList()

        // subscribe to events for postbox1
        sudoDIRelayClient.subscribeToRelayEvents(
            "forPostbox1",
            {},
            {
                if (it.postboxId == postbox1.id) {
                    incomingPostbox1Messages.add(it)
                }
            }
        )

        // subscribe to events for postbox 2
        sudoDIRelayClient.subscribeToRelayEvents(
            "forPostbox2",
            {},
            {
                if (it.postboxId == postbox2.id) {
                    incomingPostbox2Messages.add(it)
                }
            }
        )

        delay(2000)

        // post message from postbox1 to postbox2
        val messageFromPostbox1to2 = "hello postbox 2"
        if (!postMessageToEndpoint(messageFromPostbox1to2, postbox2.serviceEndpoint)) {
            fail("http post response with code other than 200..")
        }

        // check that postbox2 receives subscription notification within 20seconds
        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    incomingPostbox2Messages.size == 1
                }
            }
        incomingPostbox2Messages[0].message shouldBe messageFromPostbox1to2

        delay(2000)

        // post message from postbox2 to postbox1
        val messageFromPostbox2to1 = "hello postbox 1"
        if (!postMessageToEndpoint(messageFromPostbox2to1, postbox1.serviceEndpoint)) {
            fail("http post response with code other than 200..")
        }

        // check that postbox1 receives subscription notification within 20seconds
        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    incomingPostbox1Messages.size == 1
                }
            }
        incomingPostbox1Messages[0].message shouldBe messageFromPostbox2to1

        // check the listMessages for both postboxes are correct
        val allMessages = sudoDIRelayClient.listMessages(100)
        val postbox1Messages = allMessages.items.filter { it.postboxId == postbox1.id }
        val postbox2Messages = allMessages.items.filter { it.postboxId == postbox2.id }

        postbox1Messages.size shouldBe 1
        postbox2Messages.size shouldBe 1

        // delete postboxes
        sudoDIRelayClient.deletePostbox(postbox1.id)
        sudoDIRelayClient.deletePostbox(postbox2.id)

        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    val messagesAfterDeletion = sudoDIRelayClient.listMessages(100)
                    messagesAfterDeletion.items.isEmpty()
                }
            }
    }
}
