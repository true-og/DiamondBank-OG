package net.trueog.diamondbankog.transaction

import java.util.Collections
import java.util.UUID
import java.util.WeakHashMap

object InventoryLockExtensions {
    val lockMap: MutableSet<UUID> = Collections.newSetFromMap(Collections.synchronizedMap(WeakHashMap()))

    fun <T> UUID.withInventoryLock(block: () -> T): T {
        this.lockInventory()
        try {
            return block()
        } finally {
            this.unlockInventory()
        }
    }

    suspend fun <T> UUID.withInventoryLockSuspend(block: suspend () -> T): T {
        this.lockInventory()
        try {
            return block()
        } finally {
            this.unlockInventory()
        }
    }

    private fun UUID.lockInventory() {
        val added = lockMap.add(this)
        if (!added) throw IllegalStateException("Inventory was already locked")
    }

    private fun UUID.unlockInventory() {
        val removed = lockMap.remove(this)
        if (!removed) throw IllegalStateException("Inventory was not locked")
    }

    fun UUID.isInventoryLocked(): Boolean {
        return this in lockMap
    }
}
