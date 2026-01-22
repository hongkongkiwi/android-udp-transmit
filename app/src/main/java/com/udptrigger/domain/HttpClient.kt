package com.udptrigger.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * HTTP client for webhook triggers and REST API integration.
 * Supports GET, POST, PUT, DELETE methods with JSON/form data.
 */
class HttpClient {

    companion object {
        private const val DEFAULT_TIMEOUT = 10000 // 10 seconds
    }

    data class HttpRequest(
        val url: String,
        val method: HttpMethod = HttpMethod.GET,
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null,
        val contentType: ContentType = ContentType.NONE,
        val timeout: Int = DEFAULT_TIMEOUT
    )

    enum class HttpMethod {
        GET, POST, PUT, DELETE, PATCH
    }

    enum class ContentType {
        NONE, JSON, FORM_URLENCODED, TEXT_PLAIN, APPLICATION_OCTET_STREAM
    }

    data class HttpResponse(
        val code: Int,
        val message: String,
        val body: String?,
        val headers: Map<String, String>,
        val timestamp: Long = System.currentTimeMillis(),
        val durationMs: Long = 0
    ) {
        val isSuccess: Boolean get() = code in 200..299
        val isRedirect: Boolean get() = code in 300..399
        val isClientError: Boolean get() = code in 400..499
        val isServerError: Boolean get() = code in 500..599
    }

    /**
     * Execute HTTP request
     */
    suspend fun execute(request: HttpRequest): Result<HttpResponse> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            val url = URL(request.url)
            val connection = url.openConnection() as HttpURLConnection

            // Set timeout
            connection.connectTimeout = request.timeout
            connection.readTimeout = request.timeout

            // Set method
            connection.requestMethod = request.method.name

            // Set headers
            if (request.contentType != ContentType.NONE) {
                connection.setRequestProperty(
                    "Content-Type",
                    when (request.contentType) {
                        ContentType.JSON -> "application/json"
                        ContentType.FORM_URLENCODED -> "application/x-www-form-urlencoded"
                        ContentType.TEXT_PLAIN -> "text/plain"
                        ContentType.APPLICATION_OCTET_STREAM -> "application/octet-stream"
                        ContentType.NONE -> ""
                    }
                )
            }

            // Add custom headers
            request.headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }

            // Add common headers
            connection.setRequestProperty("User-Agent", "UDPTrigger/1.0")
            connection.setRequestProperty("Accept", "application/json, text/plain, */*")

            // Write body for methods that support it
            if (request.body != null && request.method in listOf(
                    HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH
                )
            ) {
                connection.doOutput = true
                connection.outputStream.use { os ->
                    os.write(request.body.toByteArray(StandardCharsets.UTF_8))
                }
            }

            // Get response
            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage

            // Read response body
            val body = try {
                val inputStream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                if (inputStream != null) {
                    BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                        reader.readText()
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }

            // Parse response headers
            val responseHeaders = mutableMapOf<String, String>()
            connection.headerFields?.forEach { (key, values) ->
                if (key != null && values.isNotEmpty()) {
                    responseHeaders[key] = values.first()
                }
            }

            val duration = System.currentTimeMillis() - startTime

            connection.disconnect()

            Result.success(
                HttpResponse(
                    code = responseCode,
                    message = responseMessage,
                    body = body,
                    headers = responseHeaders,
                    timestamp = System.currentTimeMillis(),
                    durationMs = duration
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Simple GET request
     */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): Result<HttpResponse> {
        return execute(HttpRequest(url = url, method = HttpMethod.GET, headers = headers))
    }

    /**
     * Simple POST request with JSON body
     */
    suspend fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): Result<HttpResponse> {
        val allHeaders = headers + ("Content-Type" to "application/json")
        return execute(HttpRequest(url = url, method = HttpMethod.POST, body = json, headers = allHeaders, contentType = ContentType.JSON))
    }

    /**
     * POST form data
     */
    suspend fun postForm(url: String, formData: Map<String, String>): Result<HttpResponse> {
        val body = formData.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        return execute(HttpRequest(url = url, method = HttpMethod.POST, body = body, contentType = ContentType.FORM_URLENCODED))
    }

    /**
     * PUT request with JSON body
     */
    suspend fun putJson(url: String, json: String, headers: Map<String, String> = emptyMap()): Result<HttpResponse> {
        val allHeaders = headers + ("Content-Type" to "application/json")
        return execute(HttpRequest(url = url, method = HttpMethod.PUT, body = json, headers = allHeaders, contentType = ContentType.JSON))
    }

    /**
     * DELETE request
     */
    suspend fun delete(url: String, headers: Map<String, String> = emptyMap()): Result<HttpResponse> {
        return execute(HttpRequest(url = url, method = HttpMethod.DELETE, headers = headers))
    }

    /**
     * PATCH request with JSON body
     */
    suspend fun patchJson(url: String, json: String, headers: Map<String, String> = emptyMap()): Result<HttpResponse> {
        val allHeaders = headers + ("Content-Type" to "application/json")
        return execute(HttpRequest(url = url, method = HttpMethod.PATCH, body = json, headers = allHeaders, contentType = ContentType.JSON))
    }

    /**
     * Build webhook URL with variables
     */
    fun buildWebhookUrl(
        baseUrl: String,
        variables: Map<String, String> = emptyMap()
    ): String {
        var url = baseUrl
        variables.forEach { (key, value) ->
            url = url.replace("{$key}", URLEncoder.encode(value, "UTF-8"))
            url = url.replace("{$key}", value) // Also replace unencoded
        }
        return url
    }
}

/**
 * Webhook trigger configuration
 */
data class WebhookConfig(
    val name: String,
    val url: String,
    val method: HttpClient.HttpMethod = HttpClient.HttpMethod.POST,
    val headers: Map<String, String> = emptyMap(),
    val bodyTemplate: String? = null,
    val contentType: HttpClient.ContentType = HttpClient.ContentType.JSON,
    val enabled: Boolean = true,
    val triggerOnPacketReceived: Boolean = false,
    val packetPattern: String? = null,
    val useRegex: Boolean = false
)

/**
 * Webhook manager for handling multiple webhooks
 */
class WebhookManager(private val httpClient: HttpClient = HttpClient()) {

    private val webhooks = mutableListOf<WebhookConfig>()

    /**
     * Add webhook configuration
     */
    fun addWebhook(webhook: WebhookConfig) {
        webhooks.add(webhook)
    }

    /**
     * Remove webhook by name
     */
    fun removeWebhook(name: String) {
        webhooks.removeAll { it.name == name }
    }

    /**
     * Get all webhooks
     */
    fun getWebhooks(): List<WebhookConfig> = webhooks.toList()

    /**
     * Update webhook
     */
    fun updateWebhook(name: String, updatedWebhook: WebhookConfig) {
        val index = webhooks.indexOfFirst { it.name == name }
        if (index >= 0) {
            webhooks[index] = updatedWebhook.copy(name = name)
        }
    }

    /**
     * Trigger webhook by name
     */
    suspend fun triggerWebhook(name: String, variables: Map<String, String> = emptyMap()): Result<HttpClient.HttpResponse>? {
        val webhook = webhooks.find { it.name == name && it.enabled } ?: return null

        val url = httpClient.buildWebhookUrl(webhook.url, variables)
        val body = webhook.bodyTemplate?.let { template ->
            var content = template
            variables.forEach { (key, value) ->
                content = content.replace("{$key}", value)
            }
            content
        }

        val headers = webhook.headers.toMutableMap()
        if (webhook.contentType != HttpClient.ContentType.NONE) {
            when (webhook.contentType) {
                HttpClient.ContentType.JSON -> headers["Content-Type"] = "application/json"
                HttpClient.ContentType.FORM_URLENCODED -> headers["Content-Type"] = "application/x-www-form-urlencoded"
                else -> {}
            }
        }

        return httpClient.execute(
            HttpClient.HttpRequest(
                url = url,
                method = webhook.method,
                headers = headers,
                body = body,
                contentType = webhook.contentType
            )
        )
    }

    /**
     * Trigger all webhooks matching a pattern
     */
    suspend fun triggerWebhooksForPacket(packetContent: String): Map<String, Result<HttpClient.HttpResponse>> {
        val results = mutableMapOf<String, Result<HttpClient.HttpResponse>>()

        for (webhook in webhooks.filter { it.enabled && it.triggerOnPacketReceived }) {
            val matches = if (webhook.useRegex && webhook.packetPattern != null) {
                try {
                    Regex(webhook.packetPattern).containsMatchIn(packetContent)
                } catch (e: Exception) {
                    false
                }
            } else {
                webhook.packetPattern != null && packetContent.contains(webhook.packetPattern)
            }

            if (matches) {
                val variables = mapOf(
                    "packet_content" to packetContent,
                    "packet_size" to packetContent.length.toString()
                )
                val result = this.triggerWebhook(webhook.name, variables)
                if (result != null) {
                    results[webhook.name] = result
                }
            }
        }

        return results
    }

    /**
     * Test webhook configuration
     */
    suspend fun testWebhook(webhook: WebhookConfig): Result<HttpClient.HttpResponse> {
        return httpClient.execute(
            HttpClient.HttpRequest(
                url = webhook.url,
                method = webhook.method,
                headers = webhook.headers,
                body = webhook.bodyTemplate,
                contentType = webhook.contentType
            )
        )
    }

    /**
     * Clear all webhooks
     */
    fun clear() {
        webhooks.clear()
    }
}
