package com.f4xizzz.serverbuddy.command;

import com.f4xizzz.serverbuddy.ServerBuddy;
import com.f4xizzz.serverbuddy.redeem.RedeemCode;
import com.f4xizzz.serverbuddy.redeem.RedeemConfig;
import com.f4xizzz.serverbuddy.redeem.RedeemConfigManager;
import com.f4xizzz.serverbuddy.redeem.RedeemManager;
import com.f4xizzz.serverbuddy.redeem.RedeemMessages;
import com.f4xizzz.serverbuddy.storage.StorageService;
import com.f4xizzz.serverbuddy.util.CommandRunner;
import com.f4xizzz.serverbuddy.util.MiniMessageUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import com.f4xizzz.serverbuddy.core.SbPermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class RedeemCommand {
   private RedeemCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      for(String alias : resolveAliases()) {
         dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(alias).requires((source) -> SbPermissions.check(source, "serverbuddy.redeem.use", 0))).then(Commands.argument("codigo", StringArgumentType.word()).executes(RedeemCommand::executeRedeem)));
      }

   }

   private static List<String> resolveAliases() {
      List<String> configured = RedeemConfigManager.getConfig().aliases;
      List<String> clean = new ArrayList<>();
      if (configured != null) {
         for(String alias : configured) {
            if (alias != null) {
               String trimmed;
               for(trimmed = alias.trim(); trimmed.startsWith("/"); trimmed = trimmed.substring(1)) {
               }

               if (!trimmed.isBlank()) {
                  clean.add(trimmed);
               }
            }
         }
      }

      return clean.isEmpty() ? List.of("redeem") : clean;
   }

   private static int executeRedeem(CommandContext<CommandSourceStack> context) {
      ServerPlayer player;
      try {
         player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      } catch (Exception var7) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("This command can only be used by a player."));
         return 0;
      }

      String code = StringArgumentType.getString(context, "codigo");
      RedeemConfig config = RedeemConfigManager.getConfig();
      RedeemMessages messages = config.mensagens;
      RedeemCode codeDef = (RedeemCode)config.codigos.get(code);
      if (codeDef == null) {
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> MiniMessageUtil.parse(fill(messages.codigo_invalido, code)), false);
         return 0;
      } else if (codeDef.permissao != null && !codeDef.permissao.isBlank() && !SbPermissions.check((SharedSuggestionProvider)context.getSource(), codeDef.permissao, 2)) {
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> MiniMessageUtil.parse(fill(messages.sem_permissao_codigo, code)), false);
         return 0;
      } else {
         RedeemManager.RedeemResult result = RedeemManager.redeem(player.getUUID(), code, codeDef.max_usos);
         switch (result) {
            case ALREADY_REDEEMED:
               ((CommandSourceStack)context.getSource()).sendSuccess(() -> MiniMessageUtil.parse(fill(messages.ja_resgatado, code)), false);
               return 0;
            case MAX_USES:
               ((CommandSourceStack)context.getSource()).sendSuccess(() -> MiniMessageUtil.parse(fill(messages.limite_atingido, code)), false);
               return 0;
            case SUCCESS:
               if (!StorageService.deliverRedeem(player, code, codeDef)) {
                  CommandRunner.runAll(((CommandSourceStack)context.getSource()).getServer(), codeDef.comandos, player.getName().getString());
               }

               ((CommandSourceStack)context.getSource()).sendSuccess(() -> MiniMessageUtil.parse(fill(messages.sucesso, code)), false);
               ServerBuddy.LOGGER.info("[Redeem] {} redeemed the code '{}'.", player.getName().getString(), code);
               return 1;
            default:
               return 0;
         }
      }
   }

   private static String fill(String template, String code) {
      return template == null ? "" : template.replace("{codigo}", code);
   }
}
