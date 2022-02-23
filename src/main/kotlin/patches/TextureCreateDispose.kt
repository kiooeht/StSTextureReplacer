package com.evacipated.cardcrawl.mod.texturereplacer.patches

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.TextureData
import com.evacipated.cardcrawl.mod.texturereplacer.TextureReplacer
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2

object TextureCreateDispose {
    @SpirePatch2(
        clz = Texture::class,
        method = SpirePatch.CONSTRUCTOR,
        paramtypez = [Int::class, Int::class, TextureData::class]
    )
    object Create {
        @JvmStatic
        fun Prefix(__instance: Texture, data: TextureData) {
            TextureReplacer.addTexture(__instance, data)
        }
    }

    @SpirePatch2(
        clz = Texture::class,
        method = "dispose"
    )
    object Dispose {
        @JvmStatic
        fun Prefix(__instance: Texture) {
            TextureReplacer.disposeTexture(__instance)
        }
    }
}
