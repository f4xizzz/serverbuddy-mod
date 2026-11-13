package com.f4xizzz.serverbuddy.announcements.config.discord

import com.f4xizzz.serverbuddy.core.Webhooks
import com.google.gson.annotations.SerializedName
import com.f4xizzz.serverbuddy.announcements.config.Announcement
import com.f4xizzz.serverbuddy.announcements.config.AnnouncementGroup
import com.f4xizzz.serverbuddy.announcements.utils.DiscordWebhookUtils
import com.f4xizzz.serverbuddy.announcements.utils.Utils
import java.io.IOException

class DiscordWebhook(
    // true = post to the shared webhook.conf (url, username and avatar); false = use the fields below
    @SerializedName("default_settings")
    val defaultSettings: Boolean = true,
    @SerializedName("webhook_url")
    val webhookURL: String = "",
    val username: String = "",
    @SerializedName("avatar_url")
    val avatarURL: String = "",
    val content: String = "",
    val embeds: MutableList<DiscordEmbed> = mutableListOf(),
) {
    fun sendWebhook(group: AnnouncementGroup, announcement: Announcement) {
        val hook = Webhooks.resolve(defaultSettings, webhookURL, username, avatarURL)
        if (hook.enabled) {
            val webhook = DiscordWebhookUtils(
                hook.url,
                hook.username.ifEmpty { "null" },
                hook.avatarUrl.ifEmpty { "null" },
                content,
                embeds,
                group,
                announcement
            )

            try {
                webhook.execute()
            } catch (e: IOException) {
                Utils.printError("Error while executing Discord webhook: ${e.printStackTrace()}")
            }
        }
    }

    override fun toString(): String {
        return "DiscordWebhook(webhookURL='$webhookURL', username='$username', avatarURL='$avatarURL', content='$content', embeds=$embeds)"
    }
}