package com.f4xizzz.serverbuddy.util;

import com.f4xizzz.serverbuddy.ServerBuddy;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

public final class CommandRunner {
   private CommandRunner() {
   }

   public static void runAll(MinecraftServer server, List<String> commands, String playerName) {
      if (commands != null && !commands.isEmpty()) {
         CommandSourceStack source = server.createCommandSourceStack();

         for(String raw : commands) {
            if (raw != null && !raw.isBlank()) {
               String command = raw.replace("%player%", playerName);

               try {
                  server.getCommands().performPrefixedCommand(source, command);
               } catch (Exception e) {
                  ServerBuddy.LOGGER.error("Error running the boss command '" + command + "'!", e);
               }
            }
         }

      }
   }
}
