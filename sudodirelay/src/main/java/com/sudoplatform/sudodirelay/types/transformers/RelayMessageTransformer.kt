/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay.types.transformers

import com.sudoplatform.sudodirelay.graphql.GetMessagesQuery
import com.sudoplatform.sudodirelay.graphql.ListPostboxesForSudoIdQuery
import com.sudoplatform.sudodirelay.graphql.OnMessageCreatedSubscription
import com.sudoplatform.sudodirelay.graphql.OnPostBoxDeletedSubscription
import com.sudoplatform.sudodirelay.graphql.type.Direction
import com.sudoplatform.sudodirelay.types.Postbox
import com.sudoplatform.sudodirelay.types.PostboxDeletionResult
import com.sudoplatform.sudodirelay.types.RelayMessage
import java.util.Date

/**
 * Transformer response for transforming the GraphQL data types to the entity type that is
 *  exposed to consumers.
 */
internal object RelayMessageTransformer {

    /**
     * Transform the result of the [OnMessageCreatedSubscription] to a [RelayMessage].
     *
     * @param newMessage the GraphQL subscriber event data from [OnMessageCreatedSubscription].
     * @return The [RelayMessage] entity type.
     */
    fun toEntityFromMessageCreatedSubscriptionEvent(
        newMessage: OnMessageCreatedSubscription.OnMessageCreated
    ): RelayMessage {
        return RelayMessage(
            messageId = newMessage.messageId(),
            connectionId = newMessage.connectionId(),
            cipherText = newMessage.cipherText(),
            direction = newMessage.direction().toEntityDirection(),
            timestamp = Date(newMessage.utcTimestamp().toLong())
        )
    }

    /**
     * Transform the result of the [GetMessagesQuery].
     *
     * @param getMessagesList list of [GetMessagesQuery.GetMessage] objects returned
     *  from the graphQL query.
     * @return The list of [RelayMessage] entity types.
     */
    fun toEntityFromGetMessages(getMessagesList: List<GetMessagesQuery.GetMessage>): List<RelayMessage> {
        return getMessagesList.map { rawMsg ->
            RelayMessage(
                messageId = rawMsg.messageId(),
                connectionId = rawMsg.connectionId(),
                cipherText = rawMsg.cipherText(),
                direction = rawMsg.direction().toEntityDirection(),
                timestamp = Date(rawMsg.utcTimestamp().toLong())
            )
        }
    }

    /**
     * Transform the result of the [OnPostBoxDeletedSubscription] to a [PostboxDeletionResult].
     *
     * @param postboxDeleted the GraphQL subscriber event data from [OnPostBoxDeletedSubscription].
     * @return the [PostboxDeletionResult] entity type.
     */
    fun toEntityFromPostboxDeleted(postboxDeleted: OnPostBoxDeletedSubscription.OnPostBoxDeleted): PostboxDeletionResult {
        return PostboxDeletionResult(
            postboxDeleted.connectionId(),
            postboxDeleted.remainingMessages().map { it.messageId() }
        )
    }

    /**
     * Transform the result of the [ListPostboxesForSudoIdQuery] to a list of [Postbox]s.
     *
     * @param [postboxesForSudoId] the GraphQL query result returned from [ListPostboxesForSudoIdQuery].
     * @return the list of [Postbox] entity types.
     */
    fun toEntityFromListPostboxesForSudoId(postboxesForSudoId: List<ListPostboxesForSudoIdQuery.ListPostboxesForSudoId>): List<Postbox> {
        return postboxesForSudoId.map { rawPostbox ->
            Postbox(
                connectionId = rawPostbox.connectionId(),
                userId = rawPostbox.owner(),
                sudoId = rawPostbox.sudoId(),
                timestamp = Date(rawPostbox.utcTimestamp().toLong())
            )
        }
    }

    internal fun Direction.toEntityDirection(): RelayMessage.Direction {
        for (value in RelayMessage.Direction.values()) {
            if (value.name == this.name) {
                return value
            }
        }
        return RelayMessage.Direction.UNKNOWN
    }
}
