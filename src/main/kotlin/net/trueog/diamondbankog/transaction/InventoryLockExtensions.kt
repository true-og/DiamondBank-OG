package net.trueog.diamondbankog.transaction

import java.util.Collections
import java.util.WeakHashMap
import org.bukkit.inventory.PlayerInventory

object InventoryLockExtensions {
    val lockMap: MutableSet<PlayerInventory> = Collections.newSetFromMap(Collections.synchronizedMap(WeakHashMap()))

    fun PlayerInventory.lock() {
        val added = lockMap.add(this)
        if (!added) throw IllegalStateException("Inventory was already locked")
    }

    fun PlayerInventory.unlock() {
        val removed = lockMap.remove(this)
        if (!removed) throw IllegalStateException("Inventory was not locked")
    }

    fun PlayerInventory.isLocked(): Boolean {
        return this in lockMap
    }
}
