package com.hana.spindle.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageVolumesTest {

    @Test
    fun removableRootOfSdCardPath() {
        assertEquals("/storage/000B-B400", StorageVolumes.removableRootOf("/storage/000B-B400/Music/a.mp3"))
    }

    @Test
    fun internalStorageIsNotRemovable() {
        assertNull(StorageVolumes.removableRootOf("/storage/emulated/0/Music/a.mp3"))
        assertNull(StorageVolumes.removableRootOf("/storage/self/primary/a.mp3"))
        assertNull(StorageVolumes.removableRootOf("/sdcard/Music/a.mp3"))
        assertNull(StorageVolumes.removableRootOf("/data/media/a.mp3"))
    }

    @Test
    fun unmountedOnlyWhenRemovableRootIsMissing() {
        val mounted = setOf("/storage/9016-4EF8")
        val isMounted: (String) -> Boolean = { it in mounted }
        assertTrue(StorageVolumes.isOnUnmountedVolume("/storage/000B-B400/Music/a.mp3", isMounted))
        assertFalse(StorageVolumes.isOnUnmountedVolume("/storage/9016-4EF8/Music/a.mp3", isMounted))
        assertFalse(StorageVolumes.isOnUnmountedVolume("/storage/emulated/0/Music/a.mp3", isMounted))
    }
}
