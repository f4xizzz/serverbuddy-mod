package com.f4xizzz.serverbuddy.dashboard

import com.f4xizzz.serverbuddy.core.AdminNotices
import com.f4xizzz.serverbuddy.core.ServerHolder
import com.f4xizzz.serverbuddy.ModPaths
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * The link between this server and the website's dashboard.
 *
 *  1. `/sb dashboard sync` asks the site for a one-use code (valid 10 minutes) and a token. The code is shown only to the admin
 *     who ran the command; the token opens the WebSocket (the connection is always OUTBOUND: nothing listens on this server).
 *  2. The person types the code on the site. The site checks the account’s trial or subscription, then tells us
 *     (`linked`) and the online admins see "dashboard connected".
 *  3. The dashboard asks for things (`request`); each one runs on the server thread through [DashboardProviders], which checks the
 *     plan again. Mutations are logged and announced to the admins.
 *  4. When the dashboard closes (or the person disconnects, or the plan ends) the site sends `unlinked`, we close the connection and
 *     the admins see "dashboard disconnected".
 *
 * Nothing is sent to the site unless an admin ran `/sb dashboard sync`. `/sb dashboard disconnect` ends it from the game side.
 */
object DashboardClient {
    private val LOGGER = LoggerFactory.getLogger("ServerBuddy")
    private val http: HttpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "ServerBuddy-Dashboard").apply { isDaemon = true } }

    @Volatile private var socket: WebSocket? = null
    @Volatile private var token: String? = null
    @Volatile private var wanted = false
    @Volatile private var attempts = 0
    private var sendChain: CompletableFuture<*> = CompletableFuture.completedFuture(null)
    @Volatile private var telemetryStarted = false
    private val pairingRequests = AtomicLong()

    @Volatile
    var linked = false
        private set

    @Volatile
    var linkedBy: String? = null
        private set

    @Volatile
    var plan: String? = null
        private set

    /** True while the WebSocket is open. */
    fun connected(): Boolean = socket != null

    // ------------------------------------------------------------------ /sb dashboard sync | disconnect | status

    /**
     * Asks the site for a code and opens the connection. [onResult] gets the code (to show ONLY to the admin who asked) or an
     * error message; it may run on any thread.
     */
    fun sync(adminName: String, onResult: (code: String?, error: String?) -> Unit) {
        if (!DashboardSettings.load().enabled) return onResult(null, "The dashboard is turned off in config/ServerBuddy/Dashboard/config.json.")
        val guid = try {
            instanceGuid()
        } catch (e: Exception) {
            LOGGER.warn("[Dashboard] Could not load the installation ID.", e)
            return onResult(null, "Could not save the dashboard installation ID.")
        }
        val body = JsonObject().apply {
            addProperty("guid", guid)
            addProperty("serverName", ServerHolder.server?.motd ?: "")
            addProperty("adminName", adminName)
        }
        val pairingRequest = pairingRequests.incrementAndGet()
        val request = HttpRequest.newBuilder(URI.create("${DashboardBuild.apiBase}/api/dashboard/pair"))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .header("User-Agent", "ServerBuddy/${DashboardBuild.version}")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete { response, error ->
            if (error != null) return@whenComplete onResult(null, "Could not reach the site (${error.cause?.javaClass?.simpleName ?: error.javaClass.simpleName}).")
            val json = try {
                JsonParser.parseString(response.body()).asJsonObject
            } catch (e: Exception) {
                return@whenComplete onResult(null, "The site sent an unreadable answer (HTTP ${response.statusCode()}).")
            }
            val code = json.get("code")?.asString
            val newToken = json.get("token")?.asString
            if (code == null || newToken == null) {
                val reason = json.getAsJsonObject("error")?.get("code")?.asString ?: "HTTP ${response.statusCode()}"
                return@whenComplete onResult(null, "The site refused the pairing: $reason.")
            }
            synchronized(this) {
                if (pairingRequest != pairingRequests.get()) return@whenComplete
                closeSocket("new pairing")
                token = newToken
                plan = null
                wanted = true
                attempts = 0
                linked = false
                linkedBy = null
                connect(json.get("wsPath")?.asString ?: "/ws/mod")
                startTelemetry()
            }
            onResult(code, null)
        }
    }

    /** Ends the link from this side (the site is told by closing the connection). */
    @Synchronized
    fun disconnect(announce: Boolean = true) {
        pairingRequests.incrementAndGet()
        val was = wanted || linked
        wanted = false
        token = null
        plan = null
        linked = false
        linkedBy = null
        closeSocket("disconnected by an admin")
        if (was && announce) AdminNotices.broadcast("<yellow><bold>[ServerBuddy]</bold> Dashboard disconnected.")
    }

    fun statusLines(): List<String> = listOf(
        "Dashboard: " + when {
            linked && connected() -> "connected and synced" + (linkedBy?.let { " (by $it)" } ?: "")
            linked -> "linked, reconnecting…"
            wanted && connected() -> "waiting for the code to be typed on the site"
            wanted -> "connecting…"
            else -> "not connected (use /sb dashboard sync)"
        }
    )

    @Synchronized
    private fun instanceGuid(): String {
        val file = ModPaths.subDir().resolve("instance.id")
        if (file.isFile) return UUID.fromString(file.readText().trim()).toString()
        file.parentFile.mkdirs()
        return UUID.randomUUID().toString().also { file.writeText(it) }
    }

    // ------------------------------------------------------------------ connection

    private fun wsUrl(path: String): String {
        val base = DashboardBuild.apiBase
        return base.replaceFirst(Regex("^http"), "ws") + path
    }

    private var wsPath = "/ws/mod"

    private fun connect(path: String = wsPath) {
        wsPath = path
        val current = token ?: return
        http.newWebSocketBuilder()
            .header("Authorization", "Bearer $current")
            .header("User-Agent", "ServerBuddy/${DashboardBuild.version}")
            .connectTimeout(Duration.ofSeconds(10))
            .buildAsync(URI.create(wsUrl(path)), Listener(current))
            .whenComplete { _, error ->
                if (current != token) return@whenComplete
                if (error != null) {
                    LOGGER.warn("[Dashboard] Could not open the connection: {}", error.cause?.toString() ?: error.toString())
                    scheduleReconnect()
                } else {
                    attempts = 0
                }
            }
    }

    private fun scheduleReconnect() {
        if (!wanted) return
        attempts++
        if (attempts > 20) {
            LOGGER.warn("[Dashboard] Giving up on the connection after {} attempts.", attempts - 1)
            val wasLinked = linked
            wanted = false
            token = null
            linked = false
            if (wasLinked) AdminNotices.broadcast("<yellow><bold>[ServerBuddy]</bold> Dashboard disconnected (the site could not be reached).")
            return
        }
        val current = token
        scheduler.schedule({ if (wanted && token == current) connect() }, (5L * attempts).coerceAtMost(60L), TimeUnit.SECONDS)
    }

    private fun closeSocket(reason: String) {
        val ws = socket ?: return
        socket = null
        try {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, reason.take(100))
        } catch (e: Exception) {
            ws.abort()
        }
    }

    /** Sends one JSON message (one at a time: the JDK WebSocket does not allow overlapping sends). */
    private fun send(message: JsonObject) {
        val ws = socket ?: return
        synchronized(this) {
            sendChain = sendChain.handle { _, _ -> null }.thenCompose { ws.sendText(message.toString(), true) }
        }
    }

    private class Listener(private val pairingToken: String) : WebSocket.Listener {
        private val incoming = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            if (pairingToken != token || !wanted) {
                webSocket.abort()
                return
            }
            socket = webSocket
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            if (socket !== webSocket) return null
            incoming.append(data)
            if (last) {
                val text = incoming.toString()
                incoming.setLength(0)
                try {
                    handle(JsonParser.parseString(text).asJsonObject)
                } catch (e: Exception) {
                    LOGGER.warn("[Dashboard] Ignoring an invalid message: {}", e.toString())
                }
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String?): CompletionStage<*>? {
            if (socket !== webSocket) return null
            socket = null
            scheduleReconnect()
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            if (socket !== webSocket) return
            socket = null
            LOGGER.warn("[Dashboard] Connection error: {}", error.toString())
            scheduleReconnect()
        }
    }

    // ------------------------------------------------------------------ messages from the site

    private fun handle(message: JsonObject) {
        when (message.get("type")?.asString) {
            "hello" -> {
                plan = message.get("plan")?.takeIf { !it.isJsonNull }?.asString ?: plan
                if (message.get("linked")?.asBoolean == true) markLinked(message.get("linkedBy")?.takeIf { !it.isJsonNull }?.asString ?: "?", announce = !linked)
            }
            "linked" -> {
                plan = message.get("plan")?.asString
                markLinked(message.get("by")?.asString ?: "?", announce = true)
            }
            "unlinked" -> {
                val reason = message.get("reason")?.asString ?: "unknown"
                val was = linked
                wanted = false
                token = null
                plan = null
                linked = false
                linkedBy = null
                closeSocket(reason)
                if (was) AdminNotices.broadcast("<yellow><bold>[ServerBuddy]</bold> Dashboard disconnected: ${reasonText(reason)}.")
            }
            "request" -> handleRequest(message)
        }
    }

    private fun markLinked(by: String, announce: Boolean) {
        linked = true
        linkedBy = by
        if (announce) AdminNotices.broadcast("<green><bold>[ServerBuddy]</bold> Dashboard connected and synced by <white>${com.f4xizzz.serverbuddy.core.Texts.escape(by)}</white> (plan $plan).")
    }

    private fun reasonText(reason: String): String = when (reason) {
        "dashboard_closed" -> "the dashboard was closed"
        "dashboard_disconnected" -> "disconnected from the site"
        "replaced" -> "replaced by a new pairing"
        "plan_expired" -> "the plan expired"
        "plan_suspended" -> "the plan is suspended"
        "trial_expired" -> "the 5-day trial ended, choose a plan at ${DashboardBuild.apiBase}/plans"
        "subscription_required" -> "a subscription is required"
        "plan_changed" -> "the plan changed, run /sb dashboard sync again"
        "server_stopped" -> "the site restarted"
        else -> reason
    }

    private fun handleRequest(message: JsonObject) {
        val id = message.get("id")
        val method = message.get("method")?.asString ?: return respond(id, null, "bad_request", null)
        val params = message.get("params")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        val by = message.get("by")?.asString ?: "?"
        val server = ServerHolder.server ?: return respond(id, null, "mod_offline", null)
        val requestSocket = socket
        server.execute {
            if (!linked || requestSocket == null || socket !== requestSocket) return@execute
            try {
                respond(id, DashboardProviders.handle(server, method, params, by), null, null)
            } catch (e: DashboardError) {
                respond(id, null, e.code, e.message?.takeIf { it != e.code })
            } catch (e: Throwable) {
                LOGGER.error("[Dashboard] Error handling '{}'", method, e)
                respond(id, null, "error", e.javaClass.simpleName)
            }
        }
    }

    private fun respond(id: JsonElement?, result: JsonElement?, errorCode: String?, errorMessage: String?) {
        send(JsonObject().apply {
            addProperty("type", "response")
            add("id", id)
            addProperty("ok", errorCode == null)
            if (errorCode == null) {
                add("result", result)
            } else {
                add("error", JsonObject().apply {
                    addProperty("code", errorCode)
                    if (errorMessage != null) addProperty("message", errorMessage)
                })
            }
        })
    }

    // ------------------------------------------------------------------ telemetry (pushed every 5 s while linked)

    private fun startTelemetry() {
        if (telemetryStarted) return
        telemetryStarted = true
        scheduler.scheduleWithFixedDelay({
            if (!linked || socket == null) return@scheduleWithFixedDelay
            val server = ServerHolder.server ?: return@scheduleWithFixedDelay
            server.execute {
                val runtime = Runtime.getRuntime()
                val mspt = server.averageTickTimeNanos / 1_000_000.0
                send(JsonObject().apply {
                    addProperty("type", "event")
                    addProperty("topic", "telemetry")
                    add("data", JsonObject().apply {
                        addProperty("tps", Math.round(minOf(20.0, 1000.0 / maxOf(mspt, 50.0)) * 10) / 10.0)
                        addProperty("online", server.playerCount)
                        addProperty("usedMb", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024)
                        addProperty("uptimeSec", ManagementFactory.getRuntimeMXBean().uptime / 1000)
                    })
                })
            }
        }, 5, 5, TimeUnit.SECONDS)
    }
}
