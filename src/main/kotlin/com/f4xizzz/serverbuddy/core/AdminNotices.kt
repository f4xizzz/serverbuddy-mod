package com.f4xizzz.serverbuddy.core

import org.slf4j.LoggerFactory

/**
 * Messages for the people who run the server: everyone online with the `serverbuddy.admin.notices` permission (operators by
 * default) sees them in chat, and the console log gets them too. Used for the license and the dashboard link.
 */
object AdminNotices {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy")

    /** Sends [template] (MiniMessage) to the online admins. Safe to call from any thread. */
    @JvmStatic
    fun broadcast(template: String, vars: Map<String, Any?> = emptyMap()) {
        val text = Texts.fill(template, vars)
        LOGGER.info("[Admin] {}", text.replace(Regex("<[^>]+>"), ""))
        val server = ServerHolder.server ?: return
        server.execute {
            val message = Texts.render(template, vars)
            for (player in server.playerList.players) {
                if (SbPermissions.check(player, "serverbuddy.admin.notices", 3)) player.sendSystemMessage(message)
            }
        }
    }
}
