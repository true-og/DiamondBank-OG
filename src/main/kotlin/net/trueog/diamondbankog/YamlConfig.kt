package net.trueog.diamondbankog

import java.io.File
import net.trueog.diamondbankog.DiamondBankOG.Companion.plugin
import org.bukkit.configuration.file.YamlConfiguration

internal class YamlConfig private constructor() : Config {
    override lateinit var prefix: String
    override lateinit var postgresUrl: String
    override lateinit var postgresUser: String
    override lateinit var postgresPassword: String
    override lateinit var redisUrl: String

    companion object : ConfigFactory {
        override fun create(): YamlConfig? {
            val config = YamlConfig()
            plugin.saveDefaultConfig()
            val file = File(plugin.dataFolder, "config.yml")
            val yamlConfig = YamlConfiguration.loadConfiguration(file)

            try {
                config.prefix = yamlConfig.get("prefix") as String
            } catch (_: Exception) {
                plugin.logger.severe("Failed to parse config option \"prefix\" as a string")
                return null
            }

            try {
                config.postgresUrl = yamlConfig.get("postgresUrl") as String
            } catch (_: Exception) {
                plugin.logger.severe("Failed to parse config option \"postgresUrl\" as a string")
                return null
            }

            try {
                config.postgresUser = yamlConfig.get("postgresUser") as String
            } catch (_: Exception) {
                plugin.logger.severe("Failed to parse config option \"postgresUser\" as a string")
                return null
            }

            try {
                config.postgresPassword = yamlConfig.get("postgresPassword") as String
            } catch (_: Exception) {
                plugin.logger.severe("Failed to parse config option \"postgresPassword\" as a string")
                return null
            }

            try {
                config.redisUrl = yamlConfig.get("redisUrl") as String
            } catch (_: Exception) {
                plugin.logger.severe("Failed to parse config option \"redisUrl\" as a string")
                return null
            }

            return config
        }
    }
}
