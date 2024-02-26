import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("multiplatform")
    id("com.github.johnrengelman.shadow") version "5.2.0"
    id("com.github.ben-manes.versions") version "0.39.0"
}

group = "net.zomis"
version = "1.0-SNAPSHOT"

val steamWorksVersion: String by project

kotlin {
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
            }
        }
        withJava()
    }
    js(IR) {
        browser {

        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("games-core/src/main/kotlin")
            resources.srcDir("games-core/src/main/resources")
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(libs.coroutines.core)
                implementation(project(":games-dsl"))
                implementation(project(":games-impl"))
                implementation(libs.klog.common)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            kotlin.srcDir("games-server/src/main/kotlin")
            resources.srcDir("games-server/src/main/resources")
            languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
            dependencies {
                implementation(libs.coroutines.core.jvm)
                implementation(libs.klog.jvm)
                implementation(kotlin("reflect"))

                // For Kotlin scripting inside a shadow jar (some of these dependencies may be removed)
                implementation(kotlin("compiler-embeddable"))
                implementation(kotlin("scripting-compiler-embeddable"))
                implementation(kotlin("scripting-compiler-impl-embeddable"))
                implementation(kotlin("scripting-jsr223"))
                implementation(kotlin("scripting-common"))
                implementation(kotlin("script-runtime"))
                implementation(kotlin("script-util"))

                implementation(libs.jackson.core)
                implementation(libs.jackson.annotations)
                implementation(libs.jackson.databind)
                implementation(libs.jackson.kotlin)

                // Ktor
                implementation(libs.ktor.jvm.server)
                implementation(libs.ktor.jvm.server.websockets)
                implementation(libs.ktor.jvm.server.content.negotiation)
                implementation(libs.ktor.jvm.serialization.jackson)
                implementation(libs.ktor.jvm.server.http.redirect)
                implementation(libs.ktor.jvm.server.default.headers)
                implementation(libs.ktor.jvm.server.cors)
                implementation(libs.ktor.jvm.server.compression)
                implementation(libs.ktor.jvm.server.netty)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.logging)

                implementation(libs.jcommander)
                implementation(libs.java.websocket)
                implementation(libs.log4j.slf4j)
                implementation(libs.log4j.core)

                implementation(libs.aws.dynamodb)
                implementation(libs.postgres)
                implementation(libs.hikari)
                implementation(libs.caffeine)
                implementation(libs.steamworks4j.server)

                implementation(libs.djl.api)
                implementation(libs.djl.model.zoo)
                runtimeOnly(libs.djl.mxnet.engine)
                runtimeOnly(libs.djl.mxnet.nativeAuto)
            }
        }
        val jvmTest by getting {
            kotlin.srcDir("games-server/src/test/kotlin")
            resources.srcDir("games-server/src/test/resources")
            languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
            dependencies {
                implementation(libs.jupiter.api)
                implementation(libs.jupiter.params)
                runtimeOnly(libs.jupiter.engine)
                implementation(libs.coroutines.test)
                implementation(libs.ktor.jvm.server.tests)
                implementation(libs.archUnit)
                implementation(libs.truth)
                implementation(libs.turbine)
            }
        }
        val jsMain by getting {
            kotlin.srcDir("games-js/src/main/kotlin")
            dependencies {
                implementation(libs.coroutines.core.js)
                implementation(libs.klog.js)
            }
        }
    }

}

tasks {
    val shadowCreate by creating(ShadowJar::class) {
        manifest {
            attributes["Main-Class"] = "net.zomis.games.server2.Main"
        }
        archiveClassifier.set("all")
        from(kotlin.jvm().compilations.getByName("main").output)
        configurations =
            mutableListOf(kotlin.jvm().compilations.getByName("main").compileDependencyFiles as Configuration)
    }
    val build by existing {
        dependsOn(shadowCreate)
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions.freeCompilerArgs += "-Xcontext-receivers"
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
    }

    withType<Test> {
        useJUnitPlatform()
    }

    withType<Delete> {
        delete("/documentation/INVITES.md")
        delete("/documentation/LOGIN_AND_LOBBY.md")
    }

}
