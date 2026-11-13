package com.f4xizzz.serverbuddy.modules.worlds

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbGson
import com.f4xizzz.serverbuddy.core.SbModule
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.google.gson.annotations.SerializedName
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.dimension.BuiltinDimensionTypes
import net.minecraft.world.level.levelgen.FlatLevelSource
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings
import org.slf4j.LoggerFactory
import xyz.nucleoid.fantasy.Fantasy
import xyz.nucleoid.fantasy.RuntimeWorldConfig
import xyz.nucleoid.fantasy.RuntimeWorldHandle
import java.io.File
import java.util.Optional

/** Where players land in a world (saved with the world; `/sb world setspawn` changes it). */
class WorldSpawn(val x: Double, val y: Double, val z: Double, val yaw: Float = 0f, val pitch: Float = 0f)

/** One world this module made. [type] is overworld, nether, end, void or flat. */
class WorldEntry(
    val name: String,
    val type: String = "overworld",
    val seed: Long = 0,
    var spawn: WorldSpawn? = null
)

private class WorldsFile(@SerializedName("worlds") val worlds: MutableList<WorldEntry> = ArrayList())

/**
 * Dimensions (Pro): create, visit and delete extra worlds while the server runs, in the style of the MultiWorld mod (`/sb world create`,
 * `delete`, `list`, `tp`, `spawn`, `setspawn`). The worlds are real dimensions made with Nucleoid's Fantasy (saved with the world, in
 * `dimensions/serverbuddy/<name>`) and are opened again every time the server starts. They also work as destinations for the Portals
 * module (`/sb portal edit tp <x y z> serverbuddy:<name>`).
 */
class WorldsModule : SbModule {
    override val id = "worlds"
    override val folder = "Worlds"
    override val legacyFolders = emptyList<String>()
    override val description = "Extra worlds made at run time: /sb world create|delete|list|tp|spawn|setspawn (overworld, nether, end, void, flat)."

    override fun init() {
        WorldsRuntime.install(folder)
    }

    override fun reload() {
        // the list of worlds is only read at startup; nothing to reload
    }
}

object WorldsRuntime {
    private val log = LoggerFactory.getLogger("ServerBuddy/Worlds")
    private const val NAMESPACE = "serverbuddy"
    private val NAME = Regex("[a-z0-9_]{1,16}")
    val TYPES = listOf("overworld", "nether", "end", "void", "flat")

    private val entries = LinkedHashMap<String, WorldEntry>()
    private val handles = HashMap<String, RuntimeWorldHandle>()
    private var file: File? = null

    fun install(folder: String) {
        file = File(ConfigFiles.dir(folder), "worlds.json")
        entries.clear()
        file?.takeIf { it.exists() }?.let { target ->
            try {
                target.reader(Charsets.UTF_8).use { SbGson.pretty.fromJson(it, WorldsFile::class.java) }?.worlds?.forEach { entries[it.name] = it }
            } catch (e: Exception) {
                log.error("Could not read worlds.json: the extra worlds stay closed until it is fixed", e)
            }
        }
        ServerLifecycleEvents.SERVER_STARTED.register { server -> openAll(server) }
        SbCommands.onRegister { dispatcher, _ -> ModuleCommands.mount(dispatcher, node().build(), "world") }
    }

    private fun save() {
        try {
            file?.parentFile?.mkdirs()
            file?.writeText(SbGson.pretty.toJson(WorldsFile(ArrayList(entries.values))), Charsets.UTF_8)
        } catch (e: Exception) {
            log.error("Could not save worlds.json", e)
        }
    }

    private fun id(name: String) = ResourceLocation.fromNamespaceAndPath(NAMESPACE, name)

    // ---------------------------------------------------------------- opening worlds

    private fun openAll(server: MinecraftServer) {
        for (entry in entries.values) {
            try {
                open(server, entry)
            } catch (e: Exception) {
                log.error("Could not open the world '{}'", entry.name, e)
            }
        }
        if (entries.isNotEmpty()) log.info("Extra worlds opened: {}", entries.keys.joinToString(", "))
    }

    private fun generator(server: MinecraftServer, type: String): ChunkGenerator? = when (type) {
        "nether" -> server.getLevel(Level.NETHER)?.chunkSource?.generator
        "end" -> server.getLevel(Level.END)?.chunkSource?.generator
        "void", "flat" -> {
            val registries = server.registryAccess()
            val biomes = registries.lookupOrThrow(Registries.BIOME)
            val settings = if (type == "void") {
                FlatLevelGeneratorSettings(Optional.empty(), biomes.getOrThrow(Biomes.THE_VOID), emptyList())
            } else {
                FlatLevelGeneratorSettings.getDefault(biomes, registries.lookupOrThrow(Registries.STRUCTURE_SET), registries.lookupOrThrow(Registries.PLACED_FEATURE))
            }
            FlatLevelSource(settings)
        }
        else -> server.overworld().chunkSource.generator
    }

    private fun dimensionType(type: String) = when (type) {
        "nether" -> BuiltinDimensionTypes.NETHER
        "end" -> BuiltinDimensionTypes.END
        else -> BuiltinDimensionTypes.OVERWORLD
    }

    private fun open(server: MinecraftServer, entry: WorldEntry): ServerLevel? {
        val generator = generator(server, entry.type) ?: run {
            log.error("The '{}' world needs the {} dimension, which this server does not have.", entry.name, entry.type)
            return null
        }
        val config = RuntimeWorldConfig()
            .setDimensionType(dimensionType(entry.type))
            .setGenerator(generator)
            .setSeed(entry.seed)
            .setShouldTickTime(true)
            .setMirrorOverworldGameRules(true)
            .setMirrorOverworldDifficulty(true)
        val handle = Fantasy.get(server).getOrOpenPersistentWorld(id(entry.name), config)
        handles[entry.name] = handle
        return handle.asWorld()
    }

    private fun levelOf(server: MinecraftServer, name: String): ServerLevel? = server.getLevel(ResourceKey.create(Registries.DIMENSION, id(name)))

    /** A safe place to arrive in a freshly made world. */
    private fun defaultSpawn(level: ServerLevel, entry: WorldEntry): WorldSpawn {
        if (entry.type == "void") {
            for (dx in -1..1) for (dz in -1..1) level.setBlock(BlockPos(dx, 64, dz), Blocks.STONE.defaultBlockState(), 3)
            return WorldSpawn(0.5, 65.0, 0.5)
        }
        level.getChunk(0, 0)
        if (entry.type == "nether") {
            for (y in 32..118) {
                val feet = BlockPos(8, y, 8)
                if (level.getBlockState(feet.below()).isSolid && level.getBlockState(feet).isAir && level.getBlockState(feet.above()).isAir) return WorldSpawn(8.5, y.toDouble(), 8.5)
            }
            level.setBlock(BlockPos(8, 70, 8), Blocks.NETHERRACK.defaultBlockState(), 3)
            return WorldSpawn(8.5, 71.0, 8.5)
        }
        if (entry.type == "end") {
            for (dx in -2..2) for (dz in -2..2) level.setBlock(BlockPos(100 + dx, 48, dz), Blocks.OBSIDIAN.defaultBlockState(), 3)
            return WorldSpawn(100.5, 49.0, 0.5)
        }
        val top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos(0, 0, 0))
        return WorldSpawn(0.5, top.y.toDouble().coerceAtLeast(level.minBuildHeight + 2.0), 0.5)
    }

    // ---------------------------------------------------------------- commands

    private val ADMIN = SbPermissions.require<CommandSourceStack>("serverbuddy.world.admin", 2)
    private val types = SuggestionProvider<CommandSourceStack> { _, builder -> SharedSuggestionProvider.suggest(TYPES, builder) }
    private val names = SuggestionProvider<CommandSourceStack> { _, builder -> SharedSuggestionProvider.suggest(entries.keys, builder) }

    private fun node(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("world").requires(ADMIN)
            .then(
                Commands.literal("create").then(
                    Commands.argument("name", StringArgumentType.word()).executes { create(it, "overworld", null) }
                        .then(
                            Commands.argument("type", StringArgumentType.word()).suggests(types).executes { create(it, StringArgumentType.getString(it, "type"), null) }
                                .then(Commands.argument("seed", LongArgumentType.longArg()).executes { create(it, StringArgumentType.getString(it, "type"), LongArgumentType.getLong(it, "seed")) })
                        )
                )
            )
            .then(Commands.literal("delete").then(Commands.argument("name", StringArgumentType.word()).suggests(names).executes { delete(it) }))
            .then(Commands.literal("list").executes { list(it) })
            .then(
                Commands.literal("tp").then(
                    Commands.argument("name", StringArgumentType.word()).suggests(names).executes { tp(it, null) }
                        .then(Commands.argument("player", EntityArgument.player()).executes { tp(it, EntityArgument.getPlayer(it, "player")) })
                )
            )
            .then(Commands.literal("spawn").executes { spawn(it) })
            .then(Commands.literal("setspawn").executes { setSpawn(it) })

    private fun create(ctx: CommandContext<CommandSourceStack>, rawType: String, seed: Long?): Int {
        val source = ctx.source
        val name = StringArgumentType.getString(ctx, "name").lowercase()
        val type = rawType.lowercase()
        if (!NAME.matches(name)) {
            Texts.fail(source, "<red>A world name has 1 to 16 letters, digits or underscores (lowercase).")
            return 0
        }
        if (type !in TYPES) {
            Texts.fail(source, "<red>Unknown type <white>${Texts.escape(type)}</white>. Use: ${TYPES.joinToString(", ")}.")
            return 0
        }
        if (name in entries || levelOf(source.server, name) != null) {
            Texts.fail(source, "<red>There is already a world called <white>${Texts.escape(name)}</white>.")
            return 0
        }
        val entry = WorldEntry(name, type, seed ?: kotlin.random.Random.nextLong())
        val level = try {
            open(source.server, entry)
        } catch (e: Exception) {
            log.error("Could not create the world '{}'", name, e)
            null
        }
        if (level == null) {
            Texts.fail(source, "<red>Could not create the world (see the console).")
            return 0
        }
        entry.spawn = defaultSpawn(level, entry)
        entries[name] = entry
        save()
        Texts.reply(source, "<green>World <white>$name</white> created (<white>$type</white>). <gray>Go there with <white>/sb world tp $name</white>.", broadcast = true)
        return 1
    }

    private fun delete(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val name = StringArgumentType.getString(ctx, "name").lowercase()
        val entry = entries[name]
        if (entry == null) {
            Texts.fail(source, "<red>There is no world called <white>${Texts.escape(name)}</white> (see /sb world list).")
            return 0
        }
        val server = source.server
        val level = levelOf(server, name)
        if (level != null) {
            // nobody may be inside when it goes: they are sent to the main spawn
            val home = server.overworld()
            for (player in ArrayList(level.players())) {
                val spawn = home.sharedSpawnPos
                player.teleportTo(home, spawn.x + 0.5, spawn.y.toDouble(), spawn.z + 0.5, player.yRot, player.xRot)
            }
        }
        val handle = handles.remove(name)
        if (handle != null) handle.delete()
        entries.remove(name)
        save()
        Texts.reply(source, "<green>World <white>$name</white> deleted.", broadcast = true)
        return 1
    }

    private fun list(ctx: CommandContext<CommandSourceStack>): Int {
        if (entries.isEmpty()) {
            Texts.reply(ctx.source, "<gray>No extra worlds yet. Make one with <white>/sb world create <name> [type] [seed]</white>.")
            return 0
        }
        Texts.reply(ctx.source, "<gold><bold>Worlds</bold> <gray>(${entries.size})")
        for (entry in entries.values) {
            val level = levelOf(ctx.source.server, entry.name)
            val state = if (level == null) "<red>closed" else "<green>${level.players().size} player(s)"
            Texts.reply(ctx.source, "<gray>- <white>${Texts.escape(entry.name)}</white> <dark_gray>(${entry.type}, seed ${entry.seed})</dark_gray> $state")
        }
        return entries.size
    }

    private fun arrive(player: ServerPlayer, level: ServerLevel, spawn: WorldSpawn) {
        player.teleportTo(level, spawn.x, spawn.y, spawn.z, spawn.yaw, spawn.pitch)
    }

    private fun tp(ctx: CommandContext<CommandSourceStack>, who: ServerPlayer?): Int {
        val source = ctx.source
        val name = StringArgumentType.getString(ctx, "name").lowercase()
        val target = who ?: (source.entity as? ServerPlayer)
        if (target == null) {
            Texts.fail(source, "<red>Only players can use this command.")
            return 0
        }
        val entry = entries[name]
        val level = levelOf(source.server, name)
        if (entry == null || level == null) {
            Texts.fail(source, "<red>There is no open world called <white>${Texts.escape(name)}</white> (see /sb world list).")
            return 0
        }
        arrive(target, level, entry.spawn ?: defaultSpawn(level, entry).also { entry.spawn = it; save() })
        Texts.reply(source, "<green>Sent <white>${Texts.escape(target.gameProfile.name)}</white> to <white>$name</white>.")
        return 1
    }

    /** The entry of the world the player is standing in (null in the vanilla dimensions). */
    private fun entryOf(player: ServerPlayer): WorldEntry? {
        val location = player.level().dimension().location()
        return if (location.namespace == NAMESPACE) entries[location.path] else null
    }

    private fun spawn(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.entity as? ServerPlayer
        if (player == null) {
            Texts.fail(ctx.source, "<red>Only players can use this command.")
            return 0
        }
        val entry = entryOf(player)
        if (entry == null) {
            val home = ctx.source.server.overworld()
            val spawn = home.sharedSpawnPos
            player.teleportTo(home, spawn.x + 0.5, spawn.y.toDouble(), spawn.z + 0.5, player.yRot, player.xRot)
            return 1
        }
        arrive(player, player.serverLevel(), entry.spawn ?: defaultSpawn(player.serverLevel(), entry))
        return 1
    }

    private fun setSpawn(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.entity as? ServerPlayer
        if (player == null) {
            Texts.fail(ctx.source, "<red>Only players can use this command.")
            return 0
        }
        val entry = entryOf(player)
        if (entry == null) {
            Texts.fail(ctx.source, "<red>You are not in one of the extra worlds. Use the vanilla /setworldspawn here.")
            return 0
        }
        entry.spawn = WorldSpawn(player.x, player.y, player.z, player.yRot, player.xRot)
        save()
        Texts.reply(ctx.source, "<green>The spawn of <white>${Texts.escape(entry.name)}</white> is now where you stand.")
        return 1
    }
}
