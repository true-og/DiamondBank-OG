package net.trueog.diamondbankog

sealed class DiamondBankException(message: String) : Exception(message) {
    object TransactionsLockedException : DiamondBankException("Transactions for player are locked") {
        @Suppress("unused") private fun readResolve(): Any = TransactionsLockedException
    }

    object EconomyDisabledException : DiamondBankException("Economy is disabled") {
        @Suppress("unused") private fun readResolve(): Any = EconomyDisabledException
    }

    object InvalidPlayerException : DiamondBankException("Invalid player") {
        @Suppress("unused") private fun readResolve(): Any = InvalidPlayerException
    }

    object PayerNotOnlineException : DiamondBankException("Payer is not online") {
        @Suppress("unused") private fun readResolve(): Any = PayerNotOnlineException
    }

    object PlayerNotOnlineException : DiamondBankException("Player is not online") {
        @Suppress("unused") private fun readResolve(): Any = PlayerNotOnlineException
    }

    object OtherException : DiamondBankException("Other exception") {
        @Suppress("unused") private fun readResolve(): Any = OtherException
    }
}
