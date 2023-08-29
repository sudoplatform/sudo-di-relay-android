package com.sudoplatform.sudodirelay

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloHttpException
import com.sudoplatform.sudodirelay.graphql.CallbackHolder
import com.sudoplatform.sudodirelay.graphql.UpdateRelayPostboxMutation
import com.sudoplatform.sudodirelay.graphql.UpdateRelayPostboxMutation.Owner
import com.sudoplatform.sudodirelay.graphql.type.UpdateRelayPostboxInput
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
 * Test the correct operation of [SudoDIRelayClient.updatePostbox] using mocks and spies.
 */
class SudoDIRelayUpdatePostboxTest : BaseTests() {

    private val mutationInput by before {
        UpdateRelayPostboxInput.builder()
            .postboxId("postbox-id")
            .isEnabled(true)
            .build()
    }

    private val mutationResult by before {
        UpdateRelayPostboxMutation.UpdateRelayPostbox(
            "typename",
            "postbox-id",
            0.0,
            1.0,
            "dummyOwner",
            listOf(Owner("owner", "dummySudoOwner", "sudoplatform.sudoservice")),
            "connection-id",
            true,
            "https://service.endpoint.test/this-should-be-our-connection-id"
        )
    }

    private val mutationResponse by before {
        Response.builder<UpdateRelayPostboxMutation.Data>(UpdateRelayPostboxMutation(mutationInput))
            .data(UpdateRelayPostboxMutation.Data(mutationResult))
            .build()
    }

    private val mutationHolder = CallbackHolder<UpdateRelayPostboxMutation.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<UpdateRelayPostboxMutation>()) } doReturn mutationHolder.mutationOperation
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
    fun `UpdatePostbox(variables) should pass variables correctly into mutation input`() =
        runBlocking<Unit> {
            val postboxId = "postbox-id"
            val enabled = true
            mutationHolder.callback shouldBe null

            val deferredResult = async(Dispatchers.IO) {
                client.updatePostbox(postboxId, enabled)
            }

            deferredResult.start()
            delay(100)

            mutationHolder.callback shouldNotBe null
            mutationHolder.callback?.onResponse(mutationResponse)
            deferredResult.await()

            val actualMutationInput = ArgumentCaptor.forClass(UpdateRelayPostboxMutation::class.java)
            verify(mockAppSyncClient).mutate(actualMutationInput.capture())
            with(actualMutationInput.value.variables().input()) {
                postboxId() shouldBe postboxId
                isEnabled shouldBe enabled
            }
        }

    @Test
    fun `UpdatePostbox(variables) should pass nullable variables correctly into mutation input`() =
        runBlocking<Unit> {
            val postboxId = "postbox-id"
            val enabled = null
            mutationHolder.callback shouldBe null

            val deferredResult = async(Dispatchers.IO) {
                client.updatePostbox(postboxId, enabled)
            }

            deferredResult.start()
            delay(100)

            mutationHolder.callback shouldNotBe null
            mutationHolder.callback?.onResponse(mutationResponse)
            deferredResult.await()

            val actualMutationInput = ArgumentCaptor.forClass(UpdateRelayPostboxMutation::class.java)
            verify(mockAppSyncClient).mutate(actualMutationInput.capture())
            with(actualMutationInput.value.variables().input()) {
                postboxId() shouldBe postboxId
                isEnabled shouldBe null
            }
        }

    @Test
    fun `UpdatePostbox() should throw with unauthorized access error`() = runBlocking<Unit> {
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.UnauthorizedPostboxException> {
                client.updatePostbox("postbox-id", null)
            }
        }

        val unauthorizedError =
            Error("mock", emptyList(), mapOf("errorType" to "sudoplatform.relay.UnauthorizedPostboxAccessError"))

        val mutationResponseUnauthorizedError by before {
            Response.builder<UpdateRelayPostboxMutation.Data>(UpdateRelayPostboxMutation(mutationInput))
                .data(null)
                .errors(mutableListOf(unauthorizedError))
                .build()
        }

        deferredResult.start()
        delay(100)

        mutationHolder.callback?.onResponse(mutationResponseUnauthorizedError)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<UpdateRelayPostboxMutation>())
    }

    @Test
    fun `UpdatePostbox() should throw generic on null response no errors`() = runBlocking<Unit> {
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.FailedException> {
                client.updatePostbox("postbox-id", null)
            }
        }

        val mutationResponseNullData by before {
            Response.builder<UpdateRelayPostboxMutation.Data>(UpdateRelayPostboxMutation(mutationInput))
                .data(null)
                .build()
        }

        deferredResult.start()
        delay(100)

        mutationHolder.callback?.onResponse(mutationResponseNullData)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<UpdateRelayPostboxMutation>())
    }

    @Test
    fun `UpdatePostbox() should throw on apollo http error`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.FailedException> {
                client.updatePostbox("postbox-id", null)
            }
        }

        deferredResult.start()
        delay(100)

        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onHttpError(ApolloHttpException(CommonData.forbiddenHTTPResponse))

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<UpdateRelayPostboxMutation>())
    }

    @Test
    fun `UpdatePostbox() should throw on unknown error on other error`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        mockAppSyncClient.stub {
            on { mutate(any<UpdateRelayPostboxMutation>()) } doThrow RuntimeException("Mock runtime error")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.UnknownException> {
                client.updatePostbox("postbox-id", null)
            }
        }

        deferredResult.start()
        delay(100)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<UpdateRelayPostboxMutation>())
    }
}
