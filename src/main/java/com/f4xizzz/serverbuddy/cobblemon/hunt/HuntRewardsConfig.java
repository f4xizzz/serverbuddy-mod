package com.f4xizzz.serverbuddy.cobblemon.hunt;

public class HuntRewardsConfig {
   public HuntReward comum = new HuntReward(100.0, new String[]{"give %player% minecraft:diamond 1"});
   public HuntReward incomum = new HuntReward(500.0, new String[]{"give %player% minecraft:diamond 2"});
   public HuntReward raro = new HuntReward(700.0, new String[]{"give %player% minecraft:diamond 3"});
   public HuntReward ultraRaro = new HuntReward(1000.0, new String[]{"give %player% minecraft:diamond 4"});

   public HuntReward get(HuntBucket bucket) {
      HuntReward var10000;
      switch (bucket) {
         case COMMON -> var10000 = this.comum;
         case UNCOMMON -> var10000 = this.incomum;
         case RARE -> var10000 = this.raro;
         case ULTRA_RARE -> var10000 = this.ultraRaro;
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }
}
