package com.f4xizzz.serverbuddy.cobblemon.boss;

import com.cobblemon.mod.common.api.dialogue.ArtificialDialogueFaceProvider;
import com.cobblemon.mod.common.api.dialogue.Dialogue;
import com.cobblemon.mod.common.api.dialogue.DialogueAction;
import com.cobblemon.mod.common.api.dialogue.DialogueGibber;
import com.cobblemon.mod.common.api.dialogue.DialogueManager;
import com.cobblemon.mod.common.api.dialogue.DialoguePage;
import com.cobblemon.mod.common.api.dialogue.DialogueSpeaker;
import com.cobblemon.mod.common.api.dialogue.DialogueText;
import com.cobblemon.mod.common.api.dialogue.FunctionDialogueAction;
import com.cobblemon.mod.common.api.dialogue.FunctionDialogueText;
import com.cobblemon.mod.common.api.dialogue.input.DialogueNoInput;
import com.cobblemon.mod.common.api.dialogue.input.DialogueOption;
import com.cobblemon.mod.common.api.dialogue.input.DialogueOptionSetInput;
import com.cobblemon.mod.common.api.dialogue.input.DialogueTimeout;
import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.util.CommandRunner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kotlin.Unit;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class BossDialogueOpener {
   private static final String SPEAKER_KEY = "boss";

   private BossDialogueOpener() {
   }

   public static void open(ServerPlayer player, BossDefinition boss, BossDialogueDefinition def, Set<String> aspects) {
      if (def != null) {
         ResourceLocation speciesIdentifier = resolveSpeciesIdentifier(boss.speciesId);
         if (speciesIdentifier == null) {
            ServerBuddy.LOGGER.warn("Could not build the dialogue of boss '{}': invalid species '{}'.", boss.id, boss.speciesId);
         } else {
            String displayName = boss.nickname != null && !boss.nickname.isBlank() ? boss.nickname : boss.speciesId;
            DialogueSpeaker speaker = new DialogueSpeaker(new FunctionDialogueText((active) -> Component.literal(displayName)), new ArtificialDialogueFaceProvider("pokemon", speciesIdentifier, aspects, true), (DialogueGibber)null);
            List<DialoguePage> allPages = new ArrayList<>();
            buildPage(def, allPages);
            DialogueAction escapeAction = new FunctionDialogueAction((active, value) -> {
               active.close();
               return Unit.INSTANCE;
            });
            DialogueAction noopInitAction = new FunctionDialogueAction((active, value) -> Unit.INSTANCE);
            Dialogue dialogue = new Dialogue(allPages, Dialogue.Companion.getDEFAULT_BACKGROUND(), escapeAction, Map.of("boss", speaker), noopInitAction);
            DialogueManager.startDialogue(player, dialogue);
         }
      }
   }

   private static DialoguePage buildPage(BossDialogueDefinition def, List<DialoguePage> collector) {
      List<DialogueText> lines = new ArrayList<>();

      for(String line : def.lines) {
         lines.add(new FunctionDialogueText((active) -> Component.literal(line)));
      }

      DialoguePage page = new DialoguePage("boss_dialogue_" + collector.size(), "boss", lines, (String)null, new DialogueNoInput(), (ResourceLocation)null, (DialogueGibber)null, new ArrayList<>(), (DialogueAction)null);
      collector.add(page);
      if (def.options != null && !def.options.isEmpty()) {
         List<DialogueOption> dialogueOptions = new ArrayList<>();

         for(BossDialogueDefinition.Option option : def.options) {
            DialoguePage nextPage = option.next != null ? buildPage(option.next, collector) : null;
            List<String> commands = option.commands;
            String text = option.text != null && !option.text.isBlank() ? option.text : "...";
            DialogueAction action = new FunctionDialogueAction((active, value) -> {
               MinecraftServer server = active.getPlayerEntity().getServer();
               if (server != null) {
                  CommandRunner.runAll(server, commands, active.getPlayerEntity().getName().getString());
               }

               if (nextPage != null) {
                  active.setPage(nextPage);
               } else {
                  active.close();
               }

               return Unit.INSTANCE;
            });
            dialogueOptions.add(new DialogueOption(new FunctionDialogueText((active) -> Component.literal(text)), text, action, (active) -> true, (active) -> true));
         }

         page.setInput(new DialogueOptionSetInput(dialogueOptions, (DialogueTimeout)null, true));
         return page;
      } else {
         DialogueAction closeAction = new FunctionDialogueAction((active, value) -> {
            active.close();
            return Unit.INSTANCE;
         });
         page.setInput(new DialogueNoInput(closeAction));
         return page;
      }
   }

   private static ResourceLocation resolveSpeciesIdentifier(String speciesId) {
      if (speciesId != null && !speciesId.isBlank()) {
         String full = speciesId.contains(":") ? speciesId : "cobblemon:" + speciesId;

         try {
            return ResourceLocation.parse(full);
         } catch (Exception var3) {
            return null;
         }
      } else {
         return null;
      }
   }
}
