package com.school.wordhelper.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", "WordHelper/1.0 (Android)")
        val code = connection.responseCode
        if (code !in 200..299) throw IOException("HTTP " + code)
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

internal suspend fun httpPostJson(
    url: String,
    jsonBody: String,
    headers: Map<String, String> = emptyMap()
): String = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(jsonBody) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299 && text.isBlank()) throw IOException("HTTP " + code)
        text
    } finally {
        connection.disconnect()
    }
}

internal suspend fun httpPostForm(url: String, body: String): String = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.outputStream.bufferedWriter().use { it.write(body) }
        val code = connection.responseCode
        if (code !in 200..299) throw IOException("HTTP " + code)
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
