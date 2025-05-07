package com.evacipated.cardcrawl.mod.texturereplacer.patches

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.evacipated.cardcrawl.mod.texturereplacer.TextureReplacer
import com.evacipated.cardcrawl.modthespire.lib.ByRef
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2

object TextureAtlasImageReplace {
    internal var disable = false

    @SpirePatch2(
        clz = TextureAtlas.TextureAtlasData::class,
        method = SpirePatch.CONSTRUCTOR
    )
    object ReplaceAtlas {
        @JvmStatic
        fun Prefix(@ByRef packFile: Array<FileHandle>) {
            if (disable) return
            packFile[0] = TextureReplacer.getFileHandle(packFile[0])
        }
    }

    @SpirePatch2(
        clz = TextureAtlas.TextureAtlasData.Page::class,
        method = SpirePatch.CONSTRUCTOR
    )
    object ReplaceImage {
        @JvmStatic
        fun Prefix (@ByRef handle: Array<FileHandle>) {
            if (disable) return
            handle[0] = TextureReplacer.getFileHandle(handle[0])
        }
    }
}
