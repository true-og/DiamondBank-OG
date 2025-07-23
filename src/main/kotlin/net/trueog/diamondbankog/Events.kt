package net.trueog.diamondbankog

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.AutoCompress.compress
import net.trueog.diamondbankog.AutoDeposit.deposit
import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.ErrorHandler.handleError
import net.trueog.diamondbankog.InventoryExtensions.countTotal
import net.trueog.diamondbankog.MainThreadBlock.runOnMainThread
import net.trueog.diamondbankog.PostgreSQL.ShardType
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack

@OptIn(DelicateCoroutinesApi::class)
internal class Events : Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (DiamondBankOG.economyDisabled) {
            event.player.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "${config.prefix}<reset>: <red>The economy is disabled. Please notify a staff member."
                )
            )
            return
        }

        val worldName = event.player.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") return

        DiamondBankOG.scope.launch {
            val inventoryShards = event.player.inventory.countTotal()
            DiamondBankOG.postgreSQL
                .setPlayerShards(event.player.uniqueId, inventoryShards, ShardType.INVENTORY)
                .getOrElse {
                    handleError(event.player.uniqueId, inventoryShards, null)
                    return@launch
                }

            val enderChestDiamonds = event.player.enderChest.countTotal()
            DiamondBankOG.postgreSQL
                .setPlayerShards(event.player.uniqueId, enderChestDiamonds, ShardType.ENDER_CHEST)
                .getOrElse {
                    handleError(event.player.uniqueId, enderChestDiamonds, null)
                    return@launch
                }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        val player = event.entity
        if (player !is Player) return
        val worldName = player.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") return

        val itemStack = event.item.itemStack
        val itemType = itemStack.type
        if (
            itemType != Material.DIAMOND &&
                itemType != Material.DIAMOND_BLOCK &&
                itemType != Material.SHULKER_BOX &&
                !(itemType == Material.PRISMARINE_SHARD && itemStack.persistentDataContainer.has(Shard.namespacedKey))
        ) {
            return
        }

        if (DiamondBankOG.economyDisabled) {
            player.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "${config.prefix}<reset>: <red>You cannot pick up any economy-related items while the economy is disabled."
                )
            )
            event.isCancelled = true
            return
        }

        if (DiamondBankOG.transactionLock.isLocked(player.uniqueId)) {
            event.isCancelled = true
            return
        }

        if (DiamondBankOG.redis.getValue("diamondbankog:${player.uniqueId}:autodeposit") == "true") {
            deposit(player, event.item)
        }

        DiamondBankOG.scope.launch {
            if (DiamondBankOG.redis.getValue("diamondbankog:${player.uniqueId}:autocompress") == "true") {
                compress(player)
            }

            DiamondBankOG.transactionLock.withLockSuspend(player.uniqueId) {
                val inventoryShards = runOnMainThread { player.inventory.countTotal() }

                DiamondBankOG.postgreSQL
                    .setPlayerShards(player.uniqueId, inventoryShards, ShardType.INVENTORY)
                    .getOrElse {
                        handleError(player.uniqueId, inventoryShards, null)
                        return@withLockSuspend
                    }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val worldName = event.player.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") return

        val itemStack = event.itemDrop.itemStack
        val itemType = itemStack.type
        if (
            itemType != Material.DIAMOND &&
                itemType != Material.DIAMOND_BLOCK &&
                itemType != Material.SHULKER_BOX &&
                !(itemType == Material.PRISMARINE_SHARD && itemStack.persistentDataContainer.has(Shard.namespacedKey))
        ) {
            return
        }

        if (DiamondBankOG.economyDisabled) {
            event.isCancelled = true
            event.player.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "${config.prefix}<reset>: <red>You cannot drop any economy-related items while the economy is disabled."
                )
            )
            return
        }

        if (DiamondBankOG.transactionLock.isLocked(event.player.uniqueId)) {
            event.isCancelled = true
            return
        }

        DiamondBankOG.scope.launch {
            DiamondBankOG.transactionLock.withLockSuspend(event.player.uniqueId) {
                val inventoryShards = runOnMainThread { event.player.inventory.countTotal() }
                DiamondBankOG.postgreSQL
                    .setPlayerShards(event.player.uniqueId, inventoryShards, ShardType.INVENTORY)
                    .getOrElse {
                        handleError(event.player.uniqueId, inventoryShards, null)
                        return@withLockSuspend
                    }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onInventoryMoveItem(event: InventoryClickEvent) {
        val player = event.whoClicked
        if (player !is Player) return
        val worldName = player.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") return

        if (event.inventory.type == InventoryType.CRAFTING) return

        val itemStack = event.currentItem
        if (itemStack == null) return
        val itemType = itemStack.type

        if (
            itemType != Material.DIAMOND &&
                itemType != Material.DIAMOND_BLOCK &&
                itemType != Material.SHULKER_BOX &&
                !(itemType == Material.PRISMARINE_SHARD && itemStack.persistentDataContainer.has(Shard.namespacedKey))
        ) {
            return
        }

        if (DiamondBankOG.economyDisabled) {
            event.isCancelled = true
            player.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "${config.prefix}<reset>: <red>You cannot move any economy-related items while the economy is disabled."
                )
            )
            return
        }

        if (DiamondBankOG.transactionLock.isLocked(player.uniqueId)) {
            event.isCancelled = true
            return
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val worldName = player.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") return

        val clickedBlock = event.clickedBlock
        if (clickedBlock == null) return
        if (clickedBlock.type == Material.AIR) return

        if (event.action.isLeftClick) {
            if (clickedBlock.type != Material.DIAMOND_BLOCK && clickedBlock.type != Material.SHULKER_BOX) {
                return
            }
        } else {
            val itemStack = event.item
            if (itemStack == null) return
            val itemType = itemStack.type

            if (
                itemType != Material.DIAMOND &&
                    itemType != Material.DIAMOND_BLOCK &&
                    itemType != Material.SHULKER_BOX &&
                    !(itemType == Material.PRISMARINE_SHARD &&
                        itemStack.persistentDataContainer.has(Shard.namespacedKey))
            ) {
                return
            }
        }

        if (DiamondBankOG.economyDisabled) {
            event.isCancelled = true
            event.player.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "${config.prefix}<reset>: <red>You cannot interact with any economy-related items while the economy is disabled."
                )
            )
            return
        }

        if (DiamondBankOG.transactionLock.isLocked(event.player.uniqueId)) {
            event.isCancelled = true
            return
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        val worldName = player.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") return

        val itemStackInMainHand = player.inventory.itemInMainHand
        val itemTypeInMainHand = itemStackInMainHand.type

        val itemStackInOffHand = player.inventory.itemInOffHand
        val itemTypeInOffHand = itemStackInOffHand.type

        if (
            (itemTypeInMainHand != Material.DIAMOND &&
                itemTypeInMainHand != Material.DIAMOND_BLOCK &&
                itemTypeInMainHand != Material.SHULKER_BOX &&
                !(itemTypeInMainHand == Material.PRISMARINE_SHARD &&
                    itemStackInMainHand.persistentDataContainer.has(Shard.namespacedKey)) &&
                (itemTypeInOffHand != Material.DIAMOND &&
                    itemTypeInOffHand != Material.DIAMOND_BLOCK &&
                    itemTypeInOffHand != Material.SHULKER_BOX &&
                    !(itemTypeInOffHand == Material.PRISMARINE_SHARD &&
                        itemStackInOffHand.persistentDataContainer.has(Shard.namespacedKey))))
        ) {
            return
        }

        if (DiamondBankOG.economyDisabled) {
            event.isCancelled = true
            event.player.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "${config.prefix}<reset>: <red>You cannot interact with any economy-related items while the economy is disabled."
                )
            )
            return
        }

        if (DiamondBankOG.transactionLock.isLocked(event.player.uniqueId)) {
            event.isCancelled = true
            return
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (DiamondBankOG.economyDisabled) {
            return
        }

        val player = event.player
        if (player !is Player) return

        if (DiamondBankOG.transactionLock.isLocked(player.uniqueId)) {
            return
        }

        val worldName = player.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") return

        DiamondBankOG.scope.launch {
            if (DiamondBankOG.redis.getValue("diamondbankog:${player.uniqueId}:autocompress") == "true") {
                compress(player)
            }

            DiamondBankOG.scope.launch {
                DiamondBankOG.transactionLock.withLockSuspend(player.uniqueId) {
                    val (inventoryShards, enderChestShards) =
                        runOnMainThread {
                            Pair(
                                player.inventory.countTotal(),
                                if (event.inventory.type == InventoryType.ENDER_CHEST) player.enderChest.countTotal()
                                else null,
                            )
                        }
                    DiamondBankOG.postgreSQL
                        .setPlayerShards(player.uniqueId, inventoryShards, ShardType.INVENTORY)
                        .getOrElse {
                            handleError(player.uniqueId, inventoryShards, null)
                            return@withLockSuspend
                        }

                    if (enderChestShards == null) return@withLockSuspend
                    DiamondBankOG.postgreSQL
                        .setPlayerShards(player.uniqueId, enderChestShards, ShardType.ENDER_CHEST)
                        .getOrElse {
                            handleError(player.uniqueId, enderChestShards, null)
                            return@withLockSuspend
                        }
                }
            }
        }
    }

    @EventHandler
    fun onPrepareItemCraft(event: PrepareItemCraftEvent) {
        val resultType = event.recipe?.result?.type
        if (
            resultType != Material.PRISMARINE &&
                resultType != Material.DARK_PRISMARINE &&
                resultType != Material.SEA_LANTERN
        ) {
            return
        }
        if (!event.inventory.any { it?.persistentDataContainer?.has(Shard.namespacedKey) == true }) {
            return
        }
        event.inventory.result = ItemStack(Material.AIR)
    }
}
