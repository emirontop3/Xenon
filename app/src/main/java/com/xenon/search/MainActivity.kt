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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
                backButton.alpha = if (webView.canGoBack()) 1f else 0.45f
                forwardButton.alpha = if (webView.canGoForward()) 1f else 0.45f
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
            userAgentString = if (desktopMode) DESKTOP_USER_AGENT else WebSettings.getDefaultUserAgent(this@MainActivity)
        }
    }

    private fun runSearch(input: String) {
        val query = input.trim()
        if (query.isBlank()) return
        if (handleInternalRoute(query)) return

        val target = when {
            query.startsWith("http://") || query.startsWith("https://") -> query
            isDomainLike(query) -> "https://$query"
            else -> activeSearchTemplate().format(urlEncode(query))
        }

        webView.loadUrl(target)
    }

    private fun isDomainLike(text: String): Boolean {
        return !text.contains(" ") && text.contains('.') && !text.contains("://")
    }

    private fun urlEncode(query: String): String = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())

    private fun activeSearchTemplate(): String {
        return prefs.getString("search_engine", DEFAULT_SEARCH_TEMPLATE) ?: DEFAULT_SEARCH_TEMPLATE
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
            "xenon://set/engine/startpage" -> {
                saveEngine("https://www.startpage.com/sp/search?query=%s", "Startpage"); true
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
        webView.loadDataWithBaseURL("xenon://home", homeHtml(), "text/html", "UTF-8", null)
        searchInput.setText("xenon://home")
    }

    private fun openSettings() {
        webView.loadDataWithBaseURL("xenon://settings", settingsHtml(), "text/html", "UTF-8", null)
        searchInput.setText("xenon://settings")
    }

    private fun homeHtml(): String = """
        <html>
          <head>
            <meta name='viewport' content='width=device-width, initial-scale=1' />
            <style>
              :root{--bg:#060b16;--panel:#0e172a;--soft:#1e293b;--line:#334155;--txt:#e2e8f0;--muted:#94a3b8;--accent:#60a5fa;}
              body{margin:0;padding:20px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:var(--bg);color:var(--txt)}
              .wrap{max-width:760px;margin:0 auto}
              .panel{background:linear-gradient(180deg,#0f172a,#0b1220);border:1px solid var(--line);border-radius:14px;padding:16px 18px;margin-bottom:12px}
              .title{font-size:24px;font-weight:650;margin:0 0 8px}
              .muted{color:var(--muted);font-size:14px;line-height:1.5}
              .row{display:flex;gap:10px;flex-wrap:wrap;margin-top:12px}
              .btn{display:inline-block;padding:10px 14px;border-radius:10px;background:#111827;border:1px solid #2c3b56;color:var(--txt);text-decoration:none}
              .btn.primary{background:var(--accent);color:#071226;border-color:#7bb7ff;font-weight:600}
              .grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}
              .chip{padding:12px;border:1px solid #2b3a55;border-radius:10px;background:#0b1528}
            </style>
          </head>
          <body>
            <div class='wrap'>
              <div class='panel'>
                <p class='title'>Xenon</p>
                <p class='muted'>Hızlı ve sade gezinme deneyimi. Arama kutusuna kelime yaz, URL yaz veya kısa yolları kullan.</p>
                <div class='row'>
                  <a class='btn primary' href='xenon://settings'>Ayarları Aç</a>
                  <a class='btn' href='https://duckduckgo.com'>Aramaya Başla</a>
                </div>
              </div>
              <div class='grid'>
                <div class='chip'><b>Bilgin VPN</b><p class='muted'>Gizlilik odaklı bağlantı katmanı için ayrılmış alan.</p></div>
                <div class='chip'><b>Hızlı Erişim</b><p class='muted'>GitHub · Wikipedia · Hacker News</p></div>
              </div>
            </div>
          </body>
        </html>
    """.trimIndent()

    private fun settingsHtml(): String {
        val engine = activeSearchTemplate()
        fun mark(condition: Boolean) = if (condition) "●" else "○"

        return """
            <html><head><meta name='viewport' content='width=device-width, initial-scale=1'/>
            <style>
            :root{--bg:#060b16;--panel:#0e172a;--line:#334155;--txt:#e2e8f0;--muted:#94a3b8;--accent:#60a5fa;}
            body{margin:0;padding:18px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:var(--bg);color:var(--txt)}
            .panel{background:linear-gradient(180deg,#0f172a,#0b1220);border:1px solid var(--line);border-radius:14px;padding:14px 16px;margin-bottom:12px}
            .title{margin:0 0 10px;font-size:22px}
            .row{margin:8px 0}
            a{display:inline-block;margin:6px 6px 0 0;padding:8px 11px;border-radius:9px;background:#111827;border:1px solid #2c3b56;color:var(--txt);text-decoration:none}
            .active{border-color:#7bb7ff;background:#12233e}
            .muted{color:var(--muted);font-size:13px}
            </style></head><body>
            <h2 class='title'>Xenon Settings</h2>
            <div class='panel'><b>Arama Motoru</b><div class='muted'>Varsayılan arama sağlayıcını seç.</div><div class='row'>
            <a class='${if (engine.contains("google")) "active" else ""}' href='xenon://set/engine/google'>${mark(engine.contains("google"))} Google</a>
            <a class='${if (engine.contains("duckduckgo")) "active" else ""}' href='xenon://set/engine/ddg'>${mark(engine.contains("duckduckgo"))} DuckDuckGo</a>
            <a class='${if (engine.contains("bing")) "active" else ""}' href='xenon://set/engine/bing'>${mark(engine.contains("bing"))} Bing</a>
            <a class='${if (engine.contains("startpage")) "active" else ""}' href='xenon://set/engine/startpage'>${mark(engine.contains("startpage"))} Startpage</a>
            </div></div>

            <div class='panel'><b>Gezinme Tercihleri</b><div class='row'>
            <div>${mark(prefs.getBoolean("javascript_enabled", true))} JavaScript <a href='xenon://set/js/on'>Aç</a><a href='xenon://set/js/off'>Kapat</a></div>
            <div>${mark(prefs.getBoolean("dom_storage_enabled", true))} DOM Storage <a href='xenon://set/dom/on'>Aç</a><a href='xenon://set/dom/off'>Kapat</a></div>
            <div>${mark(prefs.getBoolean("desktop_mode", false))} Masaüstü Modu <a href='xenon://set/desktop/on'>Aç</a><a href='xenon://set/desktop/off'>Kapat</a></div>
            </div></div>

            <div class='panel'><b>Dahili Sayfalar</b><div class='row'>
            <a href='xenon://home'>xenon://home</a>
            <a href='xenon://settings'>xenon://settings</a>
            </div></div>
            </body></html>
        """.trimIndent()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    companion object {
        private const val DEFAULT_SEARCH_TEMPLATE = "https://duckduckgo.com/?q=%s"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
