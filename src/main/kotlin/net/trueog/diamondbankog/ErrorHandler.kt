package net.trueog.diamondbankog

import net.trueog.diamondbankog.DiamondBankOG.Companion.economyDisabled

internal object ErrorHandler {
    /** Handles the error by throwing, disables the economy unless specified that it shouldn't */
    fun handleError(exception: Throwable, dontDisableEconomy: Boolean = false) {
        if (!dontDisableEconomy) economyDisabled = true
        throw exception
    }
}
