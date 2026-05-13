package com.sudoplatform.sudodirelay

import android.content.Context
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.ApiException
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import com.sudoplatform.sudodirelay.graphql.DeleteRelayPostboxMutation
import com.sudoplatform.sudodirelay.graphql.onMutate
import com.sudoplatform.sudodirelay.graphql.type.DeleteRelayPostboxInput
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
 * Test the correct operation of [SudoDIRelayClient.deletePostbox] using mocks and spies.
 */
class SudoDIRelayDeletePostboxTest : BaseTests() {

    private val mutationResponseJson =
        """
        {
            "deleteRelayPostbox": { "__typename": "DeletedPostbox", "id": "postbox-id" }
        }
        """.trimIndent()

    private val mockContext by before { mock<Context>() }

    private val mockApiCategory by before {
        mock<ApiCategory>().stub {
            onMutate(DeleteRelayPostboxMutation.OPERATION_DOCUMENT, mutationResponseJson)
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
    fun `deletePostbox(variables) should pass variables correctly into mutation input`() = runTest {
        val postboxId = "1234-1234-1234-1234"

        client.deletePostbox(postboxId)

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe DeleteRelayPostboxMutation.OPERATION_DOCUMENT
                val input = it.variables["input"] as DeleteRelayPostboxInput
                input.postboxId shouldBe postboxId
            },
            any(),
            any(),
        )
    }

    @Test
    fun `deletePostbox() should invoke graphQL client`() = runTest {
        client.deletePostbox("postbox-id")
        verifyMutateCalled()
    }

    @Test
    fun `deletePostbox() should not throw on null response`() = runTest {
        mockApiCategory.stub {
            onMutate(DeleteRelayPostboxMutation.OPERATION_DOCUMENT, "{}")
        }

        val postboxId = "postbox-id"
        val result = client.deletePostbox(postboxId)

        result shouldBe postboxId
        verifyMutateCalled()
    }

    @Test
    fun `deletePostbox() should not throw on UnauthorizedPostboxAccess response`() = runTest {
        mockApiCategory.stub {
            onMutate(
                DeleteRelayPostboxMutation.OPERATION_DOCUMENT,
                "{}",
                listOf(graphQLError("sudoplatform.relay.UnauthorizedPostboxAccessError")),
            )
        }

        val postboxId = "postbox-id"
        val result = client.deletePostbox(postboxId)

        result shouldBe postboxId
        verifyMutateCalled()
    }

    @Test
    fun `deletePostbox() should throw on api error`() = runTest {
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query == DeleteRelayPostboxMutation.OPERATION_DOCUMENT },
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
            client.deletePostbox("postbox-id")
        }

        verifyMutateCalled()
    }

    @Test
    fun `deletePostbox() should throw on unknown error`() = runTest {
        mockApiCategory.stub {
            on { mutate<String>(any(), any(), any()) } doThrow RuntimeException("Mock runtime error")
        }

        shouldThrow<SudoDIRelayClient.DIRelayException.UnknownException> {
            client.deletePostbox("postbox-id")
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
            check { it.query shouldBe DeleteRelayPostboxMutation.OPERATION_DOCUMENT },
            any(),
            any(),
        )
    }
}
