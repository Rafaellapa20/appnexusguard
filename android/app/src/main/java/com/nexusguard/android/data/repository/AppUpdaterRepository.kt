package com.nexusguard.android.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.nexusguard.android.BuildConfig
import com.nexusguard.android.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class RemoteUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String,
    val publishedAt: String
)

data class AppUpdateUiState(
    val isChecking: Boolean = false,
    val showUpdateDialog: Boolean = false,
    val isNewerThanInstalled: Boolean = false,
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val remoteInfo: RemoteUpdateInfo? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadedApkFile: File? = null,
    val needsInstallPermission: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

@Singleton
class AppUpdaterRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferencesRepository: UserPreferencesRepository
) {
    companion object {
        const val VERSION_JSON_URL =
            "https://raw.githubusercontent.com/Rafaellapa20/appnexusguard/main/version.json"
        const val DEFAULT_APK_URL =
            "https://github.com/Rafaellapa20/appnexusguard/raw/main/NexusGuard-full-debug.apk"
    }

    private val _updateState = MutableStateFlow(AppUpdateUiState())
    val updateState: StateFlow<AppUpdateUiState> = _updateState.asStateFlow()

    suspend fun checkForUpdates(isStartupCheck: Boolean) = withContext(Dispatchers.IO) {
        _updateState.value = _updateState.value.copy(
            isChecking = true,
            errorMessage = null,
            statusMessage = "A verificar atualizações no GitHub..."
        )

        val prefs = preferencesRepository.preferencesFlow.first()
        val lastConfirmedCode = prefs.lastConfirmedUpdateCode

        val remote = fetchRemoteVersionInfo() ?: RemoteUpdateInfo(
            versionCode = BuildConfig.VERSION_CODE,
            versionName = BuildConfig.VERSION_NAME,
            apkUrl = DEFAULT_APK_URL,
            releaseNotes = "• Dados 100% reais nos sensores, bateria, estado térmico e RAM\n" +
                "• Limpeza real via MediaStore com Lixo do sistema e Duplicados SHA-256\n" +
                "• Modo Jogo com serviço em primeiro plano e Não Incomodar\n" +
                "• Sistema de atualização direta integrado via GitHub",
            publishedAt = "Hoje"
        )

        val isStrictlyNewer = remote.versionCode > BuildConfig.VERSION_CODE
        val shouldPromptOnStartup = isStrictlyNewer || (isStartupCheck && lastConfirmedCode < remote.versionCode)

        _updateState.value = _updateState.value.copy(
            isChecking = false,
            remoteInfo = remote,
            isNewerThanInstalled = isStrictlyNewer,
            showUpdateDialog = if (isStartupCheck) shouldPromptOnStartup else true,
            needsInstallPermission = !canInstallUnknownApps(),
            statusMessage = if (isStrictlyNewer) {
                "Nova versão ${remote.versionName} disponível!"
            } else {
                "Versão disponível no repositório: ${remote.versionName}"
            }
        )
    }

    private fun fetchRemoteVersionInfo(): RemoteUpdateInfo? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("$VERSION_JSON_URL?t=${System.currentTimeMillis()}")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
            }
            if (connection.responseCode in 200..299) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                RemoteUpdateInfo(
                    versionCode = json.optInt("versionCode", BuildConfig.VERSION_CODE),
                    versionName = json.optString("versionName", BuildConfig.VERSION_NAME),
                    apkUrl = json.optString("apkUrl", DEFAULT_APK_URL),
                    releaseNotes = json.optString(
                        "releaseNotes",
                        "Melhorias de desempenho, sensores reais e correções."
                    ),
                    publishedAt = json.optString("publishedAt", "Recente")
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun downloadAndInstallUpdate() = withContext(Dispatchers.IO) {
        val remote = _updateState.value.remoteInfo ?: return@withContext
        val apkUrl = remote.apkUrl.ifBlank { DEFAULT_APK_URL }

        _updateState.value = _updateState.value.copy(
            isDownloading = true,
            downloadProgress = 0f,
            downloadedBytes = 0L,
            totalBytes = 0L,
            errorMessage = null,
            statusMessage = "A transferir atualização do GitHub..."
        )

        var connection: HttpURLConnection? = null
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val targetFile = File(updatesDir, "NexusGuard-update.apk")
            if (targetFile.exists()) targetFile.delete()

            val url = URL("$apkUrl?t=${System.currentTimeMillis()}")
            connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                _updateState.value = _updateState.value.copy(
                    isDownloading = false,
                    errorMessage = "Não foi possível transferir o APK (HTTP $status). Podes abrir no navegador."
                )
                return@withContext
            }

            val contentLength = connection.contentLengthLong.coerceAtLeast(0L)
            var downloaded = 0L

            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        val progress = if (contentLength > 0L) {
                            (downloaded.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0.5f
                        }
                        _updateState.value = _updateState.value.copy(
                            downloadProgress = progress,
                            downloadedBytes = downloaded,
                            totalBytes = contentLength
                        )
                    }
                    output.flush()
                }
            }

            preferencesRepository.setLastConfirmedUpdateCode(remote.versionCode)

            val hasInstallPerm = canInstallUnknownApps()
            _updateState.value = _updateState.value.copy(
                isDownloading = false,
                downloadProgress = 1f,
                downloadedApkFile = targetFile,
                needsInstallPermission = !hasInstallPerm,
                statusMessage = if (hasInstallPerm) {
                    "Transferência concluída! A abrir instalador do Android..."
                } else {
                    "APK transferido! Autoriza a instalação de fontes desconhecidas para concluir."
                }
            )

            withContext(Dispatchers.Main) {
                if (hasInstallPerm) {
                    launchApkInstaller(targetFile)
                } else {
                    openInstallUnknownAppsSettings()
                }
            }
        } catch (e: Exception) {
            _updateState.value = _updateState.value.copy(
                isDownloading = false,
                errorMessage = "Erro ao transferir: ${e.localizedMessage ?: "Verifica a ligação à Internet."}"
            )
        } finally {
            connection?.disconnect()
        }
    }

    fun installDownloadedApkIfReady() {
        val file = _updateState.value.downloadedApkFile ?: return
        if (!file.exists()) return
        if (canInstallUnknownApps()) {
            _updateState.value = _updateState.value.copy(needsInstallPermission = false)
            launchApkInstaller(file)
        } else {
            openInstallUnknownAppsSettings()
        }
    }

    fun canInstallUnknownApps(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallUnknownAppsSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    private fun launchApkInstaller(apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            _updateState.value = _updateState.value.copy(
                errorMessage = "Não foi possível iniciar o instalador: ${e.localizedMessage}"
            )
        }
    }

    fun openBrowserFallback() {
        val url = _updateState.value.remoteInfo?.apkUrl ?: DEFAULT_APK_URL
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    suspend fun dismissUpdateDialog() {
        val remoteCode = _updateState.value.remoteInfo?.versionCode ?: BuildConfig.VERSION_CODE
        preferencesRepository.setLastConfirmedUpdateCode(remoteCode)
        _updateState.value = _updateState.value.copy(showUpdateDialog = false)
    }
}
