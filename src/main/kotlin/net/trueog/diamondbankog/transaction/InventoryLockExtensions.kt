package net.trueog.diamondbankog.transaction

import java.util.Collections
import java.util.WeakHashMap
import org.bukkit.inventory.PlayerInventory

object InventoryLockExtensions {
    val lockMap: MutableSet<PlayerInventory> = Collections.newSetFromMap(Collections.synchronizedMap(WeakHashMap()))

    fun <T> PlayerInventory.withLock(block: () -> T): T {
        this.lock()
        try {
            return block()
        } finally {
            this.unlock()
        }
    }

    suspend fun <T> PlayerInventory.withLockSuspend(block: suspend () -> T): T {
        this.lock()
        try {
            return block()
        } finally {
            this.unlock()
        }
    }

    private fun PlayerInventory.lock() {
        val added = lockMap.add(this)
        if (!added) throw IllegalStateException("Inventory was already locked")
    }

    private fun PlayerInventory.unlock() {
        val removed = lockMap.remove(this)
        if (!removed) throw IllegalStateException("Inventory was not locked")
    }

    fun PlayerInventory.isLocked(): Boolean {
        return this in lockMap
    }
}
