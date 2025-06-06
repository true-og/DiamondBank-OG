plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    id("com.gradleup.shadow") version "8.3.6"
    id("maven-publish")
    id("eclipse")
}

group = "net.trueog.diamondbankog"
version = "1.1"

val apiVersion = "1.19"

publishing {
    publications {
        create<MavenPublication>("mavenPublication") {
            groupId = "net.trueog.diamondbankog"
            artifactId = "DiamondBankOG"
            version = version
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf(
        "version" to version,
        "apiVersion" to apiVersion,
    )

    filesMatching("plugin.yml") {
        expand(props)
    }
}

repositories {
    mavenCentral()

    maven {
        url = uri("https://repo.purpurmc.org/snapshots")
    }

    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:1.19.4-R0.1-SNAPSHOT")
    implementation("com.github.christianniehaus:Utilities-OG:e9ebc26c1f")
    implementation("com.github.jasync-sql:jasync-postgresql:2.2.4")

    implementation("io.sentry:sentry:8.8.0")
    implementation("io.sentry:sentry-kotlin-extensions:8.9.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

tasks.shadowJar {
    minimize()
}

tasks.shadowJar.configure {
    archiveClassifier.set("")
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.jar.configure {
    archiveClassifier.set("part")
}

kotlin {
    jvmToolchain(17)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.GRAAL_VM
    }
}
