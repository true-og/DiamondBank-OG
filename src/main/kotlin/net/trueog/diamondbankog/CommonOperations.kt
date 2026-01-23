package net.trueog.diamondbankog

import net.trueog.diamondbankog.DiamondBankException.InsufficientFundsException
import net.trueog.diamondbankog.DiamondBankException.InsufficientInventorySpaceException
import net.trueog.diamondbankog.DiamondBankOG.Companion.balanceManager
import java.util.*

object CommonOperations {
    suspend fun consume(uuid: UUID, shards: Long, inventorySnapshot: InventorySnapshot): Result<Unit> {
        val bankShards =
            balanceManager.getBankShards(uuid).getOrElse {
                return Result.failure(it)
            }
        val shardsToSubtract =
            if (bankShards < shards) {
                val toRemoveShards = shards - bankShards
                val removedInShards =
                    InventorySnapshotUtils.removeShards(inventorySnapshot, toRemoveShards)
                        .getOrElse {
                            return Result.failure(InsufficientInventorySpaceException())
                        }
                        .toLong()
                if (removedInShards != toRemoveShards) {
                    val short = toRemoveShards - removedInShards
                    throw InsufficientFundsException(short)
                }
                bankShards
            } else {
                shards
            }
        balanceManager.subtractFromBankShards(uuid, shardsToSubtract).getOrElse {
            return Result.failure(it)
        }
        return Result.success(Unit)
    }
}
