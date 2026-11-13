package com.f4xizzz.serverbuddy.economy;

import java.math.BigDecimal;
import net.impactdev.impactor.api.economy.EconomyService;
import net.impactdev.impactor.api.economy.accounts.Account;
import net.impactdev.impactor.api.economy.currency.Currency;
import net.kyori.adventure.key.Key;
import net.minecraft.server.level.ServerPlayer;

public class EconomyManager {
   private EconomyManager() {
   }

   public static BigDecimal getBalance(ServerPlayer player, String currencyId) {
      try {
         Account account = getAccount(player, currencyId);
         return account == null ? BigDecimal.ZERO : account.balance();
      } catch (Exception var3) {
         return BigDecimal.ZERO;
      }
   }

   public static boolean hasBalance(ServerPlayer player, String currencyId, double amount) {
      return getBalance(player, currencyId).compareTo(BigDecimal.valueOf(amount)) >= 0;
   }

   public static boolean charge(ServerPlayer player, String currencyId, double amount) {
      try {
         Account account = getAccount(player, currencyId);
         return account == null ? false : account.withdraw(BigDecimal.valueOf(amount)).successful();
      } catch (Exception var5) {
         return false;
      }
   }

   public static boolean refund(ServerPlayer player, String currencyId, double amount) {
      try {
         Account account = getAccount(player, currencyId);
         return account == null ? false : account.deposit(BigDecimal.valueOf(amount)).successful();
      } catch (Exception var5) {
         return false;
      }
   }

   private static Account getAccount(ServerPlayer player, String currencyId) {
      EconomyService service = EconomyService.instance();
      Key currencyKey = Key.key(currencyId);
      Currency currency = (Currency)service.currencies().currency(currencyKey).orElse(null);
      return currency == null ? null : (Account)service.account(currency, player.getUUID()).join();
   }
}
