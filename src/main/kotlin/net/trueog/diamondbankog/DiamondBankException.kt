package net.trueog.diamondbankog

sealed class DiamondBankException(message: String) : Exception(message) {
    class EconomyDisabledException : DiamondBankException("Economy is disabled")

    class InvalidPlayerException : DiamondBankException("Invalid player")

    class PayerNotOnlineException : DiamondBankException("Payer is not online")

    class PlayerNotOnlineException : DiamondBankException("Player is not online")

    class DatabaseException(message: String) : DiamondBankException(message)

    class InsufficientBalanceException(val balance: Long) : DiamondBankException("Insufficient balance")

    class InsufficientFundsException(val short: Long) : DiamondBankException("Insufficient funds")

    class InvalidArgumentException : Exception()
}

sealed class DiamondBankRuntimeException(message: String) : RuntimeException(message) {
    class MoreThanOneDecimalDigitRuntimeException : DiamondBankException("More than one decimal digit")
}
