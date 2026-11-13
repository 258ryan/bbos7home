package com.example.slatemusic

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val artCache = LruCache<Long, Bitmap>(64)

private suspend fun loadArt(ctx: Context, t: Track, px: Int): Bitmap? = withContext(Dispatchers.IO) {
    artCache.get(t.albumId)?.let { return@withContext it }
    val bmp: Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 29) {
            ctx.contentResolver.loadThumbnail(t.uri, Size(px, px), null)
        } else {
            val u = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), t.albumId)
            ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it) }
        }
    } catch (_: Exception) { null }
    if (bmp != null) artCache.put(t.albumId, bmp)
    bmp
}

/** Album artwork, or a glossy dark placeholder with a note. */
@Composable
fun AlbumArt(
    track: Track?,
    modifier: Modifier = Modifier,
    px: Int = 300,
    alignment: Alignment = Alignment.Center
) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(initialValue = null, track?.albumId) {
        value = if (track == null) null else loadArt(ctx, track, px)
    }
    Box(
        modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF5B5F66), Color(0xFF1B1D20))))
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(), contentDescription = null,
                contentScale = ContentScale.Crop, alignment = alignment,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Filled.MusicNote, contentDescription = null, tint = Color(0x99FFFFFF),
                modifier = Modifier.align(Alignment.Center).fillMaxSize(0.5f)
            )
        }
    }
}
