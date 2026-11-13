package com.f4xizzz.serverbuddy.shop.data.items.actions

import com.google.gson.*
import com.google.gson.annotations.JsonAdapter
import com.f4xizzz.serverbuddy.shop.gui.GenericClickType
import com.f4xizzz.serverbuddy.shop.gui.IRefreshableGui
import com.f4xizzz.serverbuddy.shop.utils.FlexibleListAdaptorFactory
import eu.pb4.sgui.api.ClickType
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.level.ServerPlayer
import java.lang.reflect.Type

abstract class Action(
    val type: ActionType,
    @JsonAdapter(FlexibleListAdaptorFactory::class)
    val click: List<GenericClickType> = listOf(GenericClickType.ANY)
) {
    abstract fun executeAction(player: ServerPlayer, gui: IRefreshableGui)

    fun matchesClick(buttonClick: ClickType): Boolean {
        return click.any { it.buttonClicks.contains(buttonClick) }
    }

    override fun toString(): String {
        return "Action(type=$type, click=$click)"
    }

    internal class Adapter : JsonSerializer<Action>, JsonDeserializer<Action> {
        override fun serialize(src: Action, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return context.serialize(src, src::class.java)
        }

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Action {
            val jsonObject: JsonObject = json.getAsJsonObject()
            val value = jsonObject.get("type").asString
            if (value.equals("molang", true) && !FabricLoader.getInstance().isModLoaded("cobblemon")) {
                throw JsonParseException("Molang action is not supported without the Cobblemon mod")
            }
            val type: ActionType? = ActionType.Companion.valueOfAnyCase(value)
            return try {
                context.deserialize(json, type!!.clazz)
            } catch (e: NullPointerException) {
                throw JsonParseException("Could not deserialize action type: $value", e)
            }
        }
    }
}
