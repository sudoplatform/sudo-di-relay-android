package com.sudoplatform.sudodirelay

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloHttpException
import com.sudoplatform.sudodirelay.graphql.CallbackHolder
import com.sudoplatform.sudodirelay.graphql.DeleteRelayPostboxMutation
import com.sudoplatform.sudodirelay.graphql.type.DeleteRelayPostboxInput
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
import kotlin.RuntimeException

/**
 * Test the correct operation of [SudoDIRelayClient.deletePostbox] using mocks and spies.
 */
class SudoDIRelayDeletePostboxTest : BaseTests() {
    private val mutationInput by before {
        DeleteRelayPostboxInput.builder()
            .postboxId("postbox-id")
            .build()
    }

    private val mutationResult by before {
        DeleteRelayPostboxMutation.DeleteRelayPostbox(
            "typename",
            "postbox-id"
        )
    }

    private val mutationResponse by before {
        Response.builder<DeleteRelayPostboxMutation.Data>(DeleteRelayPostboxMutation(mutationInput))
            .data(DeleteRelayPostboxMutation.Data(mutationResult))
            .build()
    }

    private val mutationHolder = CallbackHolder<DeleteRelayPostboxMutation.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<DeleteRelayPostboxMutation>()) } doReturn mutationHolder.mutationOperation
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
    fun `deletePostbox(variables) should pass variables correctly into mutation input`() =
        runBlocking<Unit> {
            mutationHolder.callback shouldBe null

            val postboxId = "1234-1234-1234-1234"

            val deferredResult = async(Dispatchers.IO) {
                client.deletePostbox(postboxId)
            }

            deferredResult.start()
            delay(100)

            mutationHolder.callback shouldNotBe null
            mutationHolder.callback?.onResponse(mutationResponse)
            deferredResult.await()

            val actualMutationInput = ArgumentCaptor.forClass(DeleteRelayPostboxMutation::class.java)
            verify(mockAppSyncClient).mutate(actualMutationInput.capture())
            actualMutationInput.value.variables().input().postboxId() shouldBe postboxId
        }

    @Test
    fun `deletePostbox() should invoke appSync client`() =
        runBlocking<Unit> {
            mutationHolder.callback shouldBe null

            val deferredResult = async(Dispatchers.IO) {
                client.deletePostbox("postbox-id")
            }

            deferredResult.start()
            delay(100)

            mutationHolder.callback shouldNotBe null
            mutationHolder.callback?.onResponse(mutationResponse)
            deferredResult.await()

            verify(mockAppSyncClient).mutate(any<DeleteRelayPostboxMutation>())
        }

    @Test
    fun `deletePostbox() should not throw on null response`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val postboxId = "postbox-id"
        val badMutationResponse by before {
            Response.builder<DeleteRelayPostboxMutation.Data>(DeleteRelayPostboxMutation(mutationInput))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.deletePostbox(postboxId)
        }

        deferredResult.start()
        delay(100)

        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onResponse(badMutationResponse)
        val result = deferredResult.await()

        verify(mockAppSyncClient).mutate(any<DeleteRelayPostboxMutation>())
        result shouldBe postboxId
    }

    @Test
    fun `deletePostbox() should not throw on UnauthorizedPostboxAccess response`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val postboxId = "postbox-id"
        val unauthorizedAccessError =
            Error("mock", emptyList(), mapOf("errorType" to "sudoplatform.relay.UnauthorizedPostboxAccessError"))
        val badMutationResponse by before {
            Response.builder<DeleteRelayPostboxMutation.Data>(DeleteRelayPostboxMutation(mutationInput))
                .data(null)
                .errors(mutableListOf(unauthorizedAccessError))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.deletePostbox(postboxId)
        }

        deferredResult.start()
        delay(100)

        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onResponse(badMutationResponse)
        val result = deferredResult.await()

        verify(mockAppSyncClient).mutate(any<DeleteRelayPostboxMutation>())
        result shouldBe postboxId
    }

    @Test
    fun `deletePostbox() should throw on apollo http error`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.FailedException> {
                client.deletePostbox("postbox-id")
            }
        }

        deferredResult.start()
        delay(100)

        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onHttpError(ApolloHttpException(CommonData.forbiddenHTTPResponse))

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<DeleteRelayPostboxMutation>())
    }

    @Test
    fun `deletePostbox() should throw on unknown error`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        mockAppSyncClient.stub {
            on { mutate(any<DeleteRelayPostboxMutation>()) } doThrow RuntimeException("Mock runtime error")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.UnknownException> {
                client.deletePostbox("postbox-id")
            }
        }

        deferredResult.start()
        delay(100)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<DeleteRelayPostboxMutation>())
    }
}
