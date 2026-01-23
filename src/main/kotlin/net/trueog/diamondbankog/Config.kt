package net.trueog.diamondbankog

import net.trueog.diamondbankog.DiamondBankOG.Companion.plugin
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import kotlin.properties.Delegates

internal class Config private constructor() {
    lateinit var prefix: String
    lateinit var postgresUrl: String
    lateinit var postgresUser: String
    lateinit var postgresPassword: String
    lateinit var postgresTable: String
    lateinit var postgresLogTable: String
    var postgresLogLimit by Delegates.notNull<Int>()
    lateinit var redisUrl: String

    companion object {
        fun create(): Config? {
            val config = Config()
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
                config.postgresTable = yamlConfig.get("postgresTable") as String
            } catch (_: Exception) {
                plugin.logger.severe("Failed to parse config option \"postgresTable\" as a string")
                return null
            }

            try {
                config.postgresLogTable = yamlConfig.get("postgresLogTable") as String
            } catch (_: Exception) {
                plugin.logger.severe("Failed to parse config option \"postgresLogTable\" as a string")
                return null
            }

            try {
                config.postgresLogLimit = yamlConfig.get("postgresLogLimit") as Int
                if (config.postgresLogLimit < 1) {
                    plugin.logger.severe("\"postgresLogLimit\" cannot be less than 1")
                    return null
                }
            } catch (_: Exception) {
                plugin.logger.severe("Failed to parse config option \"postgresLogLimit\" as an integer")
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
