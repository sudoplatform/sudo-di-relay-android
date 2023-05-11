/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay.types.transformers

import com.sudoplatform.sudodirelay.SudoDIRelayClient
import com.sudoplatform.sudodirelay.graphql.CreateRelayPostboxMutation
import com.sudoplatform.sudodirelay.graphql.CreateRelayPostboxMutation.CreateRelayPostbox
import com.sudoplatform.sudodirelay.graphql.ListRelayPostboxesQuery
import com.sudoplatform.sudodirelay.graphql.UpdateRelayPostboxMutation
import com.sudoplatform.sudodirelay.types.ListOutput
import com.sudoplatform.sudodirelay.types.Postbox

/**
 * Transformer response for transforming the GraphQL data types to the entity type that is
 *  exposed to consumers.
 */
internal object PostboxTransformer {

    /**
     * Transform the result of the [CreateRelayPostboxMutation] mutation to a [Postbox].
     *
     * @param postbox the GraphQL result data from [CreateRelayPostboxMutation].
     * @return The [Postbox] entity type.
     */
    fun toEntity(
        postbox: CreateRelayPostbox
    ): Postbox {
        val sudoOwner = postbox.owners().find { it.issuer() == "sudoplatform.sudoservice" }
            ?: throw SudoDIRelayClient.DIRelayException.FailedException()

        return Postbox(
            id = postbox.id(),
            createdAt = postbox.createdAtEpochMs().toDate(),
            updatedAt = postbox.updatedAtEpochMs().toDate(),
            ownerId = postbox.owner(),
            sudoId = sudoOwner.id(),
            connectionId = postbox.connectionId(),
            isEnabled = postbox.isEnabled,
            serviceEndpoint = postbox.serviceEndpoint()
        )
    }

    /**
     * Transform the result of the [UpdateRelayPostboxMutation] mutation to a [Postbox].
     *
     * @param postbox the GraphQL result data from [UpdateRelayPostboxMutation].
     * @return The [Postbox] entity type.
     */
    fun toEntity(
        postbox: UpdateRelayPostboxMutation.UpdateRelayPostbox
    ): Postbox {
        val sudoOwner = postbox.owners().find { it.issuer() == "sudoplatform.sudoservice" }
            ?: throw SudoDIRelayClient.DIRelayException.FailedException()

        return Postbox(
            id = postbox.id(),
            createdAt = postbox.createdAtEpochMs().toDate(),
            updatedAt = postbox.updatedAtEpochMs().toDate(),
            ownerId = postbox.owner(),
            sudoId = sudoOwner.id(),
            connectionId = postbox.connectionId(),
            isEnabled = postbox.isEnabled,
            serviceEndpoint = postbox.serviceEndpoint()
        )
    }

    /**
     * Transform the result of the [ListRelayPostboxesQuery] to a list of [Postbox]s.
     *
     * @param [postboxes] the GraphQL query result returned from [ListRelayPostboxesQuery].
     * @return the list of [Postbox] entity types and a pagination token, if any.
     */
    fun toEntityList(postboxes: ListRelayPostboxesQuery.ListRelayPostboxes): ListOutput<Postbox> {
        return ListOutput(
            items = postboxes.items().map {
                    postbox ->
                Postbox(
                    id = postbox.id(),
                    createdAt = postbox.createdAtEpochMs().toDate(),
                    updatedAt = postbox.updatedAtEpochMs().toDate(),
                    ownerId = postbox.owner(),
                    sudoId = postbox.owners().find { it.issuer() == "sudoplatform.sudoservice" }?.id()
                        ?: throw SudoDIRelayClient.DIRelayException.FailedException(),
                    connectionId = postbox.connectionId(),
                    isEnabled = postbox.isEnabled,
                    serviceEndpoint = postbox.serviceEndpoint()
                )
            },
            nextToken = postboxes.nextToken()
        )
    }
}
