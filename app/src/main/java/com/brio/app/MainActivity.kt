package com.brio.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.brio.app.databinding.ActivityMainBinding
import java.io.ByteArrayOutputStream
import android.util.Base64

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCallback: ValueCallback<Array<Uri>>? = null

    // Launcher para seleção de imagem da galeria
    private val imageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = result.data?.data?.let { arrayOf(it) }
            imageCallback?.onReceiveValue(uris)
        } else {
            imageCallback?.onReceiveValue(null)
        }
        imageCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tela cheia / imersiva
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { ctrl ->
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
            ctrl.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        with(binding.webView) {
            // Configurações essenciais
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true          // localStorage
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(false)
                displayZoomControls = false
                builtInZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = false
                // Permite acesso a arquivos locais
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
            }

            // Bridge JavaScript → Kotlin
            addJavascriptInterface(BrioBridge(this@MainActivity), "AndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Injeta informações do dispositivo
                    view?.evaluateJavascript(
                        "if(window.onAndroidReady) window.onAndroidReady('${Build.MODEL}');",
                        null
                    )
                }
                override fun shouldOverrideUrlLoading(
                    view: WebView?, request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return false
                    return if (url.startsWith("http") || url.startsWith("https")) {
                        // Links externos abrem no browser
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        true
                    } else false
                }
            }

            webChromeClient = object : WebChromeClient() {
                // Suporte a seleção de arquivo (foto de perfil, capa)
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    imageCallback?.onReceiveValue(null)
                    imageCallback = filePathCallback
                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    intent.type = "image/*"
                    imageLauncher.launch(intent)
                    return true
                }

                // Permite alerts/confirms do JS
                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    result?.confirm()
                    return true
                }
                override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    result?.confirm()
                    return true
                }
            }

            // Carrega o app da pasta assets
            loadUrl("file:///android_asset/index.html")
        }
    }

    // Botão voltar navega no histórico do WebView
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && binding.webView.canGoBack()) {
            binding.webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}

// ── JAVASCRIPT BRIDGE ────────────────────────────────
// Funções que o JS pode chamar via AndroidBridge.metodo()
class BrioBridge(private val activity: Activity) {

    @JavascriptInterface
    fun showToast(message: String) {
        activity.runOnUiThread {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun getDeviceModel(): String = Build.MODEL

    @JavascriptInterface
    fun getAndroidVersion(): String = Build.VERSION.RELEASE

    @JavascriptInterface
    fun vibrate() {
        // vibração curta de feedback
    }
}
