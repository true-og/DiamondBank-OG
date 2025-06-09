import java.io.BufferedReader

plugins {
    kotlin("jvm") version "2.1.21"
    id("com.gradleup.shadow") version "8.3.6"
    eclipse
}

val commitHash = Runtime
    .getRuntime()
    .exec(arrayOf("git", "rev-parse", "--short=10", "HEAD"))
    .let { process ->
        process.waitFor()
        val output = process.inputStream.use {
            it.bufferedReader().use(BufferedReader::readText)
        }
        process.destroy()
        output.trim()
    }

val apiVersion = "1.19"

group = "net.trueog.diamondbankog"
version = "$apiVersion-$commitHash"

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven {
        url = uri("https://repo.purpurmc.org/snapshots")
    }
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:1.19.4-R0.1-SNAPSHOT")
    implementation("com.github.jasync-sql:jasync-postgresql:2.2.4")

    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    archiveClassifier.set("part")
}

tasks.shadowJar {
    archiveClassifier.set("")
    minimize()
}

kotlin {
    jvmToolchain(17)
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf(
        "version" to version,
        "apiVersion" to apiVersion,
    )
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }

    from("LICENSE") {
        into("/")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.GRAAL_VM
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
