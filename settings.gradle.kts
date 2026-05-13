/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

if (!providers.gradleProperty("tag").isPresent && file("util/internal-repo.gradle").exists()) {
    apply(from = "util/internal-repo.gradle")
}

if (providers.gradleProperty("tag").isPresent && file("util/sonatype-id.gradle").exists()) {
    apply(from = "util/sonatype-id.gradle")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenLocal()
        google {
            content {
                excludeGroup("com.sudoplatform")
            }
        }
        mavenCentral()
    }
}

include(":sudodirelay")
