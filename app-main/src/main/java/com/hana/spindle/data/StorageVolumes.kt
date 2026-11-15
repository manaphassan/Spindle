package com.hana.spindle.data

import java.io.File

/** Helpers for telling a missing file apart from a removable volume that is simply not inserted. */
object StorageVolumes {

    /**
     * Root of the removable volume holding [path] (e.g. `/storage/9016-4EF8`), or null when the
     * path is on internal shared storage (`/storage/emulated/...`, `/sdcard`, ...) or not under
     * `/storage` at all.
     */
    fun removableRootOf(path: String): String? {
        val parts = path.trimStart('/').split('/')
        if (parts.size < 2 || parts[0] != "storage") return null
        val volume = parts[1]
        if (volume.isEmpty() || volume == "emulated" || volume == "self") return null
        return "/storage/$volume"
    }

    /**
     * True when [path] lives on a removable volume that is not mounted right now. Such a file is
     * not gone, its card is just out, so library entries for it must be left alone.
     */
    fun isOnUnmountedVolume(path: String, isMounted: (String) -> Boolean = ::isDirectoryReadable): Boolean {
        val root = removableRootOf(path) ?: return false
        return !isMounted(root)
    }

    private fun isDirectoryReadable(root: String): Boolean = File(root).let { it.isDirectory && it.canRead() }
}
