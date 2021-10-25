/*
 * Copyright © 2021 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay.appsync

import android.content.Context
import com.amazonaws.mobile.config.AWSConfiguration
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.sudoplatform.sudoconfigmanager.DefaultSudoConfigManager
import com.sudoplatform.sudologging.Logger
import org.json.JSONObject

/**
 * Manages a singleton GraphQL client instance that may be shared by multiple service clients.
 */
internal object AWSAppSyncClientFactory {

    private var logger: Logger? = null
    private var client: AWSAppSyncClient? = null

    /**
     * returns the shared instance of AWSAppSyncClient which is created with API_KEY Authorization
     *
     * @param context The context used for fetching the platform config json and the appsync API key file
     * @return [AWSAppSyncClient] that uses API_KEY authentication with the relay service
     */
    fun getClientWithAPIKeyAuth(context: Context): AWSAppSyncClient {

        this.client?.let { return it }

        val sudoConfigManager = DefaultSudoConfigManager(context, logger)
        val relayConfig = sudoConfigManager.getConfigSet("relayService")

        require(relayConfig != null) { "API service configuration is missing." }

        val apiUrl = relayConfig.get("apiUrl") as String?
        val region = relayConfig.get("region") as String?
        val apiKey = relayConfig.get("apiKey") as String?

        // The AWSAppSyncClient auth provider requires the config to be in the following format
        val awsConfig = JSONObject(
            """
                {
                    "AppSync": {
                        "Default": {
                            "ApiUrl": "$apiUrl",
                            "Region": "$region",
                            "ApiKey": "$apiKey",
                            "AuthMode": "API_KEY"
                        }
                    }
                } 
            """.trimIndent()
        )

        val awsClient = AWSAppSyncClient.builder()
            .context(context)
            .subscriptionsAutoReconnect(true)
            .awsConfiguration(AWSConfiguration(awsConfig))
            .build()

        client = awsClient

        return awsClient
    }
}
