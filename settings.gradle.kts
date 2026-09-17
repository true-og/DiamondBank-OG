plugins { id("org.gradle.toolchains.foojay-resolver-convention") version ("1.0.0") }

rootProject.name = "DiamondBank-OG"

ProcessBuilder("sh", "bootstrap.sh").directory(rootDir).inheritIO().start().let {
    if (it.waitFor() != 0) throw GradleException("bootstrap.sh failed")
}

file("libs").listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach { dir -> includeBuild(dir) }
