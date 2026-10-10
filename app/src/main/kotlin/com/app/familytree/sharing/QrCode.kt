package com.app.familytree.sharing

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun ShareQr(url: String) {
    val bitmap by produceState<ImageBitmap?>(null, url) {
        value = withContext(Dispatchers.Default) {
            val matrix = MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, 384, 384)
            val pixels = IntArray(384 * 384) { index -> if (matrix[index % 384, index / 384]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
            Bitmap.createBitmap(pixels, 384, 384, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    }
    bitmap?.let { Image(it, "QR code for your shared tree", Modifier.size(220.dp)) } ?: Text("Preparing QR code…")
}
