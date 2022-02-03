package com.sudoplatform.sudodirelay.types.transformers

import com.sudoplatform.sudodirelay.graphql.GetMessagesQuery
import com.sudoplatform.sudodirelay.graphql.OnMessageCreatedSubscription
import com.sudoplatform.sudodirelay.graphql.type.Direction
import com.sudoplatform.sudodirelay.types.RelayMessage
import com.sudoplatform.sudodirelay.types.transformers.RelayMessageTransformer.toEntityDirection
import com.sudoplatform.sudodirelay.types.transformers.RelayMessageTransformer.toEntityFromGetMessages
import io.kotlintest.shouldBe
import org.junit.Test
import java.util.Date

class RelayMessageTransformerTest {

    @Test
    fun `all direction transform cases`() {
        Direction.INBOUND.toEntityDirection() shouldBe RelayMessage.Direction.INBOUND
        Direction.OUTBOUND.toEntityDirection() shouldBe RelayMessage.Direction.OUTBOUND
    }

    @Test
    fun `test entity transform of regular onMessageCreated subscription event`() {
        // given
        val input = OnMessageCreatedSubscription.OnMessageCreated(
            "",
            "mid1",
            "cid1",
            "hello world",
            Direction.INBOUND,
            (1_624_302_710_000).toDouble()
        )
        val exceptedOutput = RelayMessage(
            "mid1",
            "cid1",
            "hello world",
            RelayMessage.Direction.INBOUND,
            Date(1624302710000)
        )

        // when
        val output = RelayMessageTransformer.toEntityFromMessageCreatedSubscriptionEvent(input)

        // then
        output shouldBe exceptedOutput
    }

    @Test
    fun `test entity transform of regular getMessage data`() {
        // given
        val msg1 = GetMessagesQuery.GetMessage(
            "",
            "msg1",
            "con1",
            "hello",
            Direction.INBOUND,
            1642255117000.toDouble()
        )
        val msg2 = GetMessagesQuery.GetMessage(
            "",
            "msg2",
            "con1",
            "bye",
            Direction.OUTBOUND,
            1642253117000.toDouble()
        )
        val getMsgs = listOf(msg1, msg2)
        val expectedOutput = listOf(
            RelayMessage(
                "msg1",
                "con1",
                "hello",
                RelayMessage.Direction.INBOUND,
                Date(1642255117000)
            ),
            RelayMessage(
                "msg2",
                "con1",
                "bye",
                RelayMessage.Direction.OUTBOUND,
                Date(1642253117000)
            )
        )

        // when
        val output = toEntityFromGetMessages(getMsgs)

        // then
        output.size shouldBe 2
        output.zip(expectedOutput).forEach {
            it.first shouldBe it.second
        }
    }
}
