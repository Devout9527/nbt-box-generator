package cn.nbtgen.app

import android.content.res.AssetManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 极简 HTTP 服务器（仅监听 127.0.0.1），给 WebView 提供首页与 api 接口。
 * 纯框架实现，无第三方依赖。
 */
class MiniHttpServer(
    private val assets: AssetManager,
    private val filesDir: File,
    private val version: String
) {
    private var serverSocket: ServerSocket? = null
    @Volatile private var running = false
    var port: Int = 0
        private set

    fun start(): Int {
        // 1) 优先绑定 IPv4 回环 127.0.0.1
        var ss: ServerSocket = try {
            ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        } catch (e: Exception) {
            ServerSocket(0)
        }
        serverSocket = ss
        port = ss.localPort

        // 2) 自检：连不上就回退绑定所有网卡
        if (!canConnect(port)) {
            log("自检失败：127.0.0.1:$port 连不上，回退绑定 0.0.0.0")
            try { ss.close() } catch (_: Exception) {}
            ss = try { ServerSocket(0) } catch (e: Exception) {
                log("绑定 0.0.0.0 也失败：${e.message}"); throw e
            }
            serverSocket = ss
            port = ss.localPort
        }

        running = true
        val listen = ss
        Thread {
            while (running) {
                val sock = try { listen.accept() } catch (e: Exception) { break }
                Thread {
                    try { handle(sock) } catch (_: Exception) {} finally {
                        try { sock.close() } catch (_: Exception) {}
                    }
                }.apply { isDaemon = true }.start()
            }
        }.apply { isDaemon = true; name = "nbt-http" }.start()

        log("=== NBT 生成器 v$version 启动，127.0.0.1:$port ===")
        log(if (canConnect(port)) "自检：127.0.0.1:$port 可连接" else "自检：仍连不上 127.0.0.1:$port")
        return port
    }

    private fun canConnect(p: Int): Boolean = try {
        Socket("127.0.0.1", p).close(); true
    } catch (e: Exception) { false }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        log("=== 已停止 ===")
    }

    // ---------- 请求处理 ----------
    private fun handle(sock: Socket) {
        val ins = BufferedInputStream(sock.getInputStream())
        val out = sock.getOutputStream()
        val reqLine = readLine(ins) ?: return
        val parts = reqLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0]
        val path = parts[1]

        var contentLen = 0
        while (true) {
            val line = readLine(ins) ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) {
                val hk = line.substring(0, idx).trim().lowercase()
                if (hk == "content-length") contentLen = line.substring(idx + 1).trim().toIntOrNull() ?: 0
            }
        }
        val body = if (contentLen > 0) readN(ins, contentLen) else ByteArray(0)
        route(method, path, body, out)
    }

    private fun route(method: String, path: String, body: ByteArray, out: OutputStream) {
        val p = path.substringBefore("?")
        val query = if (path.contains("?")) path.substringAfter("?") else ""
        when {
            method == "OPTIONS" -> respond(out, 204, "", "text/plain; charset=utf-8")

            method == "GET" && (p == "/" || p == "/index.html" || p == "/nbt_gen_web.html") -> {
                val html = try {
                    assets.open("index.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
                        .replace("__VER__", version)
                } catch (e: Exception) {
                    "<h1>index.html 缺失</h1><p>${e.message}</p>"
                }
                respond(out, 200, html, "text/html; charset=utf-8")
            }

            method == "GET" && p == "/api/ids" ->
                respond(out, 200, idsJson().toString(), "application/json; charset=utf-8")

            method == "GET" && p == "/api/log" -> {
                val n = query.split("&").map { it.split("=") }
                    .firstOrNull { it.size == 2 && it[0] == "n" }?.get(1)?.toIntOrNull() ?: 200
                respond(out, 200, logJson(n).toString(), "application/json; charset=utf-8")
            }

            method == "POST" && p == "/api/generate" -> {
                try {
                    val cfg = JSONObject(String(body, Charsets.UTF_8))
                    log("生成请求 mode=${cfg.optString("mode")}")
                    val b = buildSnbt(cfg)
                    log("生成成功 条目=${b.count} 输出=${b.snbt.length}字节")
                    respond(out, 200, JSONObject().put("ok", true)
                        .put("snbt", b.snbt).put("count", b.count).toString(),
                        "application/json; charset=utf-8")
                } catch (e: Exception) {
                    log("生成失败: ${e.message}")
                    respond(out, 400, JSONObject().put("ok", false)
                        .put("error", e.message ?: "错误").toString(),
                        "application/json; charset=utf-8")
                }
            }

            method == "POST" && p == "/api/parse" -> {
                try {
                    val data = JSONObject(String(body, Charsets.UTF_8))
                    val snbt = data.optString("snbt")
                    log("解析请求 输入=${snbt.length}字节")
                    val model = ModelParser.parseBoxModel(snbt)
                    log("解析成功 mode=${model.optString("mode")}")
                    respond(out, 200, JSONObject().put("ok", true).put("model", model).toString(),
                        "application/json; charset=utf-8")
                } catch (e: Exception) {
                    log("解析失败: ${e.message}")
                    respond(out, 400, JSONObject().put("ok", false)
                        .put("error", e.message ?: "错误").toString(),
                        "application/json; charset=utf-8")
                }
            }

            method == "POST" && p == "/api/log" -> {
                try {
                    val data = JSONObject(String(body, Charsets.UTF_8))
                    log("[前端] ${data.optString("msg")}")
                } catch (_: Exception) {}
                respond(out, 200, JSONObject().put("ok", true).toString(),
                    "application/json; charset=utf-8")
            }

            else -> respond(out, 404, "{\"error\":\"not found\"}", "application/json; charset=utf-8")
        }
    }

    // ---------- 数据 ----------
    private fun idsJson(): JSONObject {
        val text = try {
            val f = File(filesDir, "物品ID清单.txt")
            if (f.exists()) f.readText(Charsets.UTF_8)
            else assets.open("物品ID清单.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            return JSONObject().put("ok", false).put("items", JSONArray()).put("ids", JSONArray())
        }
        val items = JSONArray(); val ids = JSONArray()
        for (raw in text.split("\n")) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val sp = line.split(Regex("\\s+"), limit = 2)
            val id = sp[0]
            val cn = if (sp.size > 1) sp[1].trim() else ""
            items.put(JSONObject().put("id", id).put("cn", cn))
            ids.put(id)
        }
        return JSONObject().put("ok", true).put("items", items).put("ids", ids)
    }

    private fun logJson(n: Int): JSONObject {
        return try {
            val f = File(filesDir, "nbt_app.log")
            val lines = if (f.exists()) f.readLines(Charsets.UTF_8) else emptyList()
            val tail = lines.takeLast(n).joinToString("\n")
            JSONObject().put("ok", true)
                .put("log", if (tail.isEmpty()) "" else tail + "\n")
                .put("file", f.absolutePath)
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.message ?: "")
        }
    }

    fun log(msg: String) {
        try {
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            File(filesDir, "nbt_app.log").appendText("[$ts] [INFO] $msg\n", Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    // ---------- HTTP 底层 ----------
    private fun respond(out: OutputStream, code: Int, body: String, ctype: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = StringBuilder()
            .append("HTTP/1.1 ").append(code).append(' ').append(codeText(code)).append("\r\n")
            .append("Content-Type: ").append(ctype).append("\r\n")
            .append("Content-Length: ").append(bytes.size).append("\r\n")
            .append("Access-Control-Allow-Origin: *\r\n")
            .append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            .append("Access-Control-Allow-Headers: Content-Type\r\n")
            .append("Cache-Control: no-store\r\n")
            .append("Connection: close\r\n\r\n")
        out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
        out.write(bytes)
        out.flush()
    }

    private fun codeText(code: Int) = when (code) {
        200 -> "OK"; 204 -> "No Content"; 400 -> "Bad Request"
        404 -> "Not Found"; 500 -> "Internal Server Error"; else -> "OK"
    }

    private fun readLine(ins: BufferedInputStream): String? {
        val bos = ByteArrayOutputStream()
        var b = ins.read()
        if (b == -1) return null
        while (b != -1) {
            if (b == 10) break
            if (b != 13) bos.write(b)
            b = ins.read()
        }
        return String(bos.toByteArray(), Charsets.ISO_8859_1)
    }

    private fun readN(ins: BufferedInputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = ins.read(buf, off, n - off)
            if (r <= 0) break
            off += r
        }
        return if (off == n) buf else buf.copyOf(off)
    }
}
