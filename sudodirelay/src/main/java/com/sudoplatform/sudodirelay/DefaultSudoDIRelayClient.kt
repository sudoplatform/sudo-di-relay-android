/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.exception.ApolloException
import com.sudoplatform.sudodirelay.SudoDIRelayClient.DIRelayException.UnauthorizedPostboxException
import com.sudoplatform.sudodirelay.appsync.enqueue
import com.sudoplatform.sudodirelay.appsync.enqueueFirst
import com.sudoplatform.sudodirelay.graphql.CreateRelayPostboxMutation
import com.sudoplatform.sudodirelay.graphql.DeleteRelayMessageMutation
import com.sudoplatform.sudodirelay.graphql.DeleteRelayPostboxMutation
import com.sudoplatform.sudodirelay.graphql.ListRelayMessagesQuery
import com.sudoplatform.sudodirelay.graphql.ListRelayPostboxesQuery
import com.sudoplatform.sudodirelay.graphql.UpdateRelayPostboxMutation
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
    private val appSyncClient: AWSAppSyncClient,
    private val sudoUserClient: SudoUserClient,
    private val logger: Logger = Logger(
        LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO)
    )
) : SudoDIRelayClient {

    companion object {

        /** Exception message */
        internal const val INVALID_POSTBOX_INPUT_ERROR_MSG =
            "The input provided to postbox creation was invalid."
        internal const val DELETE_MESSAGE_ERROR_MSG = "The postbox deletion failed."
        internal const val UNAUTHORIZED_POSTBOX_ERROR_MSG =
            "Access to the postbox is unauthorized."
        private const val INVALID_TOKEN_MSG = "An invalid token error has occurred"

        /** Errors returned from the service */
        private const val ERROR_TYPE = "errorType"
        private const val SERVICE_ERROR = "sudoplatform.ServiceError"
        private const val INVALID_TOKEN_ERROR = "sudoplatform.InvalidTokenError"
        private const val INVALID_POSTBOX_INPUT_ERROR = "sudoplatform.relay.InvalidPostboxInputError"
        private const val UNAUTHORIZED_POSTBOX_ERROR = "sudoplatform.relay.UnauthorizedPostboxAccessError"
    }

    private val relayEventSubscriptions =
        SubscriptionService(appSyncClient, sudoUserClient, logger)

    @Throws(SudoDIRelayClient.DIRelayException::class)
    override suspend fun createPostbox(
        connectionId: String,
        ownershipProofToken: String,
        isEnabled: Boolean?
    ): Postbox {
        try {
            val createPostboxInput =
                CreateRelayPostboxInput.builder()
                    .connectionId(connectionId)
                    .isEnabled(isEnabled ?: true)
                    .ownershipProof(ownershipProofToken)
                    .build()

            val mutation = CreateRelayPostboxMutation.builder()
                .input(createPostboxInput)
                .build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            if (mutationResponse.hasErrors()) {
                logger.error("createPostbox errors = ${mutationResponse.errors()}")
                throw interpretError(mutationResponse.errors().first())
            }

            val postbox = mutationResponse.data()?.createRelayPostbox()
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
            val updatePostboxInput =
                UpdateRelayPostboxInput.builder()
                    .postboxId(postboxId)
                    .isEnabled(isEnabled)
                    .build()

            val mutation = UpdateRelayPostboxMutation.builder()
                .input(updatePostboxInput)
                .build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            if (mutationResponse.hasErrors()) {
                logger.error("createPostbox errors = ${mutationResponse.errors()}")
                throw interpretError(mutationResponse.errors().first())
            }

            val postbox = mutationResponse.data()?.updateRelayPostbox()
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
            val input = DeleteRelayPostboxInput.builder().postboxId(postboxId).build()
            val mutation = DeleteRelayPostboxMutation.builder().input(input).build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            if (mutationResponse.hasErrors()) {
                logger.error("deleteRelayPostbox errors = ${mutationResponse.errors()}")
                val errorText = mutationResponse.errors().first().customAttributes()[ERROR_TYPE]?.toString() ?: ""
                if (errorText.contains(UNAUTHORIZED_POSTBOX_ERROR)) {
                    return postboxId
                }
                throw interpretError(mutationResponse.errors().first())
            }

            // As long as there were no errors, we can simply return the provided it
            return postboxId
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw interpretException(e)
        }
    }

    override suspend fun listPostboxes(
        limit: Int?,
        nextToken: String?
    ): ListOutput<Postbox> {
        try {

            val query = ListRelayPostboxesQuery.builder().limit(limit).nextToken(nextToken).build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.error("getPostboxes errors = ${queryResponse.errors()}")
                throw interpretError(queryResponse.errors().first())
            }

            val rawPostboxes = queryResponse.data()?.listRelayPostboxes()
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
            val deleteMessageInput = DeleteRelayMessageInput.builder().messageId(messageId).build()
            val mutation = DeleteRelayMessageMutation.builder().input(deleteMessageInput).build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            if (mutationResponse.hasErrors()) {
                logger.error("deletePostbox errors = ${mutationResponse.errors()}")
                throw interpretError(mutationResponse.errors().first())
            }

            val result = mutationResponse.data()?.deleteRelayMessage() ?: throw SudoDIRelayClient
                .DIRelayException.FailedException(DELETE_MESSAGE_ERROR_MSG)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw interpretException(e)
        }
    }
    @Throws(SudoDIRelayClient.DIRelayException::class)
    override suspend fun listMessages(limit: Int?, nextToken: String?): ListOutput<Message> {
        try {
            val query = ListRelayMessagesQuery.builder().limit(limit).nextToken(nextToken).build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.error("listMessages errors = ${queryResponse.errors()}")
                throw interpretError(queryResponse.errors().first())
            }

            val rawMessages = queryResponse.data()?.listRelayMessages()
                ?: throw SudoDIRelayClient.DIRelayException.FailedException()

            return MessageTransformer.toEntityList(rawMessages)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw interpretException(e)
        }
    }

    override suspend fun subscribeToRelayEvents(
        subscriberId: String,
        subscriber: MessageSubscriber
    ) {
        relayEventSubscriptions.subscribe(subscriberId, subscriber)
    }

    override suspend fun unsubscribeToRelayEvents(subscriberId: String) {
        relayEventSubscriptions.unsubscribe(subscriberId)
    }

    override suspend fun unsubscribeAll() {
        relayEventSubscriptions.unsubscribeAll()
    }

    /** Private Methods */
    private fun interpretError(
        e: Error
    ): SudoDIRelayClient.DIRelayException {
        val error = e.customAttributes()[ERROR_TYPE]?.toString() ?: ""
        when {
            error.contains(SERVICE_ERROR) -> {
                return SudoDIRelayClient
                    .DIRelayException.FailedException(e.toString())
            }
            error.contains(INVALID_TOKEN_ERROR) -> {
                return SudoDIRelayClient
                    .DIRelayException.InvalidTokenException(
                        INVALID_TOKEN_MSG
                    )
            }
            error.contains(INVALID_POSTBOX_INPUT_ERROR) -> {
                return SudoDIRelayClient
                    .DIRelayException.InvalidPostboxInputException(
                        INVALID_POSTBOX_INPUT_ERROR_MSG
                    )
            }
            error.contains(UNAUTHORIZED_POSTBOX_ERROR) -> {
                return UnauthorizedPostboxException(
                    UNAUTHORIZED_POSTBOX_ERROR_MSG
                )
            }
            else ->
                return SudoDIRelayClient
                    .DIRelayException.FailedException(e.toString())
        }
    }

    private fun interpretException(e: Throwable): Throwable {
        return when (e) {
            is CancellationException,
            is SudoDIRelayClient.DIRelayException -> e
            is ApolloException ->
                SudoDIRelayClient
                    .DIRelayException.FailedException(cause = e)
            else ->
                SudoDIRelayClient
                    .DIRelayException.UnknownException(cause = e)
        }
    }
}
