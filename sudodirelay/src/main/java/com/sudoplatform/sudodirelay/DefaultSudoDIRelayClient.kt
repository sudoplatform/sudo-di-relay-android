/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay

import android.content.Context
import com.amplifyframework.api.ApiException
import com.amplifyframework.api.graphql.GraphQLResponse
import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudodirelay.graphql.BulkDeleteRelayMessageMutation
import com.sudoplatform.sudodirelay.graphql.CreateRelayPostboxMutation
import com.sudoplatform.sudodirelay.graphql.DeleteRelayMessageMutation
import com.sudoplatform.sudodirelay.graphql.DeleteRelayPostboxMutation
import com.sudoplatform.sudodirelay.graphql.ListRelayMessagesQuery
import com.sudoplatform.sudodirelay.graphql.ListRelayPostboxesQuery
import com.sudoplatform.sudodirelay.graphql.UpdateRelayPostboxMutation
import com.sudoplatform.sudodirelay.graphql.type.BulkDeleteRelayMessageInput
import com.sudoplatform.sudodirelay.graphql.type.CreateRelayPostboxInput
import com.sudoplatform.sudodirelay.graphql.type.DeleteRelayMessageInput
import com.sudoplatform.sudodirelay.graphql.type.DeleteRelayPostboxInput
import com.sudoplatform.sudodirelay.graphql.type.UpdateRelayPostboxInput
import com.sudoplatform.sudodirelay.logging.LogConstants
import com.sudoplatform.sudodirelay.subscription.MessageSubscriber
import com.sudoplatform.sudodirelay.subscription.SubscriptionService
import com.sudoplatform.sudodirelay.types.ListOutput
import com.sudoplatform.sudodirelay.types.Message
import com.sudoplatform.sudodirelay.types.Postbox
import com.sudoplatform.sudodirelay.types.transformers.MessageTransformer
import com.sudoplatform.sudodirelay.types.transformers.PostboxTransformer
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.amplify.GraphQLClient
import java.util.concurrent.CancellationException

/**
 * Default implementation of the [SudoDIRelayClient] interface.
 *
 * @property context Application context
 * @property appSyncClient GraphQL client used to make requests to AWS and call sudo decentralized
 *  identity relay service API.
 * @property sudoUserClient The [SudoUserClient] used to determine if a user is signed in and gain access to the user owner ID.
 * @property logger Errors and warnings will be logged here.
 */
internal class DefaultSudoDIRelayClient(
    private val context: Context,
    private val graphQLClient: GraphQLClient,
    private val sudoUserClient: SudoUserClient,
    private val logger: Logger = Logger(
        LogConstants.SUDOLOG_TAG,
        AndroidUtilsLogDriver(LogLevel.INFO),
    ),
) : SudoDIRelayClient {

    /**
     * Checksum's for each file are generated and are used to create a checksum that is used when
     * publishing to maven central. In order to retry a failed publish without needing to change any
     * functionality, we need a way to generate a different checksum for the source code. We can
     * change the value of this property which will generate a different checksum for publishing
     * and allow us to retry. The value of `version` doesn't need to be kept up-to-date with the
     * version of the code.
     */
    private val version: String = "4.0.0"

    companion object {
        /** Exception message */
        internal const val INVALID_POSTBOX_INPUT_ERROR_MSG = "The input provided to postbox creation was invalid."
        internal const val DELETE_MESSAGE_ERROR_MSG = "The postbox deletion failed."
        internal const val UNAUTHORIZED_POSTBOX_ERROR_MSG = "Access to the postbox is unauthorized."
        private const val INVALID_TOKEN_MSG = "An invalid token error has occurred"

        /** Errors returned from the service */
        private const val ERROR_TYPE = "errorType"
        private const val SERVICE_ERROR = "sudoplatform.ServiceError"
        private const val INVALID_TOKEN_ERROR = "sudoplatform.InvalidTokenError"
        private const val INVALID_POSTBOX_INPUT_ERROR = "sudoplatform.relay.InvalidPostboxInputError"
        private const val UNAUTHORIZED_POSTBOX_ERROR = "sudoplatform.relay.UnauthorizedPostboxAccessError"
    }

    private val relayEventSubscriptions = SubscriptionService(graphQLClient, sudoUserClient, logger)

    @Throws(SudoDIRelayClient.DIRelayException::class)
    override suspend fun createPostbox(
        connectionId: String,
        ownershipProofToken: String,
        isEnabled: Boolean?,
    ): Postbox {
        try {
            val input = CreateRelayPostboxInput(
                connectionId = connectionId,
                isEnabled = isEnabled ?: true,
                ownershipProof = ownershipProofToken,
            )

            val response = graphQLClient.mutate<CreateRelayPostboxMutation, CreateRelayPostboxMutation.Data>(
                CreateRelayPostboxMutation.OPERATION_DOCUMENT,
                mapOf("input" to input),
            )

            if (response.hasErrors()) {
                logger.error("createPostbox errors = ${response.errors}")
                throw interpretError(response.errors.first())
            }

            val postbox = response.data?.createRelayPostbox
                ?: throw SudoDIRelayClient.DIRelayException.FailedException()

            return PostboxTransformer.toEntity(postbox)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw interpretException(e)
        }
    }

    @Throws(SudoDIRelayClient.DIRelayException::class)
    override suspend fun updatePostbox(postboxId: String, isEnabled: Boolean?): Postbox {
        try {
            val input = UpdateRelayPostboxInput(
                postboxId = postboxId,
                isEnabled = Optional.presentIfNotNull(isEnabled),
            )

            val response = graphQLClient.mutate<UpdateRelayPostboxMutation, UpdateRelayPostboxMutation.Data>(
                UpdateRelayPostboxMutation.OPERATION_DOCUMENT,
                mapOf("input" to input),
            )

            if (response.hasErrors()) {
                logger.error("updatePostbox errors = ${response.errors}")
                throw interpretError(response.errors.first())
            }

            val postbox = response.data?.updateRelayPostbox
                ?: throw SudoDIRelayClient.DIRelayException.FailedException()

            return PostboxTransformer.toEntity(postbox)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw interpretException(e)
        }
    }

    @Throws(SudoDIRelayClient.DIRelayException::class)
    override suspend fun deletePostbox(postboxId: String): String {
        try {
            val input = DeleteRelayPostboxInput(postboxId = postboxId)

            val response = graphQLClient.mutate<DeleteRelayPostboxMutation, DeleteRelayPostboxMutation.Data>(
                DeleteRelayPostboxMutation.OPERATION_DOCUMENT,
                mapOf("input" to input),
            )

            if (response.hasErrors()) {
                logger.error("deleteRelayPostbox errors = ${response.errors}")
                val error = response.errors.first()
                val errorText = error.extensions?.get(ERROR_TYPE)?.toString() ?: ""
                if (errorText.contains(UNAUTHORIZED_POSTBOX_ERROR)) {
                    return postboxId
                }
                throw interpretError(error)
            }

            return postboxId
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw interpretException(e)
        }
    }

    override suspend fun listPostboxes(limit: Int?, nextToken: String?): ListOutput<Postbox> {
        try {
            val variables = mapOf(
                "limit" to Optional.presentIfNotNull(limit),
                "nextToken" to Optional.presentIfNotNull(nextToken),
            )

            val response = graphQLClient.query<ListRelayPostboxesQuery, ListRelayPostboxesQuery.Data>(
                ListRelayPostboxesQuery.OPERATION_DOCUMENT,
                variables,
            )

            if (response.hasErrors()) {
                logger.error("getPostboxes errors = ${response.errors}")
                throw interpretError(response.errors.first())
            }

            val rawPostboxes = response.data?.listRelayPostboxes
                ?: throw SudoDIRelayClient.DIRelayException.FailedException()

            return PostboxTransformer.toEntityList(rawPostboxes)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw interpretException(e)
        }
    }

    @Throws(SudoDIRelayClient.DIRelayException::class)
    override suspend fun deleteMessage(messageId: String) {
        try {
            val input = DeleteRelayMessageInput(messageId = messageId)

            val response = graphQLClient.mutate<DeleteRelayMessageMutation, DeleteRelayMessageMutation.Data>(
                DeleteRelayMessageMutation.OPERATION_DOCUMENT,
                mapOf("input" to input),
            )

            if (response.hasErrors()) {
                logger.error("deleteMessage errors = ${response.errors}")
                throw interpretError(response.errors.first())
            }

            response.data?.deleteRelayMessage ?: throw SudoDIRelayClient
                .DIRelayException.FailedException(DELETE_MESSAGE_ERROR_MSG)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw interpretException(e)
        }
    }

    @Throws(SudoDIRelayClient.DIRelayException::class)
    override suspend fun bulkDeleteMessage(messageIds: List<String>) {
        try {
            val input = BulkDeleteRelayMessageInput(messageIds = messageIds)

            val response = graphQLClient.mutate<BulkDeleteRelayMessageMutation, BulkDeleteRelayMessageMutation.Data>(
                BulkDeleteRelayMessageMutation.OPERATION_DOCUMENT,
                mapOf("input" to input),
            )

            if (response.hasErrors()) {
                logger.error("bulkDeleteMessage errors = ${response.errors}")
                throw interpretError(response.errors.first())
            }

            response.data?.bulkDeleteRelayMessage ?: throw SudoDIRelayClient
                .DIRelayException.FailedException(DELETE_MESSAGE_ERROR_MSG)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw interpretException(e)
        }
    }

    @Throws(SudoDIRelayClient.DIRelayException::class)
    override suspend fun listMessages(limit: Int?, nextToken: String?): ListOutput<Message> {
        try {
            val variables = mapOf(
                "limit" to Optional.presentIfNotNull(limit),
                "nextToken" to Optional.presentIfNotNull(nextToken),
            )

            val response = graphQLClient.query<ListRelayMessagesQuery, ListRelayMessagesQuery.Data>(
                ListRelayMessagesQuery.OPERATION_DOCUMENT,
                variables,
            )

            if (response.hasErrors()) {
                logger.error("listMessages errors = ${response.errors}")
                throw interpretError(response.errors.first())
            }

            val rawMessages = response.data?.listRelayMessages
                ?: throw SudoDIRelayClient.DIRelayException.FailedException()

            return MessageTransformer.toEntityList(rawMessages)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw interpretException(e)
        }
    }

    override suspend fun subscribeToRelayEvents(subscriberId: String, subscriber: MessageSubscriber) {
        relayEventSubscriptions.subscribe(subscriberId, subscriber)
    }

    override suspend fun unsubscribeToRelayEvents(subscriberId: String) {
        relayEventSubscriptions.unsubscribe(subscriberId)
    }

    override suspend fun unsubscribeAll() {
        relayEventSubscriptions.unsubscribeAll()
    }

    private fun interpretError(e: GraphQLResponse.Error): SudoDIRelayClient.DIRelayException {
        val error = e.extensions?.get(ERROR_TYPE)?.toString() ?: ""
        return when {
            error.contains(SERVICE_ERROR) -> SudoDIRelayClient.DIRelayException.FailedException(e.toString())

            error.contains(INVALID_TOKEN_ERROR) -> SudoDIRelayClient.DIRelayException.InvalidTokenException(INVALID_TOKEN_MSG)

            error.contains(
                INVALID_POSTBOX_INPUT_ERROR,
            ) -> SudoDIRelayClient.DIRelayException.InvalidPostboxInputException(INVALID_POSTBOX_INPUT_ERROR_MSG)

            error.contains(
                UNAUTHORIZED_POSTBOX_ERROR,
            ) -> SudoDIRelayClient.DIRelayException.UnauthorizedPostboxException(UNAUTHORIZED_POSTBOX_ERROR_MSG)

            else -> SudoDIRelayClient.DIRelayException.FailedException(e.toString())
        }
    }

    private fun interpretException(e: Throwable): Throwable {
        return when (e) {
            is CancellationException,
            is SudoDIRelayClient.DIRelayException,
            -> e

            is ApiException -> SudoDIRelayClient.DIRelayException.FailedException(cause = e)

            else -> SudoDIRelayClient.DIRelayException.UnknownException(cause = e)
        }
    }
}
