package com.f4xizzz.serverbuddy.mixin.motd;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Desliga o mixin de status se o MiniMOTD estiver instalado (os dois trocam o mesmo ponto do ping e travariam o servidor). */
public class MotdMixinPlugin implements IMixinConfigPlugin {
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
      if (mixinClassName.endsWith("MotdStatusMixin") && FabricLoader.getInstance().isModLoaded("minimotd-fabric")) {
         if (!warned) {
            warned = true;
            LoggerFactory.getLogger("ServerBuddy/Motd").warn("MiniMOTD is installed: ServerBuddy's MOTD stays off so they do not fight. Remove MiniMOTD to use ServerBuddy's.");
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
