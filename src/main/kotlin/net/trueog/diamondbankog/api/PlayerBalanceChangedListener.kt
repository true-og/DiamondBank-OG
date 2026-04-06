package net.trueog.diamondbankog.api

interface PlayerBalanceChangedListener {
    fun onUpdate(event: PlayerBalanceChangedEvent)
}
