package net.trueog.diamondbankog

import java.io.File
import net.trueog.diamondbankog.DiamondBankOG.Companion.plugin
import org.bukkit.configuration.file.YamlConfiguration

internal class YamlConfig
private constructor(
    override val prefix: String,
    override val postgresUrl: String,
    override val postgresUser: String,
    override val postgresPassword: String,
    override val redisUrl: String,
) : Config {
    companion object : ConfigFactory {
        override fun create(): YamlConfig? {
            plugin.saveDefaultConfig()
            val file = File(plugin.dataFolder, "config.yml")
            val yamlConfig = YamlConfiguration.loadConfiguration(file)

            val prefix =
                try {
                    yamlConfig.get("prefix") as String
                } catch (_: Exception) {
                    plugin.logger.severe("Failed to parse config option \"prefix\" as a string")
                    return null
                }

            val postgresUrl =
                try {
                    yamlConfig.get("postgresUrl") as String
                } catch (_: Exception) {
                    plugin.logger.severe("Failed to parse config option \"postgresUrl\" as a string")
                    return null
                }

            val postgresUser =
                try {
                    yamlConfig.get("postgresUser") as String
                } catch (_: Exception) {
                    plugin.logger.severe("Failed to parse config option \"postgresUser\" as a string")
                    return null
                }

            val postgresPassword =
                try {
                    yamlConfig.get("postgresPassword") as String
                } catch (_: Exception) {
                    plugin.logger.severe("Failed to parse config option \"postgresPassword\" as a string")
                    return null
                }

            val redisUrl =
                try {
                    yamlConfig.get("redisUrl") as String
                } catch (_: Exception) {
                    plugin.logger.severe("Failed to parse config option \"redisUrl\" as a string")
                    return null
                }

            return YamlConfig(prefix, postgresUrl, postgresUser, postgresPassword, redisUrl)
        }
    }
}
