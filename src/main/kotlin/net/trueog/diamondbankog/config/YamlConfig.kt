package net.trueog.diamondbankog.config

import java.io.File
import net.trueog.diamondbankog.DiamondBankOG.Companion.plugin
import org.bukkit.configuration.file.YamlConfiguration

internal class YamlConfig
private constructor(
    override val prefix: String,
    override val postgresHost: String,
    override val postgresPort: Int,
    override val postgresDatabase: String,
    override val postgresUser: String,
    override val postgresPassword: String?,
    override val redisHost: String,
    override val redisPort: Int,
    override val redisDatabase: Int,
    override val redisPassword: String?,
) : Config {
    companion object : ConfigFactory {
        override fun create(): YamlConfig? {
            plugin.saveDefaultConfig()
            val file = File(plugin.dataFolder, "config.yml")
            val yamlConfig = YamlConfiguration.loadConfiguration(file)

            val prefix = yamlConfig.parseKeyAs<String>("prefix")

            val postgresHost = yamlConfig.parseKeyAs<String>("postgresHost")
            val postgresPort = yamlConfig.parseKeyAs<Int>("postgresPort")
            val postgresDatabase = yamlConfig.parseKeyAs<String>("postgresDatabase")
            val postgresUser = yamlConfig.parseKeyAs<String>("postgresUser")
            val postgresPassword = yamlConfig.parseKeyAs<String?>("postgresPassword")

            val redisHost = yamlConfig.parseKeyAs<String>("redisHost")
            val redisPort = yamlConfig.parseKeyAs<Int>("redisPort")
            val redisDatabase = yamlConfig.parseKeyAs<Int>("redisDatabase")
            val redisPassword = yamlConfig.parseKeyAs<String?>("redisPassword")

            if (
                prefix == null ||
                    postgresHost == null ||
                    postgresPort == null ||
                    postgresDatabase == null ||
                    postgresUser == null ||
                    redisHost == null ||
                    redisPort == null ||
                    redisDatabase == null
            ) {
                return null
            }

            return YamlConfig(
                prefix,
                postgresHost,
                postgresPort,
                postgresDatabase,
                postgresUser,
                postgresPassword,
                redisHost,
                redisPort,
                redisDatabase,
                redisPassword,
            )
        }

        inline fun <reified T> YamlConfiguration.parseKeyAs(key: String): T? {
            if (!this.contains(key) && null is T) return null
            return this.get(key) as? T
                ?: run {
                    plugin.logger.severe("Failed to parse config option \"$key\" as ${T::class.simpleName}")
                    return null
                }
        }
    }
}
