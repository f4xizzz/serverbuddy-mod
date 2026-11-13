package com.f4xizzz.serverbuddy.core

import com.f4xizzz.serverbuddy.core.config.Doc
import com.f4xizzz.serverbuddy.logs.ServerConfigManager
import com.google.gson.annotations.SerializedName

/** `config/ServerBuddy/webhook.conf` */
@Doc(
    "Default Discord webhook\n" +
        "\n" +
        "Everything in ServerBuddy that can post to Discord (Crates, Announcements, Events, the server logs...) has a\n" +
        "\"default_settings\" option in its own config. With default_settings = true (the default) it posts to the webhook below, so you\n" +
        "set your webhook once, here. Set default_settings to false in a file to give that one its own webhook (url, username and avatar).\n" +
        "If the URL below is empty, a module falls back to the webhook written in its own file, so nothing breaks while you set this up."
)
class WebhookConfig(
    @SerializedName("webhook_url")
    @Doc("The default webhook URL (in Discord: channel settings > Integrations > Webhooks). Empty = no default webhook.")
    val webhookUrl: String = "",
    @Doc("Name shown as the sender. Empty = the name set on the webhook in Discord.")
    val username: String = "",
    @SerializedName("avatar_url")
    @Doc("Image URL shown as the sender's avatar. Empty = the avatar set on the webhook in Discord.")
    val avatarUrl: String = ""
)

/** The webhook settings every module that posts to Discord offers (see [Webhooks.resolve]). */
class ModuleWebhookConfig(
    @SerializedName("default_settings")
    @Doc("true = post to the default webhook in webhook.conf (if that has no URL, the fields below are used). false = only the fields below.")
    val defaultSettings: Boolean = true,
    @SerializedName("webhook_url")
    @Doc("This module's own webhook URL (used when default_settings is false or the default has no URL).")
    val webhookUrl: String = "",
    val username: String = "",
    @SerializedName("avatar_url")
    val avatarUrl: String = ""
) {
    fun resolve(): ResolvedWebhook = Webhooks.resolve(defaultSettings, webhookUrl, username, avatarUrl)
}

/** The webhook a module should actually post to. */
class ResolvedWebhook(val url: String, val username: String, val avatarUrl: String) {
    val enabled: Boolean get() = url.isNotBlank()
}

/**
 * The shared webhook ([WebhookConfig]) and the rule every module follows to pick a webhook: with `default_settings` on, use the
 * shared one (when it has a URL); otherwise, or when there is no shared URL, use what the module's own file says.
 */
object Webhooks {
    @Volatile
    var defaults: WebhookConfig = WebhookConfig()
        private set

    fun load() {
        defaults = ConfigFiles.load("", "webhook.conf", WebhookConfig::class.java) {
            // the first time, keep the webhook that used to live in serverconfig.conf
            WebhookConfig(webhookUrl = runCatching { ServerConfigManager.getConfig().discordWebhookUrl }.getOrNull().orEmpty())
        }
    }

    /**
     * @param useDefault the module's `default_settings`
     * @param url / [username] / [avatarUrl] what the module's own file says (may be null or empty)
     */
    @JvmStatic
    fun resolve(useDefault: Boolean, url: String?, username: String?, avatarUrl: String?): ResolvedWebhook {
        val shared = defaults
        if (useDefault && shared.webhookUrl.isNotBlank()) {
            return ResolvedWebhook(shared.webhookUrl.trim(), shared.username, shared.avatarUrl)
        }
        return ResolvedWebhook(url.orEmpty().trim(), username.orEmpty(), avatarUrl.orEmpty())
    }

    private val client: java.net.http.HttpClient by lazy { java.net.http.HttpClient.newHttpClient() }

    /** Posts a plain Discord message (markdown allowed) without blocking the server thread. Does nothing when [webhook] has no URL. */
    @JvmStatic
    fun post(webhook: ResolvedWebhook, message: String) {
        if (!webhook.enabled || message.isBlank()) return
        val body = com.google.gson.JsonObject()
        body.addProperty("content", message.take(1900))
        if (webhook.username.isNotBlank()) body.addProperty("username", webhook.username)
        if (webhook.avatarUrl.isNotBlank()) body.addProperty("avatar_url", webhook.avatarUrl)
        try {
            val request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(webhook.url))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body.toString()))
                .build()
            client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.discarding())
        } catch (e: Exception) {
            org.slf4j.LoggerFactory.getLogger("ServerBuddy").warn("Could not post to the Discord webhook: {}", e.toString())
        }
    }
}
