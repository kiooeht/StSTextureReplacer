package com.evacipated.cardcrawl.mod.texturereplacer.patches

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Texture
import com.evacipated.cardcrawl.mod.texturereplacer.TextureReplacer
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2
import com.evacipated.cardcrawl.modthespire.lib.SpirePatches2
import com.megacrit.cardcrawl.helpers.ImageMaster
import javassist.expr.ExprEditor
import javassist.expr.NewExpr

@SpirePatches2(
    SpirePatch2(
        clz = ImageMaster::class,
        method = "loadImage",
        paramtypez = [String::class]
    ),
    SpirePatch2(
        clz = ImageMaster::class,
        method = "loadImage",
        paramtypez = [String::class, Boolean::class]
    )
)
object ImageMasterLoadImage {
    @JvmStatic
    fun Instrument(): ExprEditor =
        object : ExprEditor() {
            override fun edit(e: NewExpr) {
                if (e.className == Texture::class.qualifiedName) {
                    e.replace(
                        "\$_ = new ${Texture::class.qualifiedName}(${ImageMasterLoadImage::class.qualifiedName}.getImgFile(\$1));"
                    )
                }
            }
        }

    @Suppress("unused")
    @JvmStatic
    fun getImgFile(imgUrl: String): FileHandle =
        TextureReplacer.getImgFile(imgUrl)
}
