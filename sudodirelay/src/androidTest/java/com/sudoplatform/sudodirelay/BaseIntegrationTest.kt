/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.sudoplatform.sudoentitlements.SudoEntitlementsClient
import com.sudoplatform.sudoentitlementsadmin.SudoEntitlementsAdminClient
import com.sudoplatform.sudoentitlementsadmin.types.Entitlement
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudoprofiles.DefaultSudoProfilesClient
import com.sudoplatform.sudouser.DefaultSudoUserClient
import com.sudoplatform.sudouser.TESTAuthenticationProvider
import io.kotlintest.shouldBe
import java.util.UUID

/**
 * Test the operation of the [SudoDIRelayClient].
 */
abstract class BaseIntegrationTest {

    protected val context: Context = ApplicationProvider.getApplicationContext()

    protected val userClient = run {
        DefaultSudoUserClient(context, "ids")
    }

    protected val sudoClient by lazy {
        val containerURI = Uri.fromFile(context.cacheDir)
        DefaultSudoProfilesClient(context, userClient, containerURI)
    }

    protected val entitlementsClient by lazy {
        SudoEntitlementsClient.builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .build()
    }

    protected val entitlementsAdminClient by lazy {
        val adminApiKey = readArgument("ADMIN_API_KEY", "api.key")
        SudoEntitlementsAdminClient.builder(context, adminApiKey).build()
    }

    protected val keyManager by lazy {
        KeyManagerFactory(context).createAndroidKeyManager("di-relay-client-test")
    }

    private fun readTextFile(fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use {
            it.readText().trim()
        }
    }

    private fun readArgument(argumentName: String, fallbackFileName: String?): String {
        println(InstrumentationRegistry.getArguments()).toString()
        val argumentValue = InstrumentationRegistry.getArguments().getString(argumentName)?.trim()
        if (argumentValue != null) {
            return argumentValue
        }
        if (fallbackFileName != null) {
            return readTextFile(fallbackFileName)
        }
        throw IllegalArgumentException("$argumentName property not found")
    }

    protected suspend fun register() {
        userClient.isRegistered() shouldBe false

        val privateKey = readArgument("REGISTER_KEY", "register_key.private")
        val keyId = readArgument("REGISTER_KEY_ID", "register_key.id")

        val authProvider = TESTAuthenticationProvider(
            name = "di-relay-client-test",
            privateKey = privateKey,
            publicKey = null,
            keyManager = keyManager,
            keyId = keyId
        )

        val registrationId = "di-relay-client-test_${UUID.randomUUID()}"

        userClient.registerWithAuthenticationProvider(
            authProvider,
            registrationId
        )
    }

    protected suspend fun deregister() {
        userClient.deregister()
    }

    protected suspend fun signIn() {
        userClient.signInWithKey()
    }

    private suspend fun registerAndSignIn() {
        userClient.isRegistered() shouldBe false
        register()
        userClient.isRegistered() shouldBe true
        signIn()
        userClient.isSignedIn() shouldBe true
    }

    protected suspend fun registerSignInAndEntitle() {
        registerAndSignIn()

        val externalId = entitlementsClient.getExternalId()
        val entitlements = listOf(Entitlement("sudoplatform.sudo.max", "test", 3))
        entitlementsAdminClient.applyEntitlementsToUser(externalId, entitlements)

        entitlementsClient.redeemEntitlements()
    }
}
