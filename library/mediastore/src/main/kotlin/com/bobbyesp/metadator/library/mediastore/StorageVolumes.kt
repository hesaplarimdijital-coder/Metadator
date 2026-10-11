/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.metadator.library.mediastore

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The storage volumes songs can be on: the device's own, and an SD card or a USB drive when one is
 * mounted. MediaStore lists the songs of all of them as one collection, with each song's folder
 * relative to its own volume. These say where each volume is, and what to call it.
 */
internal class StorageVolumes(private val context: Context) {
    private val storage: StorageManager? = context.getSystemService(StorageManager::class.java)

    /**
     * What each volume is called, by MediaStore's name for it, or by its root before Android 10.
     */
    private val labels = ConcurrentHashMap<String, String>()

    /**
     * Where the folder [relativePath] of the volume MediaStore names [volumeName] is, as shown: as
     * it is on the device's own storage, and under the volume's own name elsewhere ("SD card", or
     * the card's brand). Without it, `Music/Album/` on an SD card would be taken for the folder of
     * the same name on the device, and the two would be listed as one.
     */
    fun folderOf(volumeName: String?, relativePath: String?): String? {
        val relative = relativePath ?: return null
        val label = label(volumeName) ?: return relative
        return "$label/$relative"
    }

    /**
     * The same, from a file's path: what Android 9 and earlier say instead of a volume's name.
     * [roots] are the mounted volumes' ([mountedRoots]), asked for once for a whole list of files:
     * asking costs a call to the system.
     */
    fun folderOfPath(path: String, roots: List<String>): String {
        val folder = path.substringBeforeLast('/', "")
        val root = rootOf(folder, roots) ?: return "$folder/"
        val relative =
            folder.removePrefix(root).trimStart('/').let { if (it.isEmpty()) "" else "$it/" }
        // The top of the device's own storage was always "/".
        val label = labelOfRoot(root) ?: return relative.ifEmpty { "/" }
        return "$label/$relative"
    }

    /**
     * What the volume MediaStore names [volumeName] is called, or null for the device's own
     * storage: what the library tells an SD card's songs apart by.
     */
    fun volumeOf(volumeName: String?): String? = label(volumeName)

    /** The same, from a file's path, before Android 10. */
    fun volumeOfPath(path: String, roots: List<String>): String? =
        rootOf(path.substringBeforeLast('/', ""), roots)?.let(::labelOfRoot)

    /** The root of the volume [folder] is on: the longest of them it is under. */
    private fun rootOf(folder: String, roots: List<String>): String? =
        (listOf(primaryRoot()) + roots)
            .filter { folder == it || folder.startsWith("$it/") }
            .maxByOrNull { it.length }

    /** What the volume at [root] is called; null for the device's own storage. */
    private fun labelOfRoot(root: String): String? {
        if (root == primaryRoot()) return null
        return labels.getOrPut(root) {
            runCatching { storage?.getStorageVolume(File(root))?.getDescription(context) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() } ?: root.substringAfterLast('/')
        }
    }

    private fun primaryRoot(): String =
        Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')

    /**
     * The roots of the volumes mounted now, the device's own among them: what to ask the media
     * scanner to look at again.
     */
    fun mountedRoots(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            storage
                ?.storageVolumes
                .orEmpty()
                .filter { it.state == Environment.MEDIA_MOUNTED }
                .mapNotNull { it.directory?.absolutePath }
        } else {
            // Before 11 a volume does not say where it is. The app's own folder on each does: a
            // volume that is not mounted has none.
            context.getExternalFilesDirs(null).filterNotNull().map {
                it.absolutePath.substringBefore("/Android/data/")
            }
        }

    /** The roots of the volumes other than the device's own: SD cards and USB drives. */
    fun removableRoots(): List<String> {
        val primary = primaryRoot()
        return mountedRoots().map { it.trimEnd('/') }.filter { it != primary }.distinct()
    }

    /** Nothing for the device's own storage, the volume's own name for any other. */
    private fun label(volumeName: String?): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (volumeName == null || volumeName == MediaStore.VOLUME_EXTERNAL_PRIMARY) return null
        labels[volumeName]?.let {
            return it
        }
        val described = runCatching {
            storage
                ?.getStorageVolume(MediaStore.Audio.Media.getContentUri(volumeName))
                ?.getDescription(context)
        }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        // Kept once known; a volume still being mounted may not say yet, and is asked again.
        if (described != null) labels[volumeName] = described
        return described ?: volumeName.uppercase()
    }
}
