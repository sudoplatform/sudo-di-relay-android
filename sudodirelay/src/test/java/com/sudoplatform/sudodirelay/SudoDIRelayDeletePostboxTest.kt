package com.sudoplatform.sudodirelay

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloHttpException
import com.sudoplatform.sudodirelay.graphql.CallbackHolder
import com.sudoplatform.sudodirelay.graphql.DeletePostBoxMutation
import com.sudoplatform.sudodirelay.graphql.type.IdAsInput
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
 *
 * @since 2021-07-02
 */
class SudoDIRelayDeletePostboxTest : BaseTests() {
    private val mutationInput by before {
        IdAsInput.builder()
            .connectionId("cid")
            .build()
    }

    private val mutationResult by before {
        DeletePostBoxMutation.DeletePostBox(
            "typename",
            200
        )
    }

    private val mutationResponse by before {
        Response.builder<DeletePostBoxMutation.Data>(DeletePostBoxMutation(mutationInput))
            .data(DeletePostBoxMutation.Data(mutationResult))
            .build()
    }

    private val mutationHolder = CallbackHolder<DeletePostBoxMutation.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<DeletePostBoxMutation>()) } doReturn mutationHolder.mutationOperation
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

            val connectionID = "1234-1234-1234-1234"

            val deferredResult = async(Dispatchers.IO) {
                client.deletePostbox(connectionID)
            }

            deferredResult.start()
            delay(100)

            mutationHolder.callback shouldNotBe null
            mutationHolder.callback?.onResponse(mutationResponse)
            deferredResult.await()

            val actualMutationInput = ArgumentCaptor.forClass(DeletePostBoxMutation::class.java)
            verify(mockAppSyncClient).mutate(actualMutationInput.capture())
            actualMutationInput.value.variables().input().connectionId() shouldBe connectionID
        }

    @Test
    fun `deletePostbox() should pass through on normal input and 200 response`() =
        runBlocking<Unit> {
            mutationHolder.callback shouldBe null

            val deferredResult = async(Dispatchers.IO) {
                client.deletePostbox("123")
            }

            deferredResult.start()
            delay(100)

            mutationHolder.callback shouldNotBe null
            mutationHolder.callback?.onResponse(mutationResponse)
            deferredResult.await()

            verify(mockAppSyncClient).mutate(any<DeletePostBoxMutation>())
        }

    @Test
    fun `deletePostbox() should throw on null response`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val badMutationResponse by before {
            Response.builder<DeletePostBoxMutation.Data>(DeletePostBoxMutation(mutationInput))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {

            shouldThrow<SudoDIRelayClient.DIRelayException.FailedException> {
                client.deletePostbox("123")
            }
        }

        deferredResult.start()
        delay(100)

        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onResponse(badMutationResponse)
        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<DeletePostBoxMutation>())
    }

    @Test
    fun `deletePostbox() should throw on not 200 response`() =
        runBlocking<Unit> {
            mutationHolder.callback shouldBe null

            val badMutationResult by before {
                DeletePostBoxMutation.DeletePostBox(
                    "typename",
                    403
                )
            }

            val badMutationResponse by before {
                Response.builder<DeletePostBoxMutation.Data>(DeletePostBoxMutation(mutationInput))
                    .data(DeletePostBoxMutation.Data(badMutationResult))
                    .build()
            }

            val deferredResult = async(Dispatchers.IO) {

                shouldThrow<SudoDIRelayClient.DIRelayException.FailedException> {
                    client.deletePostbox("123")
                }
            }

            deferredResult.start()
            delay(100)

            mutationHolder.callback shouldNotBe null
            mutationHolder.callback?.onResponse(badMutationResponse)
            deferredResult.await()

            verify(mockAppSyncClient).mutate(any<DeletePostBoxMutation>())
        }

    @Test
    fun `deletePostbox() should throw on apollo http error`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.FailedException> {
                client.deletePostbox("123")
            }
        }

        deferredResult.start()
        delay(100)

        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onHttpError(ApolloHttpException(CommonData.forbiddenHTTPResponse))

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<DeletePostBoxMutation>())
    }

    @Test
    fun `deletePostbox() should throw on unknown error`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        mockAppSyncClient.stub {
            on { mutate(any<DeletePostBoxMutation>()) } doThrow RuntimeException("Mock runtime error")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.UnknownException> {
                client.deletePostbox("123")
            }
        }

        deferredResult.start()
        delay(100)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<DeletePostBoxMutation>())
    }
}
