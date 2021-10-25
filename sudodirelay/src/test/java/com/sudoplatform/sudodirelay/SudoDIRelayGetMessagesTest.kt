package com.sudoplatform.sudodirelay

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloHttpException
import com.sudoplatform.sudodirelay.CommonData.forbiddenHTTPResponse
import com.sudoplatform.sudodirelay.graphql.CallbackHolder
import com.sudoplatform.sudodirelay.graphql.GetMessagesQuery
import com.sudoplatform.sudodirelay.graphql.type.Direction
import com.sudoplatform.sudodirelay.graphql.type.IdAsInput
import com.sudoplatform.sudodirelay.types.RelayMessage
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
import java.util.Date

/**
 * Test the correct operation of [SudoDIRelayClient.getMessages] using mocks and spies.
 *
 * @since 2021-07-02
 */
class SudoDIRelayGetMessagesTest : BaseTests() {

    private val queryInput by before {
        IdAsInput.builder()
            .id("null")
            .build()
    }

    private val queryResult by before {
        listOf(
            GetMessagesQuery.GetMessage(
                "",
                "mid",
                "cid",
                "hello",
                Direction.INBOUND,
                "Mon, 21 Jun 2021 19:11:50 GMT",
                null
            )
        )
    }

    private val queryResultMultiple by before {
        listOf(
            GetMessagesQuery.GetMessage(
                "",
                "init",
                "123",
                "",
                Direction.OUTBOUND,
                "Fri, 2 Jul 2021 21:30:00 GMT",
                null
            ),
            GetMessagesQuery.GetMessage(
                "",
                "001",
                "123",
                "hi",
                Direction.INBOUND,
                "Sun, 4 Jul 2021 21:30:00 GMT",
                null
            ),
            GetMessagesQuery.GetMessage(
                "",
                "002",
                "123",
                "bye",
                Direction.OUTBOUND,
                "Mon, 5 Jul 2021 8:00:00 GMT",
                null
            ),
            GetMessagesQuery.GetMessage(
                "",
                "003",
                "123",
                "hello world",
                Direction.INBOUND,
                "Sat, 3 Jul 2021 12:45:00 GMT",
                null
            )
        )
    }

    private val queryResultTimestampDate = Date(1624302710000)

    private val queryResponse by before {
        Response.builder<GetMessagesQuery.Data>(GetMessagesQuery(queryInput))
            .data(GetMessagesQuery.Data(queryResult))
            .build()
    }

    private val queryHolder = CallbackHolder<GetMessagesQuery.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<GetMessagesQuery>()) } doReturn queryHolder.queryOperation
        }
    }

    private val client by before {
        DefaultSudoDIRelayClient(
            mockContext,
            mockAppSyncClient,
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
    fun `getMessages(connectionID) should pass connectionID into the query`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val connectionId = "1234-1234-1234-1234"
        val deferredResult = async(Dispatchers.IO) {
            client.getMessages(connectionId)
        }

        deferredResult.start()
        delay(100)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(queryResponse)
        deferredResult.await()

        val actualQueryInput = ArgumentCaptor.forClass(GetMessagesQuery::class.java)
        verify(mockAppSyncClient).query(actualQueryInput.capture())

        // verify input connectionID not changed
        actualQueryInput.value.variables().input().id() shouldBe connectionId
    }

    @Test
    fun `getMessages() should return results when no errors`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            client.getMessages("cid")
        }

        deferredResult.start()

        delay(100)
        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onResponse(queryResponse)

        val result = deferredResult.await()

        result shouldNotBe null
        result.size shouldBe 1

        with(result[0]) {
            messageId shouldBe "mid"
            connectionId shouldBe "cid"
            cipherText shouldBe "hello"
            direction shouldBe RelayMessage.Direction.INBOUND
            timestamp shouldBe queryResultTimestampDate
        }

        verify(mockAppSyncClient).query(any<GetMessagesQuery>())
    }

    @Test
    fun `getMessages() should return list in order of date without init message`() =
        runBlocking<Unit> {
            queryHolder.callback shouldBe null

            val responseWithMultiple by before {
                Response.builder<GetMessagesQuery.Data>(GetMessagesQuery(queryInput))
                    .data(GetMessagesQuery.Data(queryResultMultiple))
                    .build()
            }

            val deferredResult = async(Dispatchers.IO) {
                client.getMessages("123")
            }

            deferredResult.start()

            delay(100)
            queryHolder.callback shouldNotBe null
            queryHolder.callback?.onResponse(responseWithMultiple)

            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 3

            result[0].messageId shouldBe "003"
            result[1].messageId shouldBe "001"
            result[2].messageId shouldBe "002"

            verify(mockAppSyncClient).query(any<GetMessagesQuery>())
        }

    @Test
    fun `getMessages() should throw when query result data is empty list`() =
        runBlocking<Unit> {
            queryHolder.callback shouldBe null

            val queryResultWithEmptyList by before { listOf<GetMessagesQuery.GetMessage>() }

            val responseWithEmptyList by before {
                Response.builder<GetMessagesQuery.Data>(GetMessagesQuery(queryInput))
                    .data(GetMessagesQuery.Data(queryResultWithEmptyList))
                    .build()
            }

            val deferredResult = async(Dispatchers.IO) {
                shouldThrow<SudoDIRelayClient.DIRelayException.InvalidPostboxException> {
                    client.getMessages("123")
                }
            }

            deferredResult.start()

            delay(100)

            queryHolder.callback shouldNotBe null
            queryHolder.callback?.onResponse(responseWithEmptyList)

            deferredResult.await()

            verify(mockAppSyncClient).query(any<GetMessagesQuery>())
        }

    @Test
    fun `getMessages() should throw error when query response is null`() =
        runBlocking<Unit> {
            queryHolder.callback shouldBe null

            val nullQueryResponse by before {
                Response.builder<GetMessagesQuery.Data>(GetMessagesQuery(queryInput))
                    .data(null)
                    .build()
            }

            val deferredResult = async(Dispatchers.IO) {
                shouldThrow<SudoDIRelayClient.DIRelayException.InvalidPostboxException> {
                    client.getMessages("123")
                }
            }

            deferredResult.start()
            delay(100)

            queryHolder.callback shouldNotBe null
            queryHolder.callback?.onResponse(nullQueryResponse)

            deferredResult.await()

            verify(mockAppSyncClient).query(any<GetMessagesQuery>())
        }

    @Test
    fun `getMessages() should throw when http error occurs`() = runBlocking<Unit> {
        queryHolder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoDIRelayClient.DIRelayException.FailedException> {
                client.getMessages("cid")
            }
        }

        deferredResult.start()
        delay(100)

        queryHolder.callback shouldNotBe null
        queryHolder.callback?.onHttpError(ApolloHttpException(forbiddenHTTPResponse))

        deferredResult.await()

        verify(mockAppSyncClient).query(any<GetMessagesQuery>())
    }
}
