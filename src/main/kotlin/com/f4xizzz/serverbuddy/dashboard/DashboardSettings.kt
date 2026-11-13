package com.f4xizzz.serverbuddy.dashboard

import com.f4xizzz.serverbuddy.core.ConfigFiles
import com.f4xizzz.serverbuddy.core.config.Doc
import com.google.gson.annotations.SerializedName

@Doc(
    "ServerBuddy dashboard · safety settings\n" +
        "\n" +
        "The dashboard (website) only connects after an admin runs /sb dashboard sync in game, and the connection is always started by\n" +
        "this server. These settings limit what it can do once connected."
)
class DashboardConfig(
    @Doc("false = the dashboard cannot be linked to this server (/sb dashboard sync is refused).")
    val enabled: Boolean = true,
    @SerializedName("console_enabled")
    @Doc("false = the dashboard console (Pro plan) is turned off even for linked servers.")
    val consoleEnabled: Boolean = true,
    @SerializedName("blocked_commands")
    @Doc(
        "Commands the dashboard console refuses (the first word, without the slash). This protects against mistakes (for example\n" +
            "stopping the server by accident); whoever owns the server can still change this list."
    )
    val blockedCommands: List<String> = listOf("stop", "op", "deop", "whitelist", "ban-ip", "pardon-ip"),
    @SerializedName("max_output_lines")
    @Doc("How many lines of output a console command can send back to the dashboard.")
    val maxOutputLines: Int = 60
)

/** Loads `config/ServerBuddy/Dashboard/config.json` (it also appears in the dashboard's config editor). */
object DashboardSettings {
    @Volatile
    private var current: DashboardConfig? = null

    @JvmStatic
    fun get(): DashboardConfig = current ?: load()

    @JvmStatic
    fun load(): DashboardConfig =
        ConfigFiles.load("Dashboard", "config.json", DashboardConfig::class.java) { DashboardConfig() }.also { current = it }
}
