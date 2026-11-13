package com.f4xizzz.serverbuddy.core

import com.f4xizzz.serverbuddy.core.config.Doc
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import net.fabricmc.loader.api.FabricLoader

/**
 * Liga e desliga os módulos do ServerBuddy e resolve os nomes dos comandos (inclusive quando outro mod usa o mesmo
 * nome). Arquivo: `config/ServerBuddy/modules.conf`.
 */
@Doc(
    "ServerBuddy · modules and coexistence with other mods\n" +
        "\n" +
        "This file is the main panel: here you turn each module on/off and choose who keeps the name of a\n" +
        "command when another mod has the same one. After changing, use /serverbuddy reload (the on/off switch of modules only\n" +
        "takes real effect after restarting the server).\n" +
        "Tip: /serverbuddy modules shows the state of each module and /serverbuddy config check warns if any file\n" +
        "of config is missing new options."
)
class ModulesConfig(
    @Doc("Turns each module on (true) or off (false). A disabled module registers no commands and creates no config folder.")
    val modules: ModuleToggles = ModuleToggles(),
    @SerializedName("command_names")
    @Doc(
        "Renames a command. The key is the default name and the value is the new name (without the slash).\n" +
            "Example: { \"storage\": \"bau\" } makes /storage become /bau. Names that can be changed:\n" +
            "storage, itemshout, pokeshout, pokeshoutall, pokesee, compsee, poketoken, pokeodds, listmotd, tags,\n" +
            "missions, battlepass, trivia, blacklist, claimaccess, commandblock, chatguard. The essentials commands (home, warp, tpa, msg...) can also\n" +
            "be renamed here using the same default name."
    )
    val commandNames: Map<String, String> = emptyMap(),
    @SerializedName("disabled_commands")
    @Doc(
        "Switches commands off ONE BY ONE while the module that owns them keeps working. Use the default command names (no slash),\n" +
            "for example [\"pokeodds\", \"home\"]. A switched-off command is never registered: it is gone from the command list and from\n" +
            "tab completion, and its aliases go with it. /sb modules commands lists every command by module; /sb modules command <name> off|on\n" +
            "edits this list for you (off works at once, on needs a restart)."
    )
    val disabledCommands: List<String> = emptyList(),
    @Doc("What to do when another installed mod has a command with the same name as one of ours.")
    val compat: CompatConfig = CompatConfig()
)

/** Um campo para cada módulo (assim cada um ganha o seu comentário no arquivo). */
class ModuleToggles(
    @Doc("Scheduled entity cleanup (from SkiesClear).") val clear: Boolean = true,
    @Doc("Automatic chat announcements (came from SkiesAnnouncements).") val announcements: Boolean = true,
    @Doc("Kits (from SkiesKits).") val kits: Boolean = true,
    @Doc("Shop (from SkiesShop).") val shop: Boolean = true,
    @Doc("Crates and keys (from SkiesCrates).") val crates: Boolean = true,
    @Doc("Chest menus made by you (came from SkiesGUIs).") val guis: Boolean = true,
    @Doc("/storage: each player's reward chest.") val storage: Boolean = true,
    @Doc("Chat shouts: /itemshout and, with Cobblemon, /pokeshout.") val shout: Boolean = true,
    @Doc("Server list message (MOTD) with profiles per situation, such as the whitelist.") val motd: Boolean = true,
    @Doc("Chat tags with a chest menu (and GreatCosmetics integration).") val tags: Boolean = true,
    @Doc("Command blocking by tag, permission, dimension or time (such as stopping /back for players with a tag).") val commandblock: Boolean = true,
    @Doc("Chat manager: blocks repeated messages, gives warnings and applies escalating chat bans.") val chatguard: Boolean = true,
    @Doc("Banned moves in Cobblemon battles, by context.") val blacklist: Boolean = true,
    @Doc("Trivia questions in chat.") val trivia: Boolean = true,
    @Doc("Missions (plain Minecraft and, with Cobblemon, extra rewards).") val missions: Boolean = true,
    @Doc("Battle pass with seasons and free/premium tiers.") val battlepass: Boolean = true,
    @Doc("Essentials commands (homes, warps, tpa, chat, moderation...). Each group is toggled in its own file.") val essentials: Boolean = true,
    @Doc("Flan: choose by permission or by tag who can enter a claim (two buttons in the claim menu). Needs Flan.") val flan: Boolean = true,
    @Doc("Pokémon tokens (/poketoken: Shiny, Gender and Shiny Ball). Requires Cobblemon.") val poketokens: Boolean = true,
    @Doc("/pokesee and /compsee: view a player's party and PC. Needs Cobblemon.") val pokeview: Boolean = true,
    @Doc("/pokeodds: view and change the Shiny rate. Requires Cobblemon.") val pokeodds: Boolean = true,
    @Doc("Dimensions (Pro): creates, visits and deletes extra worlds while the server is running (/sb world create|delete|list|tp|spawn|setspawn).") val worlds: Boolean = true,
    @Doc("Action Items (Plus): items that run actions when clicked (commands, messages, sounds, cooldown, requirements).") val actionitems: Boolean = true,
    @Doc("Portals (Plus): instant portals made of portal blocks, with no frame (/sb portal give, /sb portal edit tp).") val portals: Boolean = true,
    @Doc("Astral Trader (Pro): trades special and shiny tokens for legendary, paradox, Ultra Beast and shiny Pokémon (/astral). Needs Cobblemon.") val astral: Boolean = true
)

enum class ConflictPolicy {
    /** Se o outro mod está instalado, o ServerBuddy usa o nome alternativo (`sb` + nome). Se não está, usa o nome normal. */
    AUTO,

    /** O ServerBuddy fica com o nome e tira o comando do outro mod da árvore de comandos. */
    SERVERBUDDY,

    /** O outro mod fica com o nome; o ServerBuddy sempre usa o nome alternativo. */
    OUTRO
}

class CompatConfig(
    @Doc(
        "CobbleUtils (zonary123's library): also has a /storage.\n" +
            "AUTO = with CobbleUtils installed, ours becomes /sbstorage; SERVERBUDDY = ours keeps /storage;\n" +
            "OUTRO (other) = /storage belongs to CobbleUtils and ours is always /sbstorage."
    )
    val cobbleutils: ConflictPolicy = ConflictPolicy.AUTO,
    @Doc(
        "CobblemonExtras: has /pokesee, /compsee, /pokeshout, /itemshout and /pokeodds. Same options as CobbleUtils."
    )
    val cobblemonextras: ConflictPolicy = ConflictPolicy.AUTO
)

object ModuleSettings {
    @Volatile
    var config: ModulesConfig = ModulesConfig()
        private set

    fun load() {
        config = ConfigFiles.load("", "modules.conf", ModulesConfig::class.java) { ModulesConfig() }
    }

    /** Só o `enabled` do módulo [id]; módulo que o arquivo não conhece (versão antiga do arquivo) fica ligado. */
    fun enabled(id: String): Boolean = try {
        ModuleToggles::class.java.getDeclaredField(id).also { it.isAccessible = true }.getBoolean(config.modules)
    } catch (e: NoSuchFieldException) {
        true
    }

    /** Is the command with this default name switched off in `disabled_commands`? */
    @JvmStatic
    fun commandDisabled(defaultName: String): Boolean =
        config.disabledCommands.any { it.trim().removePrefix("/").equals(defaultName, ignoreCase = true) }

    /** Is [id] the id of something that has an on/off switch in `modules.conf`? */
    fun isToggle(id: String): Boolean = ModuleToggles::class.java.declaredFields.any { it.name == id }

    /** Turns a module (or a Cobblemon feature) on or off in `modules.conf`; it takes effect when the server restarts. */
    fun setModuleEnabled(id: String, enabled: Boolean): Boolean {
        if (!isToggle(id)) return false
        val written = ConfigFiles.edit("", "modules.conf") { tree ->
            val modules = tree.getAsJsonObject("modules") ?: JsonObject().also { tree.add("modules", it) }
            modules.addProperty(id, enabled)
        }
        if (written) load()
        return written
    }

    /** Adds or removes [defaultName] in `disabled_commands`. */
    fun setCommandDisabled(defaultName: String, disabled: Boolean): Boolean {
        val name = defaultName.trim().removePrefix("/").lowercase()
        val written = ConfigFiles.edit("", "modules.conf") { tree ->
            val current = tree.getAsJsonArray("disabled_commands")?.map { it.asString } ?: emptyList()
            val updated = if (disabled) (current + name).distinctBy { it.lowercase() } else current.filter { !it.equals(name, ignoreCase = true) }
            tree.add("disabled_commands", JsonArray().also { array -> updated.forEach { array.add(it) } })
        }
        if (written) load()
        return written
    }

    /** Mod que tem comando de mesmo nome que um nosso (id do mod no Fabric) e a política escolhida para ele. */
    private val KNOWN_CONFLICTS: Map<String, List<String>> = mapOf(
        "storage" to listOf("cobbleutils"),
        "pokesee" to listOf("cobblemonextras"),
        "compsee" to listOf("cobblemonextras"),
        "pokeshout" to listOf("cobblemonextras"),
        "itemshout" to listOf("cobblemonextras"),
        "pokeodds" to listOf("cobblemonextras")
    )

    private fun policy(modId: String): ConflictPolicy? = when (modId) {
        "cobbleutils" -> config.compat.cobbleutils
        "cobblemonextras" -> config.compat.cobblemonextras
        else -> null
    }

    /** O nome com que o comando [default] é registrado: troca da config, ou o nome alternativo quando há conflito. */
    @JvmStatic
    fun commandName(default: String): String {
        config.commandNames[default]?.trim()?.removePrefix("/")?.takeIf { it.isNotEmpty() }?.let { return it }
        for (modId in KNOWN_CONFLICTS[default].orEmpty()) {
            val chosen = policy(modId) ?: continue
            val installed = FabricLoader.getInstance().isModLoaded(modId)
            if (chosen == ConflictPolicy.OUTRO || (chosen == ConflictPolicy.AUTO && installed)) return "sb$default"
        }
        return default
    }

    /** Mods cujo comando de mesmo nome o ServerBuddy deve tirar da árvore (política SERVERBUDDY e mod instalado). */
    @JvmStatic
    fun conflictsToRemove(default: String): Boolean {
        if (commandName(default) != default) return false
        return KNOWN_CONFLICTS[default].orEmpty().any {
            policy(it) == ConflictPolicy.SERVERBUDDY && FabricLoader.getInstance().isModLoaded(it)
        }
    }
}
