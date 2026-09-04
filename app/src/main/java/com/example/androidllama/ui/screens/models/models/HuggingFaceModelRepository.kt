package com.example.androidllama.ui.screens.models.models

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

data class ModelCatalogPage(
    val models: List<ModelInfo>,
    val nextPageUrl: String?,
    val previousPageUrl: String?
)

class HuggingFaceModelRepository(private val context: Context) {
    private val modelRoot = File(context.filesDir, "models")
    private val catalogFile = File(modelRoot, "catalog.json")
    private val pageCacheRoot = File(modelRoot, "catalog-pages")

    suspend fun fetchPublicGgufModels(
        pageUrl: String? = null,
        searchQuery: String = "",
        forceRefresh: Boolean = false
    ): ModelCatalogPage =
        withContext(Dispatchers.IO) {
        val requestUrl = validatedCatalogUrl(pageUrl ?: initialCatalogUrl(searchQuery)).toString()
        if (!forceRefresh) {
            readCachedPage(requestUrl)?.let { return@withContext it }
        }
        val connection = openConnection(URL(requestUrl))
        val repositories: JSONArray
        val nextPageUrl: String?
        val previousPageUrl: String?
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("Hugging Face returned HTTP ${connection.responseCode}")
            }
            repositories = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            val links = parsePaginationLinks(connection.getHeaderField("Link"))
            nextPageUrl = links["next"]
            previousPageUrl = links["prev"] ?: links["previous"]
        } finally {
            connection.disconnect()
        }
        val candidates = buildList {
            for (index in 0 until repositories.length()) {
                val summary = repositories.getJSONObject(index)
                if (summary.optBoolean("private") || isGated(summary)) continue
                val repositoryId = summary.optString("id")
                if (repositoryId.isBlank()) continue
                val parameterCount = summary.optJSONObject("gguf")
                    ?.optLong("total", -1)
                    ?.takeIf(::isSupportedParameterCount)
                    ?: continue
                add(repositoryId to parameterCount)
            }
        }
        val discovered = coroutineScope {
            candidates.map { (repositoryId, parameterCount) ->
                async {
                    runCatching { fetchRepositoryFiles(repositoryId, parameterCount) }
                        .getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }

        if (discovered.isEmpty()) throw IOException("No public GGUF models were returned by Hugging Face")
        cacheCatalog(discovered)
        ModelCatalogPage(discovered, nextPageUrl, previousPageUrl).also { page ->
            runCatching { cachePage(requestUrl, page) }
        }
    }

    private fun fetchRepositoryFiles(repositoryId: String, parameterCount: Long): List<ModelInfo> {
        val encodedId = repositoryId.split('/').joinToString("/") { encodePathSegment(it) }
        val json = getJsonObject("https://huggingface.co/api/models/$encodedId?blobs=true")
        if (json.optBoolean("private") || isGated(json)) return emptyList()

        val revision = json.optString("sha").ifBlank { "main" }
        val repoTags = json.optJSONArray("tags").toStringList()
            .filterNot { it == "gguf" || it.startsWith("region:") || it.startsWith("license:") }
            .take(3)
        val siblings = json.optJSONArray("siblings") ?: JSONArray()
        return buildList {
            for (index in 0 until siblings.length()) {
                val file = siblings.getJSONObject(index)
                val fileName = file.optString("rfilename")
                if (!fileName.endsWith(".gguf", ignoreCase = true) ||
                    fileName.substringAfterLast('/').startsWith("mmproj", ignoreCase = true) ||
                    SHARDED_GGUF.matches(fileName.substringAfterLast('/'))
                ) continue

                val sizeBytes = file.optLong("size", -1).takeIf { it >= 0 }
                    ?: file.optJSONObject("lfs")?.optLong("size", -1)?.takeIf { it >= 0 }
                add(
                    ModelInfo(
                        id = "$repositoryId::$fileName",
                        name = repositoryId.substringAfterLast('/'),
                        size = sizeBytes?.let(::formatBytes) ?: "Unknown size",
                        quantization = quantizationFrom(fileName),
                        tags = repoTags,
                        repositoryId = repositoryId,
                        fileName = fileName,
                        revision = revision,
                        sizeBytes = sizeBytes,
                        parameterCount = parameterCount,
                        downloaded = isStored(repositoryId, fileName, sizeBytes)
                    )
                )
            }
        }
    }

    suspend fun download(model: ModelInfo, onProgress: suspend (Int?) -> Unit): File = withContext(Dispatchers.IO) {
        val destination = storedFile(model.repositoryId, model.fileName)
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, "${destination.name}.part")
        partial.delete()

        val url = URI(
            "https",
            "huggingface.co",
            "/${model.repositoryId}/resolve/${model.revision}/${model.fileName}",
            "download=true",
            null
        ).toURL()
        val connection = openConnection(url, acceptsJson = false)
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("Hugging Face returned HTTP ${connection.responseCode}")
            }
            val expected = model.sizeBytes ?: connection.contentLengthLong.takeIf { it >= 0 }
            if (expected != null && modelRoot.usableSpace < expected) {
                throw IOException("Not enough free storage for ${formatBytes(expected)}")
            }

            var written = 0L
            var lastProgress: Int? = null
            connection.inputStream.buffered().use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        written += count
                        val progress = expected?.takeIf { it > 0 }
                            ?.let { ((written * 100) / it).toInt().coerceIn(0, 100) }
                        if (progress != lastProgress) {
                            lastProgress = progress
                            onProgress(progress)
                        }
                    }
                }
            }
            if (expected != null && written != expected) {
                throw IOException("Download was incomplete (${formatBytes(written)} of ${formatBytes(expected)})")
            }
            if (destination.exists() && !destination.delete()) throw IOException("Could not replace stored model")
            if (!partial.renameTo(destination)) throw IOException("Could not finalize the downloaded model")
            destination
        } finally {
            connection.disconnect()
            if (partial.exists()) partial.delete()
        }
    }

    suspend fun delete(model: ModelInfo) = withContext(Dispatchers.IO) {
        val storedModel = storedFile(model.repositoryId, model.fileName)
        if (storedModel.exists() && !storedModel.delete()) {
            throw IOException("Could not delete the stored model")
        }

        var directory = storedModel.parentFile
        while (directory != null && directory != modelRoot && directory.list()?.isEmpty() == true) {
            if (!directory.delete()) break
            directory = directory.parentFile
        }
    }

    fun requireStored(model: ModelInfo): File {
        if (!isStored(model.repositoryId, model.fileName, model.sizeBytes)) {
            throw IOException("The stored model file is missing or incomplete")
        }
        return storedFile(model.repositoryId, model.fileName)
    }

    fun readCachedCatalog(): List<ModelInfo> = runCatching {
        if (!catalogFile.exists()) return emptyList()
        val array = JSONArray(catalogFile.readText())
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val sizeBytes = item.optLong("sizeBytes", -1).takeIf { it >= 0 }
                val parameterCount = item.optLong("parameterCount", -1)
                if (!isSupportedParameterCount(parameterCount)) continue
                val repositoryId = item.getString("repositoryId")
                val fileName = item.getString("fileName")
                add(
                    ModelInfo(
                        id = "$repositoryId::$fileName",
                        name = item.getString("name"),
                        size = sizeBytes?.let(::formatBytes) ?: "Unknown size",
                        quantization = item.optString("quantization", "GGUF"),
                        tags = item.optJSONArray("tags").toStringList(),
                        repositoryId = repositoryId,
                        fileName = fileName,
                        revision = item.optString("revision", "main"),
                        sizeBytes = sizeBytes,
                        parameterCount = parameterCount,
                        downloaded = isStored(repositoryId, fileName, sizeBytes)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun readCachedDownloadedModels(): List<ModelInfo> = buildList {
        addAll(readCachedCatalog())
        pageCacheRoot.listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .forEach { file ->
                runCatching {
                    val json = JSONObject(file.readText())
                    if (json.optInt("version") == PAGE_CACHE_VERSION) {
                        addAll(modelsFromJson(json.getJSONArray("models")))
                    }
                }
            }
    }.filter { it.downloaded }.distinctBy { it.id }

    fun cacheCatalog(models: List<ModelInfo>) {
        modelRoot.mkdirs()
        val incomingIds = models.mapTo(mutableSetOf()) { it.id }
        val retainedDownloads = readCachedCatalog().filter { cached ->
            cached.downloaded && cached.id !in incomingIds
        }
        catalogFile.writeText(modelsToJson(retainedDownloads + models).toString())
    }

    private fun readCachedPage(requestUrl: String): ModelCatalogPage? = runCatching {
        val file = pageCacheFile(requestUrl)
        if (!file.isFile) return null
        val json = JSONObject(file.readText())
        if (json.optInt("version") != PAGE_CACHE_VERSION ||
            json.optString("requestUrl") != requestUrl
        ) return null
        val models = modelsFromJson(json.getJSONArray("models"))
        if (models.isEmpty()) return null
        ModelCatalogPage(
            models = models,
            nextPageUrl = json.optionalString("nextPageUrl"),
            previousPageUrl = json.optionalString("previousPageUrl")
        )
    }.getOrNull()

    private fun cachePage(requestUrl: String, page: ModelCatalogPage) {
        pageCacheRoot.mkdirs()
        val json = JSONObject().apply {
            put("version", PAGE_CACHE_VERSION)
            put("requestUrl", requestUrl)
            put("nextPageUrl", page.nextPageUrl ?: JSONObject.NULL)
            put("previousPageUrl", page.previousPageUrl ?: JSONObject.NULL)
            put("models", modelsToJson(page.models))
        }
        val destination = pageCacheFile(requestUrl)
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        temporary.writeText(json.toString())
        if (destination.exists()) destination.delete()
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            throw IOException("Could not cache the Hugging Face catalog page")
        }
    }

    private fun pageCacheFile(requestUrl: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(requestUrl.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return File(pageCacheRoot, "$digest.json")
    }

    private fun modelsToJson(models: List<ModelInfo>) = JSONArray().apply {
        models.forEach { model ->
            put(JSONObject().apply {
                put("name", model.name)
                put("repositoryId", model.repositoryId)
                put("fileName", model.fileName)
                put("revision", model.revision)
                put("sizeBytes", model.sizeBytes ?: JSONObject.NULL)
                put("parameterCount", model.parameterCount)
                put("quantization", model.quantization)
                put("tags", JSONArray(model.tags))
            })
        }
    }

    private fun modelsFromJson(array: JSONArray): List<ModelInfo> = buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val sizeBytes = item.optLong("sizeBytes", -1).takeIf { it >= 0 }
            val parameterCount = item.optLong("parameterCount", -1)
            if (!isSupportedParameterCount(parameterCount)) continue
            val repositoryId = item.getString("repositoryId")
            val fileName = item.getString("fileName")
            add(
                ModelInfo(
                    id = "$repositoryId::$fileName",
                    name = item.getString("name"),
                    size = sizeBytes?.let(::formatBytes) ?: "Unknown size",
                    quantization = item.optString("quantization", "GGUF"),
                    tags = item.optJSONArray("tags").toStringList(),
                    repositoryId = repositoryId,
                    fileName = fileName,
                    revision = item.optString("revision", "main"),
                    sizeBytes = sizeBytes,
                    parameterCount = parameterCount,
                    downloaded = isStored(repositoryId, fileName, sizeBytes)
                )
            )
        }
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private fun isStored(repositoryId: String, fileName: String, sizeBytes: Long?): Boolean {
        val file = storedFile(repositoryId, fileName)
        return file.isFile && (sizeBytes == null || file.length() == sizeBytes)
    }

    private fun storedFile(repositoryId: String, fileName: String): File {
        val repositoryFolder = repositoryId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val safeSegments = fileName.replace('\\', '/').split('/')
            .filter { it.isNotBlank() }
        require(safeSegments.isNotEmpty() && safeSegments.none { it == "." || it == ".." }) {
            "Invalid model file path"
        }
        return safeSegments.fold(File(modelRoot, repositoryFolder)) { parent, segment ->
            File(parent, segment)
        }
    }

    private fun getJsonArray(url: String): JSONArray = JSONArray(getText(url))
    private fun getJsonObject(url: String): JSONObject = JSONObject(getText(url))

    private fun getText(url: String): String {
        val connection = openConnection(URL(url))
        return try {
            if (connection.responseCode !in 200..299) {
                throw IOException("Hugging Face returned HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun validatedCatalogUrl(value: String): URL {
        val url = URL(value)
        require(url.protocol == "https" && url.host == "huggingface.co") {
            "Invalid Hugging Face pagination URL"
        }
        return url
    }

    private fun initialCatalogUrl(searchQuery: String): String {
        val query = searchQuery.trim()
        if (query.isEmpty()) return MODEL_CATALOG_URL
        return "$MODEL_CATALOG_URL&search=${URLEncoder.encode(query, "UTF-8")}" 
    }

    private fun parsePaginationLinks(header: String?): Map<String, String> {
        if (header.isNullOrBlank()) return emptyMap()
        return LINK_PATTERN.findAll(header).mapNotNull { match ->
            val url = match.groupValues[1]
            val relation = match.groupValues[2].lowercase()
            runCatching { validatedCatalogUrl(url) }.getOrNull()?.let { relation to it.toString() }
        }.toMap()
    }

    private fun openConnection(url: URL, acceptsJson: Boolean = true) =
        (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", if (acceptsJson) "application/json" else "application/octet-stream")
            setRequestProperty("User-Agent", "AndroidLlama/1.0")
        }

    private fun isGated(json: JSONObject): Boolean {
        val gated = json.opt("gated")
        return gated == true || (gated is String && gated.lowercase() != "false")
    }

    private fun quantizationFrom(fileName: String): String {
        val match = Regex("(?i)(?:^|[._-])((?:IQ|Q|F|BF)\\d+(?:_[A-Z0-9]+)*)").find(fileName)
        return match?.groupValues?.get(1)?.uppercase() ?: "GGUF"
    }

    private fun isSupportedParameterCount(parameterCount: Long): Boolean =
        parameterCount in MIN_PARAMETER_COUNT..MAX_PARAMETER_COUNT

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        do {
            value /= 1024.0
            unit++
        } while (value >= 1024 && unit < units.lastIndex)
        return String.format(Locale.US, "%.1f %s", value, units[unit])
    }

    private fun encodePathSegment(value: String): String = URI(null, null, value, null).rawPath

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) add(optString(index))
        }.filter { it.isNotBlank() }
    }

    private companion object {
        const val MODEL_CATALOG_URL =
            "https://huggingface.co/api/models?app=llama.cpp&apps=llama.cpp" +
                "&sort=trendingScore&direction=-1&num_parameters=max%3A9B&limit=10" +
                "&expand[]=gguf&expand[]=private&expand[]=gated&expand[]=tags" +
                "&expand[]=sha"
        const val MIN_PARAMETER_COUNT = 0L
        const val MAX_PARAMETER_COUNT = 9_000_000_000L
        const val PAGE_CACHE_VERSION = 1
        val SHARDED_GGUF = Regex("(?i).+-\\d{5}-of-\\d{5}\\.gguf")
        val LINK_PATTERN = Regex("<([^>]+)>;\\s*rel=\"([^\"]+)\"")
    }
}
