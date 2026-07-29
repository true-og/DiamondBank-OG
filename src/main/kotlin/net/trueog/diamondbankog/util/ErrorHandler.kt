package net.trueog.diamondbankog.util

import java.util.logging.Level
import net.trueog.diamondbankog.DiamondBankOG.Companion.economyDisabled
import net.trueog.diamondbankog.DiamondBankOG.Companion.plugin

internal object ErrorHandler {
    /** Handles the error by throwing, disables the economy unless specified that it shouldn't */
    fun handleError(exception: Throwable, dontDisableEconomy: Boolean = false) {
        if (!dontDisableEconomy) economyDisabled = true
        plugin.logger.log(Level.SEVERE, "Economy error", exception)
    }
}
