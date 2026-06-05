package com.android.example.cameraxbasic.utils

import android.app.Activity
import android.os.Build
import android.view.DisplayCutout
import android.view.View
import android.view.WindowInsets
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.atomic.AtomicBoolean
import android.view.Window
import android.view.WindowManager
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.spy

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.R])
class ViewExtensionsTest {

    @Test
    fun testSimulateClick() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val button = ImageButton(activity)
        activity.setContentView(button)

        val clicked = AtomicBoolean(false)
        button.setOnClickListener { clicked.set(true) }

        button.simulateClick(ANIMATION_FAST_MILLIS)

        assertTrue("Button should be clicked", clicked.get())
        assertTrue("Button should be pressed", button.isPressed)

        ShadowLooper.idleMainLooper(ANIMATION_FAST_MILLIS + 10, java.util.concurrent.TimeUnit.MILLISECONDS)

        assertFalse("Button should not be pressed after delay", button.isPressed)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun testPadWithDisplayCutout() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = spy(View(activity))

        val insets = mock<WindowInsets>()
        val cutout = mock<DisplayCutout>()
        whenever(insets.displayCutout).thenReturn(cutout)
        whenever(cutout.safeInsetLeft).thenReturn(10)
        whenever(cutout.safeInsetTop).thenReturn(20)
        whenever(cutout.safeInsetRight).thenReturn(30)
        whenever(cutout.safeInsetBottom).thenReturn(40)

        view.padWithDisplayCutout()
        view.dispatchApplyWindowInsets(insets)

        verify(view).setPadding(10, 20, 30, 40)
    }

    @Test
    fun testShowImmersive() {
        val dialog = mock<AlertDialog>()
        val window = mock<Window>()
        val view = mock<View>()

        whenever(dialog.window).thenReturn(window)
        whenever(window.decorView).thenReturn(view)

        dialog.showImmersive()

        verify(window).setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        verify(dialog).show()
        verify(window).clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O])
    fun testShowImmersiveLegacy() {
        val dialog = mock<AlertDialog>()
        val window = mock<Window>()
        val view = mock<View>()

        whenever(dialog.window).thenReturn(window)
        whenever(window.decorView).thenReturn(view)

        dialog.showImmersive()

        verify(window).setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        verify(dialog).show()
        verify(window).clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }
}
