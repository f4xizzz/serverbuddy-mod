package com.f4xizzz.serverbuddy.crates.utils

import net.fabricmc.loader.api.FabricLoader
import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin
import org.spongepowered.asm.mixin.extensibility.IMixinInfo

class CratesMixinPlugin : IMixinConfigPlugin {
    override fun onLoad(mixinPackage: String?) {}
    override fun getRefMapperConfig(): String? = null

    override fun shouldApplyMixin(targetClassName: String?, mixinClassName: String?): Boolean {
        val holoDisplays = mixinClassName == "com.f4xizzz.serverbuddy.crates.mixins.ViewerHandlerMixin" || mixinClassName == "com.f4xizzz.serverbuddy.crates.mixins.ViewerHandlerAccessor"
        return !(holoDisplays && !FabricLoader.getInstance().isModLoaded("holodisplays"))
    }

    override fun acceptTargets(p0: Set<String?>?, p1: Set<String?>?) {}
    override fun getMixins(): List<String?>? = null
    override fun preApply(p0: String?, p1: ClassNode?, p2: String?, p3: IMixinInfo?) {}
    override fun postApply(p0: String?, p1: ClassNode?, p2: String?, p3: IMixinInfo?) {}
}