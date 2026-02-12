# DiamondBank-OG API
## Getting started
In your `onEnable` function:\
Kotlin:
```kotlin
val diamondBankAPIProvider = server.servicesManager.getRegistration(DiamondBankAPIKotlin::class.java)
if (diamondBankAPIProvider == null) {
    logger.severe("DiamondBank-OG API is null")
    Bukkit.getPluginManager().disablePlugin(this)
    return
}
val diamondBankAPI = diamondBankAPIProvider.getProvider()
```
Java:
```java
final RegisteredServiceProvider<DiamondBankAPIJava> provider = getServer().getServicesManager().getRegistration(DiamondBankAPIJava.class);
if (provider == null) {
    getLogger().severe("DiamondBank-OG API is null – disabling plugin.");
    Bukkit.getPluginManager().disablePlugin(this);
    return;
}
```

## Available functions
> [!IMPORTANT]
> Always convert Diamonds to Shards before using the API using [`diamondsToShards`](#diamondstoshards)

Exceptions are only applicable when using the Java API, when using the Kotlin API the function will return a `Result` instead.
### addToPlayerShards
```kotlin
/**
 * WARNING: if the player has a transaction lock applied this function will wait until its released.
 *
 * This function also blocks for the database call, this is so you don't have to manually run .get() on a
 * CompletableFuture
 *
 * @param transactionReason the reason for this transaction for in the transaction log
 * @param notes any specifics for this transaction that may be nice to know for in the transaction log
 * @throws DiamondBankException.EconomyDisabledException
 * @throws DiamondBankException.DatabaseException
 */
fun addToPlayerBankShards(uuid: UUID, shards: Long, transactionReason: String, notes: String?)
```

### subtractFromPlayerBankShards
```kotlin
/**
 * WARNING: if the player has a transaction lock applied this function will wait until its released
 *
 * This function also blocks for the database call, this is so you don't have to manually run .get() on a
 * CompletableFuture
 *
 * @param transactionReason the reason for this transaction for in the transaction log
 * @param notes any specifics for this transaction that may be nice to know for in the transaction log
 * @throws DiamondBankException.EconomyDisabledException
 * @throws DiamondBankException.DatabaseException
 * @throws DiamondBankException.InsufficientBalanceException
 */
fun subtractFromPlayerBankShards(uuid: UUID, shards: Long, transactionReason: String, notes: String?)
```

### getBankShards
```kotlin
/**
 * WARNING: if the player has a transaction lock applied this function will wait until its released
 *
 * This function also blocks for the database call, this is so you don't have to manually run .get() on a
 * CompletableFuture
 *
 * @throws DiamondBankException.EconomyDisabledException
 * @throws DiamondBankException.DatabaseException
 */
fun getBankShards(uuid: UUID): Long
```

### getInventoryShards
```kotlin
/**
 * WARNING: if the player has a transaction lock applied this function will wait until its released
 *
 * This function also blocks for the database call, this is so you don't have to manually run .get() on a
 * CompletableFuture
 *
 * @throws DiamondBankException.EconomyDisabledException
 * @throws DiamondBankException.DatabaseException
 */
fun getInventoryShards(uuid: UUID): Long
```

### getEnderChestShards
```kotlin
/**
 * WARNING: if the player has a transaction lock applied this function will wait until its released
 *
 * This function also blocks for the database call, this is so you don't have to manually run .get() on a
 * CompletableFuture
 *
 * @throws DiamondBankException.EconomyDisabledException
 * @throws DiamondBankException.DatabaseException
 */
fun getEnderChestShards(uuid: UUID): Long
```

### getTotalShards
```kotlin
/**
 * WARNING: if the player has a transaction lock applied this function will wait until its released
 *
 * This function also blocks for the database call, this is so you don't have to manually run .get() on a
 * CompletableFuture
 *
 * @throws DiamondBankException.EconomyDisabledException
 * @throws DiamondBankException.DatabaseException
 */
fun getTotalShards(uuid: UUID): Long
```

### getBaltop
```kotlin
/**
 * WARNING: if the player has a transaction lock applied this function will wait until its released
 *
 * This function also blocks for the database call, this is so you don't have to manually run .get() on a
 * CompletableFuture
 *
 * @throws DiamondBankException.EconomyDisabledException
 * @throws DiamondBankException.DatabaseException
 */
fun getBaltop(offset: Int): Map<UUID?, Long>
```

### consumeFromPlayer
```kotlin
/**
 * WARNING: if the player has a transaction lock applied this function will wait until its released
 *
 * This function also blocks for the database call, this is so you don't have to manually run .get() on a
 * CompletableFuture
 *
 * @param transactionReason the reason for this transaction for in the transaction log
 * @param notes any specifics for this transaction that may be nice to know for in the transaction log
 * @throws DiamondBankException.EconomyDisabledException
 * @throws DiamondBankException.InvalidPlayerException
 * @throws DiamondBankException.PlayerNotOnlineException
 * @throws DiamondBankException.InsufficientFundsException
 * @throws DiamondBankException.InsufficientInventorySpaceException
 * @throws DiamondBankException.DatabaseException
 */
fun consumeFromPlayer(uuid: UUID, shards: Long, transactionReason: String, notes: String?)
```

### playerPayPlayer
```kotlin
/**
 * WARNING: if the player has a transaction lock applied this function will wait until its released
 *
 * This function also blocks for the database call, this is so you don't have to manually run .get() on a
 * CompletableFuture
 *
 * @param transactionReason the reason for this transaction for in the transaction log
 * @param notes any specifics for this transaction that may be nice to know for in the transaction log
 * @throws DiamondBankException.EconomyDisabledException
 * @throws DiamondBankException.InvalidPlayerException
 * @throws DiamondBankException.PayerNotOnlineException
 * @throws DiamondBankException.InsufficientFundsException
 * @throws DiamondBankException.InsufficientInventorySpaceException
 * @throws DiamondBankException.DatabaseException
 */
fun playerPayPlayer(payerUuid: UUID, receiverUuid: UUID, shards: Long, transactionReason: String, notes: String?)
```

### registerEventListener
```kotlin
/**
 * Register a PlayerBalanceChangedListener to listen for player balance changes
 */
fun registerEventListener(eventListener: PlayerBalanceChangedListener)
```

### diamondsToShards
```kotlin
/**
 * Converts a Diamond value to Shards
 *
 * @throws DiamondBankException.MoreThanOneDecimalDigitException
 */
fun diamondsToShards(diamonds: Float): Long
```
