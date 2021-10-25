package com.sudoplatform.sudodirelay

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloHttpException
import com.sudoplatform.sudodirelay.DefaultSudoDIRelayClient.Companion.SEND_INIT_DEFAULT_CIPHER_TEXT
import com.sudoplatform.sudodirelay.DefaultSudoDIRelayClient.Companion.SEND_INIT_DEFAULT_MESSAGE_ID
import com.sudoplatform.sudodirelay.graphql.CallbackHolder
import com.sudoplatform.sudodirelay.graphql.SendInitMutation
import com.sudoplatform.sudodirelay.graphql.type.Direction
import com.sudoplatform.sudodirelay.graphql.type.WriteToRelayInput
import com.sudoplatform.sudodirelay.types.transformers.toDate
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
import java.util.Date

/**
 * Test the correct operation of [SudoDIRelayClient.createPostbox] using mocks and spies.
 *
 * @since 2021-07-02
 */
class SudoDIRelayCreatePostboxTest : BaseTests() {

    private val connectionID = "62b39ef6-510f-4150-83e7-3ab4e71bffd8"

    private val mutationInput by before {
        WriteToRelayInput.builder()
            .messageId("init")
            .connectionId("cid")
            .cipherText("init")
            .direction(Direction.OUTBOUND)
            .utcTimestamp("Mon, 21 Jun 2021 19:11:50 GMT")
            .nextToken(null)
            .build()
    }

    private val mutationResult by before {
        SendInitMutation.SendInit(
            "typename",
            "mid",
            "cid",
            "hello world",
            Direction.OUTBOUND,
            "Mon, 21 Jun 2021 19:11:50 GMT",
            null
        )
    }

    private val mutationResponse by before {
        Response.builder<SendInitMutation.Data>(SendInitMutation(mutationInput))
            .data(SendInitMutation.Data(mutationResult))
            .build()
    }

    private val mutationHolder = CallbackHolder<SendInitMutation.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<SendInitMutation>()) } doReturn mutationHolder.mutationOperation
        }
    }

    private val client by before {
        DefaultSudoDIRelayClient(
            mockContext,
            mockAppSyncClient,
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
    fun `createPostbox(variables) should pass variables correctly into mutation input`() =
        runBlocking<Unit> {
            val twoMinutesMs = 2 * 60 * 1000L

            mutationHolder.callback shouldBe null

            val deferredResult = async(Dispatchers.IO) {
                client.createPostbox(connectionID)
            }

            deferredResult.start()
            delay(100)

            mutationHolder.callback shouldNotBe null
            mutationHolder.callback?.onResponse(mutationResponse)
            deferredResult.await()

            val actualMutationInput = ArgumentCaptor.forClass(SendInitMutation::class.java)
            verify(mockAppSyncClient).mutate(actualMutationInput.capture())
            with(actualMutationInput.value.variables().input()) {
                messageId() shouldBe SEND_INIT_DEFAULT_MESSAGE_ID
                connectionID shouldBe connectionID
                cipherText() shouldBe SEND_INIT_DEFAULT_CIPHER_TEXT
                direction() shouldBe Direction.OUTBOUND
                // check timestamp within 2 mins of accuracy
                utcTimestamp().toDate().after(Date(Date().time - twoMinutesMs)) shouldBe true
                nextToken() shouldBe null
            }
        }

    @Test
    fun `createPostbox() should throw with invalid connectionID`() = runBlocking<Unit> {
        val invalidConnectionID = "aaaaaaa-bbbbbbbbbb-cccccc"

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.InvalidConnectionIDException> {
                client.createPostbox(invalidConnectionID)
            }
        }

        val invalidInitError =
            Error("mock", emptyList(), mapOf("errorType" to "InvalidInitMessage"))

        val mutationResponseInvalidInitError by before {
            Response.builder<SendInitMutation.Data>(SendInitMutation(mutationInput))
                .data(null)
                .errors(mutableListOf(invalidInitError))
                .build()
        }

        deferredResult.start()
        delay(100)

        mutationHolder.callback?.onResponse(mutationResponseInvalidInitError)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<SendInitMutation>())
    }

    @Test
    fun `createPostbox() should throw with null response data`() = runBlocking<Unit> {

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.InvalidPostboxException> {
                client.createPostbox(connectionID)
            }
        }

        val mutationNullResponse by before {
            Response.builder<SendInitMutation.Data>(SendInitMutation(mutationInput))
                .data(null)
                .build()
        }

        deferredResult.start()
        delay(100)

        mutationHolder.callback?.onResponse(mutationNullResponse)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<SendInitMutation>())
    }

    @Test
    fun `createPostbox() should throw on apollo http error`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.FailedException> {
                client.createPostbox(connectionID)
            }
        }

        deferredResult.start()
        delay(100)

        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onHttpError(ApolloHttpException(CommonData.forbiddenHTTPResponse))

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<SendInitMutation>())
    }

    @Test
    fun `createPostbox() should throw on unknown error on other error`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        mockAppSyncClient.stub {
            on { mutate(any<SendInitMutation>()) } doThrow RuntimeException("Mock runtime error")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.UnknownException> {
                client.createPostbox("123")
            }
        }

        deferredResult.start()
        delay(100)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<SendInitMutation>())
    }
}
