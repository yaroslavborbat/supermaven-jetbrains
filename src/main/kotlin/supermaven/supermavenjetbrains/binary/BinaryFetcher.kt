package supermaven.supermavenjetbrains.binary

import java.net.URI
import com.intellij.openapi.diagnostic.Logger
import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.net.HttpURLConnection
import java.nio.file.StandardCopyOption

class BinaryFetcher {
    private val logger = Logger.getInstance(BinaryFetcher::class.java)
    private val gson = Gson()

    data class DownloadResponse(val downloadUrl: String)

    fun fetch(): String? {
        val localPath = getLocalBinaryPath() ?: return null
        val file = File(localPath)

        if (file.exists() && file.canExecute()) {
            logger.info("Binary already exists at $localPath")
            return localPath
        }

        val dir = file.parentFile
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val downloadUrl = discoverBinaryUrl() ?: return null
        logger.info("Downloading Supermaven binary from $downloadUrl")

        try {
            val uri = URI(downloadUrl)
            val conn = uri.toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 30000
            conn.readTimeout = 30000

            conn.inputStream.use { input ->
                Files.copy(input, Paths.get(localPath), StandardCopyOption.REPLACE_EXISTING)
            }
            file.setExecutable(true)

            logger.info("Downloaded binary to $localPath")
            return localPath

        } catch (e: Exception) {
            logger.error("Failed to download binary", e)
            return null
        }
    }

    private fun getPlatform(): String? {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("mac") || osName.contains("darwin") -> "macosx"
            osName.contains("win") -> "windows"
            osName.contains("linux") -> "linux"
            else -> {
                logger.error("Unsupported OS: $osName")
                null
            }
        }
    }

    private fun getArch(): String? {
        val arch = System.getProperty("os.arch").lowercase()
        return when {
            arch.contains("aarch64") || arch.contains("arm64") -> "aarch64"
            arch.contains("x86_64") || arch.contains("amd64") -> "x86_64"
            else -> {
                logger.error("Unsupported architecture: $arch")
                null
            }
        }
    }

    private fun getLocalBinaryPath(): String? {
        val homeDir = System.getProperty("user.home")
        val dataDir = System.getenv("XDG_DATA_HOME") ?: homeDir
        val platform = getPlatform() ?: return null
        val arch = getArch() ?: return null

        val binaryName = if (platform == "windows") "sm-agent.exe" else "sm-agent"
        return "$dataDir/.supermaven/binary/brbt/$platform-$arch/$binaryName"
    }

    private fun discoverBinaryUrl(): String? {
        val platform = getPlatform() ?: return null
        val arch = getArch() ?: return null

        val urlString = "https://supermaven.com/api/download-path-v2?platform=$platform&arch=$arch&editor=intellij"

        try {
            val uri = URI.create(urlString)
            val conn = uri.toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 30000
            conn.readTimeout = 30000

            val response = conn.getInputStream().bufferedReader().readText()
            val downloadResponse = gson.fromJson(response, DownloadResponse::class.java)
            return downloadResponse.downloadUrl
        } catch (e: Exception) {
            logger.error("Failed to download binary from url: $urlString:", e)
            return null
        }
    }
}