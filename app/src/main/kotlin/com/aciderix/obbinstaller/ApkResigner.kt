package com.aciderix.obbinstaller

import android.content.Context
import com.aciderix.obbinstaller.axml.ManifestPatcher
import com.android.apksig.ApkSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

const val BOOTSTRAP_PROVIDER_CLASS = "com.aciderix.obbbootstrap.ObbBootstrapProvider"
const val BOOTSTRAP_AUTHORITY_SUFFIX = ".aciderix.obb.bootstrap"
private const val BOOTSTRAP_ASSETS_DIR = "bootstrap"

object ApkResigner {

    private const val KEYSTORE_ASSET = "hub.keystore"
    private const val KEY_ALIAS = "hub"
    private const val KEY_PASSWORD = "obbinstaller"

    private fun loadKeyMaterial(context: Context): Pair<PrivateKey, X509Certificate> {
        val ks = KeyStore.getInstance("PKCS12")
        context.assets.open(KEYSTORE_ASSET).use { ks.load(it, KEY_PASSWORD.toCharArray()) }
        val key = ks.getKey(KEY_ALIAS, KEY_PASSWORD.toCharArray()) as PrivateKey
        val cert = ks.getCertificate(KEY_ALIAS) as X509Certificate
        return key to cert
    }

    /**
     * Patches the input APK to:
     * - inject a `<provider>` element for [BOOTSTRAP_PROVIDER_CLASS],
     * - inject our compiled bootstrap dex as the next available `classesN.dex`,
     * - bundle the OBB (if any) inside `assets/<obbFilename>` so the bootstrap
     *   provider can copy it into the game's own obb dir on first launch,
     * - optionally bundle a second `patch` OBB alongside the main one.
     *
     * Then re-signs the result with the hub keystore.
     */
    suspend fun patchAndResign(
        context: Context,
        inputApk: File,
        gamePackage: String,
        obbSource: FileSource?,
        obbFilename: String?,
        obbPatchSource: FileSource?,
        obbPatchFilename: String?,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val unsigned = File(context.cacheDir, "patched-unsigned.apk")
        val signed = File(context.cacheDir, "patched-signed.apk")
        val stagedObb = File(context.cacheDir, "staged.obb")
        val stagedPatch = File(context.cacheDir, "staged-patch.obb")
        listOf(unsigned, signed, stagedObb, stagedPatch).forEach { if (it.exists()) it.delete() }

        // Stage OBB(s) once to cache, computing CRC and length so we can write
        // them as STORED zip entries (mmap-friendly inside the patched APK,
        // single pass).
        var obbBytes = 0L
        var obbCrc = 0L
        if (obbSource != null) {
            val total = obbSource.length(context).coerceAtLeast(1L)
            val crc = CRC32()
            obbSource.openStream(context).use { input ->
                stagedObb.outputStream().buffered().use { output ->
                    val buf = ByteArray(512 * 1024)
                    var n: Int
                    var written = 0L
                    while (input.read(buf).also { n = it } > 0) {
                        crc.update(buf, 0, n)
                        output.write(buf, 0, n)
                        written += n
                        obbBytes = written
                        onProgress(0.20f * (written.toFloat() / total).coerceIn(0f, 1f))
                    }
                    output.flush()
                }
            }
            obbCrc = crc.value
        }

        var patchBytes = 0L
        var patchCrc = 0L
        if (obbPatchSource != null) {
            val total = obbPatchSource.length(context).coerceAtLeast(1L)
            val crc = CRC32()
            obbPatchSource.openStream(context).use { input ->
                stagedPatch.outputStream().buffered().use { output ->
                    val buf = ByteArray(512 * 1024)
                    var n: Int
                    var written = 0L
                    while (input.read(buf).also { n = it } > 0) {
                        crc.update(buf, 0, n)
                        output.write(buf, 0, n)
                        written += n
                        patchBytes = written
                        onProgress(0.20f + 0.10f * (written.toFloat() / total).coerceIn(0f, 1f))
                    }
                    output.flush()
                }
            }
            patchCrc = crc.value
        }

        // List the bootstrap dex files bundled in our assets. AGP debug builds
        // split classes across many classesN.dex files, so we ship them all and
        // re-number them when we inject so they slot in after the game's own
        // classesN.dex without any gap (ART stops scanning at the first gap).
        val bootstrapDexNames = (context.assets.list(BOOTSTRAP_ASSETS_DIR) ?: emptyArray())
            .filter { it.endsWith(".dex") }
            .sortedWith(compareBy { name ->
                // classes.dex first, then classes2, classes3, ...
                Regex("^classes(\\d*)\\.dex$").matchEntire(name)
                    ?.groupValues?.get(1)?.ifEmpty { "1" }?.toInt() ?: Int.MAX_VALUE
            })
        check(bootstrapDexNames.isNotEmpty()) { "no bootstrap dex assets bundled" }

        // Phase 1: rewrite APK with patched manifest, injected dex(s), injected obb,
        // dropping v1 signature entries.
        ZipFile(inputApk).use { zip ->
            val firstFreeDexIndex = nextDexIndex(zip)
            val injectedDexNames = bootstrapDexNames.indices.map { i ->
                val n = firstFreeDexIndex + i
                if (n == 1) "classes.dex" else "classes$n.dex"
            }.toSet()

            ZipOutputStream(unsigned.outputStream().buffered()).use { out ->
                val entries = zip.entries().toList()
                val total = entries.size.coerceAtLeast(1)
                for ((i, e) in entries.withIndex()) {
                    onProgress(0.30f + 0.30f * i / total)
                    if (e.isDirectory) continue
                    val name = e.name
                    if (isSignatureEntry(name)) continue
                    if (obbFilename != null && name == "assets/$obbFilename") continue  // we re-add fresh copies
                    if (obbPatchFilename != null && name == "assets/$obbPatchFilename") continue
                    if (name in injectedDexNames) continue  // safety
                    if (name == "AndroidManifest.xml") {
                        val original = zip.getInputStream(e).use { it.readBytes() }
                        val authority = "$gamePackage$BOOTSTRAP_AUTHORITY_SUFFIX"
                        // Android 14+ refuses targetSdk < 24 (sometimes higher).
                        // Bump the manifest first, then add our provider.
                        val bumped = ManifestPatcher.bumpTargetSdk(original, 24)
                        val patched = ManifestPatcher.addBootstrapProvider(
                            originalManifest = bumped,
                            providerClass = BOOTSTRAP_PROVIDER_CLASS,
                            authority = authority
                        )
                        writeDeflate(out, "AndroidManifest.xml", patched)
                    } else if (name.startsWith("lib/") && name.endsWith(".so")) {
                        // Old games ship .so files with text relocations rejected
                        // once targetSdk >= 24, and with RWX segments rejected by
                        // Android 13+ W^X. Patch the ELF in place (clears
                        // DT_TEXTREL, strips PF_W from executable segments) and
                        // opportunistically bump p_align for 16 KB page devices.
                        val data = zip.getInputStream(e).use { it.readBytes() }
                        // Malformed ELF must never abort the whole install: if a
                        // patch fails, ship the original bytes untouched.
                        runCatching { ElfTextrelPatcher.patch(data) }
                        runCatching { ElfAlignPatcher.patch16k(data) }
                        val newEntry = ZipEntry(name).apply {
                            method = e.method
                            if (method == ZipEntry.STORED) {
                                size = data.size.toLong()
                                compressedSize = data.size.toLong()
                                crc = java.util.zip.CRC32().apply { update(data) }.value
                            }
                        }
                        out.putNextEntry(newEntry)
                        out.write(data)
                        out.closeEntry()
                    } else {
                        copyEntry(zip, e, out)
                    }
                }

                // Inject bootstrap dex(s) - re-numbered to be contiguous with the
                // game's existing classesN.dex range.
                for ((i, assetName) in bootstrapDexNames.withIndex()) {
                    val n = firstFreeDexIndex + i
                    val outName = if (n == 1) "classes.dex" else "classes$n.dex"
                    val dexBytes = context.assets.open("$BOOTSTRAP_ASSETS_DIR/$assetName")
                        .use { it.readBytes() }
                    writeDeflate(out, outName, dexBytes)
                }

                // Inject the main OBB (STORED for mmap-friendly access in the game)
                if (obbSource != null && obbFilename != null) {
                    val obbEntry = ZipEntry("assets/$obbFilename")
                    obbEntry.method = ZipEntry.STORED
                    obbEntry.size = obbBytes
                    obbEntry.compressedSize = obbBytes
                    obbEntry.crc = obbCrc
                    out.putNextEntry(obbEntry)
                    stagedObb.inputStream().buffered().use { it.copyTo(out) }
                    out.closeEntry()
                }

                // Inject the optional patch OBB (some games need a second file)
                if (obbPatchSource != null && obbPatchFilename != null) {
                    val patchEntry = ZipEntry("assets/$obbPatchFilename")
                    patchEntry.method = ZipEntry.STORED
                    patchEntry.size = patchBytes
                    patchEntry.compressedSize = patchBytes
                    patchEntry.crc = patchCrc
                    out.putNextEntry(patchEntry)
                    stagedPatch.inputStream().buffered().use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
        }
        if (stagedObb.exists()) stagedObb.delete()
        if (stagedPatch.exists()) stagedPatch.delete()

        onProgress(0.65f)

        // Phase 2: sign with apksig (v1 + v2 + v3).
        signApk(context, unsigned, signed)
        onProgress(1f)
        signed
    }

    /**
     * Patches a split APK for install alongside a patched base: strips old
     * signature entries, fixes up native libraries (text relocations, RWX
     * segments, 16 KB alignment), and re-signs with the hub key. Split
     * manifests are left untouched — the bootstrap provider only lives in the
     * base APK.
     */
    suspend fun patchSplitApk(
        context: Context,
        inputApk: File
    ): File = withContext(Dispatchers.IO) {
        val unsigned = File(context.cacheDir, "split-unsigned.apk")
        val signed = File(context.cacheDir, "split-signed.apk")
        listOf(unsigned, signed).forEach { if (it.exists()) it.delete() }

        ZipFile(inputApk).use { zip ->
            ZipOutputStream(unsigned.outputStream().buffered()).use { out ->
                for (e in zip.entries()) {
                    if (e.isDirectory) continue
                    val name = e.name
                    if (isSignatureEntry(name)) continue
                    if (name.startsWith("lib/") && name.endsWith(".so")) {
                        val data = zip.getInputStream(e).use { it.readBytes() }
                        // Malformed ELF must never abort the whole install: if a
                        // patch fails, ship the original bytes untouched.
                        runCatching { ElfTextrelPatcher.patch(data) }
                        runCatching { ElfAlignPatcher.patch16k(data) }
                        val newEntry = ZipEntry(name).apply {
                            method = e.method
                            if (method == ZipEntry.STORED) {
                                size = data.size.toLong()
                                compressedSize = data.size.toLong()
                                crc = java.util.zip.CRC32().apply { update(data) }.value
                            }
                        }
                        out.putNextEntry(newEntry)
                        out.write(data)
                        out.closeEntry()
                    } else {
                        copyEntry(zip, e, out)
                    }
                }
            }
        }

        signApk(context, unsigned, signed)
        unsigned.delete()
        signed
    }

    private fun signApk(context: Context, unsigned: File, signed: File) {
        val (key, cert) = loadKeyMaterial(context)
        val signerConfig = ApkSigner.SignerConfig.Builder(KEY_ALIAS, key, listOf(cert)).build()
        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(unsigned)
            .setOutputApk(signed)
            .setMinSdkVersion(30)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setOtherSignersSignaturesPreserved(false)
            .build()
            .sign()
    }

    private fun isSignatureEntry(name: String): Boolean =
        name.startsWith("META-INF/") && (
            name == "META-INF/MANIFEST.MF" ||
            name.endsWith(".SF") || name.endsWith(".RSA") ||
            name.endsWith(".DSA") || name.endsWith(".EC")
        )

    private fun copyEntry(zip: ZipFile, source: ZipEntry, out: ZipOutputStream) {
        val newEntry = ZipEntry(source.name).apply {
            method = source.method
            if (method == ZipEntry.STORED) {
                size = source.size
                crc = source.crc
                compressedSize = source.compressedSize
            }
        }
        out.putNextEntry(newEntry)
        zip.getInputStream(source).use { it.copyTo(out) }
        out.closeEntry()
    }

    private fun writeDeflate(out: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name).apply { method = ZipEntry.DEFLATED }
        out.putNextEntry(entry)
        out.write(bytes)
        out.closeEntry()
    }

    private fun nextDexIndex(zip: ZipFile): Int {
        val pattern = Regex("^classes(\\d*)\\.dex$")
        var max = 0
        val it = zip.entries()
        while (it.hasMoreElements()) {
            val m = pattern.matchEntire(it.nextElement().name) ?: continue
            val n = m.groupValues[1].ifEmpty { "1" }.toInt()
            if (n > max) max = n
        }
        return max + 1
    }
}
