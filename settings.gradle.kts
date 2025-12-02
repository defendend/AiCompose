plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "AiCompose"

include(":desktop")
include(":backend")
