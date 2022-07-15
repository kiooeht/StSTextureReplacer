package com.evacipated.cardcrawl.mod.texturereplacer.patches

import com.badlogic.gdx.files.FileHandle
import com.esotericsoftware.spine.SkeletonJson
import com.evacipated.cardcrawl.mod.texturereplacer.TextureReplacer
import com.evacipated.cardcrawl.modthespire.lib.ByRef
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2

@SpirePatch2(
    clz = SkeletonJson::class,
    method = "readSkeletonData"
)
object SkeletonJsonLoad {
    @JvmStatic
    fun Prefix(@ByRef file: Array<FileHandle>) {
        file[0] = TextureReplacer.getFileHandle(file[0])
    }
}
