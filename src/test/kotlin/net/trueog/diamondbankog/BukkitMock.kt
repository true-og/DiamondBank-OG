package net.trueog.diamondbankog

import io.mockk.*
import net.kyori.adventure.text.Component
import net.trueog.diamondbankog.Utils.allImpl
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.block.ShulkerBox
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType

object BukkitMock {
    private fun first(item: ItemStack?, inventory: Array<ItemStack?>): Int {
        if (item == null) {
            return -1
        }
        for (i in inventory.indices) {
            if (inventory[i] == null) continue

            if (item.isSimilar(inventory[i])) {
                return i
            }
        }
        return -1
    }

    private fun firstPartial(item: ItemStack?, inventory: Array<ItemStack?>): Int {
        val filteredItem = item?.clone()
        if (item == null) {
            return -1
        }
        for (i in inventory.indices) {
            val cItem = inventory[i]
            if (cItem != null && cItem.amount < 64 && cItem.isSimilar(filteredItem)) {
                return i
            }
        }
        return -1
    }

    private fun firstEmpty(inventory: Array<ItemStack?>): Int {
        for (i in inventory.indices) {
            if (inventory[i] == null) {
                return i
            }
        }
        return -1
    }

    const val MAX_AMOUNT = 64

    fun mockBukkit(): Server {
        val server = mockk<Server>()
        mockkStatic(Bukkit::class)
        every { Bukkit.getServer() } returns server
        every { server.isPrimaryThread } returns true
        every { Bukkit.isPrimaryThread() } returns true

        every { Bukkit.getItemFactory() } answers { server.itemFactory }

        every { Bukkit.createInventory(anyNullable<InventoryHolder>(), any<Int>()) } answers
            {
                server.createInventory(firstArg<InventoryHolder?>(), secondArg<Int>())
            }

        val sizeSlot = slot<Int>()
        every { server.createInventory(any(), capture(sizeSlot)) } returns createInventory()

        every { server.itemFactory.getItemMeta(any()) } answers
            {
                val material = firstArg<Material>()
                val itemMeta =
                    if (material == Material.SHULKER_BOX) {
                        mockk<BlockStateMeta>()
                    } else {
                        mockk<Damageable>()
                    }

                val persistentDataContainerKeys = mutableSetOf<NamespacedKey>()
                var displayName: Component? = null
                every { itemMeta.clone() } returns itemMeta
                every { itemMeta.displayName(any()) } answers { displayName = firstArg<Component>() }
                every { itemMeta.displayName() } answers { displayName }
                every { itemMeta.addEnchant(any(), any(), any()) } returns true
                every { itemMeta.addItemFlags(any()) } just Runs
                every { itemMeta.persistentDataContainer.set(any(), PersistentDataType.STRING, any()) } answers
                    {
                        val key = firstArg<NamespacedKey>()
                        persistentDataContainerKeys.add(key)
                    }
                every { itemMeta.persistentDataContainer.has(any()) } answers
                    {
                        val key = firstArg<NamespacedKey>()
                        persistentDataContainerKeys.contains(key)
                    }
                every { itemMeta.persistentDataContainer.keys } answers { persistentDataContainerKeys }
                every { itemMeta.persistentDataContainer.isEmpty } answers { persistentDataContainerKeys.isEmpty() }

                if (itemMeta is BlockStateMeta) {
                    val blockState = mockk<ShulkerBox>()
                    var inventory = createInventory()
                    every { itemMeta.blockState } returns blockState
                    every { itemMeta.blockState = any() } answers { inventory = firstArg<ShulkerBox>().inventory }
                    every { blockState.inventory } returns inventory
                }

                if (itemMeta is Damageable) {
                    every { itemMeta.damage } returns 0
                }

                itemMeta
            }

        every { server.itemFactory.isApplicable(any(), Material.PRISMARINE_SHARD) } returns true
        every { server.itemFactory.isApplicable(any(), Material.SHULKER_BOX) } returns true

        every { server.itemFactory.asMetaFor(any(), any<Material>()) } answers
            {
                val itemMeta = firstArg<ItemMeta>()
                val persistentDataContainerKeys = mutableSetOf<NamespacedKey>()
                val displayName = itemMeta.displayName()
                itemMeta.persistentDataContainer.keys.forEach { persistentDataContainerKeys.add(it) }

                val material = secondArg<Material>()
                val newItemMeta =
                    if (material == Material.SHULKER_BOX) {
                        mockk<BlockStateMeta>()
                    } else {
                        mockk<Damageable>()
                    }

                every { newItemMeta.clone() } returns newItemMeta
                every { newItemMeta.displayName() } answers { displayName }
                every { newItemMeta.persistentDataContainer.has(any()) } answers
                    {
                        val key = firstArg<NamespacedKey>()
                        persistentDataContainerKeys.contains(key)
                    }
                every { newItemMeta.persistentDataContainer.isEmpty } answers { persistentDataContainerKeys.isEmpty() }
                every { newItemMeta.persistentDataContainer.keys } answers { persistentDataContainerKeys }

                if (newItemMeta is BlockStateMeta) {
                    val blockState = mockk<ShulkerBox>()
                    var inventory = ((itemMeta as BlockStateMeta).blockState as ShulkerBox).inventory
                    println(inventory.contents[0])
                    every { newItemMeta.blockState } returns blockState
                    every { newItemMeta.blockState = any() } answers { inventory = firstArg<ShulkerBox>().inventory }
                    every { blockState.inventory } returns inventory
                }

                if (newItemMeta is Damageable) {
                    every { newItemMeta.damage } returns 0
                }

                newItemMeta
            }

        every { server.itemFactory.equals(any(), any()) } answers
            {
                val meta1 = firstArg<ItemMeta?>()
                val meta2 = secondArg<ItemMeta?>()
                if (meta1 == meta2) return@answers true

                if (meta1 == null) {
                    return@answers (meta2!!.displayName() == null &&
                        !meta2.hasEnchants() &&
                        meta2.persistentDataContainer.isEmpty)
                }
                if (meta2 == null) {
                    return@answers (meta1.displayName() == null &&
                        !meta1.hasEnchants() &&
                        meta1.persistentDataContainer.isEmpty)
                }

                meta1.displayName() == meta2.displayName() &&
                    meta1.persistentDataContainer.keys == meta2.persistentDataContainer.keys
            }

        every { @Suppress("DEPRECATION") server.itemFactory.updateMaterial(any(), Material.PRISMARINE_SHARD) } returns
            Material.PRISMARINE_SHARD

        every { @Suppress("DEPRECATION") server.itemFactory.updateMaterial(any(), Material.SHULKER_BOX) } returns
            Material.SHULKER_BOX

        return server
    }

    fun createInventory(): Inventory {
        val inventory = mockk<Inventory>()
        var contents = arrayOfNulls<ItemStack>(36)
        every { inventory.contents = any<Array<ItemStack?>>() } answers { contents = firstArg<Array<ItemStack?>>() }
        every { inventory.contents } answers { contents }
        every { inventory.storageContents } answers { contents }
        every { inventory.all(any<Material>()) } answers
            {
                val material = firstArg<Material>()
                allImpl(contents, material)
            }
        every { inventory.addItem(any()) } answers { addToInventory(firstArg<Array<out ItemStack?>>(), contents) }
        every { inventory.removeItem(any()) } answers
            {
                val items = firstArg<Array<out ItemStack?>>()
                val leftover = HashMap<Int?, ItemStack?>()

                items.forEachIndexed { index, item ->
                    if (item == null) {
                        throw IllegalArgumentException("ItemStack cannot be null")
                    }

                    if (item.type.isAir) {
                        return@forEachIndexed
                    }
                    var toDelete = item.amount

                    while (true) {
                        val first = first(item, contents)

                        if (first == -1) {
                            item.amount = toDelete
                            leftover[index] = item
                            break
                        } else {
                            val itemStack = contents[first]
                            val amount = itemStack!!.amount

                            if (amount <= toDelete) {
                                toDelete -= amount
                                contents[first] = null
                            } else {
                                itemStack.amount = amount - toDelete
                                contents[first] = itemStack
                                toDelete = 0
                            }
                        }

                        if (toDelete <= 0) {
                            break
                        }
                    }
                }

                leftover
            }
        every { inventory.remove(anyNullable<Material>()) } answers
            {
                val material = firstArg<Material?>() ?: throw IllegalArgumentException("Material cannot be null")

                contents.forEachIndexed { index, item ->
                    if (item?.type == material) {
                        contents[index] = null
                    }
                }
            }
        every { inventory.getItem(any<Int>()) } answers { contents[firstArg()] }
        return inventory
    }

    fun addToInventory(items: Array<out ItemStack?>, contents: Array<ItemStack?>): HashMap<Int?, ItemStack?> {
        val leftover = HashMap<Int?, ItemStack?>()

        items.forEachIndexed { index, item ->
            if (item == null) {
                throw IllegalArgumentException("ItemStack cannot be null")
            }

            if (item.type.isAir) {
                return@forEachIndexed
            }
            while (true) {
                val firstPartial = firstPartial(item, contents)

                if (firstPartial == -1) {
                    val firstFree = firstEmpty(contents)

                    if (firstFree == -1) {
                        leftover[index] = item
                        break
                    } else {
                        if (item.amount > MAX_AMOUNT) {
                            val stack = item.clone()
                            stack.amount = MAX_AMOUNT
                            contents[firstFree] = stack.clone()
                            item.amount = -MAX_AMOUNT
                        } else {
                            contents[firstFree] = item.clone()
                            break
                        }
                    }
                } else {
                    val partialItem = contents[firstPartial]?.clone()

                    val amount = item.amount
                    val partialAmount = partialItem!!.amount

                    if (amount + partialAmount <= MAX_AMOUNT) {
                        partialItem.amount = amount + partialAmount
                        contents[firstPartial] = partialItem.clone()
                        break
                    }

                    partialItem.amount = MAX_AMOUNT

                    contents[firstPartial] = partialItem.clone()
                    item.amount = amount + partialAmount - MAX_AMOUNT
                }
            }
        }
        return leftover
    }
}
