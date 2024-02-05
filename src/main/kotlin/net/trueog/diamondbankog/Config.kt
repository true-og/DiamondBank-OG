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

    fun getPostgresdbUrl(): String {
        return config.get("postgresdbUrl").toString()
    }

    fun getPostgresdbUser(): String {
        return config.get("postgresdbUser").toString()
    }

    fun getPostgresdbPassword(): String {
        return config.get("postgresdbPassword").toString()
    }

    fun getPostgresTable(): String {
        return config.get("postgresdbTable").toString()
    }
}