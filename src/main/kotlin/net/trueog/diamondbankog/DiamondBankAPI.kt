package net.trueog.diamondbankog

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.bukkit.Bukkit
import java.util.*
import java.util.concurrent.CompletableFuture

@OptIn(DelicateCoroutinesApi::class)
class DiamondBankAPI(private var postgreSQL: PostgreSQL) {
    @Suppress("unused")
    fun addToPlayerBankBalance(uuid: UUID, amount: Double): CompletableFuture<Boolean> {
        return GlobalScope.future { postgreSQL.addToPlayerBalance(uuid, amount, PostgreSQL.BalanceType.BANK_BALANCE) }
    }

    @Suppress("unused")
    fun subtractFromPlayerBankBalance(uuid: UUID, amount: Double): CompletableFuture<Boolean> {
        return GlobalScope.future {
            postgreSQL.subtractFromPlayerBalance(
                uuid,
                amount,
                PostgreSQL.BalanceType.BANK_BALANCE
            )
        }
    }

    @Suppress("unused")
    fun getPlayerBalance(uuid: UUID, type: PostgreSQL.BalanceType): CompletableFuture<PostgreSQL.PlayerBalance> {
        return GlobalScope.future { postgreSQL.getPlayerBalance(uuid, type) }
    }

    @Suppress("unused")
    fun withdrawFromPlayer(uuid: UUID, amount: Double): CompletableFuture<Boolean> {
        val player = Bukkit.getPlayer(uuid) ?: Bukkit.getOfflinePlayer(uuid)
        if (!player.hasPlayedBefore()) return GlobalScope.future { true }
        if (!player.isOnline) return GlobalScope.future { true }
        val playerPlayer = player.player ?: return GlobalScope.future { true }

        return GlobalScope.future { Helper.withdrawFromPlayer(playerPlayer, amount) == null }
    }

    @Suppress("unused")
    fun payPlayer(senderUuid: UUID, receiverUuid: UUID, amount: Double): CompletableFuture<Boolean> {
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
                PostgreSQL.BalanceType.BANK_BALANCE
            )
            if (error) {
                Helper.handleError(
                    sender.uniqueId,
                    Helper.PostgresFunction.ADD_TO_PLAYER_BALANCE, amount, PostgreSQL.BalanceType.BANK_BALANCE,
                    null, "pay"
                )
                true
            } else false
        }
    }
}