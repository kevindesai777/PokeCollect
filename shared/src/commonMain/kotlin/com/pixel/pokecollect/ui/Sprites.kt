package com.pixel.pokecollect.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import pokecollect.shared.generated.resources.Res
import kotlin.math.min
import kotlin.math.roundToInt

/** A sprite plus the bounding box of its visible pixels (classic sprites carry lots of transparent padding). */
class Sprite(val bitmap: ImageBitmap, val contentOffset: IntOffset, val contentSize: IntSize)

/**
 * Sprites are bundled resources (files/sprites/{id}.png, fetched by scripts/fetch_sprites.py).
 * They are not in the public repo, so a missing file is normal and yields null.
 */
private object SpriteCache {
    private const val MAX_ENTRIES = 200 // 96x96 ARGB ≈ 36 KB each
    private val mutex = Mutex()
    private val sprites = LinkedHashMap<Int, Sprite?>()

    suspend fun get(id: Int): Sprite? {
        mutex.withLock { if (sprites.containsKey(id)) return sprites[id] }
        val sprite = withContext(Dispatchers.IO) {
            runCatching { Res.readBytes("files/sprites/$id.png").decodeToImageBitmap().trimmed() }.getOrNull()
        }
        mutex.withLock {
            sprites[id] = sprite
            if (sprites.size > MAX_ENTRIES) sprites.remove(sprites.keys.first())
        }
        return sprite
    }

    private fun ImageBitmap.trimmed(): Sprite {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        var minX = width; var minY = height; var maxX = -1; var maxY = -1
        for (y in 0 until height) for (x in 0 until width) {
            if (pixels[y * width + x] ushr 24 != 0) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return Sprite(this, IntOffset.Zero, IntSize(width, height))
        return Sprite(this, IntOffset(minX, minY), IntSize(maxX - minX + 1, maxY - minY + 1))
    }
}

@Composable
fun rememberSprite(id: Int): State<Sprite?> =
    produceState<Sprite?>(initialValue = null, id) { value = SpriteCache.get(id) }

/** Draws the sprite's visible area scaled to fit, centred, with crisp (nearest-neighbour) pixels. */
@Composable
fun SpriteImage(sprite: Sprite, modifier: Modifier = Modifier, colorFilter: ColorFilter? = null) {
    Canvas(modifier) {
        val src = sprite.contentSize
        val scale = min(size.width / src.width, size.height / src.height)
        val dst = IntSize((src.width * scale).roundToInt(), (src.height * scale).roundToInt())
        drawImage(
            image = sprite.bitmap,
            srcOffset = sprite.contentOffset,
            srcSize = src,
            dstOffset = IntOffset(((size.width - dst.width) / 2).roundToInt(), ((size.height - dst.height) / 2).roundToInt()),
            dstSize = dst,
            colorFilter = colorFilter,
            filterQuality = FilterQuality.None,
        )
    }
}
