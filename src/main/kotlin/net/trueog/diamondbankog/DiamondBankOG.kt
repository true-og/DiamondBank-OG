package net.trueog.diamondbankog

import io.sentry.Sentry
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import net.trueog.diamondbankog.commands.*
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class DiamondBankOG : JavaPlugin() {
    companion object {
        lateinit var plugin: DiamondBankOG
        lateinit var postgreSQL: PostgreSQL
        var mm = MiniMessage.builder()
            .tags(
                TagResolver.builder()
                    .resolver(StandardTags.color())
                    .resolver(StandardTags.reset())
                    .build()
            )
            .build()
        val blockInventoryFor = mutableListOf<UUID>()
        var sentryEnabled: Boolean = false
        var economyDisabled: Boolean = false
    }

    override fun onEnable() {
        plugin = this

        Config.load()

        if (Config.getSentryEnabled()) {
            try {
                Sentry.init { options ->
                    options.dsn = Config.getSentryDsn()
                }
                sentryEnabled = true
            } catch (e: Exception) {
                sentryEnabled = false
                this.logger.severe("Could not initialise Sentry/GlitchTip. The Sentry/GlitchTip DSN in your config might be invalid.")
            }
        }

        postgreSQL = PostgreSQL()
        try {
            postgreSQL.initDB()
        } catch (e: Exception) {
            plugin.logger.info(e.toString())
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
    }

    override fun onDisable() {
        postgreSQL.pool.disconnect().get()
    }
}