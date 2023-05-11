package com.sudoplatform.sudodirelay

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloHttpException
import com.sudoplatform.sudodirelay.CommonData.forbiddenHTTPResponse
import com.sudoplatform.sudodirelay.graphql.CallbackHolder
import com.sudoplatform.sudodirelay.graphql.ListRelayMessagesQuery
import com.sudoplatform.sudodirelay.graphql.ListRelayMessagesQuery.ListRelayMessages
import com.sudoplatform.sudodirelay.types.transformers.toDate
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
 * Test the correct operation of [SudoDIRelayClient.listMessages] using mocks and spies.
 */
class SudoDIRelayListMessagesTest : BaseTests() {

    private val queryInputLimit = 10
    private val queryInputNextToken = "dummyToken"

    private val queryResult by before {
        ListRelayMessages(
            "",
            listOf(
                ListRelayMessagesQuery.Item(
                    "message",
                    "message-id",
                    0.0,
                    1.0,
                    "dummyOwner",
                    listOf(ListRelayMessagesQuery.Owner("", "sudoOwner", "sudoplatform.sudoservice")),
                    "postbox-id",
                    "this is a message",
                )
            ),
            null
        )
    }

    private val queryResponse by before {
        Response.builder<ListRelayMessagesQuery.Data>(ListRelayMessagesQuery(queryInputLimit, queryInputNextToken))
            .data(ListRelayMessagesQuery.Data(queryResult))
            .build()
    }

    private val queryResultMultiple by before {
        ListRelayMessages(
            "",
            listOf(
                ListRelayMessagesQuery.Item(
                    "message",
                    "message-id-1",
                    0.0,
                    1.0,
                    "dummyOwner",
                    listOf(
                        ListRelayMessagesQuery.Owner("", "sudoOwner", "sudoplatform.sudoservice"),
                        ListRelayMessagesQuery.Owner("", "otherOwner", "sudoplatform.not.sudoservice")
                    ),
                    "postbox-id",
                    "this is a test message"
                ),
                ListRelayMessagesQuery.Item(
                    "message",
                    "message-id-2",
                    2.0,
                    3.0,
                    "dummyOwner",
                    listOf(
                        ListRelayMessagesQuery.Owner("", "sudoOwner", "sudoplatform.sudoservice"),
                        ListRelayMessagesQuery.Owner("", "otherOwner", "sudoplatform.not.sudoservice")
                    ),
                    "postbox-id",
                    "this is a test message"
                ),
                ListRelayMessagesQuery.Item(
                    "message",
                    "message-id-3",
                    4.0,
                    5.0,
                    "dummyOwner",
                    listOf(
                        ListRelayMessagesQuery.Owner("", "sudoOwner", "sudoplatform.sudoservice"),
                        ListRelayMessagesQuery.Owner("", "otherOwner", "sudoplatform.not.sudoservice")
                    ),
                    "postbox-id",
                    "this is a test message"
                )
            ),
            null
        )
    }

    private val emptyQueryResult by before {
        ListRelayMessages(
            "",
            listOf(),
            null
        )
    }

    private val queryHolder = CallbackHolder<ListRelayMessagesQuery.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<ListRelayMessagesQuery>()) } doReturn queryHolder.queryOperation
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
    fun `listMessages should pass non-null parameters into the query`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null
        val limit = 6
        val nextToken = "12345678"

        val deferredResult = async(Dispatchers.IO) {
            client.listMessages(limit, nextToken)
        }

        deferredResult.start()
        delay(100)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(queryResponse)
        deferredResult.await()

        val actualQueryInput = ArgumentCaptor.forClass(ListRelayMessagesQuery::class.java)
        verify(mockAppSyncClient).query(actualQueryInput.capture())

        // verify input not changed
        actualQueryInput.value.variables().limit() shouldBe limit
        actualQueryInput.value.variables().nextToken() shouldBe nextToken
    }

    @Test
    fun `listMessages() should return results when no errors`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            client.listMessages(queryInputLimit, queryInputNextToken)
        }

        deferredResult.start()

        delay(100)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(queryResponse)

        val result = deferredResult.await()

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

        verify(mockAppSyncClient).query(any<ListRelayMessagesQuery>())
    }

    @Test
    fun `listMessages() should return empty list for empty result`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val nullResponse by before {
            Response.builder<ListRelayMessagesQuery.Data>(
                ListRelayMessagesQuery(
                    queryInputLimit, queryInputNextToken
                )
            )
                .data(ListRelayMessagesQuery.Data(emptyQueryResult))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.listMessages(queryInputLimit, queryInputNextToken)
        }

        deferredResult.start()
        delay(100)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(nullResponse)
        val result = deferredResult.await()

        result.items shouldBe emptyList()

        verify(mockAppSyncClient).query(any<ListRelayMessagesQuery>())
    }

    @Test
    fun `listMessages() should return multiple entries list for multiple result`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val nullResponse by before {
            Response.builder<ListRelayMessagesQuery.Data>(
                ListRelayMessagesQuery(
                    queryInputLimit, queryInputNextToken
                )
            )
                .data(ListRelayMessagesQuery.Data(queryResultMultiple))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.listMessages(queryInputLimit, queryInputNextToken)
        }

        deferredResult.start()
        delay(100)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(nullResponse)
        val result = deferredResult.await()

        result.items shouldHaveSize 3

        verify(mockAppSyncClient).query(any<ListRelayMessagesQuery>())
    }
    @Test
    fun `listMessages() should throw when http error occurs`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.FailedException> {
                client.listMessages(queryInputLimit, queryInputNextToken)
            }
        }

        deferredResult.start()
        delay(100)

        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onHttpError(ApolloHttpException(forbiddenHTTPResponse))

        deferredResult.await()

        verify(mockAppSyncClient).query(any<ListRelayMessagesQuery>())
    }
}
