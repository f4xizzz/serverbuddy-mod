package com.f4xizzz.serverbuddy.mixin.blacklist;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Só aplica o mixin da Blacklist se o Cobblemon está instalado e o mod "Cobblemon Blacklist" original não está
 * (os dois mexem no mesmo ponto e a escolha seria recusada duas vezes).
 */
public class BlacklistMixinPlugin implements IMixinConfigPlugin {
   private static boolean warned = false;

   @Override
   public void onLoad(String mixinPackage) {
   }

   @Override
   public String getRefMapperConfig() {
      return null;
   }

   @Override
   public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
      FabricLoader loader = FabricLoader.getInstance();
      if (!loader.isModLoaded("cobblemon")) {
         return false;
      }
      if (loader.isModLoaded("cobblemon-blacklist")) {
         if (!warned) {
            warned = true;
            LoggerFactory.getLogger("ServerBuddy/Blacklist").warn("The Cobblemon Blacklist mod is installed: ServerBuddy's Blacklist stays off so the choice is not refused twice. Remove one of them.");
         }
         return false;
      }
      return true;
   }

   @Override
   public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
   }

   @Override
   public List<String> getMixins() {
      return null;
   }

   @Override
   public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }

   @Override
   public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }
}
