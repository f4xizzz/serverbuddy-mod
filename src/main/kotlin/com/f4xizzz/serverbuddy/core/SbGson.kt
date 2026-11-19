package com.f4xizzz.serverbuddy.core

import com.f4xizzz.serverbuddy.ModPaths
import com.f4xizzz.serverbuddy.core.config.ConfigDocs
import com.f4xizzz.serverbuddy.kits.gui.GenericClickType
import com.f4xizzz.serverbuddy.kits.gui.InventoryType
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.mojang.serialization.JsonOps
import net.minecraft.nbt.CompoundTag
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.Type

/** Gson dos módulos novos (storage, fichas, menus de Pokémon...), com os mesmos tipos dos menus dos Skies. */
object SbGson {
    val gson: Gson = GsonBuilder().disableHtmlEscaping()
        .registerTypeAdapter(GenericClickType::class.java, GenericClickType.Adapter())
        .registerTypeAdapter(InventoryType::class.java, InventoryType.Adapter())
        .registerTypeHierarchyAdapter(CompoundTag::class.java, CompoundTagAdapter())
        .registerTypeAdapterFactory(LenientEnumFactory)
        .create()

    val pretty: Gson = gson.newBuilder().setPrettyPrinting().create()

    /** Só para criar a config padrão: escreve também os campos vazios (`"custom_model_data": null`...) para o admin vê-los. */
    val defaultsWriter: Gson = pretty.newBuilder().serializeNulls().create()
}

/**
 * Valores de enum nas configs sem se preocupar com maiúsculas: `"period": "daily"` e `"DAILY"` valem igual (o Gson normal só
 * aceita o nome exato). Respeita o `@SerializedName` das constantes. Um valor que não existe vira null e avisa no log.
 */
object LenientEnumFactory : TypeAdapterFactory {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy")

    @Suppress("UNCHECKED_CAST")
    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        val raw = type.rawType
        val enumClass: Class<*> = if (raw.isEnum) raw else raw.superclass?.takeIf { it.isEnum } ?: return null
        val constants = enumClass.enumConstants as Array<Enum<*>>
        val byName = LinkedHashMap<String, Enum<*>>()
        val written = HashMap<Enum<*>, String>()
        for (constant in constants) {
            val annotation = enumClass.getField(constant.name).getAnnotation(SerializedName::class.java)
            val primary = annotation?.value ?: constant.name
            written[constant] = primary
            byName[primary.lowercase()] = constant
            annotation?.alternate?.forEach { byName[it.lowercase()] = constant }
            byName.putIfAbsent(constant.name.lowercase(), constant)
        }
        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T?) {
                if (value == null) out.nullValue() else out.value(written[value as Enum<*>] ?: (value as Enum<*>).name)
            }

            override fun read(reader: JsonReader): T? {
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull()
                    return null
                }
                val text = reader.nextString().trim()
                val found = byName[text.lowercase()]
                if (found == null) LOGGER.warn("Value '{}' does not exist in {}. Valid values: {}", text, enumClass.simpleName, constants.joinToString { written[it] ?: it.name })
                return found as T?
            }
        }
    }
}

/** `components`/`nbt` de um item, escrito como JSON na config. */
internal class CompoundTagAdapter : JsonSerializer<CompoundTag>, JsonDeserializer<CompoundTag> {
    override fun serialize(src: CompoundTag, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
        CompoundTag.CODEC.encodeStart(JsonOps.INSTANCE, src).result().orElse(JsonNull.INSTANCE)

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): CompoundTag? =
        CompoundTag.CODEC.parse(JsonOps.INSTANCE, json).result().orElse(null)
}

/** Lista de slots: aceita `5`, `"0-44"`, `[1, 2, "10-15"]`. */
internal class SlotsAdapter : TypeAdapter<List<Int>>() {
    override fun write(out: JsonWriter, value: List<Int>?) {
        if (value == null) {
            out.nullValue()
            return
        }
        // sequências de 4 ou mais slots seguidos viram "9-44", para a lista não ocupar o arquivo inteiro
        out.beginArray()
        var i = 0
        while (i < value.size) {
            var j = i
            while (j + 1 < value.size && value[j + 1] == value[j] + 1) j++
            if (j - i + 1 >= 4) out.value("${value[i]}-${value[j]}") else for (k in i..j) out.value(value[k].toLong())
            i = j + 1
        }
        out.endArray()
    }

    override fun read(reader: JsonReader): List<Int> {
        val result = ArrayList<Int>()
        when (reader.peek()) {
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                while (reader.hasNext()) readOne(reader, result)
                reader.endArray()
            }
            JsonToken.NULL -> reader.nextNull()
            else -> readOne(reader, result)
        }
        return result
    }

    private fun readOne(reader: JsonReader, out: MutableList<Int>) {
        if (reader.peek() == JsonToken.NUMBER) {
            out.add(reader.nextInt())
            return
        }
        val text = reader.nextString().trim()
        val dash = text.indexOf('-', 1)
        if (dash > 0) {
            val from = text.substring(0, dash).trim().toIntOrNull()
            val to = text.substring(dash + 1).trim().toIntOrNull()
            if (from != null && to != null && to >= from) out.addAll(from..to)
        } else {
            text.toIntOrNull()?.let { out.add(it) }
        }
    }
}

/**
 * Carrega (e cria com os padrões, se faltar) um arquivo de config dentro de `config/ServerBuddy/<pasta>`.
 *
 * O arquivo novo nasce **com comentários** (um `//` em cada opção marcada com `@Doc`) e pode ser editado e comentado
 * à vontade: a leitura aceita comentários. Um arquivo que já existe nunca é reescrito sozinho; para completar com as
 * opções que surgiram em versões novas use [update] (`/serverbuddy config update`), que guarda um `.bak` antes.
 */
object ConfigFiles {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy")

    /**
     * A config file known to the mod. [tracked] files are checked by `/serverbuddy config check` and completed by `update`; every entry is
     * listed (and edited) by the dashboard. [reload] applies the file again after a save (null = the whole module is reloaded). [gson] is the
     * Gson of the code that reads the file (a module with Minecraft types in its config brings its own adapters).
     */
    class Entry(
        val folder: String,
        val fileName: String,
        val type: Class<*>,
        val defaults: () -> Any,
        val reload: (() -> Unit)? = null,
        val gson: Gson = SbGson.gson,
        val tracked: Boolean = true
    ) {
        val file: File get() = File(dir(folder), fileName)
        val label: String get() = "$folder/$fileName"

        /** Writes the defaults, nulls included, so the admin sees every option. */
        val defaultsWriter: Gson by lazy { gson.newBuilder().setPrettyPrinting().serializeNulls().create() }
    }

    private class Collection(val folder: String, val extension: String, val type: Class<*>, val gson: Gson, val reload: (() -> Unit)?, val defaults: () -> Any, val recursive: Boolean, val template: (() -> String)?) {
        /** Is [path] (relative to `config/ServerBuddy`, with `/`) this folder or, when the folder is recursive, a folder inside it? */
        fun covers(path: String) = path == folder || (recursive && path.startsWith("$folder/"))
    }

    /** A recursive content folder is read this many levels down and no more than this many files (a folder with something huge in it must not freeze the dashboard). */
    private const val MAX_DEPTH = 6
    private const val MAX_FILES = 500

    private val registry = LinkedHashMap<String, Entry>()
    private val collections = ArrayList<Collection>()

    /** `config/ServerBuddy`. The unit tests point it at a temporary folder, so they do not need the game. */
    @Volatile
    internal var baseDir: () -> File = { ModPaths.subDir() }

    fun dir(folder: String): File = File(baseDir(), folder)

    /** Forgets every registered file and folder (unit tests only). */
    internal fun resetForTests() {
        registry.clear()
        collections.clear()
    }

    /** Every file the dashboard can edit: the registered ones and the files inside the registered folders. */
    fun entries(): List<Entry> {
        val all = LinkedHashMap<String, Entry>(registry)
        for (collection in collections) {
            val root = File(baseDir(), collection.folder)
            for (file in filesOf(collection, root)) {
                val inside = file.parentFile.relativeTo(root).invariantSeparatorsPath
                val folder = if (inside.isEmpty()) collection.folder else "${collection.folder}/$inside"
                all.putIfAbsent("$folder/${file.name}", Entry(folder, file.name, collection.type, collection.defaults, collection.reload, collection.gson, false))
            }
        }
        return all.values.toList()
    }

    /**
     * The files of a content folder: the ones right inside it or, for a recursive folder, the ones in the folders below it too (the ones right inside
     * come first; hidden folders are skipped). A folder that does not exist has none, and looking never creates it.
     */
    private fun filesOf(collection: Collection, root: File): List<File> {
        if (!root.isDirectory) return emptyList()
        val found = if (collection.recursive) {
            root.walkTopDown().maxDepth(MAX_DEPTH).onEnter { dir -> dir == root || !dir.name.startsWith(".") }.filter { it.isFile }.take(MAX_FILES).toList()
        } else {
            root.listFiles { f -> f.isFile }?.toList().orEmpty()
        }
        return found.filter { it.name.endsWith(collection.extension) }
            .sortedWith(compareBy({ it.parentFile != root }, { it.relativeTo(root).invariantSeparatorsPath }))
    }

    /** The files `/serverbuddy config check` and `update` look at. */
    fun tracked(): List<Entry> = registry.values.filter { it.tracked }

    /** Lists, for the dashboard, a file that its own module loads (the Java `*ConfigManager`s, the ported modules). Not part of `config check`. */
    fun register(folder: String, fileName: String, type: Class<*>, gson: Gson = SbGson.gson, reload: (() -> Unit)? = null, defaults: () -> Any) {
        registry["$folder/$fileName"] = Entry(folder, fileName, type, defaults, reload, gson, false)
    }

    /** Lists every [extension] file inside [folder] (content the admin adds files to: kits, crates, pools...). New files show up on their own. */
    fun collection(
        folder: String,
        type: Class<*>,
        extension: String = ".json",
        gson: Gson = SbGson.gson,
        reload: (() -> Unit)? = null,
        recursive: Boolean = false,
        template: (() -> String)? = null,
        defaults: () -> Any
    ) {
        collections += Collection(folder, extension, type, gson, reload, defaults, recursive, template)
    }

    /** Why [create] said no: [code] is what the dashboard shows (`bad_params` or `exists`) and [message] says what was wrong. */
    class CreateException(val code: String, message: String) : Exception(message)

    private val SAFE_NAME = Regex("[A-Za-z0-9_-]{1,48}")

    /**
     * Makes a NEW file in a content folder for the dashboard: a [collection] folder, or a folder inside a recursive one. It starts as a copy of [from] (another
     * file of the same folder), as the folder's own template text (a class that cannot be built empty, like a shop, has one) or as the defaults of its class.
     * Nothing outside the content folders can be made, whatever the names say: the names may only
     * have letters, numbers, `_` and `-`. Throws [CreateException] (`bad_params`: not a content folder, a name that is not allowed or a [from] that is not
     * there; `exists`: there is a file with that name already). The module still has to read the new file: the dashboard asks for a reload after saving it.
     */
    fun create(folder: String, fileName: String, from: String? = null): Entry {
        val collection = collections.firstOrNull { it.covers(folder) } ?: throw CreateException("bad_params", "not a content folder: $folder")
        val extension = collection.extension
        fun allowed(name: String) = name.endsWith(extension) && SAFE_NAME.matches(name.removeSuffix(extension))
        if (!allowed(fileName)) throw CreateException("bad_params", "file name not allowed: $fileName")
        if (from != null && !allowed(from)) throw CreateException("bad_params", "source name not allowed: $from")
        val inside = folder.removePrefix(collection.folder).trim('/')
        if (inside.isNotEmpty() && inside.split('/').any { !SAFE_NAME.matches(it) }) throw CreateException("bad_params", "folder not allowed: $folder")
        val dir = dir(folder)
        val target = File(dir, fileName)
        if (target.exists()) throw CreateException("exists", "$folder/$fileName already exists")
        val source = from?.let { File(dir, it) }
        if (source != null && !source.isFile) throw CreateException("bad_params", "source not found: $from")
        dir.mkdirs()
        val entry = Entry(folder, fileName, collection.type, collection.defaults, collection.reload, collection.gson, false)
        val template = collection.template
        when {
            source != null -> source.copyTo(target)
            template != null -> target.writeText(template(), Charsets.UTF_8)
            else -> target.writeText(ConfigDocs.render(entry.defaults(), collection.gson.newBuilder().setPrettyPrinting().create()), Charsets.UTF_8)
        }
        return entry
    }

    /**
     * [track] = false para arquivos que o usuário cria à vontade (as pools de trivia e as categorias de missões):
     * eles não entram no `/serverbuddy config check`, porque uma pool enxuta e válida não está "incompleta".
     */
    fun <T : Any> load(folder: String, fileName: String, type: Class<T>, track: Boolean = true, defaults: () -> T): T {
        val entry = Entry(folder, fileName, type, defaults)
        if (track) registry["$folder/$fileName"] = entry
        dir(folder).mkdirs()
        val file = entry.file
        if (!file.exists()) {
            try {
                file.writeText(ConfigDocs.render(defaults(), SbGson.defaultsWriter), Charsets.UTF_8)
            } catch (e: Exception) {
                LOGGER.error("Could not create $folder/$fileName", e)
            }
        }
        return try {
            file.reader(Charsets.UTF_8).use { SbGson.pretty.fromJson(it, type) } ?: defaults()
        } catch (e: Exception) {
            LOGGER.error("Error reading $folder/$fileName; using the defaults (the file was not changed).", e)
            defaults()
        }
    }

    private fun readTree(file: File): JsonObject? = try {
        file.reader(Charsets.UTF_8).use { JsonParser.parseReader(it) }.takeIf { it.isJsonObject }?.asJsonObject
    } catch (e: Exception) {
        null
    }

    /**
     * Changes a config file that is already tracked: [mutate] edits the JSON tree, a `.bak` of the old file is kept and the file is
     * written again with its comments (the values the admin set stay as they are). False if the file is unknown or unreadable.
     */
    fun edit(folder: String, fileName: String, mutate: (JsonObject) -> Unit): Boolean {
        val entry = registry["$folder/$fileName"] ?: return false
        if (!entry.file.exists()) return false
        val tree = readTree(entry.file) ?: return false
        mutate(tree)
        entry.file.copyTo(File(entry.file.parentFile, entry.fileName + ".bak"), overwrite = true)
        entry.file.writeText(ConfigDocs.renderTree(tree, entry.type, SbGson.defaultsWriter), Charsets.UTF_8)
        return true
    }

    /** As opções que o arquivo ainda não tem (null se ele não existe ou não dá para ler). */
    fun missing(entry: Entry): List<String>? {
        if (!entry.file.exists()) return null
        val user = readTree(entry.file) ?: return null
        val defaults = SbGson.defaultsWriter.toJsonTree(entry.defaults()).asJsonObject
        return ConfigDocs.mergeMissing(user, defaults, entry.type)
    }

    /**
     * Completa o arquivo com as opções novas (os valores do usuário ficam como estão; os comentários são refeitos).
     * Guarda uma cópia `.bak` antes. Devolve as opções adicionadas.
     */
    fun update(entry: Entry): List<String>? {
        if (!entry.file.exists()) return null
        val user = readTree(entry.file) ?: return null
        val defaults = SbGson.defaultsWriter.toJsonTree(entry.defaults()).asJsonObject
        val added = ConfigDocs.mergeMissing(user, defaults, entry.type)
        if (added.isEmpty()) return added
        entry.file.copyTo(File(entry.file.parentFile, entry.fileName + ".bak"), overwrite = true)
        entry.file.writeText(ConfigDocs.renderTree(user, entry.type, SbGson.defaultsWriter), Charsets.UTF_8)
        return added
    }
}
