package com.evacipated.cardcrawl.mod.texturereplacer

import basemod.IUIElement
import basemod.ModLabeledToggleButton
import basemod.ModPanel
import basemod.ReflectionHacks
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.evacipated.cardcrawl.mod.texturereplacer.extensions.drawAtOrigin
import com.evacipated.cardcrawl.mod.texturereplacer.extensions.scale
import com.megacrit.cardcrawl.core.CardCrawlGame
import com.megacrit.cardcrawl.core.Settings
import com.megacrit.cardcrawl.helpers.*
import com.megacrit.cardcrawl.helpers.input.InputHelper
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBar
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBarListener
import java.util.*
import kotlin.math.max
import kotlin.math.min

class TexPackToggleList(
    packs: List<TextureReplacer.TexPack>,
    private val xPos: Float,
    private val yPos: Float,
    private val width: Float,
    private val height: Float,
    val parent: ModPanel
) : IUIElement, ScrollBarListener {
    companion object {
        private val upArrow = ImageMaster.loadImage("img/tinyLeftArrow.png")
        private val downArrow = ImageMaster.loadImage("img/tinyRightArrow.png")

        private const val TOP_PAD = 10
    }

    private val scrollbar: MyScrollBar
    private var scrollPosition: Float = 0f
    private val maxScrollPosition: Float = max(0f, (40f * (packs.size - 1)) - height)
    private val useScrollBar: Boolean = maxScrollPosition > 0

    private val buttons: MutableList<ModLabeledToggleButton> = mutableListOf()
    private val orderButtons: MutableList<OrderButtons> = mutableListOf()

    private val reorder: MutableList<Pair<Int, Int>> = mutableListOf()

    init {
        scrollbar = MyScrollBar(
            this,
            (xPos + width - 30).scale(),
            (yPos - height/2f).scale(),
            height.scale(),
            useScrollBar
        )
        load(packs)
    }

    fun load(packs: List<TextureReplacer.TexPack>) {
        buttons.clear()
        orderButtons.clear()

        var yPosTmp = yPos - TOP_PAD
        packs.forEach { pack ->
            val btn = ModLabeledToggleButton(
                pack.name,
                xPos + 80, yPosTmp,
                Settings.CREAM_COLOR, FontHelper.charDescFont,
                pack.enabled,
                parent,
                {}
            ) { button ->
                pack.enabled = button.enabled
            }
            buttons.add(btn)
            orderButtons.add(OrderButtons())
            yPosTmp -= 40
        }
    }

    override fun render(sb: SpriteBatch) {
        val camera = ReflectionHacks.getPrivate<OrthographicCamera>(Gdx.app.applicationListener, CardCrawlGame::class.java, "camera")
        sb.flush()
        val scissors = Rectangle()
        val clipBounds = Rectangle(
            xPos.scale(),
            (yPos - 30 - height).scale(),
            width.scale(),
            (height + 60).scale()
        )
        ScissorStack.calculateScissors(camera, sb.transformMatrix, clipBounds, scissors)
        ScissorStack.pushScissors(scissors)

        sb.color = Color(0f, 0f, 0f, 0.3f)
        sb.draw(
            ImageMaster.WHITE_SQUARE_IMG,
            0f, 0f,
            Settings.WIDTH.toFloat(), Settings.HEIGHT.toFloat()
        )

        buttons.forEachIndexed { i, it ->
            sb.color = Color.WHITE
            orderButtons[i].render(sb, i, buttons.size)

            it.render(sb)
        }
        sb.color = Color.WHITE
        scrollbar.render(sb)

        sb.flush()
        ScissorStack.popScissors()
    }

    override fun update() {
        val cursorPos = ReflectionHacks.getPrivate<Float>(scrollbar, ScrollBar::class.java, "cursorDrawPosition")
        val cursorPercent = scrollbar.getPercentFromY(cursorPos)

        var yPosTmp = yPos - TOP_PAD + MathHelper.valueFromPercentBetween(0f, maxScrollPosition, cursorPercent)
        buttons.forEachIndexed { i, it ->
            it.y = yPosTmp
            val y = it.y + 16
            orderButtons[i].update(xPos, y, i, buttons.size)

            it.update()
            yPosTmp -= 40
        }
        reorder.firstOrNull()?.let { (i, rel) ->
            TextureReplacer.swap(i, i + rel)
            Collections.swap(buttons, i, i + rel)
            val yTmp = buttons[i].y
            buttons[i].y = buttons[i + rel].y
            buttons[i + rel].y = yTmp
        }
        reorder.clear()
        if (!scrollbar.update()) {
            if (InputHelper.scrolledDown) {
                scrollPosition += Settings.SCROLL_SPEED
            } else if (InputHelper.scrolledUp) {
                scrollPosition -= Settings.SCROLL_SPEED
            }
            updateBarPosition()
        }
    }

    override fun scrolledUsingBar(v: Float) {
        scrollPosition = MathHelper.valueFromPercentBetween(0f, maxScrollPosition, v)
        updateBarPosition()
    }

    private fun updateBarPosition() {
        // clamp scroll position
        scrollPosition =  scrollPosition.coerceIn(0f, maxScrollPosition)

        var percent = MathHelper.percentFromValueBetween(0f, maxScrollPosition, scrollPosition)
        if (percent.isNaN()) {
           percent = 0f
        }
        scrollbar.parentScrolledToPercent(percent)
    }

    override fun renderLayer(): Int = ModPanel.MIDDLE_LAYER

    override fun updateOrder(): Int = ModPanel.DEFAULT_UPDATE

    private inner class OrderButtons {
        val hbUp = Hitbox(32.scale(), 32.scale())
        val hbDown = Hitbox(32.scale(), 32.scale())

        fun render(sb: SpriteBatch, index: Int, size: Int) {
            if (index == 0) {
                sb.color = Color.LIGHT_GRAY
                ShaderHelper.setShader(sb, ShaderHelper.Shader.GRAYSCALE)
            } else {
                sb.color = if (hbUp.hovered) {
                    Color.WHITE
                } else {
                    Color.LIGHT_GRAY
                }
            }
            sb.drawAtOrigin(
                upArrow,
                hbUp.cX, hbUp.cY,
                24f, 24f,
                48f, 48f,
                Settings.scale, Settings.scale,
                -90f,
            )
            if (index == 0) {
                ShaderHelper.setShader(sb, ShaderHelper.Shader.DEFAULT)
            }

            if (index == size - 1) {
                sb.color = Color.LIGHT_GRAY
                ShaderHelper.setShader(sb, ShaderHelper.Shader.GRAYSCALE)
            } else {
                sb.color = if (hbDown.hovered) {
                    Color.WHITE
                } else {
                    Color.LIGHT_GRAY
                }
            }
            sb.drawAtOrigin(
                downArrow,
                hbDown.cX, hbDown.cY,
                24f, 24f,
                48f, 48f,
                Settings.scale, Settings.scale,
                -90f,
            )
            if (index == size - 1) {
                ShaderHelper.setShader(sb, ShaderHelper.Shader.DEFAULT)
            }

            hbUp.render(sb)
            hbDown.render(sb)
        }

        fun update(x: Float, y: Float, index: Int, size: Int) {
            hbUp.move((x + 20).scale(), y.scale())
            hbDown.move((x + 55).scale(), y.scale())
            if (index != 0) {
                hbUp.update()
                if (hbUp.hovered && InputHelper.justClickedLeft) {
                    hbUp.clickStarted = true
                }
                if (hbUp.clicked) {
                    hbUp.clicked = false
                    reorder.add(Pair(index, -1))
                }
            }
            if (index < size - 1) {
                hbDown.update()
                if (hbDown.hovered && InputHelper.justClickedLeft) {
                    hbDown.clickStarted = true
                }
                if (hbDown.clicked) {
                    hbDown.clicked = false
                    reorder.add(Pair(index, 1))
                }
            }
        }
    }

    private class MyScrollBar(
        listener: ScrollBarListener,
        x: Float,
        private val y: Float,
        private val height: Float,
        private val enabled: Boolean
    ) : ScrollBar(listener, x, y, height) {
        companion object {
            val CURSOR_H = 60.scale()
        }

        init {
        }

        override fun update(): Boolean {
            if (!enabled) {
                return false
            }
            return super.update()
        }

        override fun render(sb: SpriteBatch) {
            if (!enabled) {
                ShaderHelper.setShader(sb, ShaderHelper.Shader.GRAYSCALE)
            }
            super.render(sb)
            if (!enabled) {
                ShaderHelper.setShader(sb, ShaderHelper.Shader.DEFAULT)
            }
        }

        fun getPercentFromY(y: Float): Float {
            val hbY = this.y - height/2f
            val minY = hbY + height - CURSOR_H
            val maxY = hbY
            return boundedPercent(MathHelper.percentFromValueBetween(minY, maxY, y))
        }

        private fun boundedPercent(percent: Float): Float {
            return max(0f, min(percent, 1f))
        }
    }
}
