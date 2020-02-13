//  This file is part of BVE Content Service Client (BCSC).
//
//  BCSC is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.
//
//  BCSC is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with BCSC.  If not, see <https://www.gnu.org/licenses/>.

package tk.zbx1425.bvecontentservice.ui.activity

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Message
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebView.WebViewTransport
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.activity_webview.*
import tk.zbx1425.bvecontentservice.ApplicationContext
import tk.zbx1425.bvecontentservice.R
import tk.zbx1425.bvecontentservice.api.MetadataManager
import tk.zbx1425.bvecontentservice.api.model.PackageMetadata
import tk.zbx1425.bvecontentservice.log.Log


class UGCActivity : AppCompatActivity() {

    lateinit var metadata: PackageMetadata

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        if (MetadataManager.getActiveUGCServer() == null) {
            finish()
            return
        }
        metadata = intent.getSerializableExtra("metadata") as PackageMetadata
        continueButton.visibility = View.GONE
        webView.settings.javaScriptEnabled =
            PreferenceManager.getDefaultSharedPreferences(ApplicationContext.context).getBoolean(
                "enableJavascript", true
            )
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.setSupportMultipleWindows(true)
        webView.loadUrl(
            String.format(
                "%s?pkg=%s&ver=%s&author=%s",
                MetadataManager.getActiveUGCServer()?.APIURL,
                metadata.ID, metadata.Version.get(), metadata.Author.ID
            )
        )
        webView.webChromeClient =
            MultiPageChromeClient(
                this
            )
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                loadingProgress.visibility = View.VISIBLE
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loadingProgress.visibility = View.GONE
                if (url != null && url.startsWith("https://github.com/login/oauth/authorize")) {
                    // Utterances github authorization hack.
                    // For some reason this button simply does not get enabled.
                    // So I used this to manually fix that.
                    view?.loadUrl(
                        "javascript:(function(){" +
                                "document.getElementById('js-oauth-authorize-btn').disabled = false;" +
                                "document.getElementById('js-oauth-authorize-btn').click();" +
                                "})()"
                    )
                }
                super.onPageFinished(view, url)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }

    class MultiPageChromeClient(val activity: UGCActivity) : WebChromeClient() {
        override fun onReceivedTitle(view: WebView?, title: String?) {
            super.onReceivedTitle(view, title)
            activity.title = title
        }

        @JavascriptInterface
        override fun onCreateWindow(
            view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?
        ): Boolean {
            Log.i("BCSWeb", "CreateWindow Called")
            val webView = WebView(activity)
            webView.settings.javaScriptEnabled =
                PreferenceManager.getDefaultSharedPreferences(ApplicationContext.context)
                    .getBoolean(
                        "enableJavascript", true
                    )
            webView.settings.javaScriptCanOpenWindowsAutomatically = true
            webView.settings.setSupportMultipleWindows(true)
            activity.layoutFrame.addView(webView)
            val transport =
                resultMsg!!.obj as WebViewTransport
            transport.webView = webView
            resultMsg.sendToTarget()
            webView.webViewClient = WebViewClient()
            webView.webChromeClient =
                MultiPageChromeClient(
                    activity
                )
            return true
        }

        @JavascriptInterface
        override fun onCloseWindow(window: WebView?) {
            super.onCloseWindow(window)
            if (window != null) {
                activity.layoutFrame.removeView(window)
                window.destroy()
            }
        }
    }
}