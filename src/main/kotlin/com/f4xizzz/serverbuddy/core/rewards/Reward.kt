package com.f4xizzz.serverbuddy.core.rewards

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

/**
 * Uma recompensa escrita na config: um objeto com `"type"` e os campos daquele tipo, por exemplo
 * `{ "type": "money", "amount": 500 }`. O objeto fica do jeito que o administrador escreveu (por isso o arquivo de config
 * só mostra os campos que cada recompensa usa). Veja [RewardDocs] para a lista de tipos.
 */
@JsonAdapter(RewardAdapter::class)
class Reward(val json: JsonObject) {
    /** O tipo, em minúsculo (`item`, `command`, `money`...). Vazio se faltou. */
    val type: String get() = str("type")?.trim()?.lowercase().orEmpty()

    /** Peso no sorteio entre várias recompensas (padrão 1). */
    val weight: Int get() = (int("weight") ?: 1).coerceAtLeast(0)

    /** Chance de sair, em porcentagem (padrão 100). */
    val chance: Double get() = (double("chance") ?: 100.0).coerceIn(0.0, 100.0)

    /** Texto que aparece nos menus e nas mensagens no lugar da descrição automática. */
    val label: String? get() = str("label")?.takeIf { it.isNotBlank() }

    fun str(key: String): String? = json.get(key)?.takeIf { it.isJsonPrimitive }?.asString

    fun int(key: String): Int? = json.get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble.toInt() }.getOrNull() }

    fun double(key: String): Double? = json.get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble }.getOrNull() }

    fun bool(key: String): Boolean? = json.get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() }

    /** Aceita `"campo": "texto"` ou `"campo": ["texto 1", "texto 2"]`. */
    fun strings(key: String): List<String> {
        val element = json.get(key) ?: return emptyList()
        if (element.isJsonArray) return element.asJsonArray.filter { it.isJsonPrimitive }.map { it.asString }
        return if (element.isJsonPrimitive) listOf(element.asString) else emptyList()
    }

    override fun toString(): String = json.toString()

    companion object {
        /** `Reward.of("money", "amount" to 500)`. Valores nulos são ignorados. */
        @JvmStatic
        fun of(type: String, vararg fields: Pair<String, Any?>): Reward {
            val json = JsonObject()
            json.addProperty("type", type)
            for ((key, value) in fields) {
                when (value) {
                    null -> {}
                    is Number -> json.addProperty(key, value)
                    is Boolean -> json.addProperty(key, value)
                    is List<*> -> json.add(key, JsonArray().also { array -> value.forEach { array.add(it.toString()) } })
                    else -> json.addProperty(key, value.toString())
                }
            }
            return Reward(json)
        }
    }
}

/** Lê e escreve a recompensa como o objeto JSON que ela é. */
class RewardAdapter : TypeAdapter<Reward>() {
    private val plain = Gson()

    override fun write(out: JsonWriter, value: Reward?) {
        if (value == null) {
            out.nullValue()
            return
        }
        plain.toJson(value.json, out)
    }

    override fun read(reader: JsonReader): Reward? {
        val element = JsonParser.parseReader(reader)
        return if (element.isJsonObject) Reward(element.asJsonObject) else null
    }
}

/** O texto de ajuda dos tipos de recompensa, para entrar no cabeçalho das configs que usam [Reward]. */
object RewardDocs {
    const val TEXT: String =
        "REWARDS: each one is an object with \"type\" and the fields of that type (the same ones apply across all of ServerBuddy):\n" +
            "  item           { \"type\": \"item\", \"item\": \"minecraft:diamond\", \"amount\": 3, \"name\": \"<aqua>Diamond\", \"lore\": [\"line 1\"] }\n" +
            "                 (whatever does not fit in the inventory goes to /storage; also accepts custom_model_data, glint, components...)\n" +
            "  command        { \"type\": \"command\", \"command\": \"give %player% minecraft:apple 5\" }   (as console; \"as\": \"player\" runs as the player)\n" +
            "                 \"command\" also accepts a list: [\"command 1\", \"command 2\"]\n" +
            "  money          { \"type\": \"money\", \"amount\": 500 }   (uses the installed economy; \"economy\": \"impactor:currency\" picks another)\n" +
            "  xp             { \"type\": \"xp\", \"amount\": 5, \"levels\": true }   (levels; \"levels\": false gives points)\n" +
            "  message        { \"type\": \"message\", \"text\": \"<green>Nice!\" }     (only for the player)\n" +
            "  broadcast      { \"type\": \"broadcast\", \"text\": \"<gold>%player% won!\" }   (to everyone)\n" +
            "  title          { \"type\": \"title\", \"title\": \"<gold>Congratulations!\", \"subtitle\": \"<gray>you won\" }\n" +
            "  sound          { \"type\": \"sound\", \"sound\": \"minecraft:entity.player.levelup\", \"volume\": 1, \"pitch\": 1 }\n" +
            "  pokemon        { \"type\": \"pokemon\", \"spec\": \"pikachu level=10 shiny\" }   (only with Cobblemon; goes to the party or the PC)\n" +
            "  battlepass_xp  { \"type\": \"battlepass_xp\", \"amount\": 100 }   (battle pass XP)\n" +
            "  tag            { \"type\": \"tag\", \"tag\": \"champion\" }   (unlocks a tag from /tags)\n" +
            "  On any of them: \"chance\": 50 (percentage, default 100), \"weight\": 3 (weight when the list is drawn) and\n" +
            "  \"label\": \"<gold>Nice name\" (how it appears in menus and messages). Texts accept %player% and the values of each module."
}
