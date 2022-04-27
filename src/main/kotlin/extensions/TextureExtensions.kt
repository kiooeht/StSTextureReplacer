package com.evacipated.cardcrawl.mod.texturereplacer.extensions

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureAtlas

fun Texture.asAtlasRegion(): TextureAtlas.AtlasRegion =
    TextureAtlas.AtlasRegion(this, 0, 0, this.width, this.height)
        .apply { index = -1 }
