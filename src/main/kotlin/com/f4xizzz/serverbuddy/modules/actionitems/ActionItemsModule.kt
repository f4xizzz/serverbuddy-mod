package com.f4xizzz.serverbuddy.modules.actionitems

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.Cooldowns
import com.f4xizzz.serverbuddy.core.ItemSpec
import com.f4xizzz.serverbuddy.core.ModuleCommands
import com.f4xizzz.serverbuddy.core.SbCommands
import com.f4xizzz.serverbuddy.core.SbGson
import com.f4xizzz.serverbuddy.core.SbModule
import com.f4xizzz.serverbuddy.core.SbPermissions
import com.f4xizzz.serverbuddy.core.Texts
import com.f4xizzz.serverbuddy.core.config.Doc
import com.f4xizzz.serverbuddy.core.rewards.Reward
import com.f4xizzz.serverbuddy.core.rewards.RewardService
import com.f4xizzz.serverbuddy.modules.commandblock.CommandBlockService
import com.f4xizzz.serverbuddy.core.Conditions
import com.google.gson.annotations.SerializedName
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import org.slf4j.LoggerFactory
import java.io.File

/** One action item: an item that does things when a player clicks with it. Each one is a file in `ActionItems/items/`. */
class ActionItemDef(
    @Doc("The item players hold (item id, name, lore, glint, custom_model_data...).")
    val item: ItemSpec = ItemSpec("minecraft:paper", name = "<gold><bold>Gift", lore = listOf("<gray>Right-click to open it."), glint = true),
    @Doc("What fires it: \"right_click\" and/or \"left_click\". Left click works on blocks and entities (the server can't see a swing at the air).")
    val triggers: List<String> = listOf("right_click"),
    @SerializedName("cooldown_seconds")
    @Doc("Seconds before the same player can use this item again (0 = none).")
    val cooldownSeconds: Int = 3,
    @Doc("Take one item away every time it works (not in creative).")
    val consume: Boolean = true,
    @Doc("Conditions that must all hold: permission:node, tag:name, dimension:minecraft:the_nether, time:18:00-23:30, day:sat,sun... (! in front negates).")
    val requirements: List<String> = emptyList(),
    @SerializedName("deny_actions")
    @Doc("What happens instead when a requirement is not met.")
    val denyActions: List<Reward> = emptyList(),
    @Doc("What happens: any reward type (command, message, sound, title, item, money, pokemon...). %player% is the player.")
    val actions: List<Reward> = listOf(
        Reward.of("message", "text" to "<green>Enjoy your gift, %player%!"),
        Reward.of("command", "command" to "give %player% minecraft:bread 8"),
        Reward.of("sound", "sound" to "minecraft:entity.player.levelup")
    ),
    @SerializedName("random_actions")
    @Doc("Extra actions of which only some happen: \"random_count\" of them are picked, by their \"weight\".")
    val randomActions: List<Reward> = emptyList(),
    @SerializedName("random_count")
    val randomCount: Int = 1
)

class ActionItemsConfig(
    @Doc("Master switch.") val enabled: Boolean = true,
    val messages: ActionItemsMessages = ActionItemsMessages()
)

class ActionItemsMessages(
    val cooldown: String = "<red>Wait <white>%seconds%s</white> before using that again.",
    @SerializedName("unknown_item") val unknownItem: String = "<red>There is no action item called <white>%id%</white>. See <white>/sb actionitems list</white>.",
    val given: String = "<green>Gave <white>%amount%</white> x <white>%id%</white> to <white>%player%</white>.",
    @SerializedName("list_header") val listHeader: String = "<gold><bold>Action items</bold> <gray>(%count%)",
    @SerializedName("list_empty") val listEmpty: String = "<gray>No action item files in ActionItems/items yet.",
    @SerializedName("only_players") val onlyPlayers: String = "<red>Only players can use this command."
)

/**
 * Action Items (Plus): items that run actions when clicked, defined as JSON files in `config/ServerBuddy/ActionItems/items`.
 * Own implementation of the idea (clickable items with commands, messages, sounds, cooldowns and requirements) on top of ServerBuddy's
 * reward engine, so every reward type works as an action.
 */
class ActionItemsModule : SbModule {
    override val id = "actionitems"
    override val folder = "ActionItems"
    override val legacyFolders = emptyList<String>()
    override val description = "Items that run actions when clicked (commands, messages, sounds, cooldowns, requirements). Files in ActionItems/items."

    override fun init() {
        ActionItemsRuntime.install(folder)
    }

    override fun reload() {
        ActionItemsRuntime.reload(folder)
    }
}

object ActionItemsRuntime {
    private val log = LoggerFactory.getLogger("ServerBuddy/ActionItems")
    private const val TAG = "serverbuddy_action_item"

    @Volatile
    private var config = ActionItemsConfig()
    private var items = LinkedHashMap<String, ActionItemDef>()
    private val warned = HashSet<String>()
    private val lastFired = HashMap<java.util.UUID, Long>()

    fun install(folder: String) {
        load(folder)
        UseItemCallback.EVENT.register(UseItemCallback { player, _, hand -> onUse(player, hand, "right_click") })
        UseBlockCallback.EVENT.register(UseBlockCallback { player, level, hand, _ ->
            if (level.isClientSide) InteractionResult.PASS else onUse(player, hand, "right_click").result
        })
        AttackBlockCallback.EVENT.register(AttackBlockCallback { player, level, hand, _, _ ->
            if (level.isClientSide) InteractionResult.PASS else onUse(player, hand, "left_click").result
        })
        AttackEntityCallback.EVENT.register(AttackEntityCallback { player, level, hand, _, _ ->
            if (level.isClientSide) InteractionResult.PASS else onUse(player, hand, "left_click").result
        })
        SbCommands.onRegister { dispatcher, _ -> ModuleCommands.mount(dispatcher, node().build(), "actionitems") }
    }

    fun reload(folder: String) = load(folder)

    private fun load(folder: String) {
        config = ConfigFiles.load(folder, "config.json", ActionItemsConfig::class.java) { ActionItemsConfig() }
        val dir = File(ConfigFiles.dir(folder), "items")
        if (!dir.exists()) {
            // the first run leaves a documented example to copy
            ConfigFiles.load("$folder/items", "example_gift.json", ActionItemDef::class.java, track = false) { ActionItemDef() }
        }
        val loaded = LinkedHashMap<String, ActionItemDef>()
        for (file in (dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: emptyArray()).sortedBy { it.name }) {
            try {
                val def = file.reader(Charsets.UTF_8).use { SbGson.pretty.fromJson(it, ActionItemDef::class.java) }
                if (def != null) loaded[file.nameWithoutExtension.lowercase()] = def
            } catch (e: Exception) {
                log.error("Could not read the action item file {}: {}", file.name, e.toString())
            }
        }
        items = loaded
        log.info("Action items loaded: {}", loaded.size)
    }

    // ---------------------------------------------------------------- items

    private fun stackFor(id: String, def: ActionItemDef, amount: Int): ItemStack {
        val stack = def.item.build(mapOf("id" to id), amount)
        if (!stack.isEmpty) stack.set(DataComponents.CUSTOM_DATA, CustomData.of(CompoundTag().also { it.putString(TAG, id) }))
        return stack
    }

    private fun idOf(stack: ItemStack): String? =
        if (stack.isEmpty) null else stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.takeIf { it.contains(TAG) }?.getString(TAG)

    // ---------------------------------------------------------------- clicking

    /** Runs the action item in [hand] if it is one and the click [trigger] is one of its triggers. */
    private fun onUse(player: Player, hand: InteractionHand, trigger: String): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        val pass = InteractionResultHolder.pass(stack)
        if (!config.enabled || player !is ServerPlayer) return pass
        val id = idOf(stack) ?: return pass
        val def = items[id.lowercase()] ?: return pass
        if (def.triggers.none { it.equals(trigger, true) }) return pass
        // a click on a block fires both the block and the item callbacks: count it once per tick
        val now = player.server.tickCount.toLong()
        if (lastFired[player.uuid] == now) return InteractionResultHolder.success(stack)
        lastFired[player.uuid] = now

        val vars = mapOf("player" to Texts.escape(player.gameProfile.name), "id" to id)
        val subject = CommandBlockService.subjectOf(player)
        if (!def.requirements.all { Conditions.matches(it, subject) { bad -> warnOnce("$id:$bad", "Action item '$id' has a requirement that does not exist: $bad") } }) {
            RewardService.give(player, def.denyActions, vars, "actionitem:$id")
            return InteractionResultHolder.success(stack)
        }
        val wait = Cooldowns.use("actionitem:$id", player.uuid, def.cooldownSeconds)
        if (wait > 0) {
            Texts.send(player, config.messages.cooldown, vars + mapOf("seconds" to wait))
            return InteractionResultHolder.success(stack)
        }
        RewardService.give(player, def.actions + pickRandom(def), vars, "actionitem:$id")
        if (def.consume && !player.abilities.instabuild) stack.shrink(1)
        return InteractionResultHolder.success(stack)
    }

    /** [ActionItemDef.randomCount] of the random actions, by weight, without repeating one. */
    private fun pickRandom(def: ActionItemDef): List<Reward> {
        if (def.randomActions.isEmpty()) return emptyList()
        val pool = def.randomActions.filter { it.weight > 0 }.toMutableList()
        val picked = ArrayList<Reward>()
        repeat(def.randomCount.coerceIn(1, pool.size.coerceAtLeast(1))) {
            if (pool.isEmpty()) return@repeat
            var roll = kotlin.random.Random.nextInt(pool.sumOf { it.weight })
            val chosen = pool.first { roll -= it.weight; roll < 0 }
            picked.add(chosen)
            pool.remove(chosen)
        }
        return picked
    }

    private fun warnOnce(key: String, text: String) {
        if (warned.add(key)) log.warn(text)
    }

    // ---------------------------------------------------------------- /sb actionitems

    private val ADMIN = SbPermissions.require<CommandSourceStack>("serverbuddy.actionitems.admin", 2)
    private val ids = SuggestionProvider<CommandSourceStack> { _, builder -> SharedSuggestionProvider.suggest(items.keys, builder) }

    private fun node(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("actionitems").requires(ADMIN)
            .then(
                Commands.literal("give").then(
                    Commands.argument("id", StringArgumentType.word()).suggests(ids).executes { give(it, null, 1) }
                        .then(
                            Commands.argument("player", EntityArgument.player()).executes { give(it, EntityArgument.getPlayer(it, "player"), 1) }
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64)).executes { give(it, EntityArgument.getPlayer(it, "player"), IntegerArgumentType.getInteger(it, "amount")) })
                        )
                )
            )
            .then(Commands.literal("list").executes { list(it) })

    private fun give(ctx: CommandContext<CommandSourceStack>, who: ServerPlayer?, amount: Int): Int {
        val id = StringArgumentType.getString(ctx, "id").lowercase()
        val def = items[id]
        if (def == null) {
            Texts.fail(ctx.source, config.messages.unknownItem, mapOf("id" to Texts.escape(id)))
            return 0
        }
        val target = who ?: (ctx.source.entity as? ServerPlayer)
        if (target == null) {
            Texts.fail(ctx.source, config.messages.onlyPlayers)
            return 0
        }
        val stack = stackFor(id, def, amount)
        if (stack.isEmpty) return 0
        if (!target.inventory.add(stack)) target.drop(stack, false)
        Texts.reply(ctx.source, config.messages.given, mapOf("amount" to amount, "id" to id, "player" to Texts.escape(target.gameProfile.name)))
        return 1
    }

    private fun list(ctx: CommandContext<CommandSourceStack>): Int {
        if (items.isEmpty()) {
            Texts.reply(ctx.source, config.messages.listEmpty)
            return 0
        }
        Texts.reply(ctx.source, config.messages.listHeader, mapOf("count" to items.size))
        for ((id, def) in items) Texts.reply(ctx.source, "<gray>- <white>${Texts.escape(id)}</white> <dark_gray>(${def.triggers.joinToString(", ")}; ${def.actions.size + def.randomActions.size} action(s))")
        return items.size
    }
}
