package com.evacipated.cardcrawl.mod.texturereplacer

import basemod.*
import basemod.interfaces.EditStringsSubscriber
import basemod.interfaces.PostInitializeSubscriber
import basemod.interfaces.PostRenderSubscriber
import basemod.patches.com.megacrit.cardcrawl.helpers.TipHelper.HeaderlessTip
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.evacipated.cardcrawl.mod.texturereplacer.extensions.scale
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer
import com.megacrit.cardcrawl.core.CardCrawlGame
import com.megacrit.cardcrawl.core.Settings
import com.megacrit.cardcrawl.helpers.FontHelper
import com.megacrit.cardcrawl.helpers.Hitbox
import com.megacrit.cardcrawl.helpers.ImageMaster
import com.megacrit.cardcrawl.helpers.input.InputHelper
import com.megacrit.cardcrawl.localization.UIStrings
import com.megacrit.cardcrawl.screens.mainMenu.MenuCancelButton
import java.awt.Desktop
import java.io.IOException
import java.util.*
import java.util.concurrent.atomic.AtomicReference

@SpireInitializer
class TextureReplacerMod :
    PostInitializeSubscriber,
    EditStringsSubscriber,
    PostRenderSubscriber
{
    companion object Statics {
        val ID: String
        val NAME: String

        init {
            var tmpID = "texturereplacer"
            var tmpNAME = "Texture Replacer"
            val properties = Properties()
            try {
                properties.load(TextureReplacerMod::class.java.getResourceAsStream("/META-INF/" + tmpID + "_version.prop"))
                tmpID = properties.getProperty("id")
                tmpNAME = properties.getProperty("name")
            } catch (e: IOException) {
                e.printStackTrace()
            }
            ID = tmpID
            NAME = tmpNAME
        }

        @Suppress("unused")
        @JvmStatic
        fun initialize() {
            BaseMod.subscribe(TextureReplacerMod())
        }

        fun makeID(id: String) = "$ID:$id"
        fun assetPath(path: String) = "${ID}Assets/$path"

        const val TEXPACKS_PATH = "texPacks"
        const val DUMP_DIRNAME = "dump"
        var currentDump: AtomicReference<String> = AtomicReference("")

        fun getTexPackDir(): FileHandle {
            return Gdx.files.local(TEXPACKS_PATH).apply {
                mkdirs()
            }
        }
    }

    private lateinit var strings: UIStrings
    private var dumpInProgress = false
    private var button: MenuCancelButton? = null
    private var uiElementsRender: ArrayList<IUIElement>? = null
    private var uiElementsUpdate: ArrayList<IUIElement>? = null

    private fun hideCancelButton(panel: ModPanel) {
        val clsModMenuButton = Class.forName("com.evacipated.cardcrawl.modthespire.patches.modsscreen.ModMenuButton")
        val modsScreen = clsModMenuButton.getDeclaredField("modsScreen").run {
            isAccessible = true
            get(null)
        }
        button = modsScreen.javaClass.getDeclaredField("button").run {
            isAccessible = true
            get(modsScreen) as? MenuCancelButton
        }
        button?.hideInstantly()

        ModPanel::class.java.getDeclaredField("uiElementsRender").run {
            isAccessible = true
            uiElementsRender = get(panel) as? ArrayList<IUIElement> /* = java.util.ArrayList<basemod.IUIElement> */
            set(panel, arrayListOf<IUIElement>())
        }
        ModPanel::class.java.getDeclaredField("uiElementsUpdate").run {
            isAccessible = true
            uiElementsUpdate = get(panel) as? ArrayList<IUIElement> /* = java.util.ArrayList<basemod.IUIElement> */
            set(panel, arrayListOf<IUIElement>())
        }
    }

    private fun showCancelButton(panel: ModPanel) {
        if (uiElementsRender != null) {
            ModPanel::class.java.getDeclaredField("uiElementsRender").run {
                isAccessible = true
                set(panel, uiElementsRender)
            }
        }
        if (uiElementsUpdate != null) {
            ModPanel::class.java.getDeclaredField("uiElementsUpdate").run {
                isAccessible = true
                set(panel, uiElementsUpdate)
            }
        }

        button?.let { button ->
            val label = MenuCancelButton::class.java.getDeclaredField("buttonText").run {
                isAccessible = true
                get(button) as? String ?: "NOT SET"
            }
            button.show(label)
        }
    }

    override fun receivePostInitialize() {
        strings = CardCrawlGame.languagePack.getUIString(makeID("Config"))

        val settingsPanel = ModPanel()

        val packList = TexPackToggleList(TextureReplacer.packs, 370f, 650f, 1175f, 400f, settingsPanel)
        settingsPanel.addUIElement(packList)

        var x1 = 360f
        var w1 = 0f

        val refreshButton = ModLabeledButton(strings.TEXT[4], x1, 700f, settingsPanel) {
            TextureReplacer.refresh()
            packList.load(TextureReplacer.packs)
        }
        settingsPanel.addUIElement(refreshButton)

        if (Desktop.isDesktopSupported()) {
            w1 = ReflectionHacks.getPrivate(refreshButton, ModLabeledButton::class.java, "w")
            x1 += w1 / Settings.scale + 20f
            val openDirButton = ModLabeledButton(strings.TEXT[3], x1, 700f, settingsPanel) {
                try {
                    Desktop.getDesktop().open(getTexPackDir().file())
                } catch (_: IOException) {}
            }
            settingsPanel.addUIElement(openDirButton)

            w1 = ReflectionHacks.getPrivate(openDirButton, ModLabeledButton::class.java, "w")
        }

        x1 += w1 / Settings.scale + 20f
        val dumpButton = object : ModLabeledButton(strings.TEXT[0], x1, 700f, settingsPanel, {
            dumpInProgress = true
            it.parent
            hideCancelButton(it.parent)
            it.label = strings.TEXT[1]
            TextureDumper().dumpAllTextures() {
                it.label = strings.TEXT[0]
                currentDump.set("")
                showCancelButton(it.parent)
                dumpInProgress = false
            }
        }) {
            override fun update() {
                super.update()
                if (ReflectionHacks.getPrivate<Hitbox>(this, ModLabeledButton::class.java, "hb").hovered) {
                    HeaderlessTip.renderHeaderlessTip(
                        InputHelper.mX + 60.scale(),
                        InputHelper.mY - 50.scale(),
                        strings.TEXT[5]
                    )
                }
            }
        }
        settingsPanel.addUIElement(dumpButton)

        BaseMod.registerModBadge(
            ImageMaster.loadImage(assetPath("images/modBadge.png")),
            NAME,
            "kiooeht",
            "TODO",
            settingsPanel
        )
    }

    private fun makeLocPath(language: Settings.GameLanguage, filename: String): String {
        val langPath = when (language) {
            else -> "eng"
        }
        return assetPath("localization/$langPath/$filename.json")
    }

    private fun loadLocFile(language: Settings.GameLanguage, stringType: Class<*>) {
        BaseMod.loadCustomStringsFile(stringType, makeLocPath(language, stringType.simpleName))
    }

    private fun loadLocFiles(language: Settings.GameLanguage) {
        loadLocFile(language, UIStrings::class.java)
    }

    override fun receiveEditStrings() {
        loadLocFiles(Settings.GameLanguage.ENG)
        if (Settings.language != Settings.GameLanguage.ENG) {
            loadLocFiles(Settings.language)
        }
    }

    override fun receivePostRender(sb: SpriteBatch) {
        if (dumpInProgress) {
            FontHelper.bannerNameFont.data.setScale(1f)
            FontHelper.renderFontCentered(
                sb,
                FontHelper.bannerNameFont,
                strings.TEXT[2],
                Settings.WIDTH / 2f,
                Settings.HEIGHT / 2f
            )

            var name = currentDump.get()
            if (name.length > 55) {
                name = name.substring(0, 55) + "..."
            }
            FontHelper.bannerNameFont.data.setScale(0.5f)
            FontHelper.renderFont(
                sb,
                FontHelper.bannerNameFont,
                name,
                Settings.WIDTH / 2f - FontHelper.layout.width / 2f,
                Settings.HEIGHT / 2f - FontHelper.layout.height / 2f - 10 * Settings.scale,
                Color.WHITE
            )
        }
    }
}
