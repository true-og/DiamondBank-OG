package net.trueog.diamondbankog

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

object Config {
    private lateinit var config: FileConfiguration
    private lateinit var file: File

    fun load() {
        file = File(DiamondBankOG.plugin.dataFolder, "config.yml")
        if (!file.exists()) {
            DiamondBankOG.plugin.saveDefaultConfig()
        }

        config = YamlConfiguration.loadConfiguration(file)

        this.save()
    }

    private fun save() {
        config.save(file)
    }

    fun getPostgresUrl(): String {
        return config.get("postgresUrl").toString()
    }

    fun getPostgresUser(): String {
        return config.get("postgresUser").toString()
    }

    fun getPostgresPassword(): String {
        return config.get("postgresPassword").toString()
    }

    fun getPostgresTable(): String {
        return config.get("postgresTable").toString()
    }
}