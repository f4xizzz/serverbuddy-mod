package com.f4xizzz.serverbuddy.cobblemon.hunt;

import java.util.ArrayList;
import java.util.List;

public class HuntReward {
   public double preco;
   public List<String> comandos = new ArrayList<>();

   public HuntReward() {
   }

   public HuntReward(double preco, String... comandos) {
      this.preco = preco;

      for(String comando : comandos) {
         this.comandos.add(comando);
      }

   }
}
