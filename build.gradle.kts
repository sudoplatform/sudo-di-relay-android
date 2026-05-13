/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    alias(libs.plugins.nexus.publish)
}

extra["projectGroup"] = "com.sudoplatform"

if (project.hasProperty("tag")) {
    nexusPublishing {
        repositories {
            sonatype {
                nexusUrl = uri("https://ossrh-staging-api.central.sonatype.com/service/local/")
                stagingProfileId = project.findProperty("sonatypeStagingProfileId")?.toString()
                username = project.findProperty("nexusUsername")?.toString()
                password = project.findProperty("nexusPassword")?.toString()
            }
        }
    }
}

allprojects {
    gradle.projectsEvaluated {
        tasks.withType<Test>().configureEach {
            outputs.upToDateWhen { false }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
