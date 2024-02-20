package net.trueog.diamondbankog

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import kotlin.properties.Delegates

object Config {
    lateinit var prefix: String
    var sentryEnabled by Delegates.notNull<Boolean>()
    lateinit var sentryDsn: String
    lateinit var postgresUrl: String
    lateinit var postgresUser: String
    lateinit var postgresPassword: String
    lateinit var postgresTable: String

    fun load(): Boolean {
        val file = File(DiamondBankOG.plugin.dataFolder, "config.yml")
        if (!file.exists()) {
            DiamondBankOG.plugin.saveDefaultConfig()
        }
        val config = YamlConfiguration.loadConfiguration(file)
        config.save(file)

        try {
            prefix = config.get("prefix") as String
        } catch (_: Exception) {
            DiamondBankOG.plugin.logger.severe("Failed to parse config option \"prefix\" as a string")
            return true
        }

        try {
            sentryEnabled = config.get("sentryEnabled") as Boolean
        } catch (_: Exception) {
            DiamondBankOG.plugin.logger.severe("Failed to parse config option \"sentryEnabled\" as a boolean")
            return true
        }

        try {
            sentryDsn = config.get("sentryDsn") as String
        } catch (_: Exception) {
            DiamondBankOG.plugin.logger.severe("Failed to parse config option \"sentryDsn\" as a string")
            return true
        }

        try {
            postgresUrl = config.get("postgresUrl") as String
        } catch (_: Exception) {
            DiamondBankOG.plugin.logger.severe("Failed to parse config option \"postgresUrl\" as a string")
            return true
        }

        try {
            postgresUser = config.get("postgresUser") as String
        } catch (_: Exception) {
            DiamondBankOG.plugin.logger.severe("Failed to parse config option \"postgresUser\" as a string")
            return true
        }

        try {
            postgresPassword = config.get("postgresPassword") as String
        } catch (_: Exception) {
            DiamondBankOG.plugin.logger.severe("Failed to parse config option \"postgresPassword\" as a string")
            return true
        }

        try {
            postgresTable = config.get("postgresTable") as String
        } catch (_: Exception) {
            DiamondBankOG.plugin.logger.severe("Failed to parse config option \"postgresTable\" as a string")
            return true
        }

        return false
    }
}