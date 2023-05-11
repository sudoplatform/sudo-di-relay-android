/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay.types.transformers

import com.sudoplatform.sudodirelay.SudoDIRelayClient
import com.sudoplatform.sudodirelay.graphql.ListRelayMessagesQuery
import com.sudoplatform.sudodirelay.graphql.OnRelayMessageCreatedSubscription
import com.sudoplatform.sudodirelay.types.ListOutput
import com.sudoplatform.sudodirelay.types.Message

/**
 * Transformer response for transforming the GraphQL data types to the entity type that is
 *  exposed to consumers.
 */
internal object MessageTransformer {
    /**
     * Transform the result of the [OnRelayMessageCreatedSubscription] to a [Message].
     *
     * @param newMessage the GraphQL subscriber event data from [OnRelayMessageCreatedSubscription].
     * @return The [Message] entity type.
     */
    fun toEntityFromMessageCreatedSubscriptionEvent(
        newMessage: OnRelayMessageCreatedSubscription.OnRelayMessageCreated
    ): Message {
        return Message(
            id = newMessage.id(),
            createdAt = newMessage.createdAtEpochMs().toDate(),
            updatedAt = newMessage.updatedAtEpochMs().toDate(),
            ownerId = newMessage.owner(),
            sudoId = newMessage.owners().find { it.issuer() == "sudoplatform.sudoservice" }?.id()
                ?: throw SudoDIRelayClient.DIRelayException.FailedException(),
            postboxId = newMessage.postboxId(),
            message = newMessage.message(),
        )
    }

    /**
     * Transform the result of the [ListRelayMessagesQuery] to a list of [Message]s.
     *
     * @param [messages] the GraphQL query result returned from [ListRelayMessagesQuery].
     * @return the output list of [Message] entity types including pagination information
     */
    fun toEntityList(messages: ListRelayMessagesQuery.ListRelayMessages): ListOutput<Message> {
        return ListOutput(
            items = messages.items().map {
                    message ->
                Message(
                    id = message.id(),
                    createdAt = message.createdAtEpochMs().toDate(),
                    updatedAt = message.updatedAtEpochMs().toDate(),
                    ownerId = message.owner(),
                    sudoId = message.owners().find { it.issuer() == "sudoplatform.sudoservice" }?.id()
                        ?: throw SudoDIRelayClient.DIRelayException.FailedException(),
                    postboxId = message.postboxId(),
                    message = message.message()
                )
            },
            nextToken = messages.nextToken()
        )
    }
}
