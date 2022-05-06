/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.exception.ApolloException
import com.sudoplatform.sudoconfigmanager.DefaultSudoConfigManager
import com.sudoplatform.sudodirelay.appsync.enqueue
import com.sudoplatform.sudodirelay.appsync.enqueueFirst
import com.sudoplatform.sudodirelay.graphql.DeletePostBoxMutation
import com.sudoplatform.sudodirelay.graphql.GetMessagesQuery
import com.sudoplatform.sudodirelay.graphql.ListPostboxesForSudoIdQuery
import com.sudoplatform.sudodirelay.graphql.SendInitMutation
import com.sudoplatform.sudodirelay.graphql.StoreMessageMutation
import com.sudoplatform.sudodirelay.graphql.type.CreatePostboxInput
import com.sudoplatform.sudodirelay.graphql.type.Direction
import com.sudoplatform.sudodirelay.graphql.type.IdAsInput
import com.sudoplatform.sudodirelay.graphql.type.ListPostboxesForSudoIdInput
import com.sudoplatform.sudodirelay.graphql.type.WriteToRelayInput
import com.sudoplatform.sudodirelay.logging.LogConstants
import com.sudoplatform.sudodirelay.subscription.DIRelayEventSubscriber
import com.sudoplatform.sudodirelay.subscription.DIRelayEventSubscriptionService
import com.sudoplatform.sudodirelay.types.Postbox
import com.sudoplatform.sudodirelay.types.RelayMessage
import com.sudoplatform.sudodirelay.types.transformers.RelayMessageTransformer
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient
import java.util.Date
import java.util.Objects
import java.util.UUID
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
        internal const val CREATE_POSTBOX_INVALID_ID_ERROR_MSG =
            "The provided connectionId was invalid."
        internal const val DELETE_POSTBOX_ERROR_MSG = "The postbox deletion failed."
        internal const val UNAUTHORIZED_POSTBOX_ERROR_MSG =
            "Access to the postbox with provided connectionId is unauthorized."
        private const val INVALID_TOKEN_MSG = "An invalid token error has occurred"

        /** Errors returned from the service */
        private const val ERROR_TYPE = "errorType"
        private const val SERVICE_ERROR = "sudoplatform.ServiceError"
        private const val INVALID_TOKEN_ERROR = "sudoplatform.InvalidTokenError"
        private const val UNAUTHORIZED_POSTBOX_ERROR = "sudoplatform.relay.UnauthorizedPostboxAccess"
        private const val INVALID_INIT_MESSAGE_ERROR = "sudoplatform.relay.InvalidInitMessage"
    }

    private val relayEventSubscriptions =
        DIRelayEventSubscriptionService(appSyncClient, logger)

    @Throws(SudoDIRelayClient.DIRelayException::class)
    override suspend fun createPostbox(connectionId: String, ownershipProofToken: String) {
        try {
            val createPostboxInput =
                CreatePostboxInput.builder()
                    .connectionId(connectionId)
                    .ownershipProofTokens(listOf(ownershipProofToken))
                    .build()

            val mutation = SendInitMutation.builder()
                .input(createPostboxInput)
                .build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            if (mutationResponse.hasErrors()) {
                logger.error("createPostbox errors = ${mutationResponse.errors()}")
                throw interpretError(mutationResponse.errors().first())
            }

            if (mutationResponse.data()?.sendInit() == null) {
                throw SudoDIRelayClient.DIRelayException.FailedException()
            }
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw interpretException(e)
        }
    }

    @Throws(SudoDIRelayClient.DIRelayException::class)
    override suspend fun storeMessage(
        connectionId: String,
        cipherText: String
    ): RelayMessage {
        try {
            val messageId = UUID.randomUUID().toString()
            val currentDate = Date().time

            val writeToRelay =
                WriteToRelayInput.builder()
                    .connectionId(connectionId)
                    .messageId(messageId)
                    .cipherText(cipherText)
                    .direction(Direction.OUTBOUND)
                    .utcTimestamp(currentDate.toDouble())
                    .build()

            val mutation = StoreMessageMutation.builder()
                .input(writeToRelay)
                .build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            if (mutationResponse.hasErrors()) {
                logger.error("storeMessage errors = ${mutationResponse.errors()}")
                throw interpretError(mutationResponse.errors().first())
            }

            return RelayMessage(
                messageId = messageId,
                connectionId = connectionId,
                cipherText = cipherText,
                direction = RelayMessage.Direction.OUTBOUND,
                timestamp = Date(currentDate)
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw interpretException(e)
        }
    }

    @Throws(SudoDIRelayClient.DIRelayException::class)
    override suspend fun deletePostbox(connectionId: String) {
        try {
            val idAsInput = IdAsInput.builder().connectionId(connectionId).build()

            val mutation = DeletePostBoxMutation.builder().input(idAsInput).build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            if (mutationResponse.hasErrors()) {
                logger.error("deletePostbox errors = ${mutationResponse.errors()}")
                throw interpretError(mutationResponse.errors().first())
            }

            val result = mutationResponse.data()?.deletePostBox()

            if (result?.status() != 200) {
                throw SudoDIRelayClient
                    .DIRelayException.FailedException(DELETE_POSTBOX_ERROR_MSG)
            }
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw interpretException(e)
        }
    }

    @Throws(SudoDIRelayClient.DIRelayException::class)
    override suspend fun listMessages(connectionId: String): List<RelayMessage> {
        try {
            val idAsInput = IdAsInput.builder().connectionId(connectionId).build()

            val query = GetMessagesQuery.builder().input(idAsInput).build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.error("getMessages errors = ${queryResponse.errors()}")
                throw interpretError(queryResponse.errors().first())
            }

            val rawMessages = queryResponse.data()?.messages ?: emptyList()

            return RelayMessageTransformer.toEntityFromGetMessages(rawMessages)
                .sortedBy { it.timestamp }
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw interpretException(e)
        }
    }

    override suspend fun subscribeToRelayEvents(
        connectionId: String,
        subscriber: DIRelayEventSubscriber
    ) {
        relayEventSubscriptions.subscribe(connectionId, subscriber)
    }

    override suspend fun unsubscribeToRelayEvents(connectionId: String) {
        relayEventSubscriptions.unsubscribe(connectionId)
    }

    override suspend fun unsubscribeAll() {
        relayEventSubscriptions.unsubscribeAll()
    }

    override fun getPostboxEndpoint(connectionId: String): String {
        val baseEndpoint = DefaultSudoConfigManager(context, logger)
            .getConfigSet("relayService")
            ?.get("httpEndpoint") as String?

        // should already be checked by Builder.
        Objects.requireNonNull(
            baseEndpoint,
            "The parameter 'relayService.httpEndpoint' was not found in sudo configuration."
        )

        return "$baseEndpoint/$connectionId"
    }

    override suspend fun listPostboxesForSudoId(sudoId: String): List<Postbox> {
        try {
            val input = ListPostboxesForSudoIdInput.builder().sudoId(sudoId).build()

            val query = ListPostboxesForSudoIdQuery.builder().input(input).build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.error("getPostboxesForSudoId errors = ${queryResponse.errors()}")
                throw interpretError(queryResponse.errors().first())
            }

            val rawPostboxes = queryResponse.data()?.listPostboxesForSudoId() ?: emptyList()

            return RelayMessageTransformer.toEntityFromListPostboxesForSudoId(rawPostboxes)
                .sortedBy { it.timestamp }
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw interpretException(e)
        }
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
            error.contains(INVALID_INIT_MESSAGE_ERROR) -> {
                return SudoDIRelayClient
                    .DIRelayException.InvalidConnectionIDException(
                        CREATE_POSTBOX_INVALID_ID_ERROR_MSG
                    )
            }
            error.contains(UNAUTHORIZED_POSTBOX_ERROR) -> {
                return SudoDIRelayClient
                    .DIRelayException.UnauthorizedPostboxException(
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
