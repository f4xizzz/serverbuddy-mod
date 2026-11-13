package com.f4xizzz.serverbuddy.modules.craftblock

import com.f4xizzz.serverbuddy.core.config.Doc
import com.google.gson.annotations.SerializedName

/** `config/ServerBuddy/CraftBlock/config.json` */
@Doc(
    "Craft block · removes recipes from the game so players cannot craft certain items (and the recipe book / JEI / REI stop showing them).\n" +
        "\n" +
        "HOW IT WORKS\n" +
        "  Every recipe whose RESULT matches a line of \"blocked\" is taken out of the recipe manager when the server starts and after\n" +
        "  every /reload or /serverbuddy reload. A line can be:\n" +
        "      \"minecraft:tnt\"            one result item\n" +
        "      \"cobblemon:*\"              every item of a namespace (mod)\n" +
        "      \"#mypack:uncraftable\"      an ITEM TAG. Tags can come from a datapack (data/mypack/tags/item/uncraftable.json), so a\n" +
        "                                 datapack can decide what is blocked while this file only names the tag\n" +
        "      \"recipe:minecraft:ender_chest\"   one recipe by its id (also \"recipe:mymod:*\" for a whole namespace)\n" +
        "  \"allowed\" takes the same lines and wins over \"blocked\" (block \"cobblemon:*\" but allow \"cobblemon:poke_ball\").\n" +
        "\n" +
        "  \"recipe_types\" says which kinds of recipe are affected. The default only touches the crafting grid (workbench and the 2x2\n" +
        "  grid). Add \"minecraft:smelting\", \"minecraft:stonecutting\", \"minecraft:smithing\"... to block those too, or use \"*\" for every type.\n" +
        "\n" +
        "CHECKING IT (permission serverbuddy.craftblock.admin, default level 2)\n" +
        "  /craftblock list    what each line removed (counts and recipe ids)\n" +
        "  /craftblock check   audit: lines that match nothing (typos, missing mods or tags), and blocked items players can STILL make with\n" +
        "                      another kind of recipe (because of recipe_types), which is what usually leaks a blocked item"
)
class CraftBlockConfig(
    @Doc("false = no recipe is removed.")
    val enabled: Boolean = true,
    @SerializedName("recipe_types")
    @Doc("Recipe types affected: \"minecraft:crafting\" (workbench + 2x2 grid), \"minecraft:smelting\", \"minecraft:stonecutting\"... \"*\" = all.")
    val recipeTypes: List<String> = listOf("minecraft:crafting"),
    @Doc("Items / namespaces / #tags / recipe:ids whose recipes are removed (see the header). Empty = nothing is blocked.")
    val blocked: List<String> = emptyList(),
    @Doc("Exceptions to \"blocked\" (same syntax).")
    val allowed: List<String> = emptyList(),
    val messages: CraftBlockMessages = CraftBlockMessages()
)

class CraftBlockMessages(
    @SerializedName("list_header")
    @Doc("Placeholders: %recipes% (total recipes removed).")
    val listHeader: String = "<gold>Craft block: <white>%recipes%</white> recipes removed.",
    @SerializedName("list_line")
    @Doc("One line per rule. Placeholders: %rule% %count% %examples%.")
    val listLine: String = " <gray>- <white>%rule%</white>: <yellow>%count%</yellow> <dark_gray>%examples%",
    @SerializedName("list_empty")
    val listEmpty: String = "<gray>No crafting is blocked (the \"blocked\" list is empty or turned off).",
    @SerializedName("check_ok")
    val checkOk: String = "<green>Everything checks out: every line matches something and no blocked item can still be made.",
    @SerializedName("check_unmatched")
    @Doc("A line that removed nothing. Placeholders: %rule%.")
    val checkUnmatched: String = "<yellow>Matches nothing: <white>%rule%</white> <gray>(typo, mod not installed, or an empty/missing tag)",
    @SerializedName("check_leak")
    @Doc("A blocked item that another recipe type still makes. Placeholders: %item% %type% %recipe%.")
    val checkLeak: String = "<red>Still craftable: <white>%item%</white> <gray>via <white>%type%</white> (recipe %recipe%). Add that type to recipe_types."
)
