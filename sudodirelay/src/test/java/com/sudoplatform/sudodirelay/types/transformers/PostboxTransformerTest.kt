package com.sudoplatform.sudodirelay.types.transformers

import com.sudoplatform.sudodirelay.SudoDIRelayClient
import com.sudoplatform.sudodirelay.graphql.CreateRelayPostboxMutation
import com.sudoplatform.sudodirelay.graphql.ListRelayPostboxesQuery
import com.sudoplatform.sudodirelay.types.Postbox
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import org.junit.Test
import java.util.Date

class PostboxTransformerTest {
    @Test
    fun `test entity transform on postbox creation`() {
        // given
        val input = CreateRelayPostboxMutation.CreateRelayPostbox(
            "",
            "postbox-id",
            0.0,
            1.0,
            "dummyOwner",
            listOf(
                CreateRelayPostboxMutation.Owner("", "dummySudoOwner", "sudoplatform.sudoservice"),
                CreateRelayPostboxMutation.Owner("", "dummyOtherOwner", "sudoplatform.not.sudoservice")
            ),
            "connection-id",
            true,
            "https://test.service.endpoint/postbox-id"
        )
        val expectedOutput = Postbox(
            "postbox-id",
            Date(0),
            Date(1),
            "dummyOwner",
            "dummySudoOwner",
            "connection-id",
            true,
            "https://test.service.endpoint/postbox-id"
        )

        // when
        val output = PostboxTransformer.toEntity(input)

        // then
        output shouldBe expectedOutput
    }

    @Test
    fun `test entity transform of regular listPostboxes data`() {
        // given
        val postboxesList = ListRelayPostboxesQuery.ListRelayPostboxes(
            "",
            listOf(
                ListRelayPostboxesQuery.Item(
                    "",
                    "postbox-id-1",
                    0.0,
                    1.0,
                    "dummyOwner",
                    listOf(
                        ListRelayPostboxesQuery.Owner("", "dummySudoOwner", "sudoplatform.sudoservice"),
                        ListRelayPostboxesQuery.Owner("", "notSudoOwner", "sudoplatform.identityservice")
                    ),
                    "connection-id-1",
                    true,
                    "https://the.service.endpoint/relay/postbox-id-1"
                ),
                ListRelayPostboxesQuery.Item(
                    "",
                    "postbox-id-2",
                    2.0,
                    3.0,
                    "dummyOwner",
                    listOf(ListRelayPostboxesQuery.Owner("", "dummySudoOwner", "sudoplatform.sudoservice")),
                    "connection-id-2",
                    true,
                    "https://the.service.endpoint/relay/postbox-id-2"
                ),
                ListRelayPostboxesQuery.Item(
                    "",
                    "postbox-id-3",
                    4.0,
                    5.0,
                    "dummyOwner",
                    listOf(ListRelayPostboxesQuery.Owner("", "dummySudoOwner", "sudoplatform.sudoservice")),
                    "connection-id-3",
                    true,
                    "https://the.service.endpoint/relay/postbox-id-3"
                )
            ),
            null
        )

        val expectedItems = listOf(
            Postbox(
                "postbox-id-1",
                Date(0),
                Date(1),
                "dummyOwner",
                "dummySudoOwner",
                "connection-id-1",
                true,
                "https://the.service.endpoint/relay/postbox-id-1"
            ),
            Postbox(
                "postbox-id-2",
                Date(2),
                Date(3),
                "dummyOwner",
                "dummySudoOwner",
                "connection-id-2",
                true,
                "https://the.service.endpoint/relay/postbox-id-2"
            ),
            Postbox(
                "postbox-id-3",
                Date(4),
                Date(5),
                "dummyOwner",
                "dummySudoOwner",
                "connection-id-3",
                true,
                "https://the.service.endpoint/relay/postbox-id-3"
            )
        )

        // when
        val output = PostboxTransformer.toEntityList(postboxesList)

        // then
        output.items.size shouldBe 3
        output.nextToken shouldBe null

        for (i in 0..2) {
            output.items[i] shouldBe expectedItems[i]
        }
    }

    @Test
    fun `test entity transform of listPostboxes data with missing owner fails`() {
        // given
        val postboxesList = ListRelayPostboxesQuery.ListRelayPostboxes(
            "",
            listOf(
                ListRelayPostboxesQuery.Item(
                    "",
                    "postbox-id-1",
                    0.0,
                    1.0,
                    "dummyOwner",
                    listOf(
                        ListRelayPostboxesQuery.Owner("", "notSudoOwner", "sudoplatform.not.sudoservice"),
                        ListRelayPostboxesQuery.Owner("", "alsoNotSudoOwner", "sudoplatform.identityservice")
                    ),
                    "connection-id-1",
                    true,
                    "https://the.service.endpoint/relay/postbox-id-1"
                ),
                ListRelayPostboxesQuery.Item(
                    "",
                    "postbox-id-2",
                    2.0,
                    3.0,
                    "dummyOwner",
                    listOf(ListRelayPostboxesQuery.Owner("", "notSudoOwner", "sudoplatform.not.sudoservice")),
                    "connection-id-2",
                    true,
                    "https://the.service.endpoint/relay/postbox-id-2"
                )
            ),
            null
        )

        shouldThrow<SudoDIRelayClient.DIRelayException.FailedException> {
            PostboxTransformer.toEntityList(postboxesList)
        }
    }
}
