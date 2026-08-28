package com.aciderix.obbinstaller

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.aciderix.obbinstaller.axml.AxmlNode
import com.aciderix.obbinstaller.axml.AxmlReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

data class ApkMeta(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val cacheFile: File
)

data class SplitMeta(
    val splitName: String,
    val cacheFile: File
)

object ApkInstaller {

    suspend fun stageAndReadMeta(
        context: Context,
        source: FileSource,
        onProgress: (Float) -> Unit
    ): ApkMeta = withContext(Dispatchers.IO) {
        val cache = File(context.cacheDir, "staged.apk")
        if (cache.exists()) cache.delete()
        val total = source.length(context).coerceAtLeast(1L)
        source.openStream(context).use { input ->
            cache.outputStream().use { output ->
                val buf = ByteArray(256 * 1024)
                var written = 0L
                var n: Int
                while (input.read(buf).also { n = it } > 0) {
                    output.write(buf, 0, n)
                    written += n
                    onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(cache.absolutePath, 0)
            ?: error("Invalid APK file: cannot read package info")
        val vc = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
                 else @Suppress("DEPRECATION") info.versionCode.toLong()
        ApkMeta(
            packageName = info.packageName,
            versionCode = vc,
            versionName = info.versionName.orEmpty(),
            cacheFile = cache
        )
    }

    /**
     * Stages a split APK to cache and reads its `android:split` name from the
     * binary manifest. PackageInstaller needs that name to match the split
     * declared inside the APK.
     */
    suspend fun stageSplit(context: Context, source: FileSource, index: Int): SplitMeta =
        withContext(Dispatchers.IO) {
            val cache = File(context.cacheDir, "staged-split-$index.apk")
            if (cache.exists()) cache.delete()
            val total = source.length(context).coerceAtLeast(1L)
            source.openStream(context).use { input ->
                cache.outputStream().use { output ->
                    val buf = ByteArray(256 * 1024)
                    var written = 0L
                    var n: Int
                    while (input.read(buf).also { n = it } > 0) {
                        output.write(buf, 0, n)
                        written += n
                    }
                }
            }
            val splitName = readSplitNameFromApk(cache)
                ?: error("Cannot read split name from ${source.displayName}")
            SplitMeta(splitName = splitName, cacheFile = cache)
        }

    suspend fun install(
        context: Context,
        meta: ApkMeta,
        splits: List<SplitMeta>,
        onProgress: (Float) -> Unit
    ): InstallResult = withContext(Dispatchers.IO) {
        val pi = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(meta.cacheFile.length() + splits.sumOf { it.cacheFile.length() })
            setAppPackageName(meta.packageName)
        }
        val sessionId = pi.createSession(params)
        val deferred = InstallSessionManager.register(sessionId)

        try {
            pi.openSession(sessionId).use { session ->
                val total = meta.cacheFile.length() + splits.sumOf { it.cacheFile.length() }
                var written = 0L

                session.openWrite("base.apk", 0, meta.cacheFile.length()).use { output ->
                    meta.cacheFile.inputStream().use { input ->
                        val buf = ByteArray(256 * 1024)
                        var n: Int
                        while (input.read(buf).also { n = it } > 0) {
                            output.write(buf, 0, n)
                            written += n
                            onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                        }
                        session.fsync(output)
                    }
                }

                for (split in splits) {
                    session.openWrite("split-${split.splitName}.apk", 0, split.cacheFile.length()).use { output ->
                        split.cacheFile.inputStream().use { input ->
                            val buf = ByteArray(256 * 1024)
                            var n: Int
                            while (input.read(buf).also { n = it } > 0) {
                                output.write(buf, 0, n)
                                written += n
                                onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                            }
                            session.fsync(output)
                        }
                    }
                }

                val intent = Intent(InstallResultReceiver.ACTION).apply {
                    setPackage(context.packageName)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
        } catch (t: Throwable) {
            InstallSessionManager.deliver(sessionId, InstallResult.Failure(t.message ?: "unknown error"))
        }

        deferred.await()
    }

    private fun readSplitNameFromApk(apk: File): String? = runCatching {
        ZipFile(apk).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml") ?: return null
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            readSplitName(bytes)
        }
    }.getOrNull()

    private fun readSplitName(manifestBytes: ByteArray): String? {
        val doc = AxmlReader.parse(manifestBytes)
        val sp = doc.stringPool
        val manifest = doc.nodes
            .filterIsInstance<AxmlNode.StartElement>()
            .firstOrNull { sp.get(it.name) == "manifest" } ?: return null
        val attr = manifest.attributes.firstOrNull { sp.get(it.name) == "split" } ?: return null
        val idx = if (attr.rawValue >= 0) attr.rawValue else attr.typedValue.data.toInt()
        return sp.get(idx)
    }
}
