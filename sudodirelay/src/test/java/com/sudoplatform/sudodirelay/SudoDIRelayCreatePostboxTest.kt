package com.sudoplatform.sudodirelay

import android.content.Context
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.ApiException
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import com.sudoplatform.sudodirelay.graphql.CreateRelayPostboxMutation
import com.sudoplatform.sudodirelay.graphql.onMutate
import com.sudoplatform.sudodirelay.graphql.type.CreateRelayPostboxInput
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
 * Test the correct operation of [SudoDIRelayClient.createPostbox] using mocks and spies.
 */
class SudoDIRelayCreatePostboxTest : BaseTests() {

    private val connectionId = "62b39ef6-510f-4150-83e7-3ab4e71bffd8"

    private val ownershipProofToken = "eyJlYXN0ZXItZWdnIjogIndvYSwgbmljZSBkZWNvZGluZyBza2lsbHMgOjAgQikifQ=="

    private val mutationInput = CreateRelayPostboxInput(
        ownershipProof = ownershipProofToken,
        connectionId = connectionId,
        isEnabled = true,
    )

    private val mutationResponseJson by before {
        """
        {
            "createRelayPostbox": {
                "__typename": "RelayPostbox",
                "id": "postboxId",
                "createdAtEpochMs": 0.0,
                "updatedAtEpochMs": 1.0,
                "owner": "dummyOwner",
                "owners": [
                    { "__typename": "Owner", "id": "dummySudoOwner", "issuer": "sudoplatform.sudoservice" }
                ],
                "connectionId": "$connectionId",
                "isEnabled": true,
                "serviceEndpoint": "https://service.endpoint.test/this-should-be-our-connection-id"
            }
        }
        """.trimIndent()
    }

    private val mockContext by before { mock<Context>() }

    private val mockApiCategory by before {
        mock<ApiCategory>().stub {
            onMutate(CreateRelayPostboxMutation.OPERATION_DOCUMENT, mutationResponseJson)
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
    fun `createPostbox(variables) should pass variables correctly into mutation input`() = runTest {
        client.createPostbox(connectionId, ownershipProofToken)

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe CreateRelayPostboxMutation.OPERATION_DOCUMENT
                val input = it.variables["input"] as CreateRelayPostboxInput
                input.connectionId shouldBe connectionId
                input.ownershipProof shouldBe ownershipProofToken
                input.isEnabled shouldBe true
            },
            any(),
            any(),
        )
    }

    @Test
    fun `createPostbox() should throw with invalid postbox input error`() = runTest {
        mockApiCategory.stub {
            onMutate(
                CreateRelayPostboxMutation.OPERATION_DOCUMENT,
                null,
                listOf(graphQLError("sudoplatform.relay.InvalidPostboxInputError")),
            )
        }

        shouldThrow<SudoDIRelayClient.DIRelayException.InvalidPostboxInputException> {
            client.createPostbox("connection-id", ownershipProofToken)
        }

        verifyMutateCalled()
    }

    @Test
    fun `createPostbox() should throw generic on null response no errors`() = runTest {
        mockApiCategory.stub {
            onMutate(CreateRelayPostboxMutation.OPERATION_DOCUMENT, null)
        }

        shouldThrow<SudoDIRelayClient.DIRelayException.FailedException> {
            client.createPostbox(connectionId, ownershipProofToken)
        }

        verifyMutateCalled()
    }

    @Test
    fun `createPostbox() should throw on invalid token error`() = runTest {
        mockApiCategory.stub {
            onMutate(
                CreateRelayPostboxMutation.OPERATION_DOCUMENT,
                null,
                listOf(graphQLError("sudoplatform.InvalidTokenError")),
            )
        }

        shouldThrow<SudoDIRelayClient.DIRelayException.InvalidTokenException> {
            client.createPostbox(connectionId, "invalid-ownership-proof")
        }

        verifyMutateCalled()
    }

    @Test
    fun `createPostbox() should throw on api error`() = runTest {
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query == CreateRelayPostboxMutation.OPERATION_DOCUMENT },
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
            client.createPostbox(connectionId, ownershipProofToken)
        }

        verifyMutateCalled()
    }

    @Test
    fun `createPostbox() should throw on unknown error on other error`() = runTest {
        mockApiCategory.stub {
            on {
                mutate<String>(any(), any(), any())
            } doThrow RuntimeException("Mock runtime error")
        }

        shouldThrow<SudoDIRelayClient.DIRelayException.UnknownException> {
            client.createPostbox("123", ownershipProofToken)
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
            check { it.query shouldBe CreateRelayPostboxMutation.OPERATION_DOCUMENT },
            any(),
            any(),
        )
    }
}
