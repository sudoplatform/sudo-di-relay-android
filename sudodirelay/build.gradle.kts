/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import java.time.ZonedDateTime

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.apollo)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.owasp.dependencycheck)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.dokka)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.jacoco)
}

android {
    namespace = "com.sudoplatform.sudodirelay"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testApplicationId = "com.sudoplatform.sudodirelay.test"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    resourcePrefix = "sen_"

    packaging {
        resources {
            pickFirsts += "META-INF/atomicfu.kotlin_module"
            pickFirsts += "META-INF/kotlinx-coroutines-core.kotlin_module"
            pickFirsts += "META-INF/sudodirelay_debug.kotlin_module"
            pickFirsts += "META-INF/INDEX.LIST"
            pickFirsts += "META-INF/DEPENDENCIES"
            pickFirsts += "META-INF/io.netty.versions.properties"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.outputs.upToDateWhen { false }
            it.extensions.configure(JacocoTaskExtension::class.java) {
                isIncludeNoLocationClasses = false
            }
        }
    }

    lint {
        xmlReport = true
    }
}


dependencies {
    // Core library desugaring
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Kotlin
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Apollo GraphQL
    implementation(libs.apollo.runtime)

    // Sudo Platform
    implementation(libs.sudo.keymanager)
    implementation(libs.sudo.logging)
    implementation(libs.sudo.config.manager)
    implementation(libs.sudo.user)
    implementation(libs.sudo.api.client)
    implementation(libs.sudo.profiles)
    implementation(libs.sudo.entitlements)

    // Testing
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.timber)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotest.assertions)
    androidTestImplementation(libs.timber)
    androidTestImplementation(libs.awaitility)
    androidTestImplementation(libs.sudo.entitlements.admin)

    androidTestImplementation(platform(libs.awssdk.bom))
}

afterEvaluate {
    // NOTE: this must be within `afterEvaluate` to ensure all the configurations have been created before filtering them
    // https://jeremylong.github.io/DependencyCheck/dependency-check-gradle/configuration.html
    dependencyCheck {
        suppressionFile = "${layout.projectDirectory}/../dependency-suppression.xml"
        failBuildOnCVSS = 0.0f
        scanConfigurations = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath")
        nvd {
            // https://github.com/jeremylong/open-vulnerability-cli/tree/main/vulnz#caching-the-nvd-cve-data
            datafeedUrl = "https://anonyome-nist-cve-mirror.s3.amazonaws.com/"
        }
        analyzers {
            assemblyEnabled = false
            centralEnabled = false
            nexus { enabled = false }
            ossIndex {
                username = if (project.hasProperty("ossIndexUsername")) project.property("ossIndexUsername").toString() else ""
                password = if (project.hasProperty("ossIndexPassword")) project.property("ossIndexPassword").toString() else ""
                warnOnlyOnRemoteErrors = true
            }
        }
    }
}

// License checking
if (!project.hasProperty("tag") && project.file("${rootProject.projectDir}/util/check-licenses.gradle").exists()) {
    apply(from = "${rootProject.projectDir}/util/check-licenses.gradle")
}

kotlinter {
    reporters = arrayOf("checkstyle", "plain")
}

// Apollo Code Generation
apollo {
    service("service") {
        packageName.set("com.sudoplatform.sudodirelay.graphql")
    }
}

// Jacoco test coverage
jacoco {
    toolVersion = "0.8.13"
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    group = "Reporting"
    description = "Generate Jacoco coverage reports"
    classDirectories.setFrom(
        fileTree(
            mapOf(
                "dir" to layout.buildDirectory.dir("tmp/kotlin-classes/debug").get().asFile,
                "excludes" to listOf("**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*")
            )
        )
    )
    sourceDirectories.setFrom(files(layout.projectDirectory.dir("src/main/java")))
    // TODO - *.exec file should be generated to buildDir dir, not projectDir
    executionData.setFrom(
        fileTree(mapOf("dir" to "$projectDir", "includes" to listOf("**/*.exec", "**/*.ec")))
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// Dokka documentation generation
dokka {
    dokkaPublications.html {
        outputDirectory.set(layout.buildDirectory.dir("javadoc").get())
        suppressInheritedMembers.set(true)
        failOnWarning.set(true)
    }
    dokkaSourceSets {
        configureEach {
            jdkVersion.set(8)
            skipEmptyPackages.set(true)
            includes.from(files("packages.md"))
            samples.from(files("src/test/java/com/sudoplatform/sudodirelay/samples/Samples.kt"))
        }
    }
    pluginsConfiguration.html {
        val year = ZonedDateTime.now().year
        footerMessage = "Copyright © $year Anonyome Labs, Inc. All rights reserved"
    }
}

// Sonarqube code analysis
if (project.file("${rootProject.projectDir}/util/sonarqube.gradle").exists()) {
    extra["sonarProjectKey"] = "sudo-di-relay-android"
    extra["sonarProjectName"] = "sudo-di-relay-android"
    // todo, patch/* exclusion is temporary measure until aforementioned aws PR is merged
    extra["sonarExclusions"] = listOf("*.png", "*.jks", "*.json", "*.key", "src/main/patch/*")
    apply(from = "${rootProject.projectDir}/util/sonarqube.gradle")
}

// Setup common publishing variables
extra["projectArtifact"] = project.name
extra["projectDescription"] = "Sudo Decentralized Identity Relay SDK for the Sudo Platform by Anonyome Labs."
extra["projectUrl"] = "https://github.com/sudoplatform/sudo-di-relay-android"
extra["projectSCM"] = "scm:git:github.com/sudoplatform/sudo-di-relay-android.git"
extra["projectVersion"] = if (project.hasProperty("tag")) {
    project.property("tag").toString()
} else {
    val projectDirectory = project.layout.projectDirectory
    val gitHash = providers.exec {
        commandLine(listOf("git", "-C", projectDirectory, "rev-parse", "--verify", "--short=8", "HEAD"))
    }.standardOutput.asText.get().trim()
    val prevGitTag = providers.exec {
        commandLine(listOf("git", "-C", projectDirectory, "describe", "--tags", "--abbrev=0"))
    }.standardOutput.asText.get().trim()
    val timestamp = System.currentTimeMillis()
    "$prevGitTag-$gitHash-$timestamp-SNAPSHOT"
}

// Internal and External publishing
if (project.hasProperty("tag") && project.file("${rootProject.projectDir}/util/publish-mavencentral.gradle").exists()) {
    apply(from = "${rootProject.projectDir}/util/publish-mavencentral.gradle")
} else if (project.file("${rootProject.projectDir}/util/publish-internal-android.gradle").exists()) {
    apply(from = "${rootProject.projectDir}/util/publish-internal-android.gradle")
}
