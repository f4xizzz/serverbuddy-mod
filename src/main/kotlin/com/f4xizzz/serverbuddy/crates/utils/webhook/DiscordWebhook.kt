package com.f4xizzz.serverbuddy.crates.utils.webhook

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// Cliente mínimo de webhook do Discord. Substitui a biblioteca "discord-webhook" que o SkiesCrates usava
// (a versão 1.0.1 dela não existe mais no JitPack) e cobre só o que o módulo Crates precisa.

class Author(val name: String, val url: String = "", val iconUrl: String = "")

class Footer(val text: String, val iconUrl: String = "")

class Image(val url: String)

class Field(val name: String, val value: String, val inline: Boolean = false)

class Message {
    var content: String? = null
    private var username: String? = null
    private var avatarUrl: String? = null

    fun setUsername(value: String): Message {
        username = value
        return this
    }

    fun setAvatarUrl(value: String): Message {
        avatarUrl = value
        return this
    }

    internal fun applyTo(json: JsonObject) {
        content?.takeIf { it.isNotEmpty() }?.let { json.addProperty("content", it) }
        username?.takeIf { it.isNotEmpty() }?.let { json.addProperty("username", it) }
        avatarUrl?.takeIf { it.isNotEmpty() }?.let { json.addProperty("avatar_url", it) }
    }
}

class Embed {
    private var title: String? = null
    private var description: String? = null
    private var url: String? = null
    private var timestamp: String? = null
    private var color: Int? = null
    private var author: Author? = null
    private var image: Image? = null
    private var thumbnail: Image? = null
    private var footer: Footer? = null
    private var fields: Array<Field> = emptyArray()

    fun setTitle(value: String): Embed { title = value; return this }
    fun setDescription(value: String): Embed { description = value; return this }
    fun setUrl(value: String): Embed { url = value; return this }
    fun setTimestamp(value: String): Embed { timestamp = value; return this }
    fun setColor(value: Int): Embed { color = value; return this }
    fun setAuthor(value: Author): Embed { author = value; return this }
    fun setImage(value: Image): Embed { image = value; return this }
    fun setThumbnail(value: Image): Embed { thumbnail = value; return this }
    fun setFooter(value: Footer): Embed { footer = value; return this }
    fun setFields(value: Array<Field>): Embed { fields = value; return this }

    internal fun toJson(): JsonObject {
        val json = JsonObject()
        title?.takeIf { it.isNotEmpty() }?.let { json.addProperty("title", it) }
        description?.takeIf { it.isNotEmpty() }?.let { json.addProperty("description", it) }
        url?.takeIf { it.isNotEmpty() }?.let { json.addProperty("url", it) }
        timestamp?.takeIf { it.isNotEmpty() }?.let { json.addProperty("timestamp", it) }
        color?.let { json.addProperty("color", it) }
        author?.let { a ->
            val obj = JsonObject()
            obj.addProperty("name", a.name)
            if (a.url.isNotEmpty()) obj.addProperty("url", a.url)
            if (a.iconUrl.isNotEmpty()) obj.addProperty("icon_url", a.iconUrl)
            json.add("author", obj)
        }
        footer?.let { f ->
            val obj = JsonObject()
            obj.addProperty("text", f.text)
            if (f.iconUrl.isNotEmpty()) obj.addProperty("icon_url", f.iconUrl)
            json.add("footer", obj)
        }
        image?.let { i -> json.add("image", JsonObject().also { it.addProperty("url", i.url) }) }
        thumbnail?.let { i -> json.add("thumbnail", JsonObject().also { it.addProperty("url", i.url) }) }
        if (fields.isNotEmpty()) {
            val array = JsonArray()
            for (f in fields) {
                val obj = JsonObject()
                obj.addProperty("name", f.name)
                obj.addProperty("value", f.value)
                obj.addProperty("inline", f.inline)
                array.add(obj)
            }
            json.add("fields", array)
        }
        return json
    }
}

class WebhookManager {
    private var channelUrl: String = ""
    private var message: Message? = null
    private var embeds: Array<Embed> = emptyArray()

    fun setChannelUrl(value: String): WebhookManager { channelUrl = value; return this }
    fun setMessage(value: Message): WebhookManager { message = value; return this }
    fun setEmbeds(value: Array<Embed>): WebhookManager { embeds = value; return this }

    /** Envia o webhook (bloqueia até a resposta; o Crates chama isso fora da thread do servidor). */
    fun exec() {
        if (channelUrl.isBlank()) return

        val payload = JsonObject()
        message?.applyTo(payload)
        if (embeds.isNotEmpty()) {
            val array = JsonArray()
            embeds.forEach { array.add(it.toJson()) }
            payload.add("embeds", array)
        }

        val request = HttpRequest.newBuilder(URI.create(channelUrl))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build()
        val response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("O Discord respondeu ${response.statusCode()}: ${response.body()}")
        }
    }

    private companion object {
        val CLIENT: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    }
}
