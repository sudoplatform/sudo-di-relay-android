package com.sudoplatform.sudodirelay

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.mobileconnectors.appsync.AppSyncSubscriptionCall
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Subscription
import com.sudoplatform.sudodirelay.graphql.OnMessageCreatedSubscription
import com.sudoplatform.sudodirelay.graphql.OnPostBoxDeletedSubscription
import com.sudoplatform.sudodirelay.graphql.type.Direction
import com.sudoplatform.sudodirelay.subscription.DIRelayEventSubscriber
import com.sudoplatform.sudodirelay.types.PostboxDeletionResult
import com.sudoplatform.sudodirelay.types.RelayMessage
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

    private val subscriber = object : DIRelayEventSubscriber {
        override fun messageIncoming(message: RelayMessage) {
            println("nothing")
        }

        override fun postBoxDeleted(update: PostboxDeletionResult) {
            println("nothing")
        }

        override fun connectionStatusChanged(state: DIRelayEventSubscriber.ConnectionState) {
            println("nothing")
        }
    }

    private val mockContext by before {
        mock<Context>()
    }

    private val mockMessageCreatedWatcher by before {
        mock<AppSyncSubscriptionCall<OnMessageCreatedSubscription.Data>>()
    }

    private val mockPostboxDeletedWatcher by before {
        mock<AppSyncSubscriptionCall<OnPostBoxDeletedSubscription.Data>>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { subscribe(any<OnMessageCreatedSubscription>()) } doReturn mockMessageCreatedWatcher
            on { subscribe(any<OnPostBoxDeletedSubscription>()) } doReturn mockPostboxDeletedWatcher
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
            mockPostboxDeletedWatcher
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
            verify(mockAppSyncClient, times(2)).subscribe(subscriptionCaptor.capture())

            subscriptionCaptor.allValues.size shouldBe 2
            with(subscriptionCaptor.allValues[0].variables().valueMap()) {
                get("connectionId") shouldBe "123"
                get("direction").toString() shouldBe Direction.INBOUND.toString()
            }
            subscriptionCaptor.allValues[1].variables().valueMap()["connectionId"] shouldBe "123"

            verify(mockMessageCreatedWatcher).execute(any<AppSyncSubscriptionCall.Callback<OnMessageCreatedSubscription.Data>>())
            verify(mockPostboxDeletedWatcher).execute(any<AppSyncSubscriptionCall.Callback<OnPostBoxDeletedSubscription.Data>>())
        }
}
