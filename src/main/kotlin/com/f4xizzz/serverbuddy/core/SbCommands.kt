package com.f4xizzz.serverbuddy.core

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.Event
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.util.IdentityHashMap

/**
 * Command registration for the modules.
 *
 * - The name comes from [ModuleSettings.commandName]: the admin can rename a command and, when another installed mod has the
 *   same command, the alternative name (`sb` + name) takes its place (see `modules.conf`).
 * - Registration runs **after** the other mods, so ServerBuddy can remove a duplicated command when the admin picks `SERVERBUDDY`.
 * - Every command registered here is remembered with the module that owns it. That is what `/sb modules commands` lists, and what
 *   lets the admin switch ONE command off (`disabled_commands` in `modules.conf`) while the rest of the module keeps working: a
 *   switched-off command is never added to the command tree (so no tab completion, no permission node in use, no alias).
 */
object SbCommands {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy")
    private val LATE: ResourceLocation = ResourceLocation.fromNamespaceAndPath("serverbuddy", "late")

    /** One root command a module registers. */
    class Entry(val module: String, val defaultName: String, val name: String, val disabled: Boolean) {
        /** The aliases that go with it (`/bp` for `/battlepass`). */
        val aliases = ArrayList<String>()
    }

    private val entries = LinkedHashMap<String, Entry>()
    private val registered = IdentityHashMap<LiteralCommandNode<CommandSourceStack>, Entry>()

    /** The module that is initializing right now ([Modules.initAll] sets it), so [onRegister] knows who is asking. */
    @Volatile
    @JvmField
    var initializing: String = "core"

    /** The module whose command registration is running right now. */
    @Volatile
    private var owner: String = "core"

    /** Calls [callback] when the commands are registered, after the other mods. */
    @JvmStatic
    fun onRegister(callback: (CommandDispatcher<CommandSourceStack>, CommandBuildContext) -> Unit) {
        val module = initializing
        CommandRegistrationCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, LATE)
        CommandRegistrationCallback.EVENT.register(LATE) { dispatcher, registryAccess, _ ->
            owned(module) { callback(dispatcher, registryAccess) }
        }
    }

    /** Runs [block] with the commands registered inside it counted as [module]'s (for features that are not modules of their own). */
    @JvmStatic
    fun <T> owned(module: String, block: () -> T): T {
        val previous = owner
        owner = module
        try {
            return block()
        } finally {
            owner = previous
        }
    }

    /**
     * Registers the root command whose default name is [defaultName]. [build] gets the name that really applies (it may be the
     * alternative one) and returns the finished command. If the admin switched the command off, it is not registered: the node is
     * built and kept apart, only so its aliases know they have to stay out too.
     */
    @JvmStatic
    fun register(
        dispatcher: CommandDispatcher<CommandSourceStack>,
        defaultName: String,
        build: (String) -> LiteralArgumentBuilder<CommandSourceStack>
    ): LiteralCommandNode<CommandSourceStack> {
        val name = ModuleSettings.commandName(defaultName)
        val disabled = ModuleSettings.commandDisabled(defaultName)
        val entry = Entry(owner, defaultName, name, disabled)
        entries["$owner/$defaultName"] = entry
        if (disabled) {
            LOGGER.info("The command /{} is switched off (disabled_commands in modules.conf).", name)
            return build(name).build().also { registered[it] = entry }
        }
        if (ModuleSettings.conflictsToRemove(defaultName) && removeRoot(dispatcher, defaultName)) {
            LOGGER.info("The /{} command of another mod was replaced by ServerBuddy's (compat.* = SERVERBUDDY in modules.conf).", defaultName)
        }
        if (name != defaultName) LOGGER.info("The ServerBuddy command /{} will be called /{} (modules.conf).", defaultName, name)
        return dispatcher.register(build(name)).also { registered[it] = entry }
    }

    /**
     * Registers [alias] as a nickname for [target] (say `/return` for `/back`). Brigadier's `redirect` alone carries the arguments of
     * the command but NOT its action: a nickname for a command without arguments would answer "unknown command". So the nickname
     * copies the `executes` and the permission of the target as well.
     *
     * If a command called [alias] already exists (Minecraft's own, or another mod's) the nickname is NOT registered: Brigadier
     * would merge both nodes and ServerBuddy would be changing other people's command. Returns null in that case (and says so in
     * the log), and also when the command it points at is switched off.
     */
    @JvmStatic
    fun alias(
        dispatcher: CommandDispatcher<CommandSourceStack>,
        alias: String,
        target: LiteralCommandNode<CommandSourceStack>,
        requires: java.util.function.Predicate<CommandSourceStack> = target.requirement
    ): LiteralCommandNode<CommandSourceStack>? {
        val entry = registered[target]
        if (entry != null && entry.disabled) {
            entry.aliases.add(alias)
            return null
        }
        if (dispatcher.root.getChild(alias) != null) {
            LOGGER.warn("The alias /{} (of /{}) was not registered: a command with that name already exists (Minecraft's or another mod's).", alias, target.name)
            return null
        }
        entry?.aliases?.add(alias)
        return dispatcher.register(LiteralArgumentBuilder.literal<CommandSourceStack>(alias).requires(requires).executes(target.command).redirect(target))
    }

    /** Removes the root command [name] from the tree (Brigadier has no method for it, so this edits its internal maps). */
    @JvmStatic
    fun removeRoot(dispatcher: CommandDispatcher<CommandSourceStack>, name: String): Boolean {
        val root = dispatcher.root
        val removed = root.children.removeIf { it.name == name }
        try {
            val field = CommandNode::class.java.getDeclaredField("literals")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (field.get(root) as MutableMap<String, *>).remove(name)
        } catch (e: ReflectiveOperationException) {
            LOGGER.warn("Could not remove the command /{} from Brigadier completely: {}", name, e.toString())
        }
        return removed
    }

    /** Every command registered through here, by module (in the order the modules came up). */
    @JvmStatic
    fun catalog(): List<Entry> = entries.values.toList()

    /** The command registered under the default name [defaultName] (or its current name), if any. */
    @JvmStatic
    fun find(defaultName: String): Entry? {
        val wanted = defaultName.trim().removePrefix("/")
        return entries.values.firstOrNull { it.defaultName.equals(wanted, true) || it.name.equals(wanted, true) }
    }

    /**
     * Takes [entry] out of the live command tree (root and aliases) and sends the new tree to the players, so a command the admin
     * switches off stops working right away. Bringing it back needs a restart (the node is gone from the tree).
     */
    @JvmStatic
    fun removeLive(server: MinecraftServer, entry: Entry) {
        val dispatcher = server.commands.dispatcher
        removeRoot(dispatcher, entry.name)
        for (alias in entry.aliases) removeRoot(dispatcher, alias)
        for (player in server.playerList.players) server.commands.sendCommands(player)
    }
}
