package net.trueog.diamondbankog

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import net.trueog.diamondbankog.commands.Deposit
import net.trueog.diamondbankog.commands.Pay
import net.trueog.diamondbankog.commands.SetBalance
import net.trueog.diamondbankog.commands.Withdraw
import org.bukkit.plugin.java.JavaPlugin

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
    }

    override fun onEnable() {
        plugin = this

        Config.load()

        postgreSQL = PostgreSQL()
        try {
            postgreSQL.initDB()
        } catch (e: Exception) {
            plugin.logger.info(e.toString())
        }

        this.server.pluginManager.registerEvents(Events(), this)

        this.getCommand("deposit")?.setExecutor(Deposit())
        this.getCommand("withdraw")?.setExecutor(Withdraw())
        this.getCommand("setbalance")?.setExecutor(SetBalance())
        this.getCommand("setbal")?.setExecutor(SetBalance())
        this.getCommand("pay")?.setExecutor(Pay())
    }

    override fun onDisable() {
        postgreSQL.pool.disconnect().get()
    }
}