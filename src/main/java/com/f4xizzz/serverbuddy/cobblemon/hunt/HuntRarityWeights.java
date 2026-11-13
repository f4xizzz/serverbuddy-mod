package com.f4xizzz.serverbuddy.cobblemon.hunt;

import java.util.Random;

public class HuntRarityWeights {
   private static final Random RANDOM = new Random();
   public int comum = 60;
   public int incomum = 30;
   public int raro = 9;
   public int ultraRaro = 1;

   public HuntBucket getRandomBucket() {
      int total = Math.max(1, this.comum + this.incomum + this.raro + this.ultraRaro);
      int roll = RANDOM.nextInt(total);
      if (roll < this.comum) {
         return HuntBucket.COMMON;
      } else {
         roll -= this.comum;
         if (roll < this.incomum) {
            return HuntBucket.UNCOMMON;
         } else {
            roll -= this.incomum;
            return roll < this.raro ? HuntBucket.RARE : HuntBucket.ULTRA_RARE;
         }
      }
   }
}
