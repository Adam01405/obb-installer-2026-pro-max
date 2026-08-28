package com.aciderix.obbinstaller

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Conservative 16 KB page-size compatibility patcher for ELF64 shared libraries.
 *
 * Android 15+ devices shipped with 16 KB pages refuse to dlopen any library
 * whose LOAD segments are not aligned to the page size (the linker checks that
 * both p_offset and p_vaddr are 16 KB multiples, and that p_align covers it).
 * Most old-game .so files are only 4 KB aligned and use p_align = 0x1000.
 *
 * This patcher only touches libraries that are *already* 16 KB aligned at the
 * file/address level but simply advertise a smaller p_align — a common state
 * for libraries whose layout happens to satisfy the larger alignment. In that
 * case bumping p_align to 0x4000 is enough to pass the linker check without
 * any layout surgery.
 *
 * Libraries whose segments are not actually 16 KB aligned are left untouched:
 * rewriting them would require a full ELF relayout (recomputing every section
 * offset and moving padding), which is too risky to do blindly at install
 * time. For those the game behaves exactly as before this change.
 *
 * 32-bit ELF is skipped on purpose — 16 KB devices do not run 32-bit ABIs.
 */
object ElfAlignPatcher {

    private const val EI_CLASS = 4
    private const val EI_DATA = 5
    private const val ELFCLASS32 = 1
    private const val ELFCLASS64 = 2
    private const val ELFDATA2LSB = 1
    private const val PT_LOAD = 1
    private const val PAGE16K = 16384L

    /**
     * Returns true iff the buffer was modified. Non-ELF, 32-bit, or already
     * 16 KB-compliant files are left untouched.
     */
    fun patch16k(bytes: ByteArray): Boolean {
        if (bytes.size < 64) return false
        if (bytes[0] != 0x7F.toByte() || bytes[1] != 'E'.code.toByte() ||
            bytes[2] != 'L'.code.toByte() || bytes[3] != 'F'.code.toByte()) return false
        val klass = bytes[EI_CLASS].toInt() and 0xFF
        val data = bytes[EI_DATA].toInt() and 0xFF
        if (klass != ELFCLASS64 || data != ELFDATA2LSB) return false

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val phoff = buf.getLong(32)
        val phentsize = buf.getShort(54).toInt() and 0xFFFF
        val phnum = buf.getShort(56).toInt() and 0xFFFF
        if (phoff == 0L || phentsize < 56 || phnum == 0) return false

        var allAligned = true
        var needsBump = false
        val alignOffsets = mutableListOf<Int>()
        for (i in 0 until phnum) {
            val off = (phoff + i * phentsize).toInt()
            val pType = buf.getInt(off)
            if (pType != PT_LOAD) continue
            val pAlign = buf.getLong(off + 48)
            val pOffset = buf.getLong(off + 8)
            val pVaddr = buf.getLong(off + 16)
            if (pAlign < PAGE16K) needsBump = true
            if (pOffset % PAGE16K != 0L || pVaddr % PAGE16K != 0L) allAligned = false
            alignOffsets.add(off + 48)
        }
        if (alignOffsets.isEmpty() || !needsBump || !allAligned) return false

        alignOffsets.forEach { buf.putLong(it, PAGE16K) }
        return true
    }
}
