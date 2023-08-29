/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloHttpException
import com.sudoplatform.sudodirelay.graphql.CallbackHolder
import com.sudoplatform.sudodirelay.graphql.ListRelayPostboxesQuery
import com.sudoplatform.sudodirelay.graphql.ListRelayPostboxesQuery.ListRelayPostboxes
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.matchers.collections.shouldHaveSize
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

    private val queryResult by before {
        ListRelayPostboxes(
            "",
            listOf(
                ListRelayPostboxesQuery.Item(
                    "postbox",
                    "postbox-id",
                    0.0,
                    1.0,
                    "dummyOwner",
                    listOf(ListRelayPostboxesQuery.Owner("", "sudoOwner", "sudoplatform.sudoservice")),
                    "connection-id",
                    true,
                    "https://test.endpont.com/postbox-id"
                )
            ),
            null
        )
    }

    private val queryResponse by before {
        Response.builder<ListRelayPostboxesQuery.Data>(ListRelayPostboxesQuery(queryInputLimit, queryInputNextToken))
            .data(ListRelayPostboxesQuery.Data(queryResult))
            .build()
    }

    private val queryResultMultiple by before {
        ListRelayPostboxes(
            "",
            listOf(
                ListRelayPostboxesQuery.Item(
                    "postbox",
                    "postbox-id",
                    0.0,
                    1.0,
                    "dummyOwner",
                    listOf(
                        ListRelayPostboxesQuery.Owner("", "sudoOwner", "sudoplatform.sudoservice"),
                        ListRelayPostboxesQuery.Owner("", "dummyOwner", "sudoplatform.not.sudoservice")
                    ),
                    "connection-id-1",
                    true,
                    "https://test.endpont.com/postbox-id"
                ),
                ListRelayPostboxesQuery.Item(
                    "postbox",
                    "postbox-id-2",
                    2.0,
                    3.0,
                    "dummyOwner",
                    listOf(ListRelayPostboxesQuery.Owner("", "sudoOwner", "sudoplatform.sudoservice")),
                    "connection-id-2",
                    true,
                    "https://test.endpont.com/postbox-id"
                ),
                ListRelayPostboxesQuery.Item(
                    "postbox",
                    "postbox-id-2",
                    4.0,
                    5.0,
                    "dummyOwner",
                    listOf(ListRelayPostboxesQuery.Owner("", "sudoOwner", "sudoplatform.sudoservice")),
                    "connection-id-3",
                    true,
                    "https://test.endpont.com/postbox-id"
                )
            ),
            null
        )
    }

    private val emptyQueryResult by before {
        ListRelayPostboxes(
            "",
            listOf(),
            null
        )
    }

    private val queryHolder = CallbackHolder<ListRelayPostboxesQuery.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<ListRelayPostboxesQuery>()) } doReturn queryHolder.queryOperation
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
    fun `listPostboxes() should pass non-null parameters into the query`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null
        val limit = 9
        val nextToken = "12345678"

        val deferredResult = async(Dispatchers.IO) {
            client.listPostboxes(limit, nextToken)
        }

        deferredResult.start()
        delay(100)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(queryResponse)
        deferredResult.await()

        val actualQueryInput = ArgumentCaptor.forClass(ListRelayPostboxesQuery::class.java)
        verify(mockAppSyncClient).query(actualQueryInput.capture())

        // verify input not changed
        actualQueryInput.value.variables().nextToken() shouldBe nextToken
        actualQueryInput.value.variables().limit() shouldBe limit
    }

    @Test
    fun `listPostboxes() should return empty list for empty result`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val nullResponse by before {
            Response.builder<ListRelayPostboxesQuery.Data>(
                ListRelayPostboxesQuery(
                    queryInputLimit,
                    queryInputNextToken
                )
            )
                .data(ListRelayPostboxesQuery.Data(emptyQueryResult))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.listPostboxes(queryInputLimit, queryInputNextToken)
        }

        deferredResult.start()
        delay(100)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(nullResponse)
        val result = deferredResult.await()

        result.items shouldBe emptyList()

        verify(mockAppSyncClient).query(any<ListRelayPostboxesQuery>())
    }

    @Test
    fun `listPostboxes() should return multiple entries list for multiple result`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val nullResponse by before {
            Response.builder<ListRelayPostboxesQuery.Data>(
                ListRelayPostboxesQuery(
                    queryInputLimit,
                    queryInputNextToken
                )
            )
                .data(ListRelayPostboxesQuery.Data(queryResultMultiple))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.listPostboxes(queryInputLimit, queryInputNextToken)
        }

        deferredResult.start()
        delay(100)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(nullResponse)
        val result = deferredResult.await()

        result.items shouldHaveSize 3

        verify(mockAppSyncClient).query(any<ListRelayPostboxesQuery>())
    }

    @Test
    fun `listPostboxes() should throw when http error occurs`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.FailedException> {
                client.listPostboxes(null, null)
            }
        }

        deferredResult.start()
        delay(100)

        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onHttpError(ApolloHttpException(CommonData.forbiddenHTTPResponse))

        deferredResult.await()

        verify(mockAppSyncClient).query(any<ListRelayPostboxesQuery>())
    }
}
