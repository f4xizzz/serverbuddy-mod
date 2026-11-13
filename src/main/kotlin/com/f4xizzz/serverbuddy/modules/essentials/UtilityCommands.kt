package com.f4xizzz.serverbuddy.modules.essentials

import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.register
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.text
import com.f4xizzz.serverbuddy.modules.essentials.EssentialsCommands.word
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.AbstractSkullBlock
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.phys.HitResult
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.inventory.AnvilMenu
import net.minecraft.world.inventory.CartographyTableMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.CraftingMenu
import net.minecraft.world.inventory.EnchantmentMenu
import net.minecraft.world.inventory.GrindstoneMenu
import net.minecraft.world.inventory.LoomMenu
import net.minecraft.world.inventory.MenuConstructor
import net.minecraft.world.inventory.SmithingMenu
import net.minecraft.world.inventory.StonecutterMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.component.ResolvableProfile

/** Bancadas portáteis, lixeira, /item, /repair, /skull, /rules e /info. */
object UtilityCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>, utilities: Boolean, info: Boolean) {
        if (utilities) registerUtilities(dispatcher)
        if (info) {
            register(dispatcher, "rules") { executes { lines(it, EssentialsModule.config.info.rules) } }
            register(dispatcher, "info") { executes { lines(it, EssentialsModule.config.info.info) } }
        }
    }

    private fun registerUtilities(dispatcher: CommandDispatcher<CommandSourceStack>) {
        bench(dispatcher, "workbench", listOf("wb", "craft"), "container.crafting") { id, inv -> CraftingMenu(id, inv, ContainerLevelAccess.NULL) }
        bench(dispatcher, "anvil", emptyList(), "container.repair") { id, inv -> AnvilMenu(id, inv, ContainerLevelAccess.NULL) }
        bench(dispatcher, "grindstone", emptyList(), "container.grindstone_title") { id, inv -> GrindstoneMenu(id, inv, ContainerLevelAccess.NULL) }
        bench(dispatcher, "stonecutter", emptyList(), "container.stonecutter") { id, inv -> StonecutterMenu(id, inv, ContainerLevelAccess.NULL) }
        bench(dispatcher, "loom", emptyList(), "container.loom") { id, inv -> LoomMenu(id, inv, ContainerLevelAccess.NULL) }
        bench(dispatcher, "smithing", listOf("smithingtable"), "container.upgrade") { id, inv -> SmithingMenu(id, inv, ContainerLevelAccess.NULL) }
        bench(dispatcher, "cartography", listOf("cartographytable"), "container.cartography_table") { id, inv -> CartographyTableMenu(id, inv, ContainerLevelAccess.NULL) }
        bench(dispatcher, "enchanting", listOf("enchantingtable"), "container.enchant") { id, inv -> EnchantmentMenu(id, inv, ContainerLevelAccess.NULL) }

        register(dispatcher, "trash", aliases = listOf("disposal")) { executes { trash(it) } }
        register(dispatcher, "item", level = 2) {
            then(Commands.literal("name").then(text("nome").executes { itemName(it) }))
            then(Commands.literal("lore").then(text("linhas").executes { itemLore(it) }))
            then(Commands.literal("more").executes { itemMore(it) })
            then(Commands.literal("repair").executes { itemRepair(it) })
        }
        register(dispatcher, "repair", level = 2) {
            executes { itemRepair(it) }
            then(Commands.literal("all").executes { repairAll(it) })
        }
        register(dispatcher, "skull", aliases = listOf("head"), level = 2) {
            executes { skull(it, null) }
            then(Commands.literal("copy").requires(EssentialsCommands.perm("skull.copy", 2)).executes { copyHead(it) })
            then(word("jogador").executes { skull(it, StringArgumentType.getString(it, "jogador")) })
        }
    }

    private fun bench(dispatcher: CommandDispatcher<CommandSourceStack>, name: String, aliases: List<String>, titleKey: String, menu: (Int, net.minecraft.world.entity.player.Inventory) -> net.minecraft.world.inventory.AbstractContainerMenu) {
        register(dispatcher, name, aliases) {
            executes { ctx ->
                val player = EssentialsCommands.player(ctx) ?: return@executes 0
                player.openMenu(SimpleMenuProvider(MenuConstructor { id, inventory, _ -> menu(id, inventory) }, Component.translatable(titleKey)))
                1
            }
        }
    }

    private fun trash(ctx: CommandContext<CommandSourceStack>): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        // o que ficar no baú some quando ele fecha (o container não pertence a ninguém)
        player.openMenu(SimpleMenuProvider(MenuConstructor { id, inventory, _ -> ChestMenu.threeRows(id, inventory, SimpleContainer(27)) }, Component.literal("Lixeira")))
        return 1
    }

    private fun heldOrFail(ctx: CommandContext<CommandSourceStack>): Pair<ServerPlayer, ItemStack>? {
        val player = EssentialsCommands.player(ctx) ?: return null
        val held = player.mainHandItem
        if (held.isEmpty) {
            Msg.fail(ctx.source, "item_none")
            return null
        }
        return player to held
    }

    private fun itemName(ctx: CommandContext<CommandSourceStack>): Int {
        val (_, held) = heldOrFail(ctx) ?: return 0
        held.set(DataComponents.CUSTOM_NAME, Texts.parse(StringArgumentType.getString(ctx, "nome")))
        Msg.reply(ctx.source, "item_named")
        return 1
    }

    private fun itemLore(ctx: CommandContext<CommandSourceStack>): Int {
        val (_, held) = heldOrFail(ctx) ?: return 0
        val lines = StringArgumentType.getString(ctx, "linhas").split("|").map { Texts.parse(it.trim()) }
        held.set(DataComponents.LORE, ItemLore(lines))
        Msg.reply(ctx.source, "item_lore")
        return 1
    }

    private fun itemMore(ctx: CommandContext<CommandSourceStack>): Int {
        val (_, held) = heldOrFail(ctx) ?: return 0
        held.count = held.maxStackSize
        Msg.reply(ctx.source, "item_more")
        return 1
    }

    private fun itemRepair(ctx: CommandContext<CommandSourceStack>): Int {
        val (_, held) = heldOrFail(ctx) ?: return 0
        if (!held.isDamaged) {
            Msg.fail(ctx.source, "item_not_damaged")
            return 0
        }
        held.damageValue = 0
        Msg.reply(ctx.source, "item_repaired")
        return 1
    }

    private fun repairAll(ctx: CommandContext<CommandSourceStack>): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        var count = 0
        for (stack in player.inventory.items + player.inventory.armor + player.inventory.offhand) {
            if (!stack.isEmpty && stack.isDamaged) {
                stack.damageValue = 0
                count++
            }
        }
        Msg.reply(ctx.source, "repair_all", mapOf("count" to count))
        return count
    }

    private fun skull(ctx: CommandContext<CommandSourceStack>, name: String?): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        val server = ctx.source.server
        val profile = if (name == null) player.gameProfile else (server.playerList.getPlayerByName(name)?.gameProfile ?: server.profileCache?.get(name)?.orElse(null))
        if (profile == null) {
            Msg.fail(ctx.source, "player_unknown", mapOf("player" to Texts.escape(name ?: "")))
            return 0
        }
        val head = ItemStack(Items.PLAYER_HEAD)
        head.set(DataComponents.PROFILE, ResolvableProfile(profile))
        player.inventory.add(head)
        if (!head.isEmpty) player.drop(head, false)
        Msg.reply(ctx.source, "skull_given", mapOf("player" to profile.name))
        return 1
    }

    // ---------------------------------------------------------------- /skull copy

    /** `/skull copy`: copies the custom head the player is looking at (a placed head, or someone/something wearing one). */
    private fun copyHead(ctx: CommandContext<CommandSourceStack>): Int {
        val player = EssentialsCommands.player(ctx) ?: return 0
        val head = lookedAtHead(player)
        if (head == null) {
            Msg.fail(ctx.source, "skull_copy_none")
            return 0
        }
        player.inventory.add(head)
        if (!head.isEmpty) player.drop(head, false)
        Msg.reply(ctx.source, "skull_copied")
        return 1
    }

    private fun lookedAtHead(player: ServerPlayer): ItemStack? {
        val reach = 8.0
        val level = player.serverLevel()
        val start = player.eyePosition
        val end = start.add(player.lookAngle.scale(reach))
        val blockHit = level.clip(ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        val blockDistance = if (blockHit.type == HitResult.Type.BLOCK) blockHit.location.distanceToSqr(start) else reach * reach
        // an entity wearing a head (armor stand, mob, player) counts when it is closer than the block behind it
        val box = player.boundingBox.expandTowards(player.lookAngle.scale(reach)).inflate(1.0)
        val entityHit = ProjectileUtil.getEntityHitResult(player, start, end, box, { it !== player && !it.isSpectator }, blockDistance)
        (entityHit?.entity as? LivingEntity)?.let { wornHead(it) }?.let { return it }
        if (blockHit.type != HitResult.Type.BLOCK) return null
        val pos = blockHit.blockPos
        val skull = level.getBlockEntity(pos) as? SkullBlockEntity ?: return null
        val head = ItemStack(level.getBlockState(pos).block.asItem())
        // the profile (texture), custom name and note block sound of the placed head
        head.applyComponents(skull.collectComponents())
        return head
    }

    /** The head item on [entity] (any skull, with its texture), or a player's own head if they wear none. */
    private fun wornHead(entity: LivingEntity): ItemStack? {
        val worn = entity.getItemBySlot(EquipmentSlot.HEAD)
        if (!worn.isEmpty && (worn.item as? BlockItem)?.block is AbstractSkullBlock) return worn.copyWithCount(1)
        if (entity is ServerPlayer) {
            val head = ItemStack(Items.PLAYER_HEAD)
            head.set(DataComponents.PROFILE, ResolvableProfile(entity.gameProfile))
            return head
        }
        return null
    }

    private fun lines(ctx: CommandContext<CommandSourceStack>, lines: List<String>): Int {
        for (line in lines) ctx.source.sendSuccess({ Texts.render(line) }, false)
        return lines.size
    }
}
