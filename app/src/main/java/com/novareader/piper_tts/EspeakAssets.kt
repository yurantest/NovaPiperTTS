package com.novareader.piper_tts

import android.content.Context
import java.io.File

/**
 * sherpa-onnx читает espeak-ng-data по пути в файловой системе (когда AssetManager не передан),
 * поэтому папку из assets один раз копируем в filesDir.
 */
object EspeakAssets {
    private const val ASSET_DIR = "espeak-ng-data"
    private const val MARKER = ".copied"

    @Synchronized
    fun ensure(context: Context): File {
        val target = File(context.filesDir, ASSET_DIR)
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toString()
        } catch (_: Throwable) { "1" }
        val marker = File(target, MARKER)
        if (marker.isFile && marker.readText() == version && File(target, "phontab").isFile) return target

        target.deleteRecursively()
        target.mkdirs()
        copyDir(context, ASSET_DIR, target)
        check(File(target, "phontab").isFile) { "espeak-ng-data copy failed" }
        marker.writeText(version)
        FileLogger.log("EspeakAssets: copied to ${target.absolutePath}")
        return target
    }

    private fun copyDir(context: Context, assetPath: String, dest: File) {
        val children = context.assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            context.assets.open(assetPath).use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            return
        }
        dest.mkdirs()
        for (c in children) copyDir(context, "$assetPath/$c", File(dest, c))
    }
}
