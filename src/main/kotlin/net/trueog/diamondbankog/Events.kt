package net.trueog.diamondbankog

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.Helper.PostgresFunction
import net.trueog.diamondbankog.Helper.countDiamonds
import net.trueog.diamondbankog.PostgreSQL.BalanceType
import net.trueog.diamondbankog.PostgreSQL.*
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.scheduler.BukkitRunnable
import de.tr7zw.changeme.nbtapi.*

@OptIn(DelicateCoroutinesApi::class)
class Events : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (DiamondBankOG.economyDisabled) {
            event.player.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>The economy is disabled because of a severe error. Please notify a staff member."))
            return
        }

        val worldName = event.player.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") return

        GlobalScope.launch {
            val old_balance = NBT.getPersistentData(event.player, {nbt -> nbt.getInteger("diamondbank:balance")});
            val hasDatabaseEntry: Boolean = try {
              val result = DiamondBankOG.postgreSQL.isRegistered(event.player.uniqueId)
              if(result != null) {
                result
              }
              else {
                false
              }
            } catch(e: Exception) {
              Helper.handleError(event.player.uniqueId, PostgresFunction.MIGRATE_FROM_OLD_DIAMOND_BANK, old_balance, BalanceType.BANK_BALANCE, null, "The Diamond Bank Migration")
              false
            }
            if(old_balance > 0 && event.player.hasPlayedBefore() && !hasDatabaseEntry) {
                // migrate
                NBT.modifyPersistentData(event.player, {nbt -> nbt.setInteger("diamondbank:balance", 0)})
                var error = DiamondBankOG.postgreSQL.setPlayerBalance(
                    event.player.uniqueId,
                    old_balance,
                    BalanceType.BANK_BALANCE
                )
                if (error) {
                       Helper.handleError(
                       event.player.uniqueId,
                       PostgresFunction.SET_PLAYER_BALANCE,
                       old_balance,
                       BalanceType.BANK_BALANCE,
                       null,
                       "onPlayerJoin"
                   )
                   return@launch
                }
                event.player.sendMessage("Your balance has been migrated to DiamondBank-OG!")
            }
            val inventoryDiamonds = event.player.inventory.countDiamonds()
            var error = DiamondBankOG.postgreSQL.setPlayerBalance(
                event.player.uniqueId,
                inventoryDiamonds,
                BalanceType.INVENTORY_BALANCE
            )
            if (error) {
                Helper.handleError(
                    event.player.uniqueId,
                    PostgresFunction.SET_PLAYER_BALANCE,
                    inventoryDiamonds,
                    BalanceType.INVENTORY_BALANCE,
                    null,
                    "onPlayerJoin"
                )
                return@launch
            }

            val enderChestDiamonds = event.player.enderChest.countDiamonds()
            error = DiamondBankOG.postgreSQL.setPlayerBalance(
                event.player.uniqueId,
                enderChestDiamonds,
                BalanceType.ENDER_CHEST_BALANCE
            )
            if (error) {
                Helper.handleError(
                    event.player.uniqueId,
                    PostgresFunction.SET_PLAYER_BALANCE,
                    enderChestDiamonds,
                    BalanceType.ENDER_CHEST_BALANCE,
                    null,
                    "onPlayerJoin"
                )
                return@launch
            }
        }
    }

    @EventHandler
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        if (DiamondBankOG.economyDisabled) {
            return
        }

        val worldName = event.entity.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") return

        val itemType = event.item.itemStack.type
        if (itemType != Material.DIAMOND && itemType != Material.DIAMOND_BLOCK && itemType != Material.SHULKER_BOX) return

        if (DiamondBankOG.blockInventoryFor.contains(event.entity.uniqueId)) {
            event.isCancelled = true
            return
        }

        val player = event.entity
        if (player !is Player) return

        object : BukkitRunnable() {
            override fun run() {
                GlobalScope.launch {
                    val inventoryDiamonds = player.inventory.countDiamonds()
                    val error = DiamondBankOG.postgreSQL.setPlayerBalance(
                        player.uniqueId,
                        inventoryDiamonds,
                        BalanceType.INVENTORY_BALANCE
                    )
                    if (error) {
                        Helper.handleError(
                            player.uniqueId,
                            PostgresFunction.SET_PLAYER_BALANCE,
                            inventoryDiamonds,
                            BalanceType.INVENTORY_BALANCE,
                            null,
                            "onEntityPickupItem"
                        )
                        return@launch
                    }
                }
            }
        }.runTaskLater(DiamondBankOG.plugin, 1)
    }

    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        if (DiamondBankOG.economyDisabled) {
            return
        }

        val worldName = event.player.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") return

        val itemType = event.itemDrop.itemStack.type
        if (itemType != Material.DIAMOND && itemType != Material.DIAMOND_BLOCK && itemType != Material.SHULKER_BOX) return

        if (DiamondBankOG.blockInventoryFor.contains(event.player.uniqueId)) {
            event.isCancelled = true
            return
        }

        object : BukkitRunnable() {
            override fun run() {
                GlobalScope.launch {
                    val inventoryDiamonds = event.player.inventory.countDiamonds()
                    val error = DiamondBankOG.postgreSQL.setPlayerBalance(
                        event.player.uniqueId,
                        inventoryDiamonds,
                        BalanceType.INVENTORY_BALANCE
                    )
                    if (error) {
                        Helper.handleError(
                            event.player.uniqueId,
                            PostgresFunction.SET_PLAYER_BALANCE,
                            inventoryDiamonds,
                            BalanceType.INVENTORY_BALANCE,
                            null,
                            "onPlayerDropItem"
                        )
                        return@launch
                    }
                }
            }
        }.runTaskLater(DiamondBankOG.plugin, 1)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (DiamondBankOG.economyDisabled) {
            return
        }

        val worldName = event.player.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") return

        object : BukkitRunnable() {
            override fun run() {
                GlobalScope.launch {
                    val inventoryDiamonds = event.player.inventory.countDiamonds()
                    var error = DiamondBankOG.postgreSQL.setPlayerBalance(
                        event.player.uniqueId,
                        inventoryDiamonds,
                        BalanceType.INVENTORY_BALANCE
                    )
                    if (error) {
                        Helper.handleError(
                            event.player.uniqueId,
                            PostgresFunction.SET_PLAYER_BALANCE,
                            inventoryDiamonds,
                            BalanceType.INVENTORY_BALANCE,
                            null,
                            "onPlayerJoin"
                        )
                        return@launch
                    }

                    if (event.inventory.type != InventoryType.ENDER_CHEST) return@launch
                    val enderChestDiamonds = event.player.enderChest.countDiamonds()
                    error = DiamondBankOG.postgreSQL.setPlayerBalance(
                        event.player.uniqueId,
                        enderChestDiamonds,
                        BalanceType.ENDER_CHEST_BALANCE
                    )
                    if (error) {
                        Helper.handleError(
                            event.player.uniqueId,
                            PostgresFunction.SET_PLAYER_BALANCE,
                            enderChestDiamonds,
                            BalanceType.ENDER_CHEST_BALANCE,
                            null,
                            "onInventoryClose"
                        )
                        return@launch
                    }
                }
            }
        }.runTaskLater(DiamondBankOG.plugin, 1)
    }

//    @EventHandler
//    fun onPlayerCommandPreprocess(event: PlayerCommandPreprocessEvent) {
//        val args = event.message.split(" ", limit = 3)
//        handleGive(args)
//    }
//
//    @EventHandler
//    fun onServerCommand(event: ServerCommandEvent) {
//        val args = event.command.split(" ", limit = 3)
//        handleGive(args)
//    }
//
//    private fun handleGive(args: List<String>) {
//        GlobalScope.launch {
//            if (args[0] != "/give" && args[0] != "/essentials:give" && args[0] != "/minecraft:give") return@launch
//            if (args.size != 3 && args.size != 4) return@launch
//            if (args[2] != "diamond" && args[2] != "minecraft:diamond") return@launch
//
//            val player = Bukkit.getOfflinePlayer(args[1])
//            val playerPlayer = player.player ?: return@launch
//
//            if (player.isOnline) {
//                val inventoryDiamonds = playerPlayer.inventory.countDiamondsInInventory()
//                val error = DiamondBankOG.postgreSQL.setPlayerBalance(
//                    player.uniqueId,
//                    inventoryDiamonds,
//                    INVENTORY_BALANCE
//                )
//                if (error) {
//                    Helper.handleError(
//                        playerPlayer.uniqueId,
//                        SET_PLAYER_BALANCE,
//                        inventoryDiamonds,
//                        INVENTORY_BALANCE,
//                        null,
//                        "handleGive"
//                    )
//                    return@launch
//                }
//            }
//        }
//    }
}
