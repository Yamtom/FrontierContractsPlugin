# Economy System — Подвійний провайдер економіки

## Призначення

Плагін підтримує **дві системи економіки** через стратегію (Strategy Pattern):
1. **Vault** — зовнішній economy плагін (EssentialsX Economy, CMI, etc.)
2. **Internal Wallet** — вбудований SQLite гаманець

Вибір відбувається автоматично при старті через `AdaptiveEconomyGateway`.

---

## AdaptiveEconomyGateway

```java
class AdaptiveEconomyGateway {
    private EconomyProvider active;

    void refresh() {
        if (VaultHook.isAvailable()) {
            active = new VaultEconomyProvider(VaultHook.getEconomy());
        } else {
            active = new InternalWalletEconomyProvider(storageService);
        }
    }

    // Делегує до active:
    void deposit(Player, double)
    void withdraw(Player, double)
    double balance(Player)
    void depositOffline(UUID, double)
}
```

`refresh()` викликається при старті і при `PluginEnableEvent` (якщо Vault підключається пізніше).

---

## EconomyProvider (інтерфейс)

```java
interface EconomyProvider {
    boolean deposit(Player player, double amount);
    boolean withdraw(Player player, double amount);
    double balance(Player player);
    void depositOffline(UUID uuid, double amount);
    String getCurrencyName();
    boolean has(Player player, double amount);
}
```

---

## VaultEconomyProvider

Обгортка навколо Vault `Economy` API.

```java
class VaultEconomyProvider implements EconomyProvider {
    private final Economy vaultEconomy;

    boolean deposit(Player p, double amount) {
        return vaultEconomy.depositPlayer(p, amount).transactionSuccess();
    }
    boolean withdraw(Player p, double amount) {
        return vaultEconomy.withdrawPlayer(p, amount).transactionSuccess();
    }
    double balance(Player p) {
        return vaultEconomy.getBalance(p);
    }
    void depositOffline(UUID uuid, double amount) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        vaultEconomy.depositPlayer(op, amount);
    }
}
```

---

## VaultHook

```java
class VaultHook {
    static boolean setup() {
        RegisteredServiceProvider<Economy> rsp =
            Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    static boolean isAvailable() { return economy != null; }
    static Economy getEconomy() { return economy; }
}
```

---

## InternalWalletEconomyProvider

SQLite-базована економіка. Всі операції — synchronized (thread-safe).

DB-таблиця `wallet_balances`:
```sql
CREATE TABLE wallet_balances (
    player_uuid  TEXT PRIMARY KEY,
    balance      REAL NOT NULL DEFAULT 0.0
);
```

```java
class InternalWalletEconomyProvider implements EconomyProvider {

    synchronized boolean deposit(Player p, double amount) {
        double current = getBalance(p);
        setBalance(p.getUniqueId(), current + amount);
        return true;
    }

    synchronized boolean withdraw(Player p, double amount) {
        double current = getBalance(p);
        if (current < amount) return false;
        setBalance(p.getUniqueId(), current - amount);
        return true;
    }

    void depositOffline(UUID uuid, double amount) {
        // Використовує пряме SQL UPDATE або INSERT через JDBC
        storageService.addToBalance(uuid, amount);
    }
}
```

---

## depositOffline (офлайн гравці)

Важлива функція для Custom Orders (ескроу повернення):

```
if (Bukkit.getPlayer(uuid) != null) {
    // Гравець онлайн — одразу deposit
    deposit(player, amount);
} else {
    // Гравець офлайн
    if (provider == Vault) {
        vaultEconomy.depositPlayer(Bukkit.getOfflinePlayer(uuid), amount);
    } else {
        storageService.addToBalance(uuid, amount);  // пряме SQL
    }
}
```

---

## PlaceholderAPI Integration

Клас `FrontierPlaceholderExpansion` реєструє плейсхолдери:

| Плейсхолдер | Значення |
|-------------|---------|
| `%frontier_balance%` | Поточний баланс гравця |
| `%frontier_level%` | Рівень гравця |
| `%frontier_xp%` | Поточний XP |
| `%frontier_contracts_completed%` | Всього контрактів |
| `%frontier_reputation_<board_id>%` | Репутація на конкретній дошці |

---

## BonusConfig (record)

```java
record BonusConfig(
    double deathlessBonusMultiplier,     // default 1.2
    double highRankPityBonus,            // +12 вага
    double eliteRankPityBonus,           // +8 вага
    int highRankPityThreshold,           // default 12 контрактів
    int eliteRankPityThreshold           // default 25 контрактів
) {}
```

---

## Ключові класи

| Клас | Пакет | Роль |
|------|-------|------|
| `AdaptiveEconomyGateway` | `economy` | Strategy selector |
| `EconomyProvider` | `economy` | Інтерфейс провайдера |
| `VaultEconomyProvider` | `economy` | Vault обгортка |
| `InternalWalletEconomyProvider` | `economy` | SQLite гаманець |
| `VaultHook` | `hook` | Vault registration |
| `FrontierPlaceholderExpansion` | `hook` | PAPI expansion |
| `BonusConfig` | `model` | Record бонусних параметрів |