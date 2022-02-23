package com.evacipated.cardcrawl.mod.texturereplacer.patches

import com.evacipated.cardcrawl.mod.texturereplacer.TextureReplacer
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2
import com.megacrit.cardcrawl.core.CardCrawlGame

@SpirePatch2(
    clz = CardCrawlGame::class,
    method = "create"
)
object InitTextureReplacer {
    @JvmStatic
    fun Prefix() {
        TextureReplacer.initialize()
    }
}
