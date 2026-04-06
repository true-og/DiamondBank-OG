package net.trueog.diamondbankog.util

import net.trueog.diamondbankog.DiamondBankOG

internal object ErrorHandler {
    /** Handles the error by throwing, disables the economy unless specified that it shouldn't */
    fun handleError(exception: Throwable, dontDisableEconomy: Boolean = false) {
        if (!dontDisableEconomy) DiamondBankOG.economyDisabled = true
        throw exception
    }
}
