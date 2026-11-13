package com.f4xizzz.serverbuddy.modules.portals

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.DimensionArgument
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.NetherPortalBlock
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import java.util.Locale
import java.util.UUID

/** Loads the config and the saved portals, places portal blocks from the Portal Block item, teleports and registers `/sb portal`. */
object PortalsRuntime {
    private const val TAG = "serverbuddy_portal"

    @Volatile
    var config: PortalsConfig = PortalsConfig()
        private set

    @Volatile
    var active = false
        private set

    private val lastTeleport = HashMap<UUID, Long>()
    private val lastWarning = HashMap<UUID, Long>()

    fun install(folder: String) {
        config = load(folder)
        PortalRegistry.load(folder)
        active = true
        UseBlockCallback.EVENT.register(UseBlockCallback { player, level, hand, hit -> placePortal(player, level, hand, hit) })
        // creative players can break a portal block: it leaves its portal (the rest may split in two)
        PlayerBlockBreakEvents.AFTER.register(PlayerBlockBreakEvents.After { level, _, pos, state, _ ->
            if (!level.isClientSide && state.`is`(Blocks.NETHER_PORTAL)) PortalRegistry.removeBlock(dimensionOf(level), pos)
        })
        ServerPlayConnectionEvents.DISCONNECT.register(ServerPlayConnectionEvents.Disconnect { handler, _ ->
            lastTeleport.remove(handler.player.uuid)
            lastWarning.remove(handler.player.uuid)
        })
        SbCommands.onRegister { dispatcher, _ -> register(dispatcher) }
    }

    fun reload(folder: String) {
        config = load(folder)
        PortalRegistry.load(folder)
    }

    private fun load(folder: String): PortalsConfig = ConfigFiles.load(folder, "config.json", PortalsConfig::class.java) { PortalsConfig() }

    fun dimensionOf(level: Level): String = level.dimension().location().toString()

    // ---------------------------------------------------------------- the item and placing it

    fun portalItem(amount: Int): ItemStack {
        val stack = config.item.build(emptyMap(), amount)
        if (!stack.isEmpty) stack.set(DataComponents.CUSTOM_DATA, CustomData.of(CompoundTag().also { it.putBoolean(TAG, true) }))
        return stack
    }

    private fun isPortalItem(stack: ItemStack): Boolean = !stack.isEmpty && stack.get(DataComponents.CUSTOM_DATA)?.contains(TAG) == true

    /** Right-clicking with the Portal Block item puts a portal block where a normal block would go. */
    private fun placePortal(player: Player, level: Level, hand: InteractionHand, hit: BlockHitResult): InteractionResult {
        val stack = player.getItemInHand(hand)
        if (!isPortalItem(stack) || level.isClientSide || !config.enabled) return InteractionResult.PASS
        val sender = player as? ServerPlayer ?: return InteractionResult.PASS
        val clicked = hit.blockPos
        val target = if (level.getBlockState(clicked).canBeReplaced()) clicked else clicked.relative(hit.direction)
        if (!level.isInWorldBounds(target) || !level.getBlockState(target).canBeReplaced()) return InteractionResult.FAIL
        val dimension = dimensionOf(level)
        if (PortalRegistry.sizeIfAdded(dimension, target) > config.maxBlocks) {
            Texts.send(sender, config.messages.tooBig, mapOf("max" to config.maxBlocks))
            return InteractionResult.FAIL
        }
        // the plane faces the player: a player looking along X gets a portal that stretches along Z
        val axis = if (player.direction.axis == Direction.Axis.X) Direction.Axis.Z else Direction.Axis.X
        // registered first, so the block is protected from the very first update
        PortalRegistry.add(dimension, target)
        level.setBlock(target, Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis), 2)
        if (!player.abilities.instabuild) stack.shrink(1)
        level.playSound(null, target, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f)
        return InteractionResult.SUCCESS
    }

    // ---------------------------------------------------------------- touching a portal

    /** [entity] touched a block of [group]: teleport it at once (no waiting, no vanilla portal effect). */
    fun enter(entity: Entity, group: PortalGroup) {
        val config = config
        if (!config.enabled) return
        val player = entity as? ServerPlayer
        if (player == null && !config.entities) return
        val level = entity.level() as? ServerLevel ?: return
        val server = level.server
        val now = server.tickCount.toLong()
        if (now - (lastTeleport[entity.uuid] ?: -1_000_000L) < config.cooldownTicks) return
        if (player != null && !SbPermissions.check(player, config.permission, true)) {
            warn(player, now, config.messages.noPermission)
            return
        }
        val destination = group.destination
        if (destination == null) {
            if (player != null) warn(player, now, config.messages.noDestination)
            return
        }
        val key = ResourceLocation.tryParse(destination.dimension)?.let { ResourceKey.create(Registries.DIMENSION, it) }
        val target = key?.let { server.getLevel(it) } ?: return
        lastTeleport[entity.uuid] = now
        if (lastTeleport.size > 2000) lastTeleport.entries.removeIf { now - it.value > 1200 }
        entity.fallDistance = 0f
        entity.teleportTo(target, destination.x, destination.y, destination.z, emptySet(), destination.yaw, destination.pitch)
        val sound = config.sound.trim()
        if (sound.isNotEmpty()) {
            ResourceLocation.tryParse(sound)?.let { id -> BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null) }?.let {
                target.playSound(null, destination.x, destination.y, destination.z, it, SoundSource.PLAYERS, config.soundVolume, 1.0f)
            }
        }
    }

    private fun warn(player: ServerPlayer, now: Long, text: String) {
        if (now - (lastWarning[player.uuid] ?: -1_000_000L) < 60) return
        lastWarning[player.uuid] = now
        Texts.send(player, text)
    }

    // ---------------------------------------------------------------- /sb portal

    private val ADMIN = SbPermissions.require<CommandSourceStack>("serverbuddy.portal.admin", 2)

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        ModuleCommands.mount(dispatcher, node().build(), "portal")
    }

    private fun node(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("portal").requires(ADMIN)
            .then(
                Commands.literal("give").executes { give(it, null, 1) }
                    .then(
                        Commands.argument("player", EntityArgument.player()).executes { give(it, EntityArgument.getPlayer(it, "player"), 1) }
                            .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64)).executes { give(it, EntityArgument.getPlayer(it, "player"), IntegerArgumentType.getInteger(it, "amount")) })
                    )
            )
            .then(Commands.literal("list").executes { list(it) })
            .then(Commands.literal("remove").executes { remove(it) })
            .then(
                Commands.literal("edit").then(
                    Commands.literal("tp")
                        .then(Commands.literal("here").executes { editHere(it) })
                        .then(
                            Commands.argument("pos", Vec3Argument.vec3()).executes { edit(it, null) }
                                .then(Commands.argument("dimension", DimensionArgument.dimension()).executes { edit(it, DimensionArgument.getDimension(it, "dimension")) })
                        )
                )
            )

    private fun player(ctx: CommandContext<CommandSourceStack>): ServerPlayer? {
        val player = ctx.source.entity as? ServerPlayer
        if (player == null) Texts.fail(ctx.source, config.messages.onlyPlayers)
        return player
    }

    private fun give(ctx: CommandContext<CommandSourceStack>, who: ServerPlayer?, amount: Int): Int {
        val target = who ?: player(ctx) ?: return 0
        val stack = portalItem(amount)
        if (stack.isEmpty) return 0
        if (!target.inventory.add(stack)) target.drop(stack, false)
        Texts.reply(ctx.source, config.messages.given, mapOf("amount" to amount, "player" to Texts.escape(target.gameProfile.name)))
        return 1
    }

    private fun describe(destination: PortalDestination?): String =
        if (destination == null) "<red>not set" else String.format(Locale.ROOT, "<white>%.1f %.1f %.1f</white> <gray>in %s", destination.x, destination.y, destination.z, destination.dimension)

    private fun list(ctx: CommandContext<CommandSourceStack>): Int {
        val groups = PortalRegistry.all()
        if (groups.isEmpty()) {
            Texts.reply(ctx.source, config.messages.listEmpty)
            return 0
        }
        Texts.reply(ctx.source, config.messages.listHeader, mapOf("count" to groups.size))
        for (group in groups) {
            Texts.reply(ctx.source, config.messages.listLine, mapOf("id" to group.id, "dimension" to group.dimension, "blocks" to group.blocks.size, "destination" to describe(group.destination)))
        }
        return groups.size
    }

    /** The portal the player is looking at (or standing in). */
    private fun lookedAt(player: ServerPlayer): PortalGroup? {
        val dimension = dimensionOf(player.level())
        val hit = player.pick(8.0, 1.0f, false)
        if (hit is BlockHitResult && hit.type == HitResult.Type.BLOCK) PortalRegistry.at(dimension, hit.blockPos)?.let { return it }
        val feet: BlockPos = player.blockPosition()
        return PortalRegistry.at(dimension, feet) ?: PortalRegistry.at(dimension, feet.above())
    }

    private fun remove(ctx: CommandContext<CommandSourceStack>): Int {
        val player = player(ctx) ?: return 0
        val group = lookedAt(player)
        if (group == null) {
            Texts.fail(ctx.source, config.messages.noPortal)
            return 0
        }
        val level = player.serverLevel()
        PortalRegistry.remove(group)
        for (block in group.blocks) {
            val pos = BlockPos(block[0], block[1], block[2])
            if (level.getBlockState(pos).`is`(Blocks.NETHER_PORTAL)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2)
        }
        Texts.reply(ctx.source, config.messages.removed, mapOf("id" to group.id, "blocks" to group.blocks.size))
        return 1
    }

    private fun setDestination(ctx: CommandContext<CommandSourceStack>, destination: PortalDestination): Int {
        val player = player(ctx) ?: return 0
        val group = lookedAt(player)
        if (group == null) {
            Texts.fail(ctx.source, config.messages.noPortal)
            return 0
        }
        PortalRegistry.setDestination(group, destination)
        Texts.reply(
            ctx.source, config.messages.destinationSet,
            mapOf("id" to group.id, "x" to String.format(Locale.ROOT, "%.1f", destination.x), "y" to String.format(Locale.ROOT, "%.1f", destination.y), "z" to String.format(Locale.ROOT, "%.1f", destination.z), "dimension" to destination.dimension)
        )
        return 1
    }

    private fun edit(ctx: CommandContext<CommandSourceStack>, level: ServerLevel?): Int {
        val player = player(ctx) ?: return 0
        val position = Vec3Argument.getVec3(ctx, "pos")
        val dimension = dimensionOf(level ?: player.serverLevel())
        return setDestination(ctx, PortalDestination(dimension, position.x, position.y, position.z, player.yRot, player.xRot))
    }

    private fun editHere(ctx: CommandContext<CommandSourceStack>): Int {
        val player = player(ctx) ?: return 0
        return setDestination(ctx, PortalDestination(dimensionOf(player.serverLevel()), player.x, player.y, player.z, player.yRot, player.xRot))
    }
}

/** What the NetherPortalBlock mixin calls (Java-friendly). */
object PortalHooks {
    /** Is the block at [pos] one of our portal blocks? (They must not break without a frame or spawn piglins.) */
    @JvmStatic
    fun isPortal(level: LevelAccessor, pos: BlockPos): Boolean {
        if (!PortalsRuntime.active) return false
        val real = level as? Level ?: return false
        return PortalRegistry.at(PortalsRuntime.dimensionOf(real), pos) != null
    }

    /** An entity is inside the portal block at [pos]. True = it is ours and was handled: skip the vanilla portal. */
    @JvmStatic
    fun touch(level: Level, pos: BlockPos, entity: Entity): Boolean {
        if (!PortalsRuntime.active || level.isClientSide) return false
        val group = PortalRegistry.at(PortalsRuntime.dimensionOf(level), pos) ?: return false
        PortalsRuntime.enter(entity, group)
        return true
    }
}
