package com.evacipated.cardcrawl.mod.texturereplacer.extensions

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion

fun SpriteBatch.drawAtOrigin(
    region: TextureRegion,
    x: Float,
    y: Float,
    originX: Float,
    originY: Float,
    width: Float,
    height: Float,
    scaleX: Float,
    scaleY: Float,
    rotation: Float
) {
    this.draw(
        region,
        x - originX, y - originY,
        originX, originY,
        width, height,
        scaleX, scaleY,
        rotation
    )
}

fun SpriteBatch.drawAtOrigin(
    texture: Texture,
    x: Float,
    y: Float,
    originX: Float,
    originY: Float,
    width: Float,
    height: Float,
    scaleX: Float,
    scaleY: Float,
    rotation: Float,
    srcX: Int = 0,
    srcY: Int = 0,
    srcWidth: Int = width.toInt(),
    srcHeight: Int = height.toInt(),
    flipX: Boolean = false,
    flipY: Boolean = false
) {
    this.draw(
        texture,
        x - originX, y - originY,
        originX, originY,
        width, height,
        scaleX, scaleY,
        rotation,
        srcX, srcY,
        srcWidth, srcHeight,
        flipX, flipY
    )
}
