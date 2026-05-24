package com.xenon.search

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var searchInput: EditText
    private lateinit var progressBar: ProgressBar

    private val prefs by lazy { getSharedPreferences("xenon_settings", MODE_PRIVATE) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        searchInput = findViewById(R.id.searchInput)
        progressBar = findViewById(R.id.progressBar)

        val searchButton: ImageButton = findViewById(R.id.searchButton)
        val homeButton: ImageButton = findViewById(R.id.homeButton)
        val settingsButton: ImageButton = findViewById(R.id.settingsButton)
        val backButton: ImageButton = findViewById(R.id.backButton)
        val forwardButton: ImageButton = findViewById(R.id.forwardButton)

        applyWebSettings()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                searchInput.setText(url ?: "")
                backButton.alpha = if (webView.canGoBack()) 1f else 0.5f
                forwardButton.alpha = if (webView.canGoForward()) 1f else 0.5f
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return handleInternalRoute(url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress == 100) View.GONE else View.VISIBLE
            }
        }

        val submitSearch = { runSearch(searchInput.text.toString()) }

        searchButton.setOnClickListener { submitSearch() }
        homeButton.setOnClickListener { openHome() }
        settingsButton.setOnClickListener { openSettings() }
        backButton.setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        forwardButton.setOnClickListener { if (webView.canGoForward()) webView.goForward() }

        searchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                submitSearch()
                true
            } else {
                false
            }
        }

        if (savedInstanceState == null) {
            openHome()
        }
    }

    private fun applyWebSettings() {
        val desktopMode = prefs.getBoolean("desktop_mode", false)
        webView.settings.apply {
            javaScriptEnabled = prefs.getBoolean("javascript_enabled", true)
            domStorageEnabled = prefs.getBoolean("dom_storage_enabled", true)
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString = if (desktopMode) {
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            } else {
                WebSettings.getDefaultUserAgent(this@MainActivity)
            }
        }
    }

    private fun runSearch(input: String) {
        val query = input.trim()
        if (query.isBlank()) return

        if (handleInternalRoute(query)) return

        val target = if (query.startsWith("http://") || query.startsWith("https://")) {
            query
        } else if (query.toUri().scheme != null && '.' in query) {
            "https://$query"
        } else {
            "${prefs.getString("search_engine", "https://duckduckgo.com/?q=%s")}".format(query.replace(" ", "+"))
        }

        webView.loadUrl(target)
    }

    private fun handleInternalRoute(rawUrl: String): Boolean {
        return when (rawUrl.lowercase()) {
            "xenon://home" -> {
                openHome(); true
            }
            "xenon://settings" -> {
                openSettings(); true
            }
            "xenon://set/engine/google" -> {
                saveEngine("https://www.google.com/search?q=%s", "Google"); true
            }
            "xenon://set/engine/ddg" -> {
                saveEngine("https://duckduckgo.com/?q=%s", "DuckDuckGo"); true
            }
            "xenon://set/engine/bing" -> {
                saveEngine("https://www.bing.com/search?q=%s", "Bing"); true
            }
            "xenon://set/js/on" -> { saveBool("javascript_enabled", true, "JavaScript açık"); true }
            "xenon://set/js/off" -> { saveBool("javascript_enabled", false, "JavaScript kapalı"); true }
            "xenon://set/dom/on" -> { saveBool("dom_storage_enabled", true, "DOM Storage açık"); true }
            "xenon://set/dom/off" -> { saveBool("dom_storage_enabled", false, "DOM Storage kapalı"); true }
            "xenon://set/desktop/on" -> { saveBool("desktop_mode", true, "Masaüstü modu açık"); true }
            "xenon://set/desktop/off" -> { saveBool("desktop_mode", false, "Masaüstü modu kapalı"); true }
            else -> false
        }
    }

    private fun saveEngine(url: String, label: String) {
        prefs.edit().putString("search_engine", url).apply()
        Toast.makeText(this, "Arama motoru: $label", Toast.LENGTH_SHORT).show()
        openSettings()
    }

    private fun saveBool(key: String, value: Boolean, message: String) {
        prefs.edit().putBoolean(key, value).apply()
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        applyWebSettings()
        openSettings()
    }

    private fun openHome() {
        webView.loadDataWithBaseURL(
            "xenon://home",
            homeHtml(),
            "text/html",
            "UTF-8",
            null
        )
        searchInput.setText("xenon://home")
    }

    private fun openSettings() {
        webView.loadDataWithBaseURL(
            "xenon://settings",
            settingsHtml(),
            "text/html",
            "UTF-8",
            null
        )
        searchInput.setText("xenon://settings")
    }

    private fun homeHtml(): String = """
        <html><head><meta name='viewport' content='width=device-width, initial-scale=1' />
        <style>
        body{font-family:sans-serif;background:linear-gradient(180deg,#061128,#0d2248);color:#e8f0ff;padding:24px}
        .card{background:#101f3dcc;border:1px solid #66a6ff55;border-radius:16px;padding:16px;margin-bottom:16px;box-shadow:0 8px 30px #00000050}
        a{display:inline-block;margin:6px 8px 6px 0;padding:10px 14px;border-radius:12px;background:#3a7bfd;color:white;text-decoration:none}
        h1{margin-top:0} .muted{opacity:.8}
        </style></head><body>
        <div class='card'><h1>⚛ Xenon Home</h1><p class='muted'>Hydrogen tarzı sade, hızlı ve modern arama deneyimi.</p>
        <a href='xenon://settings'>Ayarlar</a>
        <a href='https://duckduckgo.com'>Aramaya Başla</a></div>
        <div class='card'><h3>Bilgin VPN</h3><p>Gizlilik odaklı gezinti modu. (Bilgilendirme kartı)</p></div>
        <div class='card'><h3>Hızlı Linkler</h3>
        <a href='https://news.ycombinator.com'>Hacker News</a><a href='https://github.com'>GitHub</a><a href='https://www.wikipedia.org'>Wikipedia</a></div>
        </body></html>
    """.trimIndent()

    private fun settingsHtml(): String {
        val engine = prefs.getString("search_engine", "https://duckduckgo.com/?q=%s") ?: ""
        fun mark(condition: Boolean) = if (condition) "✅" else "⬜"

        return """
            <html><head><meta name='viewport' content='width=device-width, initial-scale=1'/>
            <style>
            body{font-family:sans-serif;background:#07162f;color:#e8f0ff;padding:22px}
            .card{background:#13264bcc;border:1px solid #68a1ff44;border-radius:14px;padding:14px;margin-bottom:12px}
            a{display:inline-block;margin:6px 6px 0 0;padding:8px 12px;border-radius:10px;background:#4a88ff;color:#fff;text-decoration:none}
            </style></head><body>
            <h2>Xenon Settings</h2>
            <div class='card'><b>Arama Motoru</b><br/>
            ${mark(engine.contains("google"))} <a href='xenon://set/engine/google'>Google</a>
            ${mark(engine.contains("duckduckgo"))} <a href='xenon://set/engine/ddg'>DuckDuckGo</a>
            ${mark(engine.contains("bing"))} <a href='xenon://set/engine/bing'>Bing</a></div>

            <div class='card'><b>Gezinme Ayarları</b><br/>
            ${mark(prefs.getBoolean("javascript_enabled", true))} JavaScript
            <a href='xenon://set/js/on'>Aç</a><a href='xenon://set/js/off'>Kapat</a><br/>
            ${mark(prefs.getBoolean("dom_storage_enabled", true))} DOM Storage
            <a href='xenon://set/dom/on'>Aç</a><a href='xenon://set/dom/off'>Kapat</a><br/>
            ${mark(prefs.getBoolean("desktop_mode", false))} Masaüstü Modu
            <a href='xenon://set/desktop/on'>Aç</a><a href='xenon://set/desktop/off'>Kapat</a></div>

            <div class='card'><b>Rotalar</b><br/>
            <a href='xenon://home'>xenon://home</a>
            <a href='xenon://settings'>xenon://settings</a></div>
            </body></html>
        """.trimIndent()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
