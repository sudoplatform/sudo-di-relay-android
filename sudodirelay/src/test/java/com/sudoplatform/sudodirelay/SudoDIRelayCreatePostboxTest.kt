package com.sudoplatform.sudodirelay

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloHttpException
import com.sudoplatform.sudodirelay.graphql.CallbackHolder
import com.sudoplatform.sudodirelay.graphql.SendInitMutation
import com.sudoplatform.sudodirelay.graphql.type.CreatePostboxInput
import com.sudoplatform.sudouser.SudoUserClient
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

/**
 * Test the correct operation of [SudoDIRelayClient.createPostbox] using mocks and spies.
 */
class SudoDIRelayCreatePostboxTest : BaseTests() {

    private val connectionID = "62b39ef6-510f-4150-83e7-3ab4e71bffd8"

    private val ownershipProofToken = "eyJlYXN0ZXItZWdnIjogIndvYSwgbmljZSBkZWNvZGluZyBza2lsbHMgOjAgQikifQ=="

    private val mutationInput by before {
        CreatePostboxInput.builder()
            .connectionId("cid")
            .ownershipProofTokens(listOf(ownershipProofToken))
            .build()
    }

    private val mutationResult by before {
        SendInitMutation.SendInit(
            "typename",
            "mid",
            "cid",
            (1_624_302_710_000).toDouble()
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
    fun `createPostbox(variables) should pass variables correctly into mutation input`() =
        runBlocking<Unit> {

            mutationHolder.callback shouldBe null

            val deferredResult = async(Dispatchers.IO) {
                client.createPostbox(connectionID, ownershipProofToken)
            }

            deferredResult.start()
            delay(100)

            mutationHolder.callback shouldNotBe null
            mutationHolder.callback?.onResponse(mutationResponse)
            deferredResult.await()

            val actualMutationInput = ArgumentCaptor.forClass(SendInitMutation::class.java)
            verify(mockAppSyncClient).mutate(actualMutationInput.capture())
            with(actualMutationInput.value.variables().input()) {
                connectionId() shouldBe connectionID
                ownershipProofTokens() shouldBe listOf(ownershipProofToken)
            }
        }

    @Test
    fun `createPostbox() should throw with invalid connectionID`() = runBlocking<Unit> {
        val invalidConnectionID = "aaaaaaa-bbbbbbbbbb-cccccc"

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.InvalidConnectionIDException> {
                client.createPostbox(invalidConnectionID, ownershipProofToken)
            }
        }

        val invalidInitError =
            Error("mock", emptyList(), mapOf("errorType" to "sudoplatform.relay.InvalidInitMessage"))

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
    fun `createPostbox() should throw generic on null response no errors`() = runBlocking<Unit> {

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.FailedException> {
                client.createPostbox(connectionID, ownershipProofToken)
            }
        }

        val mutationResponseNullData by before {
            Response.builder<SendInitMutation.Data>(SendInitMutation(mutationInput))
                .data(null)
                .build()
        }

        deferredResult.start()
        delay(100)

        mutationHolder.callback?.onResponse(mutationResponseNullData)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<SendInitMutation>())
    }

    @Test
    fun `createPostbox() should throw on invalid token error`() = runBlocking<Unit> {
        val invalidOwnershipProofToken = "abcdef"

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.InvalidTokenException> {
                client.createPostbox(connectionID, invalidOwnershipProofToken)
            }
        }

        val invalidTokenError =
            Error("mock", emptyList(), mapOf("errorType" to "sudoplatform.InvalidTokenError"))

        val mutationResponseInvalidTokenError by before {
            Response.builder<SendInitMutation.Data>(SendInitMutation(mutationInput))
                .data(null)
                .errors(mutableListOf(invalidTokenError))
                .build()
        }

        deferredResult.start()
        delay(100)

        mutationHolder.callback?.onResponse(mutationResponseInvalidTokenError)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<SendInitMutation>())
    }

    @Test
    fun `createPostbox() should throw on apollo http error`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.FailedException> {
                client.createPostbox(connectionID, ownershipProofToken)
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
                client.createPostbox("123", ownershipProofToken)
            }
        }

        deferredResult.start()
        delay(100)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<SendInitMutation>())
    }
}
