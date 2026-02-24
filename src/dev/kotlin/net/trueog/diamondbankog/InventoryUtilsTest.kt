package net.trueog.diamondbankog

import kotlin.collections.toTypedArray
import net.trueog.diamondbankog.InventoryExtensions.countDiamondBlocks
import net.trueog.diamondbankog.InventoryExtensions.countDiamonds
import net.trueog.diamondbankog.InventoryExtensions.countShards
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object InventoryUtilsTest : Test {
    override val name: String
        get() = "InventoryUtilsTest"

    override suspend fun runTests(): Array<TestResult> {
        val testResults = mutableListOf<TestResult>()
        val tests =
            arrayOf(
                ::testShardRemoval,
                ::testDiamondRemoval,
                ::testDiamondBlockRemoval,
                ::testShardAndDiamondRemoval,
                ::testShardDiamondAndDiamondBlockRemoval,
                ::testShardAndDiamondRemovalWithShardChange,
                ::testShardDiamondAndDiamondBlockRemovalWithShardAndDiamondChange,
                ::testRemoveAll,
                ::testCountShards,
                ::testCountDiamonds,
                ::testCountDiamondBlocks,
                ::testNotEnoughSpaceForChange,
                ::testEmptyRemoveShards,
            )
        for (test in tests) {
            val criteriaResults = test()
            testResults += TestResult(test.name, criteriaResults)
        }
        return testResults.toTypedArray()
    }

    suspend fun testShardRemoval(): Array<CriteriaResult> {
        val inventorySnapshot = InventorySnapshot.withContents(arrayOf(Shard.createItemStack(9)))
        val removed =
            InventorySnapshotUtils.removeShards(inventorySnapshot, 3).getOrElse {
                val noFailureCriteria = CriteriaResult("isFailure == false", "true", false)
                return arrayOf(noFailureCriteria)
            }

        val removedCountCriteria = CriteriaResult("removed == 3", removed.toString(), removed == 3)
        val shardCount = inventorySnapshot.countShards()
        val shardCountCriteria =
            CriteriaResult("inventorySnapshot.countShards() == 6L", shardCount.toString(), shardCount == 6L)
        return arrayOf(removedCountCriteria, shardCountCriteria)
    }

    suspend fun testDiamondRemoval(): Array<CriteriaResult> {
        val inventorySnapshot = InventorySnapshot.withContents(arrayOf(ItemStack(Material.DIAMOND, 2)))
        val removed =
            InventorySnapshotUtils.removeShards(inventorySnapshot, 9).getOrElse {
                val noFailureCriteria = CriteriaResult("isFailure == false", "true", false)
                return arrayOf(noFailureCriteria)
            }

        val removedCountCriteria = CriteriaResult("removed == 9", removed.toString(), removed == 9)
        val diamondCount = inventorySnapshot.countDiamonds()
        val diamondCountCriteria =
            CriteriaResult("inventorySnapshot.countDiamonds() == 1L", diamondCount.toString(), diamondCount == 1L)
        return arrayOf(removedCountCriteria, diamondCountCriteria)
    }

    suspend fun testDiamondBlockRemoval(): Array<CriteriaResult> {
        val inventorySnapshot = InventorySnapshot.withContents(arrayOf(ItemStack(Material.DIAMOND_BLOCK, 2)))
        val removed =
            InventorySnapshotUtils.removeShards(inventorySnapshot, 81).getOrElse {
                val noFailureCriteria = CriteriaResult("isFailure == false", "true", false)
                return arrayOf(noFailureCriteria)
            }

        val removedCountCriteria = CriteriaResult("removed == 81", removed.toString(), removed == 81)
        val diamondBlockAmount = inventorySnapshot.countDiamondBlocks()
        val diamondBlockCountCriteria =
            CriteriaResult(
                "inventorySnapshot.countDiamondBlocks() == 1L",
                diamondBlockAmount.toString(),
                diamondBlockAmount == 1L,
            )
        return arrayOf(removedCountCriteria, diamondBlockCountCriteria)
    }

    suspend fun testShardAndDiamondRemoval(): Array<CriteriaResult> {
        val inventorySnapshot =
            InventorySnapshot.withContents(arrayOf(Shard.createItemStack(2), ItemStack(Material.DIAMOND, 1)))
        val removed =
            InventorySnapshotUtils.removeShards(inventorySnapshot, 11).getOrElse {
                val noFailureCriteria = CriteriaResult("isFailure == false", "true", false)
                return arrayOf(noFailureCriteria)
            }

        val removedCountCriteria = CriteriaResult("removed == 11", removed.toString(), removed == 11)
        val noContentsCriteria =
            CriteriaResult(
                "inventorySnapshot.all { it == null }",
                inventorySnapshot.contents.joinToString(","),
                inventorySnapshot.all { it == null },
            )
        return arrayOf(removedCountCriteria, noContentsCriteria)
    }

    suspend fun testShardDiamondAndDiamondBlockRemoval(): Array<CriteriaResult> {
        val inventorySnapshot =
            InventorySnapshot.withContents(
                arrayOf(Shard.createItemStack(2), ItemStack(Material.DIAMOND, 1), ItemStack(Material.DIAMOND_BLOCK, 1))
            )
        val removed =
            InventorySnapshotUtils.removeShards(inventorySnapshot, 92).getOrElse {
                val noFailureCriteria = CriteriaResult("isFailure == false", "true", false)
                return arrayOf(noFailureCriteria)
            }

        val removedCountCriteria = CriteriaResult("removed == 92", removed.toString(), removed == 92)
        val noContentsCriteria =
            CriteriaResult(
                "inventorySnapshot.all { it == null }",
                inventorySnapshot.contents.joinToString(","),
                inventorySnapshot.all { it == null },
            )
        return arrayOf(removedCountCriteria, noContentsCriteria)
    }

    suspend fun testShardAndDiamondRemovalWithShardChange(): Array<CriteriaResult> {
        val inventorySnapshot =
            InventorySnapshot.withContents(arrayOf(Shard.createItemStack(2), ItemStack(Material.DIAMOND, 1)))
        val removed =
            InventorySnapshotUtils.removeShards(inventorySnapshot, 10).getOrElse {
                val noFailureCriteria = CriteriaResult("isFailure == false", "true", false)
                return arrayOf(noFailureCriteria)
            }

        val removedCountCriteria = CriteriaResult("removed == 10", removed.toString(), removed == 10)
        val shardCount = inventorySnapshot.countShards()
        val oneShardCriteria =
            CriteriaResult("inventorySnapshot.countShards() == 1", shardCount.toString(), shardCount == 1L)
        val diamondCount = inventorySnapshot.countDiamonds()
        val noDiamondsCriteria =
            CriteriaResult("inventorySnapshot.countDiamonds() == 0", diamondCount.toString(), diamondCount == 0L)
        val diamondBlockCount = inventorySnapshot.countDiamondBlocks()
        val noDiamondBlocksCriteria =
            CriteriaResult(
                "inventorySnapshot.countDiamondBlocks() == 0",
                diamondBlockCount.toString(),
                diamondBlockCount == 0L,
            )
        return arrayOf(removedCountCriteria, oneShardCriteria, noDiamondsCriteria, noDiamondBlocksCriteria)
    }

    suspend fun testShardDiamondAndDiamondBlockRemovalWithShardAndDiamondChange(): Array<CriteriaResult> {
        val inventorySnapshot =
            InventorySnapshot.withContents(
                arrayOf(Shard.createItemStack(2), ItemStack(Material.DIAMOND, 1), ItemStack(Material.DIAMOND_BLOCK, 1))
            )
        val removed =
            InventorySnapshotUtils.removeShards(inventorySnapshot, 12).getOrElse {
                val noFailureCriteria = CriteriaResult("isFailure == false", "true", false)
                return arrayOf(noFailureCriteria)
            }

        val removedCountCriteria = CriteriaResult("removed == 12", removed.toString(), removed == 12)
        val shardCount = inventorySnapshot.countShards()
        val oneShardCriteria =
            CriteriaResult("inventorySnapshot.countShards() == 8", shardCount.toString(), shardCount == 8L)
        val diamondCount = inventorySnapshot.countDiamonds()
        val noDiamondsCriteria =
            CriteriaResult("inventorySnapshot.countDiamonds() == 8", diamondCount.toString(), diamondCount == 8L)
        val diamondBlockCount = inventorySnapshot.countDiamondBlocks()
        val noDiamondBlocksCriteria =
            CriteriaResult(
                "inventorySnapshot.countDiamondBlocks() == 0",
                diamondBlockCount.toString(),
                diamondBlockCount == 0L,
            )
        return arrayOf(removedCountCriteria, oneShardCriteria, noDiamondsCriteria, noDiamondBlocksCriteria)
    }

    suspend fun testRemoveAll(): Array<CriteriaResult> {
        val inventorySnapshot =
            InventorySnapshot.withContents(
                arrayOf(Shard.createItemStack(2), ItemStack(Material.DIAMOND, 1), ItemStack(Material.DIAMOND_BLOCK, 1))
            )
        val removed = InventorySnapshotUtils.removeAll(inventorySnapshot)

        val removedCountCriteria = CriteriaResult("removed == 92", removed.toString(), removed == 92)
        val noContentsCriteria =
            CriteriaResult(
                "inventorySnapshot.all { it == null }",
                inventorySnapshot.contents.joinToString(","),
                inventorySnapshot.all { it == null },
            )
        return arrayOf(removedCountCriteria, noContentsCriteria)
    }

    suspend fun testCountShards(): Array<CriteriaResult> {
        val inventorySnapshot =
            InventorySnapshot.withContents(
                arrayOf(Shard.createItemStack(2), Shard.createItemStack(5), Shard.createItemStack(1))
            )
        val count = InventorySnapshotUtils.countShards(inventorySnapshot)
        val countCriteria = CriteriaResult("count == 8", count.toString(), count == 8)
        return arrayOf(countCriteria)
    }

    suspend fun testCountDiamonds(): Array<CriteriaResult> {
        val inventorySnapshot =
            InventorySnapshot.withContents(
                arrayOf(ItemStack(Material.DIAMOND, 2), ItemStack(Material.DIAMOND, 5), ItemStack(Material.DIAMOND, 1))
            )
        val count = InventorySnapshotUtils.countDiamonds(inventorySnapshot)
        val countCriteria = CriteriaResult("count == 8", count.toString(), count == 8)
        return arrayOf(countCriteria)
    }

    suspend fun testCountDiamondBlocks(): Array<CriteriaResult> {
        val inventorySnapshot =
            InventorySnapshot.withContents(
                arrayOf(
                    ItemStack(Material.DIAMOND_BLOCK, 2),
                    ItemStack(Material.DIAMOND_BLOCK, 5),
                    ItemStack(Material.DIAMOND_BLOCK, 1),
                )
            )
        val count = InventorySnapshotUtils.countDiamondBlocks(inventorySnapshot)
        val countCriteria = CriteriaResult("count == 8", count.toString(), count == 8)
        return arrayOf(countCriteria)
    }

    suspend fun testNotEnoughSpaceForChange(): Array<CriteriaResult> {
        val contents = mutableListOf<ItemStack?>()
        repeat(35) { contents.add(ItemStack(Material.DIRT)) }
        contents.add(ItemStack(Material.DIAMOND_BLOCK))
        val inventorySnapshot = InventorySnapshot.withContents(contents.toTypedArray())
        val result = InventorySnapshotUtils.removeShards(inventorySnapshot, 1)
        val failureCriteria = CriteriaResult("isFailure == true", result.isFailure.toString(), result.isFailure)
        return arrayOf(failureCriteria)
    }

    suspend fun testEmptyRemoveShards(): Array<CriteriaResult> {
        val contents = emptyArray<ItemStack?>()
        val inventorySnapshot = InventorySnapshot.withContents(contents)
        val removed =
            InventorySnapshotUtils.removeShards(inventorySnapshot, 10).getOrElse {
                val noFailureCriteria = CriteriaResult("isFailure == false", "true", false)
                return arrayOf(noFailureCriteria)
            }
        val removedCriteriaResult = CriteriaResult("removed == 0", removed.toString(), removed == 0)
        return arrayOf(removedCriteriaResult)
    }
}
