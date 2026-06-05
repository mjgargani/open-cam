package com.android.example.cameraxbasic

import org.junit.Test
import org.junit.Assert.*
import java.net.URL
import java.net.HttpURLConnection

class CameraServerTest {
    @Test
    fun testServerBindsToLocalhost() {
        val server = CameraServer("127.0.0.1", 8081)
        server.start()

        try {
            val url = URL("http://127.0.0.1:8081/cam")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            // It might return 200 with an image or 404 with "Aguardando..." depending on whether the frame is set,
            // but the connection will succeed without error instead of connection refused.
            val code = connection.responseCode
            assertTrue(code == 200 || code == 404)
        } finally {
            server.stop()
        }
    }
}
