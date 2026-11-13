package com.example.qr

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream

data class QrContactPayload(
    val uid: String,
    val name: String
)

object QrCodeManager {
    private const val SCHEME = "chatgaurav"
    private const val HOST = "connect"

    fun buildQrPayload(uid: String, name: String): String {
        return Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendQueryParameter("uid", uid)
            .appendQueryParameter("name", name)
            .build()
            .toString()
    }

    fun parseQrPayload(raw: String): QrContactPayload? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val uri = Uri.parse(trimmed)
            if (uri.scheme == SCHEME && uri.host == HOST) {
                val uid = uri.getQueryParameter("uid")?.trim().orEmpty()
                val name = uri.getQueryParameter("name")?.trim().orEmpty()
                if (uid.isNotBlank()) QrContactPayload(uid, name) else null
            } else if (trimmed.matches(Regex("^[a-zA-Z0-9_\\-]{10,128}$"))) {
                QrContactPayload(trimmed, "")
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun generateQrBitmap(
        content: String,
        sizePx: Int = 720,
        foregroundColor: Int = Color.parseColor("#064E4A"),
        backgroundColor: Int = Color.WHITE
    ): Bitmap {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) foregroundColor else backgroundColor)
            }
        }
        return bitmap
    }

    fun shareQrBitmap(context: Context, qrBitmap: Bitmap, userName: String) {
        val cardWidth = 900
        val cardHeight = 1120
        val card = Bitmap.createBitmap(cardWidth, cardHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(card)
        canvas.drawColor(Color.parseColor("#064E4A"))

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(RectF(60f, 90f, 840f, 1020f), 48f, 48f, cardPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#064E4A")
            textSize = 52f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText("Chat Gaurav", cardWidth / 2f, 175f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4A635E")
            textSize = 34f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(userName.take(28), cardWidth / 2f, 230f, subPaint)

        val scaledQr = Bitmap.createScaledBitmap(qrBitmap, 640, 640, false)
        canvas.drawBitmap(scaledQr, (cardWidth - 640) / 2f, 270f, null)

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4A635E")
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Scan in Chat Gaurav to connect instantly", cardWidth / 2f, 970f, footerPaint)

        val dir = File(context.cacheDir, "qr_share").apply { mkdirs() }
        val file = File(dir, "chat_gaurav_qr.png")
        FileOutputStream(file).use { out ->
            card.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Scan my Chat Gaurav QR code to message & call me securely!")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share QR Code"))
    }

    suspend fun decodeQrFromUri(context: Context, uri: Uri): QrContactPayload? {
        // Primary: ML Kit Barcode Scanning from Uri
        try {
            val image = InputImage.fromFilePath(context, uri)
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            val scanner = BarcodeScanning.getClient(options)
            val barcodes = scanner.process(image).await()
            for (barcode in barcodes) {
                val raw = barcode.rawValue ?: continue
                parseQrPayload(raw)?.let { return it }
            }
        } catch (_: Exception) {
            // Fall through to ZXing fallback
        }

        // Fallback: Decode Bitmap and scan via ZXing MultiFormatReader
        return try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return null
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = MultiFormatReader().decode(binaryBitmap)
            parseQrPayload(result.text)
        } catch (_: Exception) {
            null
        }
    }
}
