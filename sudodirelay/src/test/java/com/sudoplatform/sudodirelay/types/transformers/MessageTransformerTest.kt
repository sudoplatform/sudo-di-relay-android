package com.sudoplatform.sudodirelay.types.transformers

import com.sudoplatform.sudodirelay.SudoDIRelayClient
import com.sudoplatform.sudodirelay.graphql.ListRelayMessagesQuery
import com.sudoplatform.sudodirelay.graphql.OnRelayMessageCreatedSubscription
import com.sudoplatform.sudodirelay.graphql.OnRelayMessageCreatedSubscription.Owner
import com.sudoplatform.sudodirelay.types.Message
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import org.junit.Test
import java.util.Date

class MessageTransformerTest {
    @Test
    fun `test entity transform of regular onMessageCreated subscription event`() {
        // given
        val input = OnRelayMessageCreatedSubscription.OnRelayMessageCreated(
            "",
            "mid1",
            0.0,
            1.0,
            "dummyOwner",
            listOf(Owner("", "dummySudoOwner", "sudoplatform.sudoservice")),
            "dummyPostboxId",
            "the message"
        )
        val expectedOutput = Message(
            "mid1",
            Date(0),
            Date(1),
            "dummyOwner",
            "dummySudoOwner",
            "dummyPostboxId",
            "the message"
        )

        // when
        val output = MessageTransformer.toEntityFromMessageCreatedSubscriptionEvent(input)

        // then
        output shouldBe expectedOutput
    }

    @Test
    fun `test entity transform of regular listMessages data`() {
        // given
        val messagesList = ListRelayMessagesQuery.ListRelayMessages(
            "",
            listOf(
                ListRelayMessagesQuery.Item(
                    "",
                    "msg1",
                    0.0,
                    1.0,
                    "dummyOwner",
                    listOf(
                        ListRelayMessagesQuery.Owner("", "dummySudoOwner", "sudoplatform.sudoservice"),
                        ListRelayMessagesQuery.Owner("", "notSudoOwner", "sudoplatform.identityservice")
                    ),
                    "dummyPostboxId",
                    "the message"
                ),
                ListRelayMessagesQuery.Item(
                    "",
                    "msg2",
                    2.0,
                    3.0,
                    "dummyOwner",
                    listOf(ListRelayMessagesQuery.Owner("", "dummySudoOwner", "sudoplatform.sudoservice")),
                    "dummyPostboxId",
                    "the other message"
                ),
                ListRelayMessagesQuery.Item(
                    "",
                    "msg3",
                    4.0,
                    5.0,
                    "dummyOwner",
                    listOf(ListRelayMessagesQuery.Owner("", "dummySudoOwner", "sudoplatform.sudoservice")),
                    "dummyPostboxId2",
                    "the message from another postbox"
                )
            ),
            null
        )

        val expectedItems = listOf(
            Message(
                "msg1",
                Date(0),
                Date(1),
                "dummyOwner",
                "dummySudoOwner",
                "dummyPostboxId",
                "the message"
            ),
            Message(
                "msg2",
                Date(2),
                Date(3),
                "dummyOwner",
                "dummySudoOwner",
                "dummyPostboxId",
                "the other message"
            ),
            Message(
                "msg3",
                Date(4),
                Date(5),
                "dummyOwner",
                "dummySudoOwner",
                "dummyPostboxId2",
                "the message from another postbox"
            )
        )

        // when
        val output = MessageTransformer.toEntityList(messagesList)

        // then
        output.items.size shouldBe 3
        output.nextToken shouldBe null

        for (i in 0..2) {
            output.items[i] shouldBe expectedItems[i]
        }
    }

    @Test
    fun `test entity transform of listMessages data with missing owner fails`() {
        // given
        val messagesList = ListRelayMessagesQuery.ListRelayMessages(
            "",
            listOf(
                ListRelayMessagesQuery.Item(
                    "",
                    "msg1",
                    0.0,
                    1.0,
                    "dummyOwner",
                    listOf(
                        ListRelayMessagesQuery.Owner("", "notSudoOwner", "sudoplatform.not.sudoservice"),
                        ListRelayMessagesQuery.Owner("", "alsoNotSudoOwner", "sudoplatform.identityservice")
                    ),
                    "dummyPostboxId",
                    "the message"
                ),
                ListRelayMessagesQuery.Item(
                    "",
                    "msg2",
                    2.0,
                    3.0,
                    "dummyOwner",
                    listOf(ListRelayMessagesQuery.Owner("", "notSudoOwner", "sudoplatform.not.sudoservice")),
                    "dummyPostboxId",
                    "the other message"
                )
            ),
            null
        )

        shouldThrow<SudoDIRelayClient.DIRelayException.FailedException> {
            MessageTransformer.toEntityList(messagesList)
        }
    }
}
