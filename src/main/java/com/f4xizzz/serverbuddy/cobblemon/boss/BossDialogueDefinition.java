package com.f4xizzz.serverbuddy.cobblemon.boss;

import java.util.ArrayList;
import java.util.List;

public class BossDialogueDefinition {
   public List<String> lines = new ArrayList<>();
   public List<Option> options = new ArrayList<>();

   public static class Option {
      public String text;
      public List<String> commands = new ArrayList<>();
      public BossDialogueDefinition next;
   }
}
