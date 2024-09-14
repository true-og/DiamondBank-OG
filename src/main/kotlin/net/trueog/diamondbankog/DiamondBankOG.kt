package net.trueog.diamondbankog

import io.sentry.Sentry
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import net.trueog.diamondbankog.commands.*
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class DiamondBankOG : JavaPlugin() {
    companion object {
        lateinit var plugin: DiamondBankOG
        lateinit var postgreSQL: PostgreSQL
        fun isPostgreSQLInitialised() = ::postgreSQL.isInitialized
        var mm = MiniMessage.builder()
            .tags(
                TagResolver.builder()
                    .resolver(StandardTags.color())
                    .resolver(StandardTags.reset())
                    .build()
            )
            .build()
        val blockInventoryFor = mutableListOf<UUID>()
        val blockCommandsWithInventoryActionsFor = mutableListOf<UUID>()
        var sentryEnabled: Boolean = false
        var economyDisabled: Boolean = false

        // API
        @JvmStatic
        @Suppress("unused")
        fun getApi(): DiamondBankAPI? {
            if (!isPostgreSQLInitialised()) return null
            return DiamondBankAPI(postgreSQL)
        }
    }

    override fun onEnable() {
        plugin = this

        if (Config.load()) {
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }

        if (Config.sentryEnabled) {
            try {
                Sentry.init { options ->
                    options.dsn = Config.sentryDsn
                }
                sentryEnabled = true
            } catch (e: Exception) {
                sentryEnabled = false
                this.logger.severe("Could not initialise Sentry. The Sentry(-compatible) DSN in your config might be invalid.")
            }
        }

        postgreSQL = PostgreSQL()
        try {
            postgreSQL.initDB()
        } catch (e: Exception) {
            plugin.logger.info(e.toString())
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }

        this.server.pluginManager.registerEvents(Events(), this)

        this.getCommand("deposit")?.setExecutor(Deposit())
        this.getCommand("withdraw")?.setExecutor(Withdraw())
        this.getCommand("setbankbalance")?.setExecutor(SetBankBalance())
        this.getCommand("setbankbal")?.setExecutor(SetBankBalance())
        this.getCommand("pay")?.setExecutor(Pay())
        this.getCommand("balancetop")?.setExecutor(Balancetop())
        this.getCommand("baltop")?.setExecutor(Balancetop())
        this.getCommand("balance")?.setExecutor(Balance())
        this.getCommand("bal")?.setExecutor(Balance())
        this.getCommand("diamondbankreload")?.setExecutor(DiamondBankReload())
    }

    override fun onDisable() {
        if (isPostgreSQLInitialised()) postgreSQL.pool.disconnect().get()
    }

}
