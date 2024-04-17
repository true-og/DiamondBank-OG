package net.trueog.diamondbankog

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import net.trueog.diamondbankog.PostgreSQL.*
import org.bukkit.Bukkit
import java.util.*
import java.util.concurrent.CompletableFuture

@OptIn(DelicateCoroutinesApi::class)
class DiamondBankAPI(private var postgreSQL: PostgreSQL) {
    @Suppress("unused")
    fun addToPlayerBankShards(uuid: UUID, amount: Int): CompletableFuture<Boolean> {
        return GlobalScope.future { postgreSQL.addToPlayerShards(uuid, amount, ShardType.BANK) }
    }

    @Suppress("unused")
    fun subtractFromPlayerBankShards(uuid: UUID, amount: Int): CompletableFuture<Boolean> {
        return GlobalScope.future {
            postgreSQL.subtractFromPlayerShards(
                uuid,
                amount,
                ShardType.BANK
            )
        }
    }

    @Suppress("unused")
    fun getPlayerShards(uuid: UUID, type: ShardType): CompletableFuture<PlayerShards> {
        return GlobalScope.future { postgreSQL.getPlayerShards(uuid, type) }
    }

    @Suppress("unused")
    fun withdrawFromPlayer(uuid: UUID, amount: Int): CompletableFuture<Boolean> {
        val player = Bukkit.getPlayer(uuid) ?: Bukkit.getOfflinePlayer(uuid)
        if (!player.hasPlayedBefore()) return GlobalScope.future { true }
        if (!player.isOnline) return GlobalScope.future { true }
        val playerPlayer = player.player ?: return GlobalScope.future { true }

        return GlobalScope.future { Helper.withdrawFromPlayer(playerPlayer, amount) == null }
    }

    @Suppress("unused")
    fun playerPayPlayer(senderUuid: UUID, receiverUuid: UUID, amount: Int): CompletableFuture<Boolean> {
        val sender = Bukkit.getPlayer(senderUuid) ?: Bukkit.getOfflinePlayer(senderUuid)
        if (!sender.hasPlayedBefore()) return GlobalScope.future { true }
        if (!sender.isOnline) return GlobalScope.future { true }
        val senderPlayer = sender.player ?: return GlobalScope.future { true }

        val receiver = Bukkit.getPlayer(receiverUuid) ?: Bukkit.getOfflinePlayer(receiverUuid)
        if (!receiver.hasPlayedBefore()) return GlobalScope.future { true }

        return GlobalScope.future {
            Helper.withdrawFromPlayer(senderPlayer, amount) ?: GlobalScope.future { true }

            val error = postgreSQL.addToPlayerShards(
                receiver.uniqueId,
                amount,
                ShardType.BANK
            )
            if (error) {
                Helper.handleError(
                    sender.uniqueId,
                    Helper.PostgresFunction.ADD_TO_PLAYER_DIAMONDS, amount, ShardType.BANK,
                    null, "pay"
                )
                true
            } else false
        }
    }
}