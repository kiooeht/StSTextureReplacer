package com.evacipated.cardcrawl.mod.texturereplacer.patches

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.evacipated.cardcrawl.mod.texturereplacer.TextureReplacer
import com.evacipated.cardcrawl.modthespire.lib.*
import javassist.CtBehavior
import java.nio.file.Paths

object TextureAtlasLoad {
    @SpirePatch2(
        clz = TextureAtlas::class,
        method = "load"
    )
    object ReplaceRegions {
        @JvmStatic
        @SpireInsertPatch(
            locator = Locator::class,
            localvars = ["atlasRegion"]
        )
        fun Insert(__instance: TextureAtlas, data: TextureAtlas.TextureAtlasData, @ByRef atlasRegion: Array<TextureAtlas.AtlasRegion>) {
            TextureReplacer.addOriginalRegion(atlasRegion[0], data)

            val region = TextureReplacer.getAtlasRegion(data, atlasRegion[0].name)
            if (region != null) {
                region.name = atlasRegion[0].name
                region.texture.setFilter(atlasRegion[0].texture.minFilter, atlasRegion[0].texture.magFilter)
                __instance.textures.add(region.texture)
                atlasRegion[0] = region
            }

            TextureReplacer.addRegion(atlasRegion[0], data)
        }

        class Locator : SpireInsertLocator() {
            override fun Locate(ctBehavior: CtBehavior?): IntArray {
                val finalMatcher = Matcher.MethodCallMatcher(com.badlogic.gdx.utils.Array::class.java, "add")
                return LineFinder.findInOrder(ctBehavior, finalMatcher)
            }
        }
    }

    @SpirePatch2(
        clz = TextureAtlas::class,
        method = "dispose"
    )
    object AtlasDispose {
        @JvmStatic
        fun Prefix(__instance: TextureAtlas) {
            TextureReplacer.disposeAtlas(__instance)
        }
    }

    @SpirePatch(
        clz = TextureAtlas.TextureAtlasData::class,
        method = SpirePatch.CLASS
    )
    object DataField {
        @JvmField
        val filename: SpireField<String> = SpireField { "" }
    }

    @SpirePatch2(
        clz = TextureAtlas.TextureAtlasData::class,
        method = SpirePatch.CONSTRUCTOR
    )
    object SaveDataFilename {
        @JvmStatic
        fun Prefix(__instance: TextureAtlas.TextureAtlasData, packFile: FileHandle) {
            DataField.filename.set(__instance, Paths.get(packFile.path()).toString())
        }
    }
}
