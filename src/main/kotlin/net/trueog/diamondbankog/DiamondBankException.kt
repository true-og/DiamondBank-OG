package net.trueog.diamondbankog

sealed class DiamondBankException(message: String) : Exception(message) {
    class EconomyDisabledException : DiamondBankException("Economy is disabled")

    class InvalidPlayerException : DiamondBankException("Invalid player")

    class PayerNotOnlineException : DiamondBankException("Payer is not online")

    class PlayerNotOnlineException : DiamondBankException("Player is not online")

    class DatabaseException(message: String) : DiamondBankException(message)

    class InsufficientBalanceException(val balance: Long) : DiamondBankException("Insufficient balance")

    class CouldNotRemoveEnoughException(val notRemoved: Int) : Exception("Could not remove enough")

    class OtherException : DiamondBankException("Other exception")

    class InvalidArgumentException : Exception()
}
