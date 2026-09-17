package net.trueog.diamondbankog

import io.mockk.every
import io.mockk.mockk
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory

object Utils {
    suspend fun waitForCoroutines(scope: CoroutineScope) {
        scope.coroutineContext[Job]?.children?.forEach { it.join() }
    }

    fun allImpl(contents: Array<ItemStack?>, material: Material): HashMap<Int, out ItemStack> =
        HashMap(
            contents.mapIndexedNotNull { index, item -> if (item?.type == material) index to item else null }.toMap()
        )

    fun mockPlayerInventory(player: Player, uuid: UUID, contents: Array<ItemStack?>): PlayerInventory {
        val playerInventory = mockk<PlayerInventory>()
        var contents = contents.copyOf(41)
        every { playerInventory.contents } answers { contents }
        every { playerInventory.contents = any() } answers { contents = firstArg() }
        every { playerInventory.armorContents } answers { contents.dropLast(1).takeLast(4).toTypedArray() }
        every { playerInventory.itemInOffHand } answers { contents[40] ?: ItemStack(Material.AIR) }
        every { playerInventory.all(any<Material>()) } answers
            {
                val material = firstArg<Material>()
                allImpl(contents.take(36).toTypedArray(), material)
            }
        every { playerInventory.heldItemSlot } returns 0
        every { playerInventory.holder!!.uniqueId } returns uuid
        every { player.inventory } returns playerInventory
        return playerInventory
    }
}
