package cn.nbtgen.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {

    private var server: MiniHttpServer? = null
    private var web: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wv = WebView(this)
        web = wv
        @Suppress("SetJavaScriptEnabled")
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            textZoom = 100
        }
        wv.webViewClient = object : WebViewClient() {
            private var retried = false
            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                // 首次连接失败就重试一次（服务器可能还没就绪）
                if (request?.isForMainFrame == true && !retried) {
                    retried = true
                    view?.postDelayed({ view.loadUrl(view.url.toString()) }, 500)
                }
            }
        }
        wv.webChromeClient = WebChromeClient()
        wv.addJavascriptInterface(Bridge(), "AndroidBridge")
        setContentView(wv)

        try {
            val srv = MiniHttpServer(assets, filesDir, BuildConfig.VERSION_NAME)
            val port = srv.start()
            server = srv
            wv.loadUrl("http://127.0.0.1:$port/")
        } catch (e: Exception) {
            wv.loadData(
                "<h2>启动失败</h2><p>${e.message}</p>", "text/html", "utf-8")
        }
    }

    override fun onDestroy() {
        try { server?.stop() } catch (_: Exception) {}
        try { web?.destroy() } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val wv = web
            if (wv != null && wv.canGoBack()) { wv.goBack(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    /** 暴露给网页的接口（window.AndroidBridge） */
    inner class Bridge {
        @JavascriptInterface
        fun saveFile(name: String, content: String): String {
            return try {
                val f = File(saveDir(), safeName(name))
                f.writeText(content, Charsets.UTF_8)
                toast("已保存：${f.absolutePath}")
                f.absolutePath
            } catch (e: Exception) {
                "保存失败: ${e.message}"
            }
        }

        @JavascriptInterface
        fun copyText(text: String) {
            try {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("nbt", text))
                toast("已复制")
            } catch (e: Exception) {
                toast("复制失败: ${e.message}")
            }
        }

        @JavascriptInterface
        fun toast(msg: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show() }
        }

        @JavascriptInterface
        fun getVersion(): String = BuildConfig.VERSION_NAME

        /** 列出保存目录里的文件（按修改时间倒序），返回 JSON 数组 */
        @JavascriptInterface
        fun listFiles(): String {
            val arr = org.json.JSONArray()
            try {
                val dir = saveDir()
                dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
                    ?.forEach { f ->
                        arr.put(org.json.JSONObject()
                            .put("name", f.name)
                            .put("size", f.length())
                            .put("time", f.lastModified()))
                    }
            } catch (_: Exception) {}
            return arr.toString()
        }

        /** 读取保存目录里某个文件的内容 */
        @JavascriptInterface
        fun readFile(name: String): String {
            return try {
                File(saveDir(), safeName(name)).readText(Charsets.UTF_8)
            } catch (e: Exception) { "" }
        }

        /** 删除保存目录里的文件 */
        @JavascriptInterface
        fun deleteFile(name: String): Boolean {
            return try { File(saveDir(), safeName(name)).delete() } catch (e: Exception) { false }
        }
    }

    private fun saveDir(): File {
        val dir = getExternalFilesDir("nbt") ?: filesDir
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun safeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifEmpty { "nbt.txt" }
}
