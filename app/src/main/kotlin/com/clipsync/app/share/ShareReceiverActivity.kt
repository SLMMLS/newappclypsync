package com.clipsync.app.share

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.clipsync.app.data.Repository
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Target for Android's native "Share" action from any app. This - not
 * background clipboard watching, which the OS does not allow past
 * Android 10 - is the main way of sending content TO the PC.
 */
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = Repository(applicationContext)

        lifecycleScope.launch {
            try {
                when {
                    intent?.type == "text/plain" -> handleText(repository)
                    intent?.type?.startsWith("image/") == true -> handleImage(repository)
                    else -> toast("Этот тип содержимого не поддерживается")
                }
            } catch (e: Exception) {
                toast("Не удалось отправить: ${e.message}")
            } finally {
                finish()
            }
        }
    }

    private suspend fun handleText(repository: Repository) {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (text.isNullOrBlank()) {
            toast("Пустой текст")
            return
        }
        repository.sendToPc("text", text.toByteArray(Charsets.UTF_8))
        toast("Отправлено на ПК")
    }

    private suspend fun handleImage(repository: Repository) {
        val uri = extraStreamUri() ?: run {
            toast("Не удалось прочитать изображение")
            return
        }
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
        val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (bitmap == null) {
            toast("Не удалось прочитать изображение")
            return
        }
        val png = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
        repository.sendToPc("image", png, bitmap.width, bitmap.height)
        toast("Отправлено на ПК")
    }

    private fun extraStreamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
