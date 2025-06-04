package net.trueog.diamondbankog

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.future.future
import net.trueog.diamondbankog.PostgreSQL.PlayerShards
import net.trueog.diamondbankog.PostgreSQL.ShardType
import org.bukkit.Bukkit
import java.util.*
import java.util.concurrent.CompletableFuture

@OptIn(DelicateCoroutinesApi::class)
class DiamondBankAPI(private var postgreSQL: PostgreSQL) {
    @Suppress("unused")
    fun addToPlayerBankShards(uuid: UUID, shards: Int): CompletableFuture<Boolean?> {
        if (DiamondBankOG.economyDisabled) return DiamondBankOG.scope.future { null }

        return DiamondBankOG.scope.future { postgreSQL.addToPlayerShards(uuid, shards, ShardType.BANK) }
    }

    @Suppress("unused")
    fun subtractFromPlayerBankShards(uuid: UUID, shards: Int): CompletableFuture<Boolean?> {
        if (DiamondBankOG.economyDisabled) return DiamondBankOG.scope.future { null }

        return DiamondBankOG.scope.future {
            postgreSQL.subtractFromPlayerShards(
                uuid,
                shards,
                ShardType.BANK
            )
        }
    }

    @Suppress("unused")
    fun getPlayerShards(uuid: UUID, type: ShardType): CompletableFuture<PlayerShards?> {
        if (DiamondBankOG.economyDisabled) return DiamondBankOG.scope.future { null }

        return DiamondBankOG.scope.future { postgreSQL.getPlayerShards(uuid, type) }
    }

    @Suppress("unused")
    fun withdrawFromPlayer(uuid: UUID, shards: Int): CompletableFuture<Boolean?> {
        if (DiamondBankOG.economyDisabled) return DiamondBankOG.scope.future { null }

        val player = Bukkit.getPlayer(uuid) ?: Bukkit.getOfflinePlayer(uuid)
        if (!player.hasPlayedBefore()) return DiamondBankOG.scope.future { true }
        if (!player.isOnline) return DiamondBankOG.scope.future { true }
        val playerPlayer = player.player ?: return DiamondBankOG.scope.future { true }

        return DiamondBankOG.scope.future { Helper.withdrawFromPlayer(playerPlayer, shards) == null }
    }

    @Suppress("unused")
    fun playerPayPlayer(senderUuid: UUID, receiverUuid: UUID, shards: Int): CompletableFuture<Boolean?> {
        if (DiamondBankOG.economyDisabled) return DiamondBankOG.scope.future { null }

        val sender = Bukkit.getPlayer(senderUuid) ?: Bukkit.getOfflinePlayer(senderUuid)
        if (!sender.hasPlayedBefore()) return DiamondBankOG.scope.future { true }
        if (!sender.isOnline) return DiamondBankOG.scope.future { true }
        val senderPlayer = sender.player ?: return DiamondBankOG.scope.future { true }

        val receiver = Bukkit.getPlayer(receiverUuid) ?: Bukkit.getOfflinePlayer(receiverUuid)
        if (!receiver.hasPlayedBefore()) return DiamondBankOG.scope.future { true }

        return DiamondBankOG.scope.future {
            Helper.withdrawFromPlayer(senderPlayer, shards) ?: DiamondBankOG.scope.future { true }

            val error = postgreSQL.addToPlayerShards(
                receiver.uniqueId,
                shards,
                ShardType.BANK
            )
            if (error) {
                Helper.handleError(
                    sender.uniqueId,
                    shards,
                    null
                )
                true
            } else false
        }
    }
}