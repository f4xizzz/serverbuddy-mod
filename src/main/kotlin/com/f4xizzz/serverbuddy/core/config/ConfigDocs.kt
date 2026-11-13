package com.f4xizzz.serverbuddy.core.config

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

/**
 * Escreve configs em JSON **com comentários**: cada campo com [Doc] ganha um `//` em cima. O Gson lê esse formato
 * normalmente (modo leniente), então o arquivo pode ser editado e comentado à vontade.
 *
 * - [render] cria o texto de uma config nova a partir dos valores padrão.
 * - [mergeMissing] completa um arquivo antigo com as opções que foram criadas depois (sem mexer nas do usuário).
 */
object ConfigDocs {
    private const val INDENT = "  "

    /** Texto do arquivo para [config]: cabeçalho (o [Doc] da classe) e um comentário por opção documentada. */
    @JvmStatic
    fun render(config: Any, gson: Gson): String = renderTree(gson.toJsonTree(config), config.javaClass, gson)

    @JvmStatic
    fun renderTree(tree: JsonElement, rootType: Type, gson: Gson): String {
        val sb = StringBuilder()
        rawClass(rootType)?.getAnnotation(Doc::class.java)?.let { doc ->
            comment(sb, doc.value, 0)
            sb.append('\n')
        }
        write(sb, tree, rootType, 0, gson)
        sb.append('\n')
        return sb.toString()
    }

    /**
     * Copia para [user] as opções de [defaults] que ainda não existem nele (só em objetos "fixos"; mapas com chaves
     * escolhidas pelo usuário, como a lista de fichas, não são tocados). Devolve os caminhos adicionados.
     */
    @JvmStatic
    fun mergeMissing(user: JsonObject, defaults: JsonObject, type: Type, path: String = ""): List<String> {
        val added = ArrayList<String>()
        for ((key, defaultValue) in defaults.entrySet()) {
            val here = if (path.isEmpty()) key else "$path.$key"
            if (!user.has(key)) {
                user.add(key, defaultValue.deepCopy())
                added.add(here)
                continue
            }
            val current = user.get(key)
            if (current.isJsonObject && defaultValue.isJsonObject) {
                val fieldType = field(rawClass(type), key)?.genericType ?: continue
                val raw = rawClass(fieldType)
                if (raw == null || Map::class.java.isAssignableFrom(raw)) continue
                added.addAll(mergeMissing(current.asJsonObject, defaultValue.asJsonObject, fieldType, here))
            }
        }
        return added
    }

    // ---------------------------------------------------------------- escrita

    // [comments] = escreve os `//` das opções. Num mapa ou lista de objetos iguais, só o primeiro ganha os comentários
    // (os outros repetiriam o mesmo texto e deixariam o arquivo comprido demais).
    private fun write(sb: StringBuilder, el: JsonElement, type: Type?, level: Int, gson: Gson, comments: Boolean = true) {
        when {
            el.isJsonObject -> writeObject(sb, el.asJsonObject, type, level, gson, comments)
            el.isJsonArray -> writeArray(sb, el.asJsonArray, type, level, gson, comments)
            else -> sb.append(el.toString())
        }
    }

    private fun writeObject(sb: StringBuilder, obj: JsonObject, type: Type?, level: Int, gson: Gson, comments: Boolean) {
        if (obj.size() == 0) {
            sb.append("{}")
            return
        }
        val raw = rawClass(type)
        val isMap = raw != null && Map::class.java.isAssignableFrom(raw)
        val valueType = if (isMap) typeArgument(type, 1) else null
        sb.append("{\n")
        var first = true
        for ((key, value) in obj.entrySet()) {
            val fieldInfo = if (isMap) null else field(raw, key)
            val doc = if (comments) fieldInfo?.getAnnotation(Doc::class.java)?.value else null
            if (!first) sb.append(",\n")
            if (doc != null) {
                if (!first && level == 0) sb.append('\n')
                comment(sb, doc, level + 1)
            }
            sb.append(INDENT.repeat(level + 1)).append(gson.toJson(key)).append(": ")
            write(sb, value, if (isMap) valueType else fieldInfo?.genericType, level + 1, gson, if (isMap) comments && first else comments)
            first = false
        }
        sb.append('\n').append(INDENT.repeat(level)).append('}')
    }

    private fun writeArray(sb: StringBuilder, array: JsonArray, type: Type?, level: Int, gson: Gson, comments: Boolean) {
        if (array.size() == 0) {
            sb.append("[]")
            return
        }
        val itemType = typeArgument(type, 0)
        val simple = array.all { it.isJsonPrimitive || it.isJsonNull }
        if (simple) {
            val inline = array.joinToString(", ") { it.toString() }
            if (inline.length <= 70 && array.size() <= 12) {
                sb.append('[').append(inline).append(']')
                return
            }
        }
        sb.append("[\n")
        array.forEachIndexed { index, item ->
            sb.append(INDENT.repeat(level + 1))
            val inline = if (item.isJsonObject && !hasDocs(itemType)) simpleObject(item.asJsonObject) else null
            if (inline != null) sb.append(inline) else write(sb, item, itemType, level + 1, gson, comments && index == 0)
            if (index < array.size() - 1) sb.append(',')
            sb.append('\n')
        }
        sb.append(INDENT.repeat(level)).append(']')
    }

    /** Objeto de uma lista (ex.: uma recompensa) que só tem valores simples e cabe numa linha: `{ "type": "money", "amount": 5 }`. */
    private fun simpleObject(obj: JsonObject): String? {
        if (obj.size() == 0) return "{}"
        val parts = ArrayList<String>()
        for ((key, value) in obj.entrySet()) {
            val simple = value.isJsonPrimitive || value.isJsonNull || (value.isJsonArray && value.asJsonArray.all { it.isJsonPrimitive })
            if (!simple) return null
            parts.add("\"$key\": $value")
        }
        val text = parts.joinToString(", ", prefix = "{ ", postfix = " }")
        return if (text.length <= 130) text else null
    }

    /** A classe tem algum campo com [Doc]? Aí o objeto fica em várias linhas, para os comentários caberem. */
    private fun hasDocs(type: Type?): Boolean {
        var current = rawClass(type)
        while (current != null && current != Any::class.java) {
            if (current.declaredFields.any { it.getAnnotation(Doc::class.java) != null }) return true
            current = current.superclass
        }
        return false
    }

    private fun comment(sb: StringBuilder, text: String, level: Int) {
        for (line in text.split("\n")) {
            sb.append(INDENT.repeat(level)).append("//")
            if (line.isNotEmpty()) sb.append(' ').append(line)
            sb.append('\n')
        }
    }

    // ---------------------------------------------------------------- reflexão

    private fun field(owner: Class<*>?, jsonName: String): Field? {
        var current = owner
        while (current != null && current != Any::class.java) {
            for (candidate in current.declaredFields) {
                if (Modifier.isStatic(candidate.modifiers) || Modifier.isTransient(candidate.modifiers)) continue
                val serialized = candidate.getAnnotation(SerializedName::class.java)
                val name = serialized?.value ?: candidate.name
                if (name == jsonName || serialized?.alternate?.contains(jsonName) == true) return candidate
            }
            current = current.superclass
        }
        return null
    }

    private fun rawClass(type: Type?): Class<*>? = when (type) {
        is Class<*> -> type
        is ParameterizedType -> type.rawType as? Class<*>
        is WildcardType -> rawClass(type.upperBounds.firstOrNull())
        else -> null
    }

    private fun typeArgument(type: Type?, index: Int): Type? {
        val args = (type as? ParameterizedType)?.actualTypeArguments ?: return null
        val arg = args.getOrNull(index) ?: return null
        return if (arg is WildcardType) arg.upperBounds.firstOrNull() else arg
    }
}
