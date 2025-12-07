package dev.skorobogatov.services

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Информация о веб-приложении
 */
@Serializable
data class AppInfo(
    val name: String,
    val createdAt: String,
    val modifiedAt: String,
    val files: List<String>,
    val url: String,
    val hasIndex: Boolean
)

/**
 * Содержимое файла приложения
 */
@Serializable
data class AppFile(
    val name: String,
    val path: String,
    val content: String,
    val size: Long
)

/**
 * Метаданные приложения (сохраняются в app.json)
 */
@Serializable
data class AppMetadata(
    val name: String,
    val description: String = "",
    val createdAt: String,
    val modifiedAt: String,
    val tags: List<String> = emptyList()
)

/**
 * Сервис для управления веб-приложениями в директории apps/
 */
class AppsService(
    private val appsDirectory: String = "src/main/resources/static/apps"
) {
    private val logger = LoggerFactory.getLogger(AppsService::class.java)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private val appsDir: File by lazy {
        File(appsDirectory).also {
            if (!it.exists()) {
                it.mkdirs()
                logger.info("Created apps directory: ${it.absolutePath}")
            }
        }
    }

    /**
     * Получить список всех приложений
     */
    fun listApps(): List<AppInfo> {
        return try {
            appsDir.listFiles()
                ?.filter { it.isDirectory }
                ?.map { getAppInfo(it.name) }
                ?.filterNotNull()
                ?.sortedByDescending { it.modifiedAt }
                ?: emptyList()
        } catch (e: Exception) {
            logger.error("Error listing apps", e)
            emptyList()
        }
    }

    /**
     * Получить информацию о конкретном приложении
     */
    fun getAppInfo(name: String): AppInfo? {
        return try {
            val appDir = File(appsDir, name)
            if (!appDir.exists() || !appDir.isDirectory) {
                return null
            }

            val files = appDir.listFiles()?.map { it.name } ?: emptyList()
            val hasIndex = files.any { it.equals("index.html", ignoreCase = true) }

            // Читаем метаданные если есть
            val metadata = loadMetadata(name)

            val createdAt = metadata?.createdAt ?: getFileCreatedAt(appDir)
            val modifiedAt = metadata?.modifiedAt ?: getFileModifiedAt(appDir)

            AppInfo(
                name = name,
                createdAt = createdAt,
                modifiedAt = modifiedAt,
                files = files.filter { it != "app.json" },
                url = "/apps/$name/",
                hasIndex = hasIndex
            )
        } catch (e: Exception) {
            logger.error("Error getting app info: $name", e)
            null
        }
    }

    /**
     * Проверить существование приложения
     */
    fun appExists(name: String): Boolean {
        return File(appsDir, name).let { it.exists() && it.isDirectory }
    }

    /**
     * Получить файлы приложения
     */
    fun getAppFiles(name: String): List<AppFile> {
        return try {
            val appDir = File(appsDir, name)
            if (!appDir.exists()) {
                return emptyList()
            }

            appDir.listFiles()
                ?.filter { it.isFile && it.name != "app.json" }
                ?.map { file ->
                    AppFile(
                        name = file.name,
                        path = "$name/${file.name}",
                        content = file.readText(),
                        size = file.length()
                    )
                }
                ?: emptyList()
        } catch (e: Exception) {
            logger.error("Error getting app files: $name", e)
            emptyList()
        }
    }

    /**
     * Удалить приложение
     */
    fun deleteApp(name: String): Boolean {
        return try {
            val appDir = File(appsDir, name)
            if (!appDir.exists()) {
                logger.warn("App not found for deletion: $name")
                return false
            }

            val deleted = appDir.deleteRecursively()
            if (deleted) {
                logger.info("Deleted app: $name")
            } else {
                logger.error("Failed to delete app: $name")
            }
            deleted
        } catch (e: Exception) {
            logger.error("Error deleting app: $name", e)
            false
        }
    }

    /**
     * Создать или обновить метаданные приложения
     */
    fun saveMetadata(name: String, description: String = "", tags: List<String> = emptyList()) {
        try {
            val appDir = File(appsDir, name)
            if (!appDir.exists()) {
                appDir.mkdirs()
            }

            val existingMetadata = loadMetadata(name)
            val now = LocalDateTime.now().format(dateFormatter)

            val metadata = AppMetadata(
                name = name,
                description = description.ifBlank { existingMetadata?.description ?: "" },
                createdAt = existingMetadata?.createdAt ?: now,
                modifiedAt = now,
                tags = tags.ifEmpty { existingMetadata?.tags ?: emptyList() }
            )

            val metadataFile = File(appDir, "app.json")
            metadataFile.writeText(json.encodeToString(metadata))
            logger.debug("Saved metadata for app: $name")
        } catch (e: Exception) {
            logger.error("Error saving metadata for app: $name", e)
        }
    }

    /**
     * Загрузить метаданные приложения
     */
    fun loadMetadata(name: String): AppMetadata? {
        return try {
            val metadataFile = File(appsDir, "$name/app.json")
            if (!metadataFile.exists()) {
                return null
            }
            json.decodeFromString<AppMetadata>(metadataFile.readText())
        } catch (e: Exception) {
            logger.debug("No metadata found for app: $name")
            null
        }
    }

    /**
     * Сгенерировать уникальное имя для приложения
     */
    fun generateAppName(baseName: String): String {
        // Нормализуем имя: lowercase, заменяем пробелы на дефисы
        val normalized = baseName
            .lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
            .take(50) // Ограничиваем длину

        if (normalized.isBlank()) {
            return "app-${System.currentTimeMillis()}"
        }

        // Проверяем уникальность
        var candidate = normalized
        var counter = 1
        while (appExists(candidate)) {
            candidate = "$normalized-$counter"
            counter++
        }

        return candidate
    }

    /**
     * Получить время создания файла
     */
    private fun getFileCreatedAt(file: File): String {
        return try {
            val attrs = java.nio.file.Files.readAttributes(
                file.toPath(),
                java.nio.file.attribute.BasicFileAttributes::class.java
            )
            LocalDateTime.ofInstant(
                attrs.creationTime().toInstant(),
                java.time.ZoneId.systemDefault()
            ).format(dateFormatter)
        } catch (e: Exception) {
            LocalDateTime.now().format(dateFormatter)
        }
    }

    /**
     * Получить время модификации файла
     */
    private fun getFileModifiedAt(file: File): String {
        return try {
            LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(file.lastModified()),
                java.time.ZoneId.systemDefault()
            ).format(dateFormatter)
        } catch (e: Exception) {
            LocalDateTime.now().format(dateFormatter)
        }
    }
}
