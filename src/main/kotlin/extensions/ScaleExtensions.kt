package com.evacipated.cardcrawl.mod.texturereplacer.extensions

import com.badlogic.gdx.Gdx
import com.megacrit.cardcrawl.core.Settings

fun Int.scale(): Float =
    this * Settings.scale

fun Float.scale(): Float =
    this * Settings.scale

fun Int.timeScale(): Float =
    this * Gdx.graphics.deltaTime

fun Float.timeScale(): Float =
    this * Gdx.graphics.deltaTime
