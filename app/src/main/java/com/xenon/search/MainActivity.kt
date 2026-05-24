package com.xenon.search

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var searchInput: EditText
    private lateinit var progressBar: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        searchInput = findViewById(R.id.searchInput)
        progressBar = findViewById(R.id.progressBar)
        val searchButton: ImageButton = findViewById(R.id.searchButton)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                searchInput.setText(url ?: "")
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress == 100) View.GONE else View.VISIBLE
            }
        }

        val submitSearch = {
            runSearch(searchInput.text.toString())
        }

        searchButton.setOnClickListener { submitSearch() }

        searchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                submitSearch()
                true
            } else {
                false
            }
        }

        if (savedInstanceState == null) {
            runSearch("Xenon search")
        }
    }

    private fun runSearch(input: String) {
        val query = input.trim()
        if (query.isBlank()) return

        val target = if (query.startsWith("http://") || query.startsWith("https://")) {
            query
        } else if (query.toUri().scheme != null && '.' in query) {
            "https://$query"
        } else {
            "https://duckduckgo.com/?q=${query.replace(" ", "+")}" 
        }

        webView.loadUrl(target)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
