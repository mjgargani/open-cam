package com.android.example.cameraxbasic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest=Config.NONE, packageName="com.android.example.cameraxbasic")
class MainInstrumentedTest {

    @Test
    fun useAppContext() {
        // Context of the app under test
        val context = ApplicationProvider.getApplicationContext() as Context
        assertEquals("com.android.example.cameraxbasic", context.packageName)
    }
}
