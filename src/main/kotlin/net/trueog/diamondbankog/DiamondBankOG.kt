package net.trueog.diamondbankog

import io.sentry.Sentry
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import net.trueog.diamondbankog.Helper.PostgresFunction
import net.trueog.diamondbankog.PostgreSQL.BalanceType
import net.trueog.diamondbankog.commands.*
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import java.util.concurrent.CompletableFuture

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

    // API
    @OptIn(DelicateCoroutinesApi::class)
    public fun addToPlayerBankBalance(uuid: UUID, amount: Double): CompletableFuture<Boolean> {
        return GlobalScope.future { postgreSQL.addToPlayerBalance(uuid, amount, BalanceType.BANK_BALANCE) }
    }

    @OptIn(DelicateCoroutinesApi::class)
    public fun subtractFromPlayerBankBalance(uuid: UUID, amount: Double): CompletableFuture<Boolean> {
        return GlobalScope.future { postgreSQL.subtractFromPlayerBalance(uuid, amount, BalanceType.BANK_BALANCE) }
    }

    @OptIn(DelicateCoroutinesApi::class)
    public fun getPlayerBalance(uuid: UUID, type: BalanceType): CompletableFuture<PostgreSQL.PlayerBalance> {
        return GlobalScope.future { postgreSQL.getPlayerBalance(uuid, type) }
    }

    @OptIn(DelicateCoroutinesApi::class)
    public fun withdrawFromPlayer(uuid: UUID, amount: Double): CompletableFuture<Boolean> {
        val player = Bukkit.getPlayer(uuid) ?: Bukkit.getOfflinePlayer(uuid)
        if (!player.hasPlayedBefore()) return GlobalScope.future { true }
        if (!player.isOnline) return GlobalScope.future { true }
        val playerPlayer = player.player ?: return GlobalScope.future { true }

        return GlobalScope.future { Helper.withdrawFromPlayer(playerPlayer, amount) == null }
    }

    @OptIn(DelicateCoroutinesApi::class)
    public fun payPlayer(senderUuid: UUID, receiverUuid: UUID, amount: Double): CompletableFuture<Boolean> {
        val sender = Bukkit.getPlayer(senderUuid) ?: Bukkit.getOfflinePlayer(senderUuid)
        if (!sender.hasPlayedBefore()) return GlobalScope.future { true }
        if (!sender.isOnline) return GlobalScope.future { true }
        val senderPlayer = sender.player ?: return GlobalScope.future { true }

        val receiver = Bukkit.getPlayer(receiverUuid) ?: Bukkit.getOfflinePlayer(receiverUuid)
        if (!receiver.hasPlayedBefore()) return GlobalScope.future { true }

        return GlobalScope.future {
            Helper.withdrawFromPlayer(senderPlayer, amount) ?: GlobalScope.future { true }

            val error = postgreSQL.addToPlayerBalance(
                receiver.uniqueId,
                amount,
                BalanceType.BANK_BALANCE
            )
            if (error) {
                Helper.handleError(
                    sender.uniqueId,
                    PostgresFunction.ADD_TO_PLAYER_BALANCE, amount, BalanceType.BANK_BALANCE,
                    null, "pay"
                )
                true
            } else false
        }
    }
}