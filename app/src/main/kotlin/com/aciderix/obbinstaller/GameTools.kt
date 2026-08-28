package com.aciderix.obbinstaller

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Date

data class InstalledGame(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val signatureMatches: Boolean?
)

object GameTools {

    fun detectInstalled(context: Context, meta: ApkMeta): InstalledGame? = runCatching {
        val pm = context.packageManager
        val info = pm.getPackageInfo(meta.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: return null
        val vc = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
                 else @Suppress("DEPRECATION") info.versionCode.toLong()
        val sigs = info.signingInfo.apkContentsSigners
        val hubHash = hubCertSha256(context)
        val installedHash = sigs.firstOrNull()?.let { sha256Hex(it.toByteArray()) }
        val matches = when {
            hubHash == null || installedHash == null -> null
            else -> hubHash == installedHash
        }
        InstalledGame(
            packageName = meta.packageName,
            versionName = info.versionName ?: "",
            versionCode = vc,
            signatureMatches = matches
        )
    }.getOrNull()

    /**
     * Reinstalling the same/newer version of a re-signed app fails when the
     * installed signature differs, and same-version reinstall is pointless.
     * Old-version installs with a matching signature proceed silently.
     */
    fun needsConfirmation(installed: InstalledGame, meta: ApkMeta): Boolean {
        if (installed.signatureMatches == false) return true
        return installed.versionCode >= meta.versionCode
    }

    private fun hubCertSha256(context: Context): String? = runCatching {
        val ks = java.security.KeyStore.getInstance("PKCS12")
        context.assets.open("hub.keystore").use { ks.load(it, "obbinstaller".toCharArray()) }
        val cert = ks.getCertificate("hub") as X509Certificate
        sha256Hex(cert.encoded)
    }.getOrNull()

    fun sha256Hex(data: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(data)
        return d.joinToString("") { "%02x".format(it) }
    }

    fun exportApk(
        context: Context,
        src: File,
        packageName: String,
        versionName: String,
        folderUri: Uri? = null
    ): String {
        val safeName = (if (versionName.isNotBlank()) versionName else "1.0")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val fileName = "$packageName-$safeName.apk"
        val folderUriNonNull = folderUri
        if (folderUriNonNull != null) {
            val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUriNonNull)
                ?: error("cannot open the selected folder")
            val target = tree.findFile(fileName)
                ?: tree.createFile("application/vnd.android.package-archive", fileName)
                ?: error("cannot create file in the selected folder")
            context.contentResolver.openOutputStream(target.uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            } ?: error("cannot open output stream")
            val folderName = tree.name ?: uriLastSegment(folderUriNonNull)
            return "$folderName/$fileName"
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/OBBInstaller")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("cannot create Downloads entry")
        resolver.openOutputStream(uri)?.use { out ->
            src.inputStream().use { it.copyTo(out) }
        } ?: error("cannot open output stream")
        val actualName = resolver.query(
            uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null
        )?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }
        return "Download/OBBInstaller/${actualName ?: fileName}"
    }

    private fun uriLastSegment(uri: Uri): String = uri.lastPathSegment ?: "Folder"

    fun obbDir(packageName: String): File =
        File(Environment.getExternalStorageDirectory(), "Android/obb/$packageName")

    fun openObbDirIntent(context: Context, packageName: String): Intent {
        val dir = obbDir(packageName)
        dir.mkdirs()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", dir)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "resource/folder")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))

    fun formatTime(context: Context, ts: Long): String {
        val date = Date(ts)
        val d = android.text.format.DateFormat.getDateFormat(context).format(date)
        val t = android.text.format.DateFormat.getTimeFormat(context).format(date)
        return "$d $t"
    }
}
