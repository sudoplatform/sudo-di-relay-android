package com.sudoplatform.sudodirelay

import android.content.Context
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.ApiException
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudodirelay.graphql.UpdateRelayPostboxMutation
import com.sudoplatform.sudodirelay.graphql.onMutate
import com.sudoplatform.sudodirelay.graphql.type.UpdateRelayPostboxInput
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.amplify.GraphQLClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
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

    private val mutationResponseJson =
        """
        {
            "updateRelayPostbox": {
                "__typename": "RelayPostbox",
                "id": "postbox-id",
                "createdAtEpochMs": 0.0,
                "updatedAtEpochMs": 1.0,
                "owner": "dummyOwner",
                "owners": [
                    { "__typename": "Owner", "id": "dummySudoOwner", "issuer": "sudoplatform.sudoservice" }
                ],
                "connectionId": "connection-id",
                "isEnabled": true,
                "serviceEndpoint": "https://service.endpoint.test/this-should-be-our-connection-id"
            }
        }
        """.trimIndent()

    private val mockContext by before { mock<Context>() }

    private val mockApiCategory by before {
        mock<ApiCategory>().stub {
            onMutate(UpdateRelayPostboxMutation.OPERATION_DOCUMENT, mutationResponseJson)
        }
    }

    private val graphQLClient by before { GraphQLClient(mockApiCategory) }

    private val mockUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn "subject"
            on { getRefreshToken() } doReturn "refreshToken"
        }
    }

    private val client by before {
        DefaultSudoDIRelayClient(
            mockContext,
            graphQLClient,
            mockUserClient,
            mockLogger,
        )
    }

    @After
    fun teardown() {
        verifyNoMoreInteractions(mockContext, mockApiCategory)
    }

    @Test
    fun `UpdatePostbox(variables) should pass variables correctly into mutation input`() = runTest {
        val postboxId = "postbox-id"
        val enabled = true

        client.updatePostbox(postboxId, enabled)

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe UpdateRelayPostboxMutation.OPERATION_DOCUMENT
                val input = it.variables["input"] as UpdateRelayPostboxInput
                input.postboxId shouldBe postboxId
                input.isEnabled shouldBe Optional.presentIfNotNull(enabled)
            },
            any(),
            any(),
        )
    }

    @Test
    fun `UpdatePostbox(variables) should pass nullable variables correctly into mutation input`() = runTest {
        val postboxId = "postbox-id"

        client.updatePostbox(postboxId, null)

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe UpdateRelayPostboxMutation.OPERATION_DOCUMENT
                val input = it.variables["input"] as UpdateRelayPostboxInput
                input.postboxId shouldBe postboxId
                input.isEnabled shouldBe Optional.presentIfNotNull(null)
            },
            any(),
            any(),
        )
    }

    @Test
    fun `UpdatePostbox() should throw with unauthorized access error`() = runTest {
        mockApiCategory.stub {
            onMutate(
                UpdateRelayPostboxMutation.OPERATION_DOCUMENT,
                null,
                listOf(graphQLError("sudoplatform.relay.UnauthorizedPostboxAccessError")),
            )
        }

        shouldThrow<SudoDIRelayClient.DIRelayException.UnauthorizedPostboxException> {
            client.updatePostbox("postbox-id", null)
        }

        verifyMutateCalled()
    }

    @Test
    fun `UpdatePostbox() should throw generic on null response no errors`() = runTest {
        mockApiCategory.stub {
            onMutate(UpdateRelayPostboxMutation.OPERATION_DOCUMENT, null)
        }

        shouldThrow<SudoDIRelayClient.DIRelayException.FailedException> {
            client.updatePostbox("postbox-id", null)
        }

        verifyMutateCalled()
    }

    @Test
    fun `UpdatePostbox() should throw on api error`() = runTest {
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query == UpdateRelayPostboxMutation.OPERATION_DOCUMENT },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[2] as Consumer<ApiException>)
                    .accept(ApiException("forbidden", "denied"))
                mock<GraphQLOperation<String>>()
            }
        }

        shouldThrow<SudoDIRelayClient.DIRelayException.FailedException> {
            client.updatePostbox("postbox-id", null)
        }

        verifyMutateCalled()
    }

    @Test
    fun `UpdatePostbox() should throw on unknown error on other error`() = runTest {
        mockApiCategory.stub {
            on { mutate<String>(any(), any(), any()) } doThrow RuntimeException("Mock runtime error")
        }

        shouldThrow<SudoDIRelayClient.DIRelayException.UnknownException> {
            client.updatePostbox("postbox-id", null)
        }

        verifyMutateCalled()
    }

    private fun graphQLError(errorType: String) = GraphQLResponse.Error(
        "mock",
        null,
        null,
        mapOf("errorType" to errorType),
    )

    private fun verifyMutateCalled() {
        verify(mockApiCategory).mutate<String>(
            check { it.query shouldBe UpdateRelayPostboxMutation.OPERATION_DOCUMENT },
            any(),
            any(),
        )
    }
}
