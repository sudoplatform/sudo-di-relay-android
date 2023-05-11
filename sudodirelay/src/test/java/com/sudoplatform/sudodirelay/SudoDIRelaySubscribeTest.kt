package com.sudoplatform.sudodirelay

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.mobileconnectors.appsync.AppSyncSubscriptionCall
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Subscription
import com.sudoplatform.sudodirelay.graphql.OnRelayMessageCreatedSubscription
import com.sudoplatform.sudodirelay.subscription.MessageSubscriber
import com.sudoplatform.sudodirelay.subscription.Subscriber
import com.sudoplatform.sudodirelay.types.Message
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

/**
 * Test the correct operation of [SudoDIRelayClient.subscribeToRelayEvents] using mocks and spies.
 */
class SudoDIRelaySubscribeTest : BaseTests() {

    private val subscriber = object : MessageSubscriber {
        override fun messageCreated(message: Message) {
            println("nothing")
        }

        override fun connectionStatusChanged(state: Subscriber.ConnectionState) {
            println("nothing")
        }
    }

    private val mockContext by before {
        mock<Context>()
    }

    private val mockMessageCreatedWatcher by before {
        mock<AppSyncSubscriptionCall<OnRelayMessageCreatedSubscription.Data>>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { subscribe(any<OnRelayMessageCreatedSubscription>()) } doReturn mockMessageCreatedWatcher
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
        verifyNoMoreInteractions(
            mockContext,
            mockAppSyncClient,
            mockMessageCreatedWatcher,
        )
    }

    @Test
    fun `subscribe(variables) should subscribe with correct variables input`() =
        runBlocking<Unit> {
            val deferredResult = async(Dispatchers.IO) {
                client.subscribeToRelayEvents("123", subscriber)
            }

            deferredResult.start()
            delay(100)

            deferredResult.await()

            // check subscribe correct
            val subscriptionCaptor: KArgumentCaptor<Subscription<Operation.Data, Any, Operation.Variables>> =
                argumentCaptor()
            verify(mockAppSyncClient, times(1)).subscribe(subscriptionCaptor.capture())

            subscriptionCaptor.allValues.size shouldBe 1
            with(subscriptionCaptor.allValues[0].variables().valueMap()) {
                get("owner") shouldBe "subject" // automatically retrieved from user client
            }

            verify(mockMessageCreatedWatcher).execute(any<AppSyncSubscriptionCall.Callback<OnRelayMessageCreatedSubscription.Data>>())
        }
}
