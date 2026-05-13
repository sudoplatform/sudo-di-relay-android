package com.sudoplatform.sudodirelay

import android.content.Context
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.ApiException
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.core.Consumer
import com.sudoplatform.sudodirelay.graphql.ListRelayMessagesQuery
import com.sudoplatform.sudodirelay.graphql.onQuery
import com.sudoplatform.sudodirelay.types.transformers.toDate
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.amplify.GraphQLClient
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

/**
 * Test the correct operation of [SudoDIRelayClient.listMessages] using mocks and spies.
 */
class SudoDIRelayListMessagesTest : BaseTests() {

    private val queryInputLimit = 10
    private val queryInputNextToken = "dummyToken"

    private fun messageItemJson(
        id: String,
        createdAt: Double,
        updatedAt: Double,
        message: String = "this is a message",
    ) = """
        {
            "__typename": "RelayMessage",
            "id": "$id",
            "createdAtEpochMs": $createdAt,
            "updatedAtEpochMs": $updatedAt,
            "owner": "dummyOwner",
            "owners": [
                { "__typename": "Owner", "id": "sudoOwner", "issuer": "sudoplatform.sudoservice" }
            ],
            "postboxId": "postbox-id",
            "message": "$message"
        }
        """.trimIndent()

    private val singleItemResponse by before {
        """
        {
            "listRelayMessages": {
                "__typename": "RelayMessageConnection",
                "items": [ ${messageItemJson("message-id", 0.0, 1.0)} ],
                "nextToken": null
            }
        }
        """.trimIndent()
    }

    private val emptyResponse by before {
        """
        {
            "listRelayMessages": {
                "__typename": "RelayMessageConnection",
                "items": [],
                "nextToken": null
            }
        }
        """.trimIndent()
    }

    private val multipleResponse by before {
        """
        {
            "listRelayMessages": {
                "__typename": "RelayMessageConnection",
                "items": [
                    ${messageItemJson("message-id-1", 0.0, 1.0, "test message 1")},
                    ${messageItemJson("message-id-2", 2.0, 3.0, "test message 2")},
                    ${messageItemJson("message-id-3", 4.0, 5.0, "test message 3")}
                ],
                "nextToken": null
            }
        }
        """.trimIndent()
    }

    private val mockContext by before { mock<Context>() }

    private val mockApiCategory by before {
        mock<ApiCategory>().stub {
            onQuery(ListRelayMessagesQuery.OPERATION_DOCUMENT, singleItemResponse)
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
    fun `listMessages should pass non-null parameters into the query`() = runTest {
        val limit = 6
        val nextToken = "12345678"

        client.listMessages(limit, nextToken)

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe ListRelayMessagesQuery.OPERATION_DOCUMENT
                it.variables["limit"] shouldBe limit
                it.variables["nextToken"] shouldBe nextToken
            },
            any(),
            any(),
        )
    }

    @Test
    fun `listMessages() should return results when no errors`() = runTest {
        val result = client.listMessages(queryInputLimit, queryInputNextToken)

        result shouldNotBe null
        result.items.size shouldBe 1
        result.nextToken shouldBe null

        with(result.items[0]) {
            id shouldBe "message-id"
            postboxId shouldBe "postbox-id"
            message shouldBe "this is a message"
            createdAt shouldBe (0.0).toDate()
            updatedAt shouldBe (1.0).toDate()
            ownerId shouldBe "dummyOwner"
            sudoId shouldBe "sudoOwner"
        }

        verifyQueryCalled()
    }

    @Test
    fun `listMessages() should return empty list for empty result`() = runTest {
        mockApiCategory.stub {
            onQuery(ListRelayMessagesQuery.OPERATION_DOCUMENT, emptyResponse)
        }

        val result = client.listMessages(queryInputLimit, queryInputNextToken)

        result.items shouldBe emptyList()
        verifyQueryCalled()
    }

    @Test
    fun `listMessages() should return multiple entries list for multiple result`() = runTest {
        mockApiCategory.stub {
            onQuery(ListRelayMessagesQuery.OPERATION_DOCUMENT, multipleResponse)
        }

        val result = client.listMessages(queryInputLimit, queryInputNextToken)

        result.items shouldHaveSize 3
        verifyQueryCalled()
    }

    @Test
    fun `listMessages() should throw when api error occurs`() = runTest {
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query == ListRelayMessagesQuery.OPERATION_DOCUMENT },
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
            client.listMessages(queryInputLimit, queryInputNextToken)
        }

        verifyQueryCalled()
    }

    private fun verifyQueryCalled() {
        verify(mockApiCategory).query<String>(
            check { it.query shouldBe ListRelayMessagesQuery.OPERATION_DOCUMENT },
            any(),
            any(),
        )
    }
}
