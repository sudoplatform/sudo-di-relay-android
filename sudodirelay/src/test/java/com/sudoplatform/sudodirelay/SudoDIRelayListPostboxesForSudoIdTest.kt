/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloHttpException
import com.sudoplatform.sudodirelay.graphql.CallbackHolder
import com.sudoplatform.sudodirelay.graphql.ListPostboxesForSudoIdQuery
import com.sudoplatform.sudodirelay.graphql.type.ListPostboxesForSudoIdInput
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

/**
 * Test the correct operation of [SudoDIRelayClient.listPostboxesForSudoId] using mocks and spies.
 */
class SudoDIRelayListPostboxesForSudoIdTest : BaseTests() {

    private val queryInput by before {
        ListPostboxesForSudoIdInput.builder()
            .sudoId("sudo1")
            .build()
    }

    private val queryResult by before {
        listOf(
            ListPostboxesForSudoIdQuery.ListPostboxesForSudoId(
                "",
                "0-0-0-1",
                "sudo1",
                "user1",
                (1_651_030_295_659).toDouble()
            )
        )
    }

    private val queryResultMultiple by before {
        listOf(
            ListPostboxesForSudoIdQuery.ListPostboxesForSudoId(
                "",
                "0-0-0-1",
                "sudo1",
                "user1",
                (1_623_302_710_000).toDouble()
            ),
            ListPostboxesForSudoIdQuery.ListPostboxesForSudoId(
                "",
                "0-0-0-2",
                "sudo1",
                "user1",
                (1_622_342_710_000).toDouble()
            ),
            ListPostboxesForSudoIdQuery.ListPostboxesForSudoId(
                "",
                "0-0-0-3",
                "sudo1",
                "user1",
                (1_624_902_710_000).toDouble()
            )
        )
    }

    private val queryResponse by before {
        Response.builder<ListPostboxesForSudoIdQuery.Data>(ListPostboxesForSudoIdQuery(queryInput))
            .data(ListPostboxesForSudoIdQuery.Data(queryResult))
            .build()
    }

    private val queryHolder = CallbackHolder<ListPostboxesForSudoIdQuery.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<ListPostboxesForSudoIdQuery>()) } doReturn queryHolder.queryOperation
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
    fun `listPostboxesForSudoId() should pass sudoId into the query`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val sudoId = "sudo1"
        val deferredResult = async(Dispatchers.IO) {
            client.listPostboxesForSudoId(sudoId)
        }

        deferredResult.start()
        delay(100)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(queryResponse)
        deferredResult.await()

        val actualQueryInput = ArgumentCaptor.forClass(ListPostboxesForSudoIdQuery::class.java)
        verify(mockAppSyncClient).query(actualQueryInput.capture())

        // verify input sudoId not changed
        actualQueryInput.value.variables().input()?.sudoId() shouldBe sudoId
    }

    @Test
    fun `listPostboxesForSudoId() should return empty list for null result`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val nullResponse by before {
            Response.builder<ListPostboxesForSudoIdQuery.Data>(
                ListPostboxesForSudoIdQuery(
                    queryInput
                )
            )
                .data(null)
                .build()
        }

        val sudoId = "sudo1"
        val deferredResult = async(Dispatchers.IO) {
            client.listPostboxesForSudoId(sudoId)
        }

        deferredResult.start()
        delay(100)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(nullResponse)
        val result = deferredResult.await()

        result shouldBe emptyList()

        verify(mockAppSyncClient).query(any<ListPostboxesForSudoIdQuery>())
    }

    @Test
    fun `listPostboxesForSudoId() should return results list in order of date`() =
        runBlocking<Unit> {
            queryHolder.callback shouldBe null

            val responseWithMultiple by before {
                Response.builder<ListPostboxesForSudoIdQuery.Data>(
                    ListPostboxesForSudoIdQuery(
                        queryInput
                    )
                )
                    .data(ListPostboxesForSudoIdQuery.Data(queryResultMultiple))
                    .build()
            }

            val deferredResult = async(Dispatchers.IO) {
                client.listPostboxesForSudoId("123")
            }

            deferredResult.start()

            delay(100)
            queryHolder.callback shouldNotBe null
            queryHolder.callback?.onResponse(responseWithMultiple)

            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 3

            result[0].connectionId shouldBe "0-0-0-2"
            result[1].connectionId shouldBe "0-0-0-1"
            result[2].connectionId shouldBe "0-0-0-3"

            verify(mockAppSyncClient).query(any<ListPostboxesForSudoIdQuery>())
        }

    @Test
    fun `listPostboxesForSudoId() should throw when http error occurs`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.FailedException> {
                client.listPostboxesForSudoId("sudo")
            }
        }

        deferredResult.start()
        delay(100)

        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onHttpError(ApolloHttpException(CommonData.forbiddenHTTPResponse))

        deferredResult.await()

        verify(mockAppSyncClient).query(any<ListPostboxesForSudoIdQuery>())
    }
}
