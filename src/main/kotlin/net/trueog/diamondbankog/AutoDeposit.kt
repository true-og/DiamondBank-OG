package net.trueog.diamondbankog

import kotlinx.coroutines.launch
import net.trueog.diamondbankog.DiamondBankOG.Companion.balanceManager
import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.DiamondBankOG.Companion.mm
import net.trueog.diamondbankog.DiamondBankOG.Companion.scope
import net.trueog.diamondbankog.DiamondBankOG.Companion.transactionLock
import net.trueog.diamondbankog.ErrorHandler.handleError
import net.trueog.diamondbankog.PostgreSQL.ShardType
import org.bukkit.Material
import org.bukkit.entity.Item
import org.bukkit.entity.Player

internal object AutoDeposit {
    fun deposit(player: Player, item: Item) {
        val worldName = player.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") {
            return
        }

        if (!player.hasPermission("diamondbank-og.deposit")) {
            player.sendMessage(mm.deserialize("${config.prefix}<reset>: <red>You do not have permission to deposit."))
            return
        }

        val itemStack = item.itemStack
        val shards =
            if (itemStack.type == Material.DIAMOND_BLOCK) {
                (itemStack.amount * 9 * 9).toLong()
            } else if (itemStack.type == Material.DIAMOND) {
                (itemStack.amount * 9).toLong()
            } else if (itemStack.persistentDataContainer.has(Shard.namespacedKey)) {
                itemStack.amount.toLong()
            } else {
                return
            }

        itemStack.amount = 0
        item.itemStack = itemStack

        scope.launch {
            transactionLock.withLockSuspend(player.uniqueId) {
                balanceManager.addToPlayerShards(player.uniqueId, shards, ShardType.BANK).getOrElse {
                    handleError(player.uniqueId, shards, null)
                    player.sendMessage(
                        mm.deserialize(
                            "${config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                        )
                    )
                    return@withLockSuspend
                }
            }
        }
    }
}
