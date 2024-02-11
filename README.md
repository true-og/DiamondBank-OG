# DiamondBank-OG

A free and open source Diamond Economy plugin for Spigot inspired by [The Diamond Bank](https://www.spigotmc.org/resources/the-diamond-bank.72020/).\
Maintained for TrueOG.

## PostgreSQL
This plugin uses PostgreSQL which needs to be set up first. The Arch Linux wiki has a [pretty good guide](https://wiki.archlinux.org/title/PostgreSQL#Initial_configuration).\
The PostgreSQL URL, user, password and the table that should be used are configurable in the config.

## User Commands

/balance or /bal and /balance \<player> or /bal \<player>\
/baltop <page number (optional)>\
/deposit <amount (number or "all")>\
/withdraw <amount (number or "all")>\
/pay \<player> \<amount (number or "all")>

## Admin Commands

/setbankbalance \<player> \<balance>

## Permissions
diamondbank-og.balance\
diamondbank-og.balance.others\
diamondbank-og.baltop\
diamondbank-og.deposit\
diamondbank-og.withdraw\
diamondbank-og.pay\
diamondbank-og.setbankbalance