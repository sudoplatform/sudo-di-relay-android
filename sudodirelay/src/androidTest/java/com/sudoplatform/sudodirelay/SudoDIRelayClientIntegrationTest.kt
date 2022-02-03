/*
 * Copyright © 2021 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudoconfigmanager.DefaultSudoConfigManager
import com.sudoplatform.sudodirelay.subscription.DIRelayEventSubscriber
import com.sudoplatform.sudodirelay.types.PostboxDeletionResult
import com.sudoplatform.sudodirelay.types.RelayMessage
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import io.kotlintest.fail
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.awaitility.Awaitility
import org.junit.After
import org.junit.Assume.assumeTrue
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
 *
 * @since 2021-06-23
 */
@RunWith(AndroidJUnit4::class)
class SudoDIRelayClientIntegrationTest : BaseIntegrationTest() {

    private val verbose = false
    private val logLevel = if (verbose) LogLevel.VERBOSE else LogLevel.INFO
    private val logger = Logger("di-relay-test", AndroidUtilsLogDriver(logLevel))

    private lateinit var sudoDIRelayClient: SudoDIRelayClient

    private var existingConnectionID: String? = null

    @Before
    fun init() = runBlocking<Unit> {
        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

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
        if (clientConfigFilesPresent()) {
            if (userClient.isRegistered()) {
                deregister()
            }
            userClient.reset()
        }

        Timber.uprootAll()
    }

    private suspend fun getExistingConnectionId(): String {
        if (existingConnectionID == null) {
            existingConnectionID = UUID.randomUUID().toString()
            sudoDIRelayClient.createPostbox(existingConnectionID!!)
        }
        return existingConnectionID!!
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
    fun getPostboxEndpointShouldReturnTheExpectedEndpoint() {
        val baseConfigEndpoint = DefaultSudoConfigManager(context, logger)
            .getConfigSet("relayService")
            ?.get("httpEndpoint") as String?

        val dummyConnectionId = UUID.randomUUID().toString()

        sudoDIRelayClient.getPostboxEndpoint(dummyConnectionId) shouldBe "$baseConfigEndpoint/$dummyConnectionId"
    }

    @Test
    fun shouldBeAbleToRegisterAndDeregister() = runBlocking<Unit> {

        // Can only run if client config files are present
        assumeTrue(clientConfigFilesPresent())

        // given
        userClient.isRegistered() shouldBe false

        // when
        register()

        // then
        userClient.isRegistered() shouldBe true

        // when
        signIn()

        // then
        userClient.isSignedIn() shouldBe true

        // when
        deregister()

        // then
        userClient.isRegistered() shouldBe false
    }

    @Test
    fun createPostboxShouldNotThrowWithValidConnectionID() = runBlocking {
        signInAndRegister()

        val validConnectionID = UUID.randomUUID().toString()
        sudoDIRelayClient.createPostbox(validConnectionID)
    }

    @Test
    fun createPostboxShouldThrowWithInvalidConnectionID() = runBlocking<Unit> {
        signInAndRegister()

        val invalidConnectionID = "helloworld123"
        shouldThrow<SudoDIRelayClient.DIRelayException.InvalidConnectionIDException> {
            sudoDIRelayClient.createPostbox(invalidConnectionID)
        }
    }

    @Test
    fun createPostboxShouldThrowWithDuplicateConnectionID() = runBlocking<Unit> {
        signInAndRegister()

        shouldThrow<SudoDIRelayClient.DIRelayException.FailedException> {
            sudoDIRelayClient.createPostbox(getExistingConnectionId())
        }
    }

    @Test
    fun storeMessageShouldThrowOnInvalidPostboxID() = runBlocking<Unit> {
        signInAndRegister()

        val invalidConnectionID = "123123123123123"
        shouldThrow<SudoDIRelayClient.DIRelayException.UnauthorizedPostboxException> {
            sudoDIRelayClient.storeMessage(invalidConnectionID, "hello")
        }
    }

    @Test
    fun storeMessageShouldThrowOnNonExistentPostboxID() = runBlocking<Unit> {
        signInAndRegister()

        val nonExistingConnectionID = UUID.randomUUID().toString()
        shouldThrow<SudoDIRelayClient.DIRelayException.UnauthorizedPostboxException> {
            sudoDIRelayClient.storeMessage(nonExistingConnectionID, "hello")
        }
    }

    @Test
    fun storeMessageShouldReturnCorrectMessageOnSuccess() = runBlocking<Unit> {
        signInAndRegister()

        val twoMinutesMs = 2 * 60 * 1000L

        val msg = sudoDIRelayClient.storeMessage(getExistingConnectionId(), "hello")

        with(msg) {
            try {
                UUID.fromString(messageId)
            } catch (e: IllegalArgumentException) {
                fail("UUID passed into stored messages is not valid")
            }
            connectionId shouldBe getExistingConnectionId()
            cipherText shouldBe "hello"
            direction shouldBe RelayMessage.Direction.OUTBOUND
            timestamp.after(Date(Date().time - twoMinutesMs)) shouldBe true
        }
    }

    @Test
    fun getMessagesShouldThrowOnInvalidConnectionID() = runBlocking<Unit> {
        signInAndRegister()

        val invalidConnectionID = "123123123123123"

        shouldThrow<SudoDIRelayClient.DIRelayException.UnauthorizedPostboxException> {
            sudoDIRelayClient.getMessages(invalidConnectionID)
        }
    }

    @Test
    fun getMessagesShouldThrowOnNonExistingConnectionID() = runBlocking<Unit> {
        signInAndRegister()

        val nonExistingConnectionID = UUID.randomUUID().toString()

        shouldThrow<SudoDIRelayClient.DIRelayException.UnauthorizedPostboxException> {
            sudoDIRelayClient.getMessages(nonExistingConnectionID)
        }
    }

    @Test
    fun getMessagesShouldNotFail() = runBlocking<Unit> {
        signInAndRegister()

        sudoDIRelayClient.getMessages(getExistingConnectionId())
    }

    @Test
    fun getMessagesShouldReturnEmptyListForNewPostbox() = runBlocking<Unit> {
        signInAndRegister()

        val connectionId = UUID.randomUUID().toString()
        sudoDIRelayClient.createPostbox(connectionId)
        val messages = sudoDIRelayClient.getMessages(connectionId)

        messages.size shouldBe 0
    }

    @Test
    fun getMessagesShouldReturnMessagePostedToIt() = runBlocking<Unit> {
        signInAndRegister()

        val twoMinutesMs = 2 * 60 * 1000L

        val connectionId = UUID.randomUUID().toString()
        sudoDIRelayClient.createPostbox(connectionId)

        if (!postMessageToEndpoint("hi", connectionId)) {
            fail("http post response with code other than 200..")
        }

        val messages = sudoDIRelayClient.getMessages(connectionId)
        messages.size shouldBe 1

        with(messages[0]) {
            try {
                UUID.fromString(messageId)
            } catch (e: IllegalArgumentException) {
                fail("UUID passed into stored messages is not valid")
            }
            this.connectionId shouldBe connectionId
            cipherText shouldBe "hi"
            direction shouldBe RelayMessage.Direction.INBOUND
            timestamp.after(Date(Date().time - twoMinutesMs)) shouldBe true
        }
    }

    @Test
    fun getMessagesShouldReturnMessageStoredInIt() = runBlocking<Unit> {
        signInAndRegister()

        val twoMinutesMs = 2 * 60 * 1000L

        val connectionId = UUID.randomUUID().toString()
        sudoDIRelayClient.createPostbox(connectionId)

        val expectedMessage =
            sudoDIRelayClient.storeMessage(connectionId, "storeGetTest")

        val msgs = sudoDIRelayClient.getMessages(connectionId)

        msgs.size shouldBe 1

        val actualMessage = msgs[0]

        with(actualMessage) {
            messageId shouldBe expectedMessage.messageId
            this.connectionId shouldBe expectedMessage.connectionId
            cipherText shouldBe expectedMessage.cipherText
            direction shouldBe expectedMessage.direction
            timestamp.after(Date(expectedMessage.timestamp.time - twoMinutesMs)) shouldBe true
        }
    }

    @Test
    fun deleteMessagePassOnNormalInput() = runBlocking<Unit> {
        signInAndRegister()

        val connectionId = UUID.randomUUID().toString()
        sudoDIRelayClient.createPostbox(connectionId)

        sudoDIRelayClient.deletePostbox(connectionId)
    }

    @Test
    fun subscriberShouldInvokeOnMessageConnectionIdOnIncoming() = runBlocking<Unit> {
        signInAndRegister()

        val connectionId = UUID.randomUUID().toString()
        sudoDIRelayClient.createPostbox(connectionId)

        val messageList = mutableListOf<RelayMessage>()

        val subscriber = object : DIRelayEventSubscriber {
            override fun messageIncoming(message: RelayMessage) {
                messageList.add(message)
            }

            override fun postBoxDeleted(update: PostboxDeletionResult) {}
            override fun connectionStatusChanged(state: DIRelayEventSubscriber.ConnectionState) {}
        }

        sudoDIRelayClient.subscribeToRelayEvents(connectionId, subscriber)

        delay(1000)

        if (!postMessageToEndpoint("hello world", connectionId)) {
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
        messageList[0].cipherText shouldBe "hello world"
    }

    @Test
    fun subscribeLambdaShouldInvokeOnMessageConnectionIdOnIncoming() = runBlocking {
        signInAndRegister()

        val connectionId = UUID.randomUUID().toString()
        sudoDIRelayClient.createPostbox(connectionId)

        val messageList = mutableListOf<RelayMessage>()

        sudoDIRelayClient.subscribeToRelayEvents(
            connectionId,
            onMessageIncoming = { messageList.add(it) },
            onPostboxDeleted = {}
        )
        delay(1000)

        if (!postMessageToEndpoint("hello world", connectionId)) {
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
        messageList[0].cipherText shouldBe "hello world"
    }

    @Test
    fun multipleSubscribersWithDifferentConnectionIDShouldSucceed() = runBlocking {
        signInAndRegister()

        val connectionId1 = UUID.randomUUID().toString()
        val connectionId2 = UUID.randomUUID().toString()
        sudoDIRelayClient.createPostbox(connectionId1)
        sudoDIRelayClient.createPostbox(connectionId2)

        val messageList1 = mutableListOf<RelayMessage>()
        val messageList2 = mutableListOf<RelayMessage>()

        sudoDIRelayClient.subscribeToRelayEvents(
            connectionId1,
            onMessageIncoming = { messageList1.add(it) },
            onPostboxDeleted = {}
        )
        sudoDIRelayClient.subscribeToRelayEvents(
            connectionId2,
            onMessageIncoming = { messageList2.add(it) },
            onPostboxDeleted = {}
        )

        delay(2000)

        if (!postMessageToEndpoint("hello 1", connectionId1)) {
            fail("http post response with code other than 200..")
        }
        if (!postMessageToEndpoint("hello 2", connectionId2)) {
            fail("http post response with code other than 200..")
        }

        Awaitility.await()
            .atMost(30, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    messageList1.size == 1
                }
            }
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    messageList2.size == 1
                }
            }

        messageList1.size shouldBe 1
        messageList2.size shouldBe 1
        messageList1[0].cipherText shouldBe "hello 1"
        messageList1[0].connectionId shouldBe connectionId1
        messageList2[0].cipherText shouldBe "hello 2"
        messageList2[0].connectionId shouldBe connectionId2
    }

    @Test
    fun newSubscriberWithSameConnectionIDShouldReplace() = runBlocking<Unit> {
        signInAndRegister()

        val connectionId = UUID.randomUUID().toString()

        sudoDIRelayClient.createPostbox(connectionId)

        val messageList1 = mutableListOf<RelayMessage>()
        val messageList2 = mutableListOf<RelayMessage>()

        sudoDIRelayClient.subscribeToRelayEvents(
            connectionId,
            onMessageIncoming = { messageList1.add(it) },
            onPostboxDeleted = {}
        )

        delay(1000)

        if (!postMessageToEndpoint("hello 1", connectionId)) {
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
            connectionId,
            onMessageIncoming = { messageList2.add(it) },
            onPostboxDeleted = {}
        )

        if (!postMessageToEndpoint("hello 2", connectionId)) {
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
    fun deletePostboxShouldSucceedAndDeletePostbox() = runBlocking<Unit> {
        signInAndRegister()

        val connectionId = UUID.randomUUID().toString()
        sudoDIRelayClient.createPostbox(connectionId)

        sudoDIRelayClient.deletePostbox(connectionId)

        // TODO - re-enable after DIP-741 is resolved
//        shouldThrow<SudoDIRelayClient.DIRelayException.UnauthorizedPostboxException> {
//            sudoDIRelayClient.getMessages(connectionId)
//        }
    }

    @Test
    fun deletePostboxShouldNotifySubscriber() = runBlocking<Unit> {
        signInAndRegister()

        val connectionId = UUID.randomUUID().toString()
        sudoDIRelayClient.createPostbox(connectionId)

        var postboxDeleted = false

        sudoDIRelayClient.subscribeToRelayEvents(
            connectionId,
            {},
            {},
            { postboxDeleted = true }
        )

        delay(3000)

        sudoDIRelayClient.deletePostbox(connectionId)

        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    postboxDeleted
                }
            }
    }

    @Test
    fun fastUnsubscribeSubscribeShouldNotUnsubscribe() = runBlocking<Unit> {
        signInAndRegister()

        val connectionId1 = UUID.randomUUID().toString()
        var connectionId1Notification = false

        sudoDIRelayClient.createPostbox(connectionId1)

        sudoDIRelayClient.subscribeToRelayEvents(
            connectionId1,
            {},
            { connectionId1Notification = true },
            {}
        )

        delay(1000)

        if (!postMessageToEndpoint("test", connectionId1)) {
            fail("http post response with code other than 200..")
        }

        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    connectionId1Notification
                }
            }

        connectionId1Notification = false

        sudoDIRelayClient.unsubscribeToRelayEvents(connectionId1)

        sudoDIRelayClient.subscribeToRelayEvents(
            connectionId1,
            {},
            { connectionId1Notification = true },
            {}
        )

        delay(1000)

        if (!postMessageToEndpoint("test", connectionId1)) {
            fail("http post response with code other than 200..")
        }

        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    connectionId1Notification
                }
            }
    }

    @Test
    fun unsubscribeShouldNotUnsubscribeAll() = runBlocking<Unit> {
        signInAndRegister()

        val connectionId1 = UUID.randomUUID().toString()
        var connectionId1Notification = false

        val connectionId2 = UUID.randomUUID().toString()
        var connectionId2Notification = false

        sudoDIRelayClient.createPostbox(connectionId1)
        sudoDIRelayClient.createPostbox(connectionId2)

        sudoDIRelayClient.subscribeToRelayEvents(
            connectionId1,
            {},
            { connectionId1Notification = true },
            {}
        )

        sudoDIRelayClient.subscribeToRelayEvents(
            connectionId2,
            {},
            { connectionId2Notification = true },
            {}
        )

        delay(2000)

        sudoDIRelayClient.unsubscribeToRelayEvents(connectionId1)

        if (!postMessageToEndpoint("test", connectionId2)) {
            fail("http post response with code other than 200..")
        }

        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    connectionId2Notification
                }
            }

        connectionId1Notification shouldBe false
        connectionId2Notification shouldBe true
    }

    @Test
    fun unsubscribeAllShouldSucceed() = runBlocking<Unit> {
        signInAndRegister()

        val connectionId = UUID.randomUUID().toString()
        sudoDIRelayClient.createPostbox(connectionId)

        sudoDIRelayClient.subscribeToRelayEvents(
            connectionId,
            {},
            {},
            {}
        )

        sudoDIRelayClient.unsubscribeAll()
    }

    @Test
    fun completeFlowShouldSucceed() = runBlocking<Unit> {
        signInAndRegister()

        // set up postbox1
        val postbox1 = UUID.randomUUID().toString()
        sudoDIRelayClient.createPostbox(postbox1)
        var postbox1Deleted = false
        val postbox1IncomingMessages = mutableListOf<RelayMessage>()

        // set up postbox2
        val postbox2 = UUID.randomUUID().toString()
        sudoDIRelayClient.createPostbox(postbox2)
        var postbox2Deleted = false
        val postbox2IncomingMessages = mutableListOf<RelayMessage>()

        // check both postboxes have no messages
        sudoDIRelayClient.getMessages(postbox1) shouldBe emptyList()
        sudoDIRelayClient.getMessages(postbox2) shouldBe emptyList()

        // subscribe to events for postbox 1
        sudoDIRelayClient.subscribeToRelayEvents(
            postbox1,
            {},
            { postbox1IncomingMessages.add(it) },
            { postbox1Deleted = true }
        )

        // subscribe to events for postbox 2
        sudoDIRelayClient.subscribeToRelayEvents(
            postbox2,
            {},
            { postbox2IncomingMessages.add(it) },
            { postbox2Deleted = true }
        )

        delay(2000)

        // post message from postbox1 to postbox2
        val messageFromPostbox1to2 = "hello postbox 2"
        if (!postMessageToEndpoint(messageFromPostbox1to2, postbox2)) {
            fail("http post response with code other than 200..")
        }
        sudoDIRelayClient.storeMessage(postbox1, messageFromPostbox1to2)
        // check that postbox2 receives subscription notification within 20seconds
        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    postbox2IncomingMessages.size == 1
                }
            }
        postbox2IncomingMessages[0].cipherText shouldBe messageFromPostbox1to2

        delay(2000)

        // post message from postbox2 to postbox1
        val messageFromPostbox2to1 = "hello postbox 1"
        if (!postMessageToEndpoint(messageFromPostbox2to1, postbox1)) {
            fail("http post response with code other than 200..")
        }
        sudoDIRelayClient.storeMessage(postbox2, messageFromPostbox2to1)
        // check that postbox1 receives subscription notification within 20seconds
        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    postbox1IncomingMessages.size == 1
                }
            }
        postbox1IncomingMessages[0].cipherText shouldBe messageFromPostbox2to1

        // check the getMessages for both postboxes are correct
        val postbox1Messages = sudoDIRelayClient.getMessages(postbox1)
        val postbox2Messages = sudoDIRelayClient.getMessages(postbox2)

        postbox1Messages.size shouldBe 2
        postbox2Messages.size shouldBe 2

        val pairedMessages = postbox1Messages.zip(postbox2Messages)
        with(pairedMessages[0]) {
            first.cipherText shouldBe messageFromPostbox1to2
            first.cipherText shouldBe second.cipherText
            first.direction shouldBe RelayMessage.Direction.OUTBOUND
            second.direction shouldBe RelayMessage.Direction.INBOUND
        }
        with(pairedMessages[1]) {
            first.cipherText shouldBe messageFromPostbox2to1
            first.cipherText shouldBe second.cipherText
            first.direction shouldBe RelayMessage.Direction.INBOUND
            second.direction shouldBe RelayMessage.Direction.OUTBOUND
        }

        // delete postboxes
        postbox1Deleted shouldBe false
        postbox2Deleted shouldBe false

        sudoDIRelayClient.deletePostbox(postbox1)
        sudoDIRelayClient.deletePostbox(postbox2)

        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    postbox1Deleted
                }
            }
        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until {
                runBlocking {
                    postbox2Deleted
                }
            }
    }

    /**
     * does a HTTP POST to the supplied [connectionId], containing the string body of [msg].
     *
     * @return whether the POST succeeded or not
     */
    private suspend fun postMessageToEndpoint(msg: String, connectionId: String): Boolean {
        val endpoint = sudoDIRelayClient.getPostboxEndpoint(connectionId)
        val postRequest = Request.Builder()
            .url(endpoint)
            .post(msg.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()
        val client = OkHttpClient()
        val response = withContext(Dispatchers.IO) {
            client.newCall(postRequest).execute()
        }

        return response.code == 200
    }
}
