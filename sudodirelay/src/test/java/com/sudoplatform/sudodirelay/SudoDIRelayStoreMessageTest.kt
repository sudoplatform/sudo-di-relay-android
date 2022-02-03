package com.sudoplatform.sudodirelay

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloHttpException
import com.sudoplatform.sudodirelay.graphql.CallbackHolder
import com.sudoplatform.sudodirelay.graphql.StoreMessageMutation
import com.sudoplatform.sudodirelay.graphql.type.Direction
import com.sudoplatform.sudodirelay.graphql.type.WriteToRelayInput
import com.sudoplatform.sudodirelay.types.RelayMessage
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.fail
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.lang.IllegalArgumentException
import java.util.Date
import java.util.UUID

/**
 * Test the correct operation of [SudoDIRelayClient.storeMessage] using mocks and spies.
 *
 * @since 2021-07-02
 */
class SudoDIRelayStoreMessageTest : BaseTests() {
    private val mutationInput by before {
        WriteToRelayInput.builder()
            .messageId("init")
            .connectionId("cid")
            .cipherText("init")
            .direction(Direction.OUTBOUND)
            .utcTimestamp((1_624_302_710_000).toDouble())
            .build()
    }

    private val mutationResult by before {
        StoreMessageMutation.StoreMessage(
            "typename",
            "mid",
            "cid",
            "hello world",
            Direction.OUTBOUND,
            (1_624_302_710_000).toDouble()
        )
    }

    private val mutationResponse by before {
        Response.builder<StoreMessageMutation.Data>(StoreMessageMutation(mutationInput))
            .data(StoreMessageMutation.Data(mutationResult))
            .build()
    }

    private val mutationHolder = CallbackHolder<StoreMessageMutation.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<StoreMessageMutation>()) } doReturn mutationHolder.mutationOperation
        }
    }

    private val mockUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn "subject"
            on { getRefreshToken() } doReturn "refreshToken"
        }
    }

    private val client by before {
        DefaultSudoDIRelayClient(
            mockContext,
            mockAppSyncClient,
            mockUserClient,
            mockLogger
        )
    }

    @Before
    fun setup() {
    }

    @After
    fun teardown() {
        verifyNoMoreInteractions(mockContext, mockAppSyncClient)
    }

    @Test
    fun `storeMessage(variables) should pass variables correctly into mutation input`() =
        runBlocking<Unit> {
            val twoMinutesMs = 2 * 60 * 1000L

            mutationHolder.callback shouldBe null

            val connectionID = "1234-1234-1234-1234"

            val deferredResult = async(Dispatchers.IO) {
                client.storeMessage(connectionID, "hello")
            }

            deferredResult.start()
            delay(100)

            mutationHolder.callback shouldNotBe null
            mutationHolder.callback?.onResponse(mutationResponse)
            deferredResult.await()

            val actualMutationInput = ArgumentCaptor.forClass(StoreMessageMutation::class.java)
            verify(mockAppSyncClient).mutate(actualMutationInput.capture())
            with(actualMutationInput.value.variables().input()) {
                try {
                    UUID.fromString(messageId())
                } catch (e: IllegalArgumentException) {
                    fail("UUID passed into stored messages is not valid")
                }
                connectionID shouldBe connectionID
                cipherText() shouldBe "hello"
                direction() shouldBe Direction.OUTBOUND
                // check timestamp within 2 mins of accuracy
                Date(utcTimestamp().toLong()).after(Date(Date().time - twoMinutesMs)) shouldBe true
            }
        }

    @Test
    fun `storeMessage() should pass and return the correct relayMessage object`() =
        runBlocking<Unit> {
            val twoMinutesMs = 2 * 60 * 1000L

            mutationHolder.callback shouldBe null

            val connectionID = "1234-1234-1234-1234"

            val deferredResult = async(Dispatchers.IO) {
                client.storeMessage(connectionID, "hello")
            }

            deferredResult.start()
            delay(100)

            mutationHolder.callback shouldNotBe null
            mutationHolder.callback?.onResponse(mutationResponse)
            val msg = deferredResult.await()

            verify(mockAppSyncClient).mutate(any<StoreMessageMutation>())
            with(msg) {
                try {
                    UUID.fromString(messageId)
                } catch (e: IllegalArgumentException) {
                    fail("UUID passed into stored messages is not valid")
                }
                connectionId shouldBe connectionID
                cipherText shouldBe "hello"
                direction shouldBe RelayMessage.Direction.OUTBOUND
                // check timestamp within 2 mins of accuracy
                timestamp.after(Date(Date().time - twoMinutesMs)) shouldBe true
            }
        }

    @Test
    fun `storeMessage() should throw on apollo http error`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.FailedException> {
                client.storeMessage("123", "hello")
            }
        }

        deferredResult.start()
        delay(100)

        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onHttpError(ApolloHttpException(CommonData.forbiddenHTTPResponse))

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<StoreMessageMutation>())
    }

    @Test
    fun `storeMessages() should throw on unknown error on other error`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        mockAppSyncClient.stub {
            on { mutate(any<StoreMessageMutation>()) } doThrow RuntimeException("Mock runtime error")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.UnknownException> {
                client.storeMessage("123", "error test")
            }
        }

        deferredResult.start()
        delay(100)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<StoreMessageMutation>())
    }
}
