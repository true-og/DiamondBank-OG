package net.trueog.diamondbankog

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.AutoCompress.compress
import net.trueog.diamondbankog.AutoDeposit.deposit
import net.trueog.diamondbankog.DiamondBankOG.Companion.balanceManager
import net.trueog.diamondbankog.DiamondBankOG.Companion.config
import net.trueog.diamondbankog.DiamondBankOG.Companion.economyDisabled
import net.trueog.diamondbankog.DiamondBankOG.Companion.mm
import net.trueog.diamondbankog.DiamondBankOG.Companion.redis
import net.trueog.diamondbankog.DiamondBankOG.Companion.scope
import net.trueog.diamondbankog.DiamondBankOG.Companion.transactionLock
import net.trueog.diamondbankog.ErrorHandler.handleError
import net.trueog.diamondbankog.InventoryExtensions.countTotal
import net.trueog.diamondbankog.InventoryExtensions.isLocked
import net.trueog.diamondbankog.MainThreadBlock.runOnMainThread
import net.trueog.diamondbankog.PostgreSQL.ShardType
import org.bukkit.Material
import org.bukkit.NamespacedKey
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
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

@OptIn(DelicateCoroutinesApi::class)
internal class Events : Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (economyDisabled) {
            event.player.sendMessage(
                mm.deserialize("${config.prefix}<reset>: <red>The economy is disabled. Please notify a staff member.")
            )
            return
        }

        val worldName = event.player.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") return

        scope.launch {
            val hasEntry =
                balanceManager.hasEntry(event.player.uniqueId).getOrElse {
                    economyDisabled = true
                    return@launch
                }
            if (!hasEntry) {
                val legacyBalance =
                    event.player.persistentDataContainer.get(
                        NamespacedKey.fromString("diamondbank:balance")!!,
                        PersistentDataType.DOUBLE,
                    )
                if (legacyBalance != null) {
                    balanceManager
                        .setPlayerShards(event.player.uniqueId, legacyBalance.toLong() * 9, ShardType.BANK)
                        .getOrElse {
                            handleError(event.player.uniqueId, legacyBalance.toLong() * 9, null)
                            return@launch
                        }
                }
                event.player.persistentDataContainer.remove(NamespacedKey.fromString("diamondbank:balance")!!)
                event.player.sendMessage(
                    mm.deserialize(
                        "${config.prefix}<reset>: <green>Your old balance has been successfully migrated to DiamondBank-OG!"
                    )
                )
            }

            transactionLock.withLockSuspend(event.player.uniqueId) {
                val inventoryShards = event.player.inventory.countTotal()
                balanceManager.setPlayerShards(event.player.uniqueId, inventoryShards, ShardType.INVENTORY).getOrElse {
                    handleError(event.player.uniqueId, inventoryShards, null)
                    return@withLockSuspend
                }

                val enderChestDiamonds = event.player.enderChest.countTotal()
                balanceManager
                    .setPlayerShards(event.player.uniqueId, enderChestDiamonds, ShardType.ENDER_CHEST)
                    .getOrElse {
                        handleError(event.player.uniqueId, enderChestDiamonds, null)
                        return@withLockSuspend
                    }

                balanceManager.cacheForPlayer(event.player.uniqueId)
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        scope.launch {
            transactionLock.withLockSuspend(event.player.uniqueId) {
                balanceManager.removeCacheForPlayer(event.player.uniqueId)
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
                !(itemType == Material.PRISMARINE_SHARD && Shard.isShardItem(itemStack))
        ) {
            return
        }

        if (economyDisabled) {
            player.sendMessage(
                mm.deserialize(
                    "${config.prefix}<reset>: <red>You cannot pick up any economy-related items while the economy is disabled."
                )
            )
            event.isCancelled = true
            return
        }

        if (player.inventory.isLocked()) {
            event.isCancelled = true
            return
        }

        if (redis.getValue("diamondbankog:${player.uniqueId}:autodeposit") == "true") {
            deposit(player, event.item)
        }

        scope.launch {
            if (redis.getValue("diamondbankog:${player.uniqueId}:autocompress") == "true") {
                compress(player)
            }

            transactionLock.withLockSuspend(player.uniqueId) {
                val inventoryShards = runOnMainThread { player.inventory.countTotal() }

                balanceManager.setPlayerShards(player.uniqueId, inventoryShards, ShardType.INVENTORY).getOrElse {
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
                !(itemType == Material.PRISMARINE_SHARD && Shard.isShardItem(itemStack))
        ) {
            return
        }

        if (economyDisabled) {
            event.isCancelled = true
            event.player.sendMessage(
                mm.deserialize(
                    "${config.prefix}<reset>: <red>You cannot drop any economy-related items while the economy is disabled."
                )
            )
            return
        }

        if (event.player.inventory.isLocked()) {
            event.isCancelled = true
            return
        }

        scope.launch {
            transactionLock.withLockSuspend(event.player.uniqueId) {
                val inventoryShards = runOnMainThread { event.player.inventory.countTotal() }
                balanceManager.setPlayerShards(event.player.uniqueId, inventoryShards, ShardType.INVENTORY).getOrElse {
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

        val itemStack = event.currentItem ?: return
        val itemType = itemStack.type

        if (
            itemType != Material.DIAMOND &&
                itemType != Material.DIAMOND_BLOCK &&
                itemType != Material.SHULKER_BOX &&
                !(itemType == Material.PRISMARINE_SHARD && itemStack.persistentDataContainer.has(Shard.namespacedKey))
        ) {
            return
        }

        if (economyDisabled) {
            event.isCancelled = true
            player.sendMessage(
                mm.deserialize(
                    "${config.prefix}<reset>: <red>You cannot move any economy-related items while the economy is disabled."
                )
            )
            return
        }

        if (player.inventory.isLocked()) {
            event.isCancelled = true
            return
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val worldName = player.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") return

        val clickedBlock = event.clickedBlock ?: return
        if (clickedBlock.type == Material.AIR) return

        if (event.action.isLeftClick) {
            if (clickedBlock.type != Material.DIAMOND_BLOCK && clickedBlock.type != Material.SHULKER_BOX) {
                return
            }
        } else {
            val itemStack = event.item ?: return
            val itemType = itemStack.type

            if (
                itemType != Material.DIAMOND &&
                    itemType != Material.DIAMOND_BLOCK &&
                    itemType != Material.SHULKER_BOX &&
                    !(itemType == Material.PRISMARINE_SHARD && Shard.isShardItem(itemStack))
            ) {
                return
            }
        }

        if (economyDisabled) {
            event.isCancelled = true
            event.player.sendMessage(
                mm.deserialize(
                    "${config.prefix}<reset>: <red>You cannot interact with any economy-related items while the economy is disabled."
                )
            )
            return
        }

        if (player.inventory.isLocked()) {
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
                !(itemTypeInMainHand == Material.PRISMARINE_SHARD && Shard.isShardItem(itemStackInMainHand)) &&
                (itemTypeInOffHand != Material.DIAMOND &&
                    itemTypeInOffHand != Material.DIAMOND_BLOCK &&
                    itemTypeInOffHand != Material.SHULKER_BOX &&
                    !(itemTypeInOffHand == Material.PRISMARINE_SHARD && Shard.isShardItem(itemStackInOffHand))))
        ) {
            return
        }

        if (economyDisabled) {
            event.isCancelled = true
            event.player.sendMessage(
                mm.deserialize(
                    "${config.prefix}<reset>: <red>You cannot interact with any economy-related items while the economy is disabled."
                )
            )
            return
        }

        if (player.inventory.isLocked()) {
            event.isCancelled = true
            return
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (economyDisabled) {
            return
        }

        val player = event.player
        if (player !is Player) return

        if (player.inventory.isLocked()) {
            return
        }

        val worldName = player.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") return

        scope.launch {
            if (redis.getValue("diamondbankog:${player.uniqueId}:autocompress") == "true") {
                compress(player)
            }

            scope.launch {
                transactionLock.withLockSuspend(player.uniqueId) {
                    val (inventoryShards, enderChestShards) =
                        runOnMainThread {
                            Pair(
                                player.inventory.countTotal(),
                                if (event.inventory.type == InventoryType.ENDER_CHEST) player.enderChest.countTotal()
                                else null,
                            )
                        }
                    balanceManager.setPlayerShards(player.uniqueId, inventoryShards, ShardType.INVENTORY).getOrElse {
                        handleError(player.uniqueId, inventoryShards, null)
                        return@withLockSuspend
                    }

                    if (enderChestShards == null) return@withLockSuspend
                    balanceManager.setPlayerShards(player.uniqueId, enderChestShards, ShardType.ENDER_CHEST).getOrElse {
                        handleError(player.uniqueId, enderChestShards, null)
                        return@withLockSuspend
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
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
