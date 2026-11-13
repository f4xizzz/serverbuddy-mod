package com.f4xizzz.serverbuddy.kits.config.requirements

import com.google.gson.*
import com.f4xizzz.serverbuddy.core.script.GraalScripts
import com.f4xizzz.serverbuddy.kits.config.requirements.types.extensions.plan.PlanPlaytimeRequirement
import com.f4xizzz.serverbuddy.kits.config.requirements.types.internal.*
import java.lang.reflect.Type

enum class RequirementType(val identifier: String, val clazz: Class<*>) {
    // Internal
    PERMISSION("permission", PermissionRequirement::class.java),
    ITEM("item", ItemRequirement::class.java),
    CURRENCY("currency", CurrencyRequirement::class.java),
    DIMENSION("dimension", DimensionRequirement::class.java),
    PLACEHOLDER("placeholder", PlaceholderRequirement::class.java),
    JAVASCRIPT("javascript", JavaScriptRequirement::class.java),
    EMPTY_SLOTS("empty_slots", EmptySlotsRequirement::class.java),

    // Extensions
    PLAN_PLAYTIME("plan_playtime", PlanPlaytimeRequirement::class.java);

    companion object {
        fun valueOfAnyCase(name: String): RequirementType? {
            for (type in entries) {
                if (name.equals(type.identifier, true)) return type
            }
            return null
        }
    }

    internal class Adapter : JsonSerializer<Requirement>, JsonDeserializer<Requirement> {
        override fun serialize(src: Requirement, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return context.serialize(src, src::class.java)
        }

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Requirement {
            val jsonObject: JsonObject = json.getAsJsonObject()
            val typeName = jsonObject.get("type").asString
            if (typeName.equals("javascript", true) && !GraalScripts.available()) {
                throw JsonParseException("JavaScript requirements need the GraalVM JS engine (installed together with Cobblemon)")
            }
            val type: RequirementType? = RequirementType.valueOfAnyCase(typeName)
            return try {
                context.deserialize(json, type!!.clazz)
            } catch (e: NullPointerException) {
                throw JsonParseException("Could not deserialize requirement type: $type", e)
            }
        }
    }
}
