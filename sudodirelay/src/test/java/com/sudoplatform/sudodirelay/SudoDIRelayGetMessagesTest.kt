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
import java.util.Date

/**
 * Test the correct operation of [SudoDIRelayClient.getMessages] using mocks and spies.
 *
 * @since 2021-07-02
 */
class SudoDIRelayGetMessagesTest : BaseTests() {

    private val queryInput by before {
        IdAsInput.builder()
            .connectionId("null")
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
                (1_624_302_710_000).toDouble()
            )
        )
    }

    private val queryResultMultiple by before {
        listOf(
            GetMessagesQuery.GetMessage(
                "",
                "001",
                "123",
                "hi",
                Direction.INBOUND,
                (1_623_302_710_000).toDouble()
            ),
            GetMessagesQuery.GetMessage(
                "",
                "002",
                "123",
                "bye",
                Direction.OUTBOUND,
                (1_622_342_710_000).toDouble()
            ),
            GetMessagesQuery.GetMessage(
                "",
                "003",
                "123",
                "hello world",
                Direction.INBOUND,
                (1_624_902_710_000).toDouble()
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
        actualQueryInput.value.variables().input().connectionId() shouldBe connectionId
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
    fun `getMessages() should return list in order of date`() =
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

            result[0].messageId shouldBe "002"
            result[1].messageId shouldBe "001"
            result[2].messageId shouldBe "003"

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
