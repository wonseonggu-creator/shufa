package com.seonggu.seoye

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileLauncher: ActivityResultLauncher<Intent>

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fileLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            filePathCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            )
            filePathCallback = null
        }

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                return try {
                    fileLauncher.launch(Intent.createChooser(intent, "选择照片"))
                    true
                } catch (e: Exception) {
                    filePathCallback = null
                    false
                }
            }
        }

        webView.addJavascriptInterface(Bridge(), "AndroidBridge")
        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class Bridge {

        @JavascriptInterface
        fun callClaude(id: String, apiKey: String, body: String) {
            Thread {
                var b64 = ""
                var err = ""
                try {
                    val conn = URL("https://api.anthropic.com/v1/messages")
                        .openConnection() as HttpsURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("x-api-key", apiKey)
                    conn.setRequestProperty("anthropic-version", "2023-06-01")
                    conn.doOutput = true
                    conn.connectTimeout = 30000
                    conn.readTimeout = 120000
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val text = stream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
                    if (code in 200..299) {
                        b64 = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    } else {
                        err = "HTTP_$code"
                    }
                } catch (e: Exception) {
                    err = "NETWORK"
                }
                runOnUiThread {
                    webView.evaluateJavascript("onClaudeResult('$id','$b64','$err')", null)
                }
            }.start()
        }

        @JavascriptInterface
        fun listTie(): String {
            return try {
                val names = assets.list("tie")
                    ?.filter { it.matches(Regex("(?i).+\\.(jpg|jpeg|png|webp)")) }
                    ?.sorted() ?: emptyList()
                org.json.JSONArray(names).toString()
            } catch (e: Exception) { "[]" }
        }

        @JavascriptInterface
        fun readTie(name: String): String {
            if (name.contains("..") || name.contains("/")) return ""
            return try {
                val bytes = assets.open("tie/$name").readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } catch (e: Exception) { "" }
        }

        @JavascriptInterface
        fun printPage() {
            runOnUiThread {
                val pm = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val adapter = webView.createPrintDocumentAdapter("字帖")
                pm.print("打印字帖", adapter, PrintAttributes.Builder().build())
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
