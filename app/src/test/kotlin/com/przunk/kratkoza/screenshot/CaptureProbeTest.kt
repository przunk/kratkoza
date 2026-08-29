package com.przunk.kratkoza.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/**
 * Can this environment produce real pixels at all?
 *
 * The compose test rule's captureToImage cannot: it asks the platform to redraw a window and waits
 * for the frame, and there is no window here to draw one. Drawing the view straight onto a bitmap
 * asks for the same pixels without involving one.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureProbeTest {

    @Test
    fun `a flat colour comes back as that colour`() {
        val bitmap = render(120, 80) {
            Box(Modifier.fillMaxSize().background(Color(0xFF2196F3)))
        }

        File("build/probe.png").apply { parentFile?.mkdirs() }
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        assertEquals(0xFF2196F3.toInt(), bitmap.getPixel(60, 40))
    }
}

/**
 * Composes [content] at a fixed size and draws it, with no window in the way.
 *
 * Time is advanced by a stated amount rather than run to idle. A screen carrying an endless
 * animation is never idle — it posts the next frame for ever — so waiting for quiet hangs instead of
 * failing. Advancing to [atMillis] also picks *which* frame is drawn, which is the only way to see a
 * particular moment of a loop.
 */
internal fun render(
    width: Int,
    height: Int,
    atMillis: Long = 0,
    content: @androidx.compose.runtime.Composable () -> Unit,
): Bitmap {
    val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    activity.setContent(content = content)

    val view: View = activity.window.decorView
    view.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
    )
    view.layout(0, 0, width, height)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(atMillis.coerceAtLeast(FIRST_FRAME_MILLIS)))

    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
}

/** Enough for composition and one layout pass; less and nothing has been drawn yet. */
private const val FIRST_FRAME_MILLIS = 32L
