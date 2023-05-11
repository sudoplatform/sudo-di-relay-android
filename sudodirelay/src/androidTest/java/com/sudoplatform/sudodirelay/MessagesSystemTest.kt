/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudodirelay.TestData.TWO_MINUTE_MS
import com.sudoplatform.sudodirelay.TestData.buildTestSudo
import com.sudoplatform.sudodirelay.subscription.MessageSubscriber
import com.sudoplatform.sudodirelay.subscription.Subscriber
import com.sudoplatform.sudodirelay.types.Message
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudoprofiles.Sudo
import io.kotlintest.fail
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.awaitility.Awaitility
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.lang.IllegalArgumentException
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Test the operation of the [SudoDIRelayClient].
 */
@RunWith(AndroidJUnit4::class)
class MessagesSystemTest : BaseSystemTest() {

    private val verbose = false
    private val logLevel = if (verbose) LogLevel.VERBOSE else LogLevel.INFO
    private val logger = Logger("di-relay-messages-test", AndroidUtilsLogDriver(logLevel))

    private lateinit var sudoDIRelayClient: SudoDIRelayClient

    private val sudo: Sudo by lazy {
        runBlocking {
            sudoClient.createSudo(buildTestSudo("messages"))
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
    }

    @After
    fun fini() = runBlocking {
        if (userClient.isRegistered() && userClient.isSignedIn()) {
            deregister()
        }
        sudoClient.reset()

        Timber.uprootAll()
    }

    @Test
    fun listMessagesShouldNotFail() = runBlocking<Unit> {
        registerSignInAndEntitle()

        val result = sudoDIRelayClient.listMessages(null, null)
        result.nextToken shouldBe null
        result.items shouldHaveSize 0
    }

    @Test
    fun listMessagesShouldReturnEmptyListForNewPostbox() = runBlocking<Unit> {
        registerSignInAndEntitle()

        val connectionId = UUID.randomUUID().toString()
        val postbox = sudoDIRelayClient.createPostbox(connectionId, ownershipProof)
        val messages = sudoDIRelayClient.listMessages()

        messages.items.size shouldBe 0
    }

    @Test
    fun listMessagesShouldReturnMessagePostedToIt() = runBlocking<Unit> {
        registerSignInAndEntitle()

        val connectionId = UUID.randomUUID().toString()
        val postbox = sudoDIRelayClient.createPostbox(connectionId, ownershipProof)

        if (!postMessageToEndpoint("hi", postbox.serviceEndpoint)) {
            fail("http post response with code other than 200..")
        }

        val messages = sudoDIRelayClient.listMessages(100)
        messages.items.size shouldBe 1
        messages.nextToken shouldBe null

        with(messages.items[0]) {
            try {
                UUID.fromString(id)
            } catch (e: IllegalArgumentException) {
                fail("UUID passed into stored messages is not valid")
            }
            postboxId shouldBe postbox.id
            message shouldBe "hi"
            createdAt.after(Date(Date().time - TWO_MINUTE_MS)) shouldBe true
        }
    }

    @Test
    fun createMessageShouldNotStoreForDisabledPostbox() = runBlocking<Unit> {
        registerSignInAndEntitle()

        val connectionId = UUID.randomUUID().toString()
        val postbox = sudoDIRelayClient.createPostbox(connectionId, ownershipProof)
        var messages = getAllMessagesForPostbox(sudoDIRelayClient, postbox.id)
        messages.size shouldBe 0

        if (!postMessageToEndpoint("hi", postbox.serviceEndpoint)) {
            fail("http post response with code other than 200..")
        }
        messages = getAllMessagesForPostbox(sudoDIRelayClient, postbox.id)
        messages.size shouldBe 1

        val updatedPostbox = sudoDIRelayClient.updatePostbox(postbox.id, false)
        updatedPostbox.isEnabled shouldBe false

        if (!postMessageToEndpoint("there", postbox.serviceEndpoint)) {
            fail("http post response with code other than 200..")
        }

        messages = getAllMessagesForPostbox(sudoDIRelayClient, postbox.id)
        messages.size shouldBe 1
    }

    @Test
    fun deleteMessagesShouldDelete() = runBlocking {
        registerSignInAndEntitle()

        val connectionId = UUID.randomUUID().toString()
        val postbox = sudoDIRelayClient.createPostbox(connectionId, ownershipProof)

        if (!postMessageToEndpoint("hi", postbox.serviceEndpoint)) {
            fail("http post response with code other than 200..")
        }

        val messages = getAllMessagesForPostbox(sudoDIRelayClient, postbox.id)

        with(messages[0]) {
            try {
                UUID.fromString(id)
            } catch (e: IllegalArgumentException) {
                fail("UUID passed into stored messages is not valid")
            }
            postboxId shouldBe postbox.id
            message shouldBe "hi"
            createdAt.after(Date(Date().time - TWO_MINUTE_MS)) shouldBe true
        }

        sudoDIRelayClient.deleteMessage(messages[0].id)

        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    val messagesAfterDeletion = getAllMessagesForPostbox(sudoDIRelayClient, postbox.id)
                    messagesAfterDeletion.isEmpty()
                }
            }
    }

    @Test
    fun listMessagesShouldHonourDefaultPagination() = runBlocking<Unit> {
        registerSignInAndEntitle()

        val connectionId = UUID.randomUUID().toString()
        val postbox = sudoDIRelayClient.createPostbox(connectionId, ownershipProof)

        if (!postMessageToEndpoint("hi", postbox.serviceEndpoint)) {
            fail("http post response with code other than 200..")
        }
        if (!postMessageToEndpoint("there", postbox.serviceEndpoint)) {
            fail("http post response with code other than 200..")
        }

        val messages = sudoDIRelayClient.listMessages()
        messages.items.size shouldBe 1
        messages.nextToken shouldNotBe null

        val messagesForPostbox = messages.items.filter { it.postboxId == postbox.id }

        with(messagesForPostbox[0]) {
            try {
                UUID.fromString(id)
            } catch (e: IllegalArgumentException) {
                fail("UUID passed into stored messages is not valid")
            }
            postboxId shouldBe postbox.id
            message shouldBe "hi"
            createdAt.after(Date(Date().time - TWO_MINUTE_MS)) shouldBe true
        }
    }

    @Test
    fun subscriberShouldInvokeOnMessageCreated() = runBlocking<Unit> {
        registerSignInAndEntitle()

        val connectionId = UUID.randomUUID().toString()
        val postbox = sudoDIRelayClient.createPostbox(connectionId, ownershipProof)

        val messageList = mutableListOf<Message>()

        val subscriber = object : MessageSubscriber {
            override fun messageCreated(message: Message) {
                messageList.add(message)
            }

            override fun connectionStatusChanged(state: Subscriber.ConnectionState) {}
        }

        sudoDIRelayClient.subscribeToRelayEvents("subscription-test", subscriber)

        delay(1000)

        if (!postMessageToEndpoint("hello world", postbox.serviceEndpoint)) {
            fail("http post response with code other than 200..")
        }
        Awaitility.await()
            .atMost(30, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    messageList.size == 1
                }
            }
        messageList[0].message shouldBe "hello world"
    }

    @Test
    fun multipleSubscribersWithDifferentSubscriberIdsShouldSucceed() = runBlocking {
        registerSignInAndEntitle()

        val connectionId1 = UUID.randomUUID().toString()
        val connectionId2 = UUID.randomUUID().toString()
        val postbox1 = sudoDIRelayClient.createPostbox(connectionId1, ownershipProof)
        val postbox2 = sudoDIRelayClient.createPostbox(connectionId2, ownershipProof)

        val messageList1 = mutableListOf<Message>()
        val messageList2 = mutableListOf<Message>()

        sudoDIRelayClient.subscribeToRelayEvents(
            "subscriber-id-1",
            messageCreated = { messageList1.add(it) },
        )
        sudoDIRelayClient.subscribeToRelayEvents(
            "subscriber-id-2",
            messageCreated = { messageList2.add(it) },
        )

        delay(2000)

        if (!postMessageToEndpoint("hello 1", postbox1.serviceEndpoint)) {
            fail("http post response with code other than 200..")
        }
        if (!postMessageToEndpoint("hello 2", postbox2.serviceEndpoint)) {
            fail("http post response with code other than 200..")
        }

        Awaitility.await()
            .atMost(30, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    messageList1.size == 2
                }
            }
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    messageList2.size == 2
                }
            }

        messageList1.size shouldBe 2
        messageList2.size shouldBe 2
        messageList1[0].message shouldBe "hello 1"
        messageList1[0].postboxId shouldBe postbox1.id
        messageList1[1].message shouldBe "hello 2"
        messageList2[1].postboxId shouldBe postbox2.id
    }

    @Test
    fun newSubscriberWithSameIdShouldReplace() = runBlocking<Unit> {
        registerSignInAndEntitle()

        val subscriberId = "subscriber-id"

        val connectionId = UUID.randomUUID().toString()
        val postbox = sudoDIRelayClient.createPostbox(connectionId, ownershipProof)

        val messageList1 = mutableListOf<Message>()
        val messageList2 = mutableListOf<Message>()

        sudoDIRelayClient.subscribeToRelayEvents(
            subscriberId,
            messageCreated = { messageList1.add(it) },
        )

        delay(1000)

        if (!postMessageToEndpoint("hello 1", postbox.serviceEndpoint)) {
            fail("http post response with code other than 200..")
        }

        Awaitility.await()
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    messageList1.size > 0
                }
            }

        sudoDIRelayClient.subscribeToRelayEvents(
            subscriberId,
            messageCreated = { messageList2.add(it) },
        )

        if (!postMessageToEndpoint("hello 2", postbox.serviceEndpoint)) {
            fail("http post response with code other than 200..")
        }

        Awaitility.await()
            .atMost(300, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    messageList2.size > 0
                }
            }

        messageList1.size shouldBe 1
        messageList2.size shouldBe 1
    }

    @Test
    fun fastUnsubscribeSubscribeShouldNotUnsubscribe() = runBlocking<Unit> {
        registerSignInAndEntitle()

        val subscriberId = "subscriber-id"
        val connectionId1 = UUID.randomUUID().toString()
        var subscriber1Notification = false

        val postbox = sudoDIRelayClient.createPostbox(connectionId1, ownershipProof)

        sudoDIRelayClient.subscribeToRelayEvents(
            subscriberId,
            {},
            { subscriber1Notification = true },
        )

        delay(1000)

        if (!postMessageToEndpoint("test", postbox.serviceEndpoint)) {
            fail("http post response with code other than 200..")
        }

        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    subscriber1Notification
                }
            }

        subscriber1Notification = false

        sudoDIRelayClient.unsubscribeToRelayEvents(subscriberId)

        delay(1000)

        sudoDIRelayClient.subscribeToRelayEvents(
            subscriberId,
            {},
            { subscriber1Notification = true },
        )

        delay(1000)

        if (!postMessageToEndpoint("test", postbox.serviceEndpoint)) {
            fail("http post response with code other than 200..")
        }

        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    subscriber1Notification
                }
            }
    }

    @Test
    fun unsubscribeShouldNotUnsubscribeAll() = runBlocking<Unit> {
        registerSignInAndEntitle()

        val connectionId1 = UUID.randomUUID().toString()
        var subscriber1Notification = false
        val subscriberId1 = "subscriber-id-1"

        var subscriber2Notification = false
        val subscriberId2 = "subscriber-id-2"

        val postbox = sudoDIRelayClient.createPostbox(connectionId1, ownershipProof)

        sudoDIRelayClient.subscribeToRelayEvents(
            subscriberId1,
            messageCreated = { subscriber1Notification = true },
        )

        sudoDIRelayClient.subscribeToRelayEvents(
            subscriberId2,
            messageCreated = { subscriber2Notification = true },
        )

        delay(2000)

        sudoDIRelayClient.unsubscribeToRelayEvents(subscriberId1)

        if (!postMessageToEndpoint("test", postbox.serviceEndpoint)) {
            fail("http post response with code other than 200..")
        }

        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    subscriber2Notification
                }
            }

        subscriber1Notification shouldBe false
        subscriber2Notification shouldBe true
    }

    @Test
    fun unsubscribeAllShouldSucceed() = runBlocking<Unit> {
        registerSignInAndEntitle()

        val connectionId = UUID.randomUUID().toString()
        sudoDIRelayClient.createPostbox(connectionId, ownershipProof)

        sudoDIRelayClient.subscribeToRelayEvents(
            connectionId,
            {},
            {}
        )

        sudoDIRelayClient.unsubscribeAll()
    }
}
