package com.f4xizzz.serverbuddy;

import java.io.File;
import net.fabricmc.loader.api.FabricLoader;

public class ModPaths {
   private ModPaths() {
   }

   public static File subDir(String... parts) {
      File dir = new File(FabricLoader.getInstance().getConfigDir().toFile(), "ServerBuddy");

      for(String part : parts) {
         dir = new File(dir, part);
      }

      return dir;
   }
}
