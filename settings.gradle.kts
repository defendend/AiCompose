plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "AiCompose"

include(":shared")
include(":desktop")
include(":backend")
