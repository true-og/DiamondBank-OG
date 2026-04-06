package net.trueog.diamondbankog

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
    override val postgresPassword: String,
    override val redisHost: String,
    override val redisPort: Int,
    override val redisDatabase: Int,
    override val redisPassword: String
) : Config {
    companion object : ConfigFactory {
        override fun create(): YamlConfig? {
            plugin.saveDefaultConfig()
            val file = File(plugin.dataFolder, "config.yml")
            val yamlConfig = YamlConfiguration.loadConfiguration(file)

            val prefix = yamlConfig.parseKeyAs<String>("prefix") ?: return null

            val postgresHost = yamlConfig.parseKeyAs<String>("postgresHost") ?: return null
            val postgresPort = yamlConfig.parseKeyAs<Int>("postgresPort") ?: return null
            val postgresDatabase = yamlConfig.parseKeyAs<String>("postgresDatabase") ?: return null
            val postgresUser = yamlConfig.parseKeyAs<String>("postgresUser") ?: return null
            val postgresPassword = yamlConfig.parseKeyAs<String>("postgresPassword") ?: return null

            val redisHost = yamlConfig.parseKeyAs<String>("redisHost") ?: return null
            val redisPort = yamlConfig.parseKeyAs<Int>("redisPort") ?: return null
            val redisDatabase = yamlConfig.parseKeyAs<Int>("redisDatabase") ?: return null
            val redisPassword = yamlConfig.parseKeyAs<String>("redisPassword") ?: return null

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
                redisPassword
            )
        }

        inline fun <reified T : Any> YamlConfiguration.parseKeyAs(key: String): T? {
            return this.get(key) as? T ?: run {
                plugin.logger.severe("Failed to parse config option \"$key\" as ${T::class.simpleName}")
                return null
            }
        }
    }
}
