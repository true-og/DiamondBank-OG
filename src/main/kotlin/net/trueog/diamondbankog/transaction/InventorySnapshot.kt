package net.trueog.diamondbankog.transaction

import java.util.*
import kotlin.UnsupportedOperationException
import kotlin.collections.HashMap
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.DiamondBankOG.Companion.scope
import net.trueog.diamondbankog.balance.BalanceManager
import net.trueog.diamondbankog.balance.shard.ShardType
import net.trueog.diamondbankog.transaction.InventoryLockExtensions.isInventoryLocked
import net.trueog.diamondbankog.util.ErrorHandler.handleError
import net.trueog.diamondbankog.util.PlayerInventoryExtensions.countTotal
import net.trueog.utilitiesog.UtilitiesOG
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.HumanEntity
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory

class InventorySnapshot
private constructor(
    private var contents: Array<ItemStack?>,
    private val heldItemSlot: Int,
    private var helmet: ItemStack?,
    private var chestplate: ItemStack?,
    private var leggings: ItemStack?,
    private var boots: ItemStack?,
    val holder: UUID,
    val balanceManager: BalanceManager,
) : PlayerInventory {
    private val maxAmount = 64

    companion object {
        fun from(uuid: UUID, balanceManager: BalanceManager): InventorySnapshot {
            if (!Bukkit.isPrimaryThread()) {
                throw IllegalStateException("This method should only be called on the main thread")
            }
            if (!uuid.isInventoryLocked()) {
                throw IllegalStateException("Can only take a snapshot from a locked inventory")
            }
            val player = Bukkit.getPlayer(uuid)
            val contents =
                player?.inventory?.contents?.map { it?.clone() }?.toTypedArray()
                    ?: UtilitiesOG.getInventoryData(uuid).map { it?.clone() }.toTypedArray()

            return InventorySnapshot(
                contents.filterIndexed { i, _ -> i !in 36..39 }.toTypedArray(),
                player?.inventory?.heldItemSlot ?: UtilitiesOG.getHeldItemSlot(uuid),
                contents[39],
                contents[38],
                contents[37],
                contents[36],
                uuid,
                balanceManager,
            )
        }
    }

    fun restoreTo(uuid: UUID) {
        if (!Bukkit.isPrimaryThread()) {
            throw IllegalStateException("This method should only be called on the main thread")
        }
        if (!uuid.isInventoryLocked()) {
            throw IllegalStateException("Can only restore to a locked inventory")
        }
        val player = Bukkit.getPlayer(uuid)
        val newContents = arrayOfNulls<ItemStack>(41)
        contents.take(36).toTypedArray().copyInto(newContents)
        newContents[39] = helmet
        newContents[38] = chestplate
        newContents[37] = leggings
        newContents[36] = boots
        newContents[40] = contents[36]
        if (player == null) {
            UtilitiesOG.setInventoryData(uuid, newContents.map { it?.clone() }.toTypedArray())
        } else {
            player.inventory.contents = newContents.map { it?.clone() }.toTypedArray()
        }
        val inventoryShards = this.countTotal()
        scope.launch {
            balanceManager.setPlayerShards(uuid, inventoryShards, ShardType.INVENTORY).getOrElse { handleError(it) }
        }
    }

    private fun firstPartial(item: ItemStack?, inventory: Array<ItemStack?>): Int {
        if (item == null) return -1
        return inventory.indexOfFirst { it != null && it.amount < 64 && it.isSimilar(item) }
    }

    override fun getSize() = contents.size

    override fun getMaxStackSize() = maxAmount

    override fun setMaxStackSize(size: Int) {
        throw UnsupportedOperationException()
    }

    override fun getItem(index: Int) = contents[index]

    override fun getArmorContents(): Array<out ItemStack?> {
        throw UnsupportedOperationException()
    }

    override fun getExtraContents(): Array<out ItemStack?> {
        throw UnsupportedOperationException()
    }

    override fun getHelmet(): ItemStack? {
        throw UnsupportedOperationException()
    }

    override fun getChestplate(): ItemStack? {
        throw UnsupportedOperationException()
    }

    override fun getLeggings(): ItemStack? {
        throw UnsupportedOperationException()
    }

    override fun getBoots(): ItemStack? {
        throw UnsupportedOperationException()
    }

    override fun setItem(index: Int, item: ItemStack?) = contents.set(index, item)

    override fun setItem(slot: EquipmentSlot, item: ItemStack?) {
        throw UnsupportedOperationException()
    }

    override fun getItem(slot: EquipmentSlot): ItemStack {
        throw UnsupportedOperationException()
    }

    override fun setArmorContents(items: Array<out ItemStack?>?) {
        throw UnsupportedOperationException()
    }

    override fun setExtraContents(items: Array<out ItemStack?>?) {
        throw UnsupportedOperationException()
    }

    override fun setHelmet(helmet: ItemStack?) {
        throw UnsupportedOperationException()
    }

    override fun setChestplate(chestplate: ItemStack?) {
        throw UnsupportedOperationException()
    }

    override fun setLeggings(leggings: ItemStack?) {
        throw UnsupportedOperationException()
    }

    override fun setBoots(boots: ItemStack?) {
        throw UnsupportedOperationException()
    }

    override fun getItemInMainHand() = contents[heldItemSlot] ?: ItemStack(Material.AIR)

    override fun setItemInMainHand(item: ItemStack?) = contents.set(heldItemSlot, item)

    override fun getItemInOffHand() = contents[36] ?: ItemStack(Material.AIR)

    override fun setItemInOffHand(item: ItemStack?) = contents.set(36, item)

    @Deprecated("Deprecated in Java")
    override fun getItemInHand(): ItemStack {
        throw UnsupportedOperationException()
    }

    @Deprecated("Deprecated in Java")
    override fun setItemInHand(stack: ItemStack?) {
        throw UnsupportedOperationException()
    }

    override fun getHeldItemSlot(): Int {
        throw UnsupportedOperationException()
    }

    override fun setHeldItemSlot(slot: Int) {
        throw UnsupportedOperationException()
    }

    override fun addItem(vararg items: ItemStack): HashMap<Int, ItemStack> {
        val leftover = HashMap<Int, ItemStack>()

        items.forEachIndexed { index, item ->
            if (item.type.isAir) return@forEachIndexed

            while (item.amount > 0) {
                val firstPartial = firstPartial(item, contents)

                if (firstPartial != -1) {
                    val stack = contents[firstPartial]!!
                    val space = maxAmount - stack.amount
                    val amount = minOf(item.amount, space)

                    stack.amount += amount
                    item.amount -= amount
                    continue
                }

                val empty = firstEmpty()

                if (empty == -1) {
                    leftover[index] = item.clone()
                    break
                }

                val amount = minOf(item.amount, maxAmount)

                contents[empty] = item.clone().apply { this.amount = amount }

                item.amount -= amount
            }
        }
        return leftover
    }

    override fun removeItem(vararg items: ItemStack): HashMap<Int, ItemStack> {
        val leftover = HashMap<Int, ItemStack>()

        items.forEachIndexed { index, item ->
            if (item.type.isAir) return@forEachIndexed
            var toDelete = item.amount

            while (toDelete > 0) {
                val firstSlot = first(item)

                if (firstSlot == -1) {
                    item.amount = toDelete
                    leftover[index] = item
                    toDelete = 0
                } else {
                    val itemStack = contents[firstSlot]!!
                    val removed = minOf(itemStack.amount, toDelete)

                    itemStack.amount -= removed
                    toDelete -= removed

                    if (itemStack.amount == 0) {
                        contents[firstSlot] = null
                    } else {
                        contents[firstSlot] = itemStack
                    }
                }
            }
        }

        return leftover
    }

    override fun removeItemAnySlot(vararg items: ItemStack): HashMap<Int?, ItemStack?> {
        throw UnsupportedOperationException()
    }

    override fun getContents(): Array<out ItemStack?> {
        return (contents.dropLast(1) + boots + leggings + chestplate + helmet + contents.takeLast(1)).toTypedArray()
    }

    override fun setContents(items: Array<out ItemStack?>) {
        throw UnsupportedOperationException()
    }

    override fun getStorageContents(): Array<out ItemStack?> = contents

    override fun setStorageContents(items: Array<out ItemStack?>) {
        throw UnsupportedOperationException()
    }

    override fun contains(material: Material): Boolean {
        throw UnsupportedOperationException()
    }

    override fun contains(item: ItemStack?): Boolean {
        throw UnsupportedOperationException()
    }

    override fun contains(material: Material, amount: Int): Boolean {
        throw UnsupportedOperationException()
    }

    override fun contains(item: ItemStack?, amount: Int): Boolean {
        throw UnsupportedOperationException()
    }

    override fun containsAtLeast(item: ItemStack?, amount: Int): Boolean {
        throw UnsupportedOperationException()
    }

    override fun all(material: Material): HashMap<Int, out ItemStack?> =
        contents
            .dropLast(1) // Bukkit's implementation doesn't include the offhand slot
            .withIndex()
            .filter { (_, value) -> value?.type == material }
            .associateTo(HashMap()) { (index, value) -> index to value }

    override fun all(item: ItemStack?): HashMap<Int?, out ItemStack?> {
        throw UnsupportedOperationException()
    }

    override fun first(material: Material): Int {
        throw UnsupportedOperationException()
    }

    override fun first(item: ItemStack): Int {
        return contents.indexOfFirst { it != null && item.isSimilar(it) }
    }

    override fun firstEmpty(): Int {
        return contents.indexOfFirst { it == null }
    }

    override fun isEmpty(): Boolean {
        throw UnsupportedOperationException()
    }

    override fun remove(material: Material) {
        contents.forEachIndexed { index, item ->
            if (item?.type == material) {
                contents[index] = null
            }
        }
    }

    override fun remove(itemToRemove: ItemStack) {
        contents.forEachIndexed { index, item ->
            if (item?.equals(itemToRemove) == true) {
                contents[index] = null
            }
        }
    }

    override fun clear(index: Int) {
        throw UnsupportedOperationException()
    }

    override fun clear() {
        throw UnsupportedOperationException()
    }

    override fun close(): Int {
        throw UnsupportedOperationException()
    }

    override fun getViewers(): List<HumanEntity?> {
        throw UnsupportedOperationException()
    }

    override fun getType(): InventoryType {
        throw UnsupportedOperationException()
    }

    override fun getHolder(): HumanEntity? {
        throw UnsupportedOperationException()
    }

    override fun getHolder(useSnapshot: Boolean): InventoryHolder? {
        throw UnsupportedOperationException()
    }

    override fun iterator(): MutableListIterator<ItemStack?> {
        throw UnsupportedOperationException()
    }

    override fun iterator(index: Int): ListIterator<ItemStack?> {
        throw UnsupportedOperationException()
    }

    override fun getLocation(): Location? {
        throw UnsupportedOperationException()
    }
}
