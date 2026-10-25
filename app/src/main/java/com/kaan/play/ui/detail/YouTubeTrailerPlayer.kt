package com.kaan.play.ui.detail

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.kaan.play.R

/** Dedicated native window avoids composing Chromium's video surface inside a Compose dialog. */
class YouTubeTrailerActivity : AppCompatActivity() {
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val videoKey = intent.getStringExtra(EXTRA_VIDEO_KEY)
        val html = videoKey?.let(::youtubeTrailerEmbedHtml)
        if (html == null) {
            finish()
            return
        }

        val web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            setLayerType(View.LAYER_TYPE_NONE, null)
            overScrollMode = View.OVER_SCROLL_NEVER
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString = settings.userAgentString
                .replace("; wv", "")
                .replace(Regex("Version/\\d+\\.\\d+ "), "")
            isFocusable = true
            isFocusableInTouchMode = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return true
                    return shouldBlockTrailerNavigation(request.isForMainFrame, uri.scheme, uri.host)
                }
            }
            webChromeClient = WebChromeClient()
        }
        webView = web
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(
                AspectRatioFrameLayout(this@YouTubeTrailerActivity).apply {
                    addView(web, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ))
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                ),
            )
            addView(
                Button(this@YouTubeTrailerActivity).apply {
                    text = getString(R.string.detail_trailer_close)
                    minHeight = resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
                    setOnClickListener { finish() }
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                ).apply {
                    bottomMargin = (16 * resources.displayMetrics.density).toInt()
                },
            )
        }
        setContentView(root)

        // A real HTTPS base document gives the official YouTube iframe a valid Referer.
        web.loadDataWithBaseURL(
            "https://www.youtube-nocookie.com/",
            html,
            "text/html",
            "UTF-8",
            null,
        )
    }

    override fun onDestroy() {
        webView?.let { web ->
            web.stopLoading()
            web.loadUrl("about:blank")
            web.webChromeClient = null
            web.webViewClient = WebViewClient()
            web.destroy()
        }
        webView = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    private class AspectRatioFrameLayout(context: Context) : FrameLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
            val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
            if (availableWidth == 0 || availableHeight == 0) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }
            val ratio = 16f / 9f
            val measuredWidth: Int
            val measuredHeight: Int
            if (availableWidth.toFloat() / availableHeight > ratio) {
                measuredHeight = availableHeight
                measuredWidth = (availableHeight * ratio).toInt()
            } else {
                measuredWidth = availableWidth
                measuredHeight = (availableWidth / ratio).toInt()
            }
            val exactWidth = MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY)
            val exactHeight = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
            measureChildren(exactWidth, exactHeight)
            setMeasuredDimension(measuredWidth, measuredHeight)
        }
    }

    companion object {
        private const val EXTRA_VIDEO_KEY = "trailer_video_key"

        fun createIntent(context: Context, videoKey: String): Intent? {
            if (youtubeTrailerEmbedUrl(videoKey) == null) return null
            return Intent(context, YouTubeTrailerActivity::class.java)
                .putExtra(EXTRA_VIDEO_KEY, videoKey.trim())
        }
    }
}
