import java.io.BufferedReader

/* ------------------------------ Plugins ------------------------------ */
plugins {
    id("java") // Import Java plugin.
    id("java-library") // Import Java Library plugin.
    id("com.diffplug.spotless") version "7.0.4" // Import Spotless plugin.
    id("com.gradleup.shadow") version "8.3.6" // Import Shadow plugin.
    eclipse // Import Eclipse plugin.
    kotlin("jvm") version "2.1.21" // Import Kotlin JVM plugin.
}

/* ---------------------------- Java / Kotlin -------------------------- */
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.GRAAL_VM
    }
}

kotlin { jvmToolchain(17) }

/* ----------------------------- Metadata ------------------------------ */
val commitHash =
    Runtime.getRuntime().exec(arrayOf("git", "rev-parse", "--short=10", "HEAD")).let { process ->
        process.waitFor()
        val output = process.inputStream.use { it.bufferedReader().use(BufferedReader::readText) }
        process.destroy()
        output.trim()
    }

group = "net.trueog.diamondbank-og" // Declare bundle identifier.

val apiVersion = "1.19" // Declare plugin version (will be in .jar).

version = "$apiVersion-$commitHash" // Declare minecraft server target version.

/* ----------------------------- Resources ----------------------------- */
tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to version, "apiVersion" to apiVersion)
    inputs.properties(props) // Indicates to rerun if version changes.
    filesMatching("plugin.yml") { expand(props) }
    from("LICENSE") { into("/") } // Bundle licenses into jarfiles.
}

/* ---------------------------- Repos ---------------------------------- */
repositories {
    mavenCentral() // Import the Maven Central Maven Repository.
    gradlePluginPortal() // Import the Gradle Plugin Portal Maven Repository.
    maven { url = uri("https://repo.purpurmc.org/snapshots") } // Import the PurpurMC Maven Repository.
    maven { url = uri("https://jitpack.io") } // Import the Jitpack Maven Repository.
}

/* ---------------------- Java project deps ---------------------------- */
dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:1.19.4-R0.1-SNAPSHOT") // Declare Purpur API version to be packaged.
    compileOnly("net.luckperms:api:5.5") // Import the LuckPerms API.
    implementation("io.lettuce:lettuce-core:6.7.1.RELEASE") // Import the Lettuce API for keydb.
    implementation("com.github.jasync-sql:jasync-postgresql:2.2.4") // Import the jasync Postgres API.
    implementation("it.unimi.dsi:fastutil-core:8.5.16")
    implementation("org.jetbrains.kotlin:kotlin-stdlib") // Import the Kotlin standard library.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

configurations.all { exclude(group = "io.projectreactor") }

/* ---------------------- Reproducible jars ---------------------------- */
tasks.withType<AbstractArchiveTask>().configureEach { // Ensure reproducible .jars
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

/* ----------------------------- Shadow -------------------------------- */
tasks.shadowJar {
    archiveClassifier.set("") // Use empty string instead of null.
    relocate("io.lettuce", "net.trueog.diamondbankog.shaded.io.lettuce")
    relocate("com.github.jasync", "net.trueog.diamondbankog.shaded.com.github.jasync")
}

tasks.jar { archiveClassifier.set("part") } // Applies to root jarfile only.

tasks.build { dependsOn(tasks.spotlessApply, tasks.shadowJar) } // Build depends on spotless and shadow.

/* --------------------------- Javac opts ------------------------------- */
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation") // Trigger deprecation warning messages.
    options.encoding = "UTF-8" // Use UTF-8 file encoding.
}

/* ----------------------------- Auto Formatting ------------------------ */
spotless {
    kotlin { ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) } }
    kotlinGradle {
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) } // JetBrains Kotlin formatting.
        target("build.gradle.kts", "settings.gradle.kts") // Gradle files to format.
    }
}

tasks.named("spotlessCheck") {
    dependsOn("spotlessApply") // Run spotless before checking if spotless ran.
}
