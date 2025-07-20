import java.io.BufferedReader

plugins {
    kotlin("jvm") version "2.1.21" // Import kotlin jvm plugin for kotlin/java integration.
    id("com.diffplug.spotless") version "7.0.4"
    id("com.gradleup.shadow") version "8.3.6" // Import shadow API.
    eclipse // Import eclipse plugin for IDE integration.
}

val commitHash =
    Runtime.getRuntime().exec(arrayOf("git", "rev-parse", "--short=10", "HEAD")).let { process ->
        process.waitFor()
        val output = process.inputStream.use { it.bufferedReader().use(BufferedReader::readText) }
        process.destroy()
        output.trim()
    }

val apiVersion = "1.19"

group = "net.trueog.diamondbank-og"

version = "$apiVersion-$commitHash"

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven { url = uri("https://repo.purpurmc.org/snapshots") }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:1.19.4-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.5")
    implementation("io.lettuce:lettuce-core:6.7.1.RELEASE")
    implementation("com.github.jasync-sql:jasync-postgresql:2.2.4")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

configurations.all { exclude(group = "io.projectreactor") }

tasks.build {
    dependsOn(tasks.spotlessApply)
    dependsOn(tasks.shadowJar)
}

tasks.jar { archiveClassifier.set("part") }

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("io.lettuce", "net.trueog.diamondbankog.shaded.io.lettuce")
    relocate("com.github.jasync", "net.trueog.diamondbankog.shaded.com.github.jasync")
}

kotlin { jvmToolchain(17) }

tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to version, "apiVersion" to apiVersion)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") { expand(props) }
    from("LICENSE") { into("/") }
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

spotless {
    kotlin { ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) } }
    kotlinGradle {
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
        target("build.gradle.kts", "settings.gradle.kts")
    }
}
