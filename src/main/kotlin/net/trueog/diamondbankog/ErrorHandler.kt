package net.trueog.diamondbankog

import java.util.*
import net.trueog.diamondbankog.DiamondBankOG.Companion.economyDisabled
import net.trueog.diamondbankog.PostgreSQL.PlayerShards

internal object ErrorHandler {
    class EconomyException(message: String) : RuntimeException(message)

    /** Handles the error by throwing, disables the economy unless you specify it shouldn't */
    fun handleError(
        uuid: UUID,
        expectedMutatedShards: Long,
        playerShards: PlayerShards?,
        otherUuid: UUID? = null,
        dontDisableEconomy: Boolean = false,
    ) {
        if (!dontDisableEconomy) economyDisabled = true

        throw EconomyException(
            """

            Player UUID: $uuid
            ${
                if (otherUuid != null) "Other Player UUID: $otherUuid" else ""
            }Expected Mutated Shards = $expectedMutatedShards${
                if (playerShards != null) {
                    if (playerShards.bank != -1L) "Player Bank Balance: ${playerShards.bank}" else ""
                } else ""
            }${
                if (playerShards != null) {
                    if (playerShards.inventory != -1L) "Player Inventory Balance: ${playerShards.inventory}" else ""
                } else ""
            }${
                if (playerShards != null) {
                    if (playerShards.enderChest != -1L) "Player Ender Chest Balance: ${playerShards.enderChest}" else ""
                } else ""
            }
        """
                .trimIndent()
        )
    }
}
