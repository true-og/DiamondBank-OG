plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "net.trueog.diamondbank"
version = "1.0.0"

val apiVersion = "1.19"

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
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    implementation("com.github.christianniehaus:Utilities-OG:e9ebc26c1f")
    implementation("com.github.jasync-sql:jasync-postgresql:2.2.4")
}

tasks.shadowJar {
    minimize()
}

tasks.shadowJar.configure {
    archiveClassifier.set("")
}

tasks.jar {
    dependsOn("shadowJar")
}

tasks.jar.configure {
    archiveClassifier.set("part")
}

kotlin {
    jvmToolchain(17)
}