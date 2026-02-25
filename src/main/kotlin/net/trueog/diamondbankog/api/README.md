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
val diamondBankAPI = diamondBankAPIProvider.provider
```
Java:
```java
final RegisteredServiceProvider<DiamondBankAPIJava> diamondBankAPIProvider = getServer().getServicesManager().getRegistration(DiamondBankAPIJava.class);
if (diamondBankAPIProvider == null) {
    getLogger().severe("DiamondBank-OG API is null");
    Bukkit.getPluginManager().disablePlugin(this);
    return;
}
DiamondBankAPIJava diamondBankAPI = diamondBankAPIProvider.getProvider();
```

## Available functions
> [!IMPORTANT]
> Always convert Diamonds to Shards before using the API using [`diamondsToShards`](#diamondstoshards).

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
 */
```
Kotlin:
```kotlin
suspend fun addToPlayerBankShards(uuid: UUID, shards: Long, transactionReason: String, notes: String?): Result<Unit>
```
Java:
```java
public void addToPlayerBankShards(UUID uuid, long shards, String transactionReason, @Nullable String notes)
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
 * @throws DiamondBankException.InsufficientBalanceException
 */
```
Kotlin:
```kotlin
suspend fun subtractFromPlayerBankShards(uuid: UUID, shards: Long, transactionReason: String, notes: String?): Result<Unit>
```
Java:
```java
public void subtractFromPlayerBankShards(UUID uuid, long shards, String transactionReason, @Nullable String notes)
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
 */
```
Kotlin:
```kotlin
suspend fun getBankShards(uuid: UUID): Result<Long>
```
Java:
```java
public long getBankShards(UUID uuid)
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
 */
```
Kotlin:
```kotlin
suspend fun getInventoryShards(uuid: UUID): Result<Long>
```
Java:
```java
public long getInventoryShards(UUID uuid)
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
 */
```
Kotlin:
```kotlin
suspend fun getEnderChestShards(uuid: UUID): Result<Long>
```
Java:
```java
public long getEnderChestShards(UUID uuid)
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
 */

```
Kotlin:
```kotlin
suspend fun getTotalShards(uuid: UUID): Result<Long>
```
Java:
```java
public long getTotalShards(UUID uuid)
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
 */
```
Kotlin:
```kotlin
suspend fun getBaltop(offset: Int): Result<Map<UUID?, Long>>
```
Java:
```java
public Map<@Nullable UUID, Long> getBaltop(int offset)
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
 */
```
Kotlin:
```kotlin
suspend fun consumeFromPlayer(uuid: UUID, shards: Long, transactionReason: String, notes: String?): Result<Unit>
```
Java:
```java
public void consumeFromPlayer(UUID uuid, long shards, String transactionReason, @Nullable String notes)
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
 */
```
Kotlin:
```kotlin
suspend fun playerPayPlayer(payerUuid: UUID, receiverUuid: UUID, shards: Long, transactionReason: String, notes: String?): Result<Unit>
```
Java:
```java
public void playerPayPlayer(UUID payerUuid, UUID receiverUuid, long shards, String transactionReason, @Nullable String notes)
```

### registerEventListener
```kotlin
/**
 * Register a PlayerBalanceChangedListener to listen for player balance changes
 */
```
Kotlin:
```kotlin
fun registerEventListener(eventListener: PlayerBalanceChangedListener)
```
Java:
```java
public void registerEventListener(PlayerBalanceChangedListener eventListener)
```

### diamondsToShards
> [!NOTE]
> This function also has an overload for a double.
```kotlin
/**
 * WARNING: This function can throw a MoreThanOneDecimalDigitRuntimeException,
 * make sure it's checked when handling arbitrary input
 *
 * Converts a Diamond float to Shards
 */
```
Kotlin:
```kotlin
fun diamondsToShards(diamonds: Float): Result<Long>
```
Java:
```java
public long diamondsToShards(float diamonds)
```

### shardsToDiamonds
> [!CAUTION]
> This function should only be used for visual output, not calculations.
```kotlin
/** Converts Shards into a formatted Diamonds string */
```
Kotlin:
```kotlin
fun shardsToDiamonds(shards: Long): String
```
Java:
```java
public String shardsToDiamonds(long shards)
```
