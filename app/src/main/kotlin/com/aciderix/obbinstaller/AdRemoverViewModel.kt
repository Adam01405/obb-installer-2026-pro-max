package com.aciderix.obbinstaller

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shinegirls.apkadremovereditor.core.AdPatternConfig
import com.shinegirls.apkadremovereditor.core.AdRemover
import com.shinegirls.apkadremovereditor.core.ApkProcessor
import com.shinegirls.apkadremovereditor.core.DataMultiplexingHelper
import com.shinegirls.apkadremovereditor.core.FlutterAdRemover
import com.shinegirls.apkadremovereditor.core.LanguageManager
import com.shinegirls.apkadremovereditor.core.ProcessingReport
import com.shinegirls.apkadremovereditor.core.ReportGenerator
import com.shinegirls.apkadremovereditor.core.SignatureVerificationRemover
import com.shinegirls.apkadremovereditor.core.Signer
import com.shinegirls.apkadremovereditor.core.SubscriptionManager
import com.shinegirls.apkadremovereditor.utils.Format
import com.shinegirls.apkadremovereditor.utils.PathPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AdRemoverResult(
    val exportDesc: String,
    val reportPath: String?,
    val originalApkSize: Long,
    val finalSize: Long,
    val savedBytes: Long,
    val totalTimeMs: Long
)

data class AdRemoverUiState(
    val apk: FileSource? = null,
    val isProcessing: Boolean = false,
    val log: String = "",
    val result: AdRemoverResult? = null,
    val error: String? = null,
    // --- 高级设置 ---
    val signMode: Int = 0,
    val flutterEnabled: Boolean = true,
    val dexOptimizeEnabled: Boolean = true,
    val skipSigning: Boolean = false,
    val outputDir: String = "",
    val configPath: String = "",
    val categories: List<Pair<AdPatternConfig.Category, Boolean>> = emptyList(),
    val subscriptions: List<SubscriptionManager.Subscription> = emptyList()
)

class AdRemoverViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(AdRemoverUiState())
    val state: StateFlow<AdRemoverUiState> = _state.asStateFlow()

    private val logLock = Any()
    private val logBuffer = StringBuilder()
    private var lastFlush = 0L
    private var processing = false

    companion object {
        private const val MAX_LOG_CHARS = 200_000
        private const val FLUSH_INTERVAL_MS = 120L
    }

    init {
        // 核心引擎依赖全局注入的应用上下文（替代已删除的 App.init）
        val ctx = getApplication<Application>()
        runCatching { LanguageManager.init(ctx) }
        refreshSettings()
    }

    private fun refreshSettings() {
        val ctx = getApplication<Application>()
        _state.update {
            it.copy(
                signMode = PathPreferences.getSignRemovalMode(ctx),
                flutterEnabled = PathPreferences.isFlutterLibappEnabled(ctx),
                dexOptimizeEnabled = PathPreferences.isDexOptimizeEnabled(ctx),
                skipSigning = PathPreferences.isSigningSkipped(ctx),
                outputDir = PathPreferences.getOutputDir(ctx),
                configPath = PathPreferences.getConfigFilePath(ctx),
                categories = AdPatternConfig.Category.values()
                    .map { cat -> cat to PathPreferences.isCategoryEnabled(ctx, cat.name) },
                subscriptions = SubscriptionManager.loadSubscriptions(ctx)
            )
        }
    }

    // ==================== APK 选择 ====================

    fun setApkUri(uri: Uri) {
        val ctx = getApplication<Application>()
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = resolveDisplayName(ctx, uri) ?: "output.apk"
        _state.update { it.copy(apk = FileSource.UriSource(uri, name), error = null, result = null) }
    }

    // ==================== 设置项 ====================

    fun setSignMode(mode: Int) {
        PathPreferences.setSignRemovalMode(getApplication(), mode)
        _state.update { it.copy(signMode = mode) }
    }

    fun setFlutterEnabled(enabled: Boolean) {
        PathPreferences.setFlutterLibappEnabled(getApplication(), enabled)
        _state.update { it.copy(flutterEnabled = enabled) }
    }

    fun setDexOptimizeEnabled(enabled: Boolean) {
        PathPreferences.setDexOptimizeEnabled(getApplication(), enabled)
        _state.update { it.copy(dexOptimizeEnabled = enabled) }
    }

    fun setSkipSigning(skip: Boolean) {
        PathPreferences.setSigningSkipped(getApplication(), skip)
        _state.update { it.copy(skipSigning = skip) }
    }

    fun setCategoryEnabled(category: AdPatternConfig.Category, enabled: Boolean) {
        PathPreferences.setCategoryEnabled(getApplication(), category.name, enabled)
        _state.update { s ->
            s.copy(categories = s.categories.map { if (it.first == category) category to enabled else it })
        }
    }

    fun setOutputDir(path: String) {
        if (PathPreferences.setOutputDir(getApplication(), path)) {
            _state.update { it.copy(outputDir = path) }
        }
    }

    fun resetOutputDir() {
        PathPreferences.resetOutputDir(getApplication())
        _state.update { it.copy(outputDir = PathPreferences.DEFAULT_OUTPUT_DIR) }
    }

    fun setConfigPath(path: String) {
        if (PathPreferences.setConfigFilePath(getApplication(), path)) {
            _state.update { it.copy(configPath = path) }
        }
    }

    fun resetConfigPath() {
        PathPreferences.resetConfigPath(getApplication())
        _state.update { it.copy(configPath = PathPreferences.DEFAULT_CONFIG_PATH) }
    }

    fun resetConfigToDefault() {
        val ctx = getApplication<Application>()
        AdPatternConfig.resetToDefault(ctx)
    }

    fun configTotalCount(): Int {
        val config = AdPatternConfig.loadConfig(getApplication())
        return config.totalCount()
    }

    // ==================== 订阅管理 ====================

    fun addSubscription(input: String): Boolean {
        val ctx = getApplication<Application>()
        val token = input.trim()
        val decoded = SubscriptionManager.decodeToken(token)
        if (decoded != null) {
            val sub = SubscriptionManager.Subscription(
                id = java.util.UUID.randomUUID().toString(),
                name = decoded.name,
                type = decoded.type,
                url = decoded.url,
                contentJson = decoded.contentJson
            )
            val ok = SubscriptionManager.addSubscription(sub, ctx)
            if (ok) _state.update { it.copy(subscriptions = SubscriptionManager.loadSubscriptions(ctx)) }
            return ok
        }
        if (token.startsWith("http://") || token.startsWith("https://")) {
            val name = runCatching { java.net.URI(token).host }
                .getOrNull()
                ?.removePrefix("www.")
                ?.takeIf { it.isNotBlank() }
                ?: "Subscription"
            val sub = SubscriptionManager.Subscription(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                type = SubscriptionManager.Type.URL,
                url = token
            )
            val ok = SubscriptionManager.addSubscription(sub, ctx)
            if (ok) {
                _state.update { it.copy(subscriptions = SubscriptionManager.loadSubscriptions(ctx)) }
                validateRemoteSubscription(sub, ctx)
            }
            return ok
        }
        return false
    }

    private fun validateRemoteSubscription(sub: SubscriptionManager.Subscription, ctx: Context) {
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) { SubscriptionManager.fetchRemoteConfig(sub.url) }
            if (json != null && SubscriptionManager.isValidConfigJson(json)) {
                log("订阅校验成功: ${sub.name}")
            } else {
                log("订阅校验失败(无效配置): ${sub.name}")
            }
        }
    }

    fun updateSubscription(sub: SubscriptionManager.Subscription) {
        val ctx = getApplication<Application>()
        SubscriptionManager.updateSubscription(sub, ctx)
        _state.update { it.copy(subscriptions = SubscriptionManager.loadSubscriptions(ctx)) }
    }

    fun deleteSubscription(id: String) {
        val ctx = getApplication<Application>()
        SubscriptionManager.deleteSubscription(id, ctx)
        _state.update { it.copy(subscriptions = SubscriptionManager.loadSubscriptions(ctx)) }
    }

    fun setSubscriptionEnabled(id: String, enabled: Boolean) {
        val ctx = getApplication<Application>()
        SubscriptionManager.setSubscriptionEnabled(id, enabled, ctx)
        _state.update { it.copy(subscriptions = SubscriptionManager.loadSubscriptions(ctx)) }
    }

    fun shareSubscriptionToken(sub: SubscriptionManager.Subscription): String =
        when (sub.type) {
            SubscriptionManager.Type.URL ->
                SubscriptionManager.encodeToken(sub.name, SubscriptionManager.Type.URL, url = sub.url)
            SubscriptionManager.Type.CONTENT ->
                SubscriptionManager.encodeToken(sub.name, SubscriptionManager.Type.CONTENT, contentJson = sub.contentJson)
        }

    /** 应用所有已开启订阅（内嵌直接解析 + URL 异步拉取），并保存合并配置。 */
    fun applyEnabledSubscriptions() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val allSubs = _state.value.subscriptions
            val enabledSubs = allSubs.filter { it.enabled }
            if (enabledSubs.isEmpty()) {
                AdPatternConfig.resetToDefault(ctx)
                return@launch
            }
            val configs = mutableListOf<AdPatternConfig.AdPatterns>()
            for (sub in enabledSubs.filter { it.type == SubscriptionManager.Type.CONTENT }) {
                runCatching {
                    configs.add(AdPatternConfig.fromJson(JSONObject(sub.contentJson), ctx))
                }
            }
            for (sub in enabledSubs.filter { it.type == SubscriptionManager.Type.URL }) {
                val jsonStr = withContext(Dispatchers.IO) { SubscriptionManager.fetchRemoteConfig(sub.url) }
                if (jsonStr != null && SubscriptionManager.isValidConfigJson(jsonStr)) {
                    runCatching { configs.add(AdPatternConfig.fromJson(JSONObject(jsonStr), ctx)) }
                }
            }
            if (configs.isNotEmpty()) {
                val merged = AdPatternConfig.merge(configs)
                AdPatternConfig.saveConfig(merged, ctx)
            }
        }
    }

    // ==================== 日志 ====================

    private fun log(message: String) {
        synchronized(logLock) {
            logBuffer.append(message).append('\n')
            if (logBuffer.length > MAX_LOG_CHARS) {
                logBuffer.delete(0, logBuffer.length / 3)
            }
            val now = System.currentTimeMillis()
            if (now - lastFlush >= FLUSH_INTERVAL_MS) {
                lastFlush = now
                val chunk = logBuffer.toString()
                logBuffer.setLength(0)
                _state.update { it.copy(log = appendCappedLog(it.log, chunk)) }
            }
        }
    }

    private fun flushLog() {
        synchronized(logLock) {
            if (logBuffer.isNotEmpty()) {
                val chunk = logBuffer.toString()
                logBuffer.setLength(0)
                _state.update { it.copy(log = appendCappedLog(it.log, chunk)) }
            }
        }
    }

    /** 追加日志并限制累积总长度，防止长任务运行期间 state.log 无限增长导致内存膨胀。 */
    private fun appendCappedLog(existing: String, chunk: String): String {
        if (chunk.length >= MAX_LOG_CHARS) return chunk.takeLast(MAX_LOG_CHARS)
        val merged = existing + chunk
        return if (merged.length > MAX_LOG_CHARS) merged.takeLast(MAX_LOG_CHARS) else merged
    }

    fun clearLog() {
        _state.update { it.copy(log = "") }
    }

    // ==================== 主流程 ====================

    fun start() {
        val s = _state.value
        val apk = s.apk ?: return
        if (processing) return
        val ctx = getApplication<Application>()
        _state.update {
            it.copy(
                isProcessing = true,
                log = "",
                result = null,
                error = null,
                apk = apk
            )
        }
        processing = true
        log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_f7da955e))

        val totalStartTime = System.currentTimeMillis()
        val contentResolver = ctx.contentResolver

        viewModelScope.launch {
            val apkUri = (apk as? FileSource.UriSource)?.uri
                ?: return@launch
            var workDir: File? = null
            try {
                workDir = File(ctx.cacheDir, "apk_work_${System.currentTimeMillis()}")
                workDir.mkdirs()

                // 1. 读取 APK
                val step1Start = System.currentTimeMillis()
                log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_9380d8c8))
                val sourceApk = File(workDir, "source.apk")
                contentResolver.openInputStream(apkUri)?.use { input ->
                    sourceApk.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_c15415d3))
                val originalApkSize = sourceApk.length()

                val apkProcessor = ApkProcessor()
                val apkInfo = withContext(Dispatchers.IO) { apkProcessor.getApkInfo(sourceApk) }
                log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.log_apkinfo,
                    sourceApk.name, Format.formatSize(originalApkSize), apkInfo["dex_count"],
                    apkInfo["res_count"], apkInfo["lib_count"], elapsedMs(step1Start)))

                // 2. 解包
                val step2Start = System.currentTimeMillis()
                log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_5503edc4))
                val extractDir = File(workDir, "extracted")
                extractDir.mkdirs()
                withContext(Dispatchers.IO) { apkProcessor.extractApk(sourceApk, extractDir) }
                val dexCount = extractDir.listFiles { f -> f.name.endsWith(".dex") }?.size ?: 0
                val totalFiles = extractDir.walkTopDown().filter { it.isFile }.count()
                log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_fbfccfde,
                    totalFiles, dexCount, elapsedMs(step2Start)))

                // 3. 直接修补 DEX 去广告
                log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_d086cb5b))
                var processingReport: ProcessingReport? = null
                try {
                    processingReport = withContext(Dispatchers.IO) {
                        AdRemover.removeAds(extractDir, ctx) { msg -> log(msg) }
                    }
                } catch (e: OutOfMemoryError) {
                    log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_ba6a6863, e.message))
                    log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_6c5f734b))
                    System.gc()
                } catch (e: Exception) {
                    log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_09306e74, e.message))
                    log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_e63572b7, e.stackTraceToString().take(200)))
                }

                // 3.4 去签名校验
                val signStart = System.currentTimeMillis()
                val signMode = PathPreferences.getSignRemovalMode(ctx)
                if (signMode != SignatureVerificationRemover.MODE_OFF) {
                    log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_9b708f36))
                    val signModeName = if (signMode == SignatureVerificationRemover.MODE_ORIGINAL)
                        ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_15286e90)
                    else
                        ctx.getString(com.shinegirls.apkadremovereditor.R.string.s_636c1a42)
                    log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_23cc02a1))
                    val signOriginPath = PathPreferences.getSignOriginPath(ctx)
                    val signExtractPath = PathPreferences.getSignExtractPath(ctx)
                    val signSoName = PathPreferences.getSignSoName(ctx)
                    val signHookClass = PathPreferences.getSignHookClass(ctx)
                    val signInfo = PathPreferences.getSignInfo(ctx)
                    val signEntry = PathPreferences.getSignEntry(ctx)
                    if (signMode == SignatureVerificationRemover.MODE_ORIGINAL) {
                        log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_ae3222f3))
                    }
                    val signReport = withContext(Dispatchers.IO) {
                        SignatureVerificationRemover.removeSignatures(
                            ctx, extractDir, sourceApk, signMode, ::log,
                            signOriginPath, signExtractPath, signSoName, signHookClass,
                            signInfo, signEntry
                        )
                    }
                    processingReport?.signRemovalMode = signMode
                    processingReport?.originalSignerFingerprint = signReport.originalSignerFingerprint
                    processingReport?.signDexStats?.clear()
                    processingReport?.signDexStats?.addAll(signReport.dexStats)
                    log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_deb381d0,
                        signReport.totalPatchedMethods, signReport.totalPatchedDex, elapsedMs(signStart)))
                } else {
                    log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_ba715cf4))
                }

                // 3.5 Flutter libapp.so 处理
                val flutterStart = System.currentTimeMillis()
                if (PathPreferences.isFlutterLibappEnabled(ctx)) {
                    log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_7dfeadd0))
                    val flutterConfig = withContext(Dispatchers.IO) { AdPatternConfig.loadConfig(ctx) }
                    val flutterResult = withContext(Dispatchers.IO) {
                        FlutterAdRemover.process(
                            extractDir, flutterConfig,
                            File(PathPreferences.getOutputDir(ctx)),
                            context = ctx,
                            logger = ::log
                        )
                    }
                    processingReport?.flutterDetected = flutterResult.detected
                    processingReport?.flutterStats = flutterResult.stats
                    if (flutterResult.detected) {
                        val totalRep = flutterResult.stats.sumOf { it.replacedCount }
                        log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_8d698916,
                            flutterResult.stats.size, totalRep, elapsedMs(flutterStart)))
                    }
                } else {
                    log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_7f604c7a))
                }

                // 4. 打包并（可选）签名
                val step4Start = System.currentTimeMillis()
                log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_348519cb))
                val unsignedApk = File(workDir, "unsigned.apk")
                withContext(Dispatchers.IO) {
                    apkProcessor.buildApk(extractDir, unsignedApk, context = ctx, logger = { msg -> log(msg) })
                }
                val unsignedSize = unsignedApk.length()
                log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_a2d56d37, Format.formatSize(unsignedSize)))

                val skipSigning = PathPreferences.isSigningSkipped(ctx)
                val tempSigned: File
                if (skipSigning) {
                    log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_7fc2a18e))
                    tempSigned = unsignedApk
                } else {
                    val embeddedPaths = apkProcessor.lastEmbeddedApkPaths
                    val bestHost = withContext(Dispatchers.IO) {
                        DataMultiplexingHelper.findBestHost(unsignedApk, embeddedPaths)
                    }
                    if (bestHost != null) {
                        log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_f7aabc54))
                        val v1Signed = File(workDir, "v1_signed.apk")
                        withContext(Dispatchers.IO) { Signer.signApkV1(ctx, unsignedApk, v1Signed) }
                        log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_60a5152a, Format.formatSize(v1Signed.length())))
                        val optimized = File(workDir, "optimized.apk")
                        val optimizedSize = withContext(Dispatchers.IO) {
                            DataMultiplexingHelper.optimize(v1Signed, optimized, bestHost) { msg -> log(msg) }
                        }
                        if (optimizedSize != null) {
                            withContext(Dispatchers.IO) { Signer.signV2V3(ctx, optimized) }
                            log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_b60f14f9, Format.formatSize(optimized.length())))
                            tempSigned = optimized
                        } else {
                            log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_11b8b2c9))
                            val fallback = File(workDir, "temp_signed.apk")
                            withContext(Dispatchers.IO) { Signer.signApk(ctx, unsignedApk, fallback) }
                            tempSigned = fallback
                        }
                    } else {
                        log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_a4c8a5bb))
                        val fallback = File(workDir, "temp_signed.apk")
                        withContext(Dispatchers.IO) { Signer.signApk(ctx, unsignedApk, fallback) }
                        tempSigned = fallback
                    }
                }
                val signedSize = tempSigned.length()
                log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_02cd9acd,
                    Format.formatSize(signedSize), elapsedMs(step4Start)))

                // 5. 导出
                log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_f235618a))
                val displayName = apk.displayName
                val baseName = displayName.substringBeforeLast('.').ifBlank { "output" }
                val exportTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "${baseName}_${exportTime}_clean.apk"

                var finalSize = 0L
                var exportDesc = ""
                var exported = false

                val sourcePath = queryRealPath(apkUri)
                if (sourcePath != null) {
                    val sourceFile = File(sourcePath)
                    val sourceDir = sourceFile.parentFile
                    if (sourceDir != null && sourceDir.exists() && sourceDir.canWrite()) {
                        val exportFile = File(sourceDir, fileName)
                        withContext(Dispatchers.IO) { tempSigned.copyTo(exportFile, overwrite = true) }
                        finalSize = exportFile.length()
                        exportDesc = exportFile.absolutePath
                        exported = true
                        log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_4267eb34, exportDesc))
                    }
                }

                if (!exported) {
                    val exportedViaSaf = try {
                        val resultUri = createOutputInSelectedDir(apkUri, fileName)
                        if (resultUri != null) {
                            withContext(Dispatchers.IO) {
                                contentResolver.openOutputStream(resultUri)?.use { out ->
                                    tempSigned.inputStream().use { it.copyTo(out) }
                                }
                            }
                            true
                        } else false
                    } catch (_: Exception) {
                        false
                    }
                    if (exportedViaSaf) {
                        finalSize = tempSigned.length()
                        exportDesc = docUriToReadablePath(apkUri, fileName)
                        exported = true
                        log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_4267eb34, exportDesc))
                    }
                }

                if (!exported) {
                    val exportedViaMedia = try {
                        val values = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                            put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                            put(
                                android.provider.MediaStore.Downloads.RELATIVE_PATH,
                                android.os.Environment.DIRECTORY_DOWNLOADS + "/OBBInstaller"
                            )
                        }
                        val uri = contentResolver.insert(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                        )
                        if (uri != null) {
                            withContext(Dispatchers.IO) {
                                contentResolver.openOutputStream(uri)?.use { out ->
                                    tempSigned.inputStream().use { it.copyTo(out) }
                                }
                            }
                            true
                        } else false
                    } catch (_: Exception) {
                        false
                    }
                    if (exportedViaMedia) {
                        finalSize = tempSigned.length()
                        exportDesc = "${android.os.Environment.DIRECTORY_DOWNLOADS}/OBBInstaller/$fileName"
                        exported = true
                        log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_4267eb34, exportDesc))
                    }
                }

                if (!exported) {
                    val exportDir = File(PathPreferences.getOutputDir(ctx))
                    if (!exportDir.exists()) exportDir.mkdirs()
                    val exportFile = File(exportDir, fileName)
                    withContext(Dispatchers.IO) { tempSigned.copyTo(exportFile, overwrite = true) }
                    finalSize = exportFile.length()
                    exportDesc = exportFile.absolutePath
                    log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_4267eb34, exportDesc))
                }

                // 6. 生成 Markdown 报告
                var reportPath: String? = null
                processingReport?.let { rep ->
                    rep.sourceApkName = displayName
                    rep.originalApkSize = originalApkSize
                    rep.finalApkSize = finalSize
                    try {
                        val reportName = "${baseName}_${exportTime}_report.md"
                        val reportContent = withContext(Dispatchers.IO) {
                            ReportGenerator.generate(ctx, rep)
                        }
                        val viaMedia = exportDesc.startsWith("Download/OBBInstaller")
                        val reportSaved = if (viaMedia) {
                            try {
                                val values = android.content.ContentValues().apply {
                                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, reportName)
                                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/markdown")
                                    put(
                                        android.provider.MediaStore.Downloads.RELATIVE_PATH,
                                        android.os.Environment.DIRECTORY_DOWNLOADS + "/OBBInstaller"
                                    )
                                }
                                val uri = contentResolver.insert(
                                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                                )
                                if (uri != null) {
                                    withContext(Dispatchers.IO) {
                                        contentResolver.openOutputStream(uri)?.use { out ->
                                            out.write(reportContent.toByteArray(Charsets.UTF_8))
                                        }
                                    }
                                    reportPath = "${android.os.Environment.DIRECTORY_DOWNLOADS}/OBBInstaller/$reportName"
                                    true
                                } else false
                            } catch (_: Exception) {
                                false
                            }
                        } else {
                            val reportDir = File(exportDesc).parentFile
                            if (reportDir != null && reportDir.exists()) {
                                val reportFile = File(reportDir, reportName)
                                withContext(Dispatchers.IO) {
                                    reportFile.writeText(reportContent, Charsets.UTF_8)
                                }
                                reportPath = reportFile.absolutePath
                                true
                            } else false
                        }
                        if (reportSaved) {
                            log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_d6fab15d, reportPath))
                        }
                    } catch (e: Exception) {
                        log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_b86b62e5, e.message))
                    }
                }

                val savedBytes = originalApkSize - finalSize
                val totalTime = System.currentTimeMillis() - totalStartTime
                log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_5a046f71))
                val sizeDesc = if (savedBytes > 0) {
                    ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_4f305d7d,
                        Format.formatSize(originalApkSize), Format.formatSize(finalSize), Format.formatSize(savedBytes))
                } else {
                    "${Format.formatSize(originalApkSize)} → ${Format.formatSize(finalSize)}"
                }
                log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_618d3deb,
                    sizeDesc, String.format(Locale.US, "%.1f", totalTime / 1000.0)))

                flushLog()
                _state.update {
                    it.copy(
                        isProcessing = false,
                        result = AdRemoverResult(
                            exportDesc = exportDesc,
                            reportPath = reportPath,
                            originalApkSize = originalApkSize,
                            finalSize = finalSize,
                            savedBytes = savedBytes,
                            totalTimeMs = totalTime
                        )
                    )
                }
            } catch (e: OutOfMemoryError) {
                log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_6a80c39a))
                log("  ✗ ${e.message}")
                log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_9d99f2d3))
                System.gc()
                flushLog()
                _state.update { it.copy(isProcessing = false, error = ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_3a3bf3f6)) }
            } catch (e: StackOverflowError) {
                log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_b93b4fec))
                log("  ✗ ${e.message}")
                flushLog()
                _state.update { it.copy(isProcessing = false, error = ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_8eaa86c7)) }
            } catch (e: Exception) {
                log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_acd3e0fc))
                log("  ✗ ${e.message}")
                log(ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_e63572b7b, e.stackTraceToString().take(300)))
                flushLog()
                _state.update { it.copy(isProcessing = false, error = ctx.getString(com.shinegirls.apkadremovereditor.R.string.h_9bec2ff2, e.message)) }
            } finally {
                processing = false
                workDir?.let { dir ->
                    try {
                        withContext(Dispatchers.IO) { dir.deleteRecursively() }
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    // ==================== 导出辅助 ====================

    private fun queryRealPath(uri: Uri): String? {
        return try {
            getApplication<Application>().contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex("_data")
                    if (idx >= 0) c.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun createOutputInSelectedDir(uri: Uri, fileName: String): Uri? {
        val ctx = getApplication<Application>()
        return try {
            if (!DocumentsContract.isDocumentUri(ctx, uri)) return null
            val docId = DocumentsContract.getDocumentId(uri)
            val slash = docId.lastIndexOf('/')
            if (slash <= 0) return null
            val parentDocId = docId.substring(0, slash)
            val parentUri = DocumentsContract.buildDocumentUri(uri.authority, parentDocId)
                ?: return null
            DocumentsContract.createDocument(
                ctx.contentResolver,
                parentUri,
                "application/vnd.android.package-archive",
                fileName
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun docUriToReadablePath(uri: Uri, fileName: String): String {
        val ctx = getApplication<Application>()
        return try {
            if (DocumentsContract.isDocumentUri(ctx, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val slash = docId.lastIndexOf('/')
                if (slash > 0) {
                    val parentDocId = docId.substring(0, slash)
                    if (parentDocId.startsWith("primary:")) {
                        val dir = parentDocId.substringAfter(':')
                        val base = if (dir.isEmpty()) "/storage/emulated/0" else "/storage/emulated/0/$dir"
                        return "$base/$fileName"
                    }
                }
            }
            uri.toString()
        } catch (_: Exception) {
            uri.toString()
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun elapsedMs(startTime: Long): String = "${System.currentTimeMillis() - startTime}ms"
}
