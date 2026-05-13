/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay

import android.content.Context
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.ApiException
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.core.Consumer
import com.sudoplatform.sudodirelay.graphql.ListRelayPostboxesQuery
import com.sudoplatform.sudodirelay.graphql.onQuery
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.amplify.GraphQLClient
import io.kotlintest.matchers.collections.shouldHaveSize
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

/**
 * Test the correct operation of [SudoDIRelayClient.listPostboxes] using mocks and spies.
 */
class SudoDIRelayListPostboxesTest : BaseTests() {

    private val queryInputLimit = 10
    private val queryInputNextToken = "dummyToken"

    private fun postboxItemJson(id: String, connectionId: String) =
        """
        {
            "__typename": "RelayPostbox",
            "id": "$id",
            "createdAtEpochMs": 0.0,
            "updatedAtEpochMs": 1.0,
            "owner": "dummyOwner",
            "owners": [
                { "__typename": "Owner", "id": "sudoOwner", "issuer": "sudoplatform.sudoservice" }
            ],
            "connectionId": "$connectionId",
            "isEnabled": true,
            "serviceEndpoint": "https://test.endpont.com/postbox-id"
        }
        """.trimIndent()

    private val singleItemResponse by before {
        """
        {
            "listRelayPostboxes": {
                "__typename": "RelayPostboxConnection",
                "items": [ ${postboxItemJson("postbox-id", "connection-id")} ],
                "nextToken": null
            }
        }
        """.trimIndent()
    }

    private val emptyResponse by before {
        """
        {
            "listRelayPostboxes": {
                "__typename": "RelayPostboxConnection",
                "items": [],
                "nextToken": null
            }
        }
        """.trimIndent()
    }

    private val multipleResponse by before {
        """
        {
            "listRelayPostboxes": {
                "__typename": "RelayPostboxConnection",
                "items": [
                    ${postboxItemJson("postbox-id", "connection-id-1")},
                    ${postboxItemJson("postbox-id-2", "connection-id-2")},
                    ${postboxItemJson("postbox-id-3", "connection-id-3")}
                ],
                "nextToken": null
            }
        }
        """.trimIndent()
    }

    private val mockContext by before { mock<Context>() }

    private val mockApiCategory by before {
        mock<ApiCategory>().stub {
            onQuery(ListRelayPostboxesQuery.OPERATION_DOCUMENT, singleItemResponse)
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
    fun `listPostboxes() should pass non-null parameters into the query`() = runTest {
        val limit = 9
        val nextToken = "12345678"

        client.listPostboxes(limit, nextToken)

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe ListRelayPostboxesQuery.OPERATION_DOCUMENT
                it.variables["limit"] shouldBe limit
                it.variables["nextToken"] shouldBe nextToken
            },
            any(),
            any(),
        )
    }

    @Test
    fun `listPostboxes() should return empty list for empty result`() = runTest {
        mockApiCategory.stub {
            onQuery(ListRelayPostboxesQuery.OPERATION_DOCUMENT, emptyResponse)
        }

        val result = client.listPostboxes(queryInputLimit, queryInputNextToken)

        result.items shouldBe emptyList()
        verifyQueryCalled()
    }

    @Test
    fun `listPostboxes() should return multiple entries list for multiple result`() = runTest {
        mockApiCategory.stub {
            onQuery(ListRelayPostboxesQuery.OPERATION_DOCUMENT, multipleResponse)
        }

        val result = client.listPostboxes(queryInputLimit, queryInputNextToken)

        result.items shouldHaveSize 3
        verifyQueryCalled()
    }

    @Test
    fun `listPostboxes() should throw when api error occurs`() = runTest {
        mockApiCategory.stub {
            on {
                query<String>(
                    argThat { this.query == ListRelayPostboxesQuery.OPERATION_DOCUMENT },
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
            client.listPostboxes(null, null)
        }

        verifyQueryCalled()
    }

    private fun verifyQueryCalled() {
        verify(mockApiCategory).query<String>(
            check { it.query shouldBe ListRelayPostboxesQuery.OPERATION_DOCUMENT },
            any(),
            any(),
        )
    }
}
