package net.trueog.diamondbankog

import java.util.*
import net.trueog.diamondbankog.PostgreSQL.PlayerShards

internal object ErrorHandler {
    class EconomyException(message: String) : RuntimeException(message)

    /** Handles the error by throwing, disables the economy unless you specify it shouldn't */
    fun handleError(
        uuid: UUID,
        expectedMutatedShards: Int,
        playerShards: PlayerShards?,
        otherUuid: UUID? = null,
        dontDisableEconomy: Boolean = false,
    ) {
        if (!dontDisableEconomy) DiamondBankOG.economyDisabled = true

        throw EconomyException(
            """

            Player UUID: $uuid
            ${
                if (otherUuid != null) "Other Player UUID: $otherUuid" else ""
            }Expected Mutated Shards = $expectedMutatedShards${
                if (playerShards != null) {
                    if (playerShards.bank != -1) "Player Bank Balance: ${playerShards.bank}" else ""
                } else ""
            }${
                if (playerShards != null) {
                    if (playerShards.inventory != -1) "Player Inventory Balance: ${playerShards.inventory}" else ""
                } else ""
            }${
                if (playerShards != null) {
                    if (playerShards.enderChest != -1) "Player Ender Chest Balance: ${playerShards.enderChest}" else ""
                } else ""
            }
        """
                .trimIndent()
        )
    }
}
