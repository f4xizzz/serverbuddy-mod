package com.f4xizzz.serverbuddy.cobblemon.poke;

import com.f4xizzz.serverbuddy.cobblemon.poke.builder.commands.PokeBuilderCommand;
import com.f4xizzz.serverbuddy.cobblemon.poke.builder.config.BuilderConfigManager;
import com.f4xizzz.serverbuddy.cobblemon.poke.editor.commands.PokeEditorCommand;
import com.f4xizzz.serverbuddy.cobblemon.poke.editor.config.EditorConfigManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class PokeEditor {
   public static final String MOD_ID = "pokeeditor";
   public static final Logger LOGGER = LogManager.getLogger("pokeeditor");

   private PokeEditor() {
   }

   public static void init() {
      LOGGER.info("Initializing PokeEditor + PokeBuilder...");
      EditorConfigManager.loadConfigs();
      BuilderConfigManager.loadConfigs();
      PokeEditorCommand.register();
      PokeBuilderCommand.register();
   }
}
