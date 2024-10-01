package com.example.instadownload

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    private lateinit var urlEditText: EditText
    private lateinit var downloadButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlEditText = findViewById(R.id.urlEditText)
        downloadButton = findViewById(R.id.downloadButton)

        downloadButton.setOnClickListener {
            val url = urlEditText.text.toString()
            if (url.isNotEmpty()) {
                if (checkPermissions()) {
                    downloadInstagramVideo(url)
                } else {
                    requestPermissions()
                }
            } else {
                Toast.makeText(this, "Por favor, ingrese una URL", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_CODE
        )
    }

    private fun downloadInstagramVideo(url: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val shortcode = extractShortcode(url)
                Log.d("Instagram", "Shortcode extraído: $shortcode")
                val videoUrl = getVideoUrl(shortcode)
                if (videoUrl != null) {
                    withContext(Dispatchers.Main) {
                        startDownload(videoUrl, shortcode)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "No se pudo obtener la URL del video", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("Instagram", "Error en downloadInstagramVideo", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun extractShortcode(url: String): String {
        val regex = Regex("/reel/([^/]+)/")
        val matchResult = regex.find(url)
        return matchResult?.groupValues?.get(1) ?: throw IllegalArgumentException("URL inválida")
    }

    private suspend fun getVideoUrl(shortcode: String): String? = withContext(Dispatchers.IO) {
        val url = URL("https://www.instagram.com/graphql/query/?query_hash=b3055c01b4b222b8a47dc12b090e4e64&variables={\"shortcode\":\"$shortcode\"}")
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")

        try {
            Log.d("Instagram", "Iniciando conexión a la API de Instagram")
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            reader.close()

            val jsonResponse = JSONObject(response.toString())
            Log.d("Instagram", "Respuesta de la API recibida")

            val mediaData = jsonResponse
                .getJSONObject("data")
                .getJSONObject("shortcode_media")

            if (mediaData.has("video_url")) {
                val videoUrl = mediaData.getString("video_url")
                Log.d("Instagram", "URL de video encontrada: $videoUrl")
                return@withContext videoUrl
            } else {
                Log.e("Instagram", "No se encontró la URL del video en la respuesta de la API")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e("Instagram", "Error obteniendo la URL del video", e)
            return@withContext null
        } finally {
            connection.disconnect()
        }
    }

    private fun startDownload(videoUrl: String, filename: String) {
        Log.d("Instagram", "Iniciando descarga de $videoUrl")
        val request = DownloadManager.Request(Uri.parse(videoUrl))
            .setTitle("Instagram Video $filename")
            .setDescription("Descargando video de Instagram")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$filename.mp4")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)

        Toast.makeText(this, "Descarga iniciada", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val STORAGE_PERMISSION_CODE = 1
    }
}