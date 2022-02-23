package com.evacipated.cardcrawl.mod.texturereplacer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.TextureData
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.glutils.FileTextureData
import com.evacipated.cardcrawl.mod.texturereplacer.extensions.asAtlasRegion
import com.evacipated.cardcrawl.mod.texturereplacer.patches.TextureAtlasLoad
import com.evacipated.cardcrawl.modthespire.Loader
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.math.min
import kotlin.streams.asSequence

object TextureReplacer {
    private val configFilename = SpireConfig.makeFilePath(TextureReplacerMod.ID, "pack_order", "json")

    private val managedTextures: MutableMap<String, Texture> = mutableMapOf()
    private val managedRegions: MutableMap<String, TextureAtlas.AtlasRegion> = mutableMapOf()
    private val originalRegions: MutableMap<String, TextureAtlas.AtlasRegion> = mutableMapOf()

    val packs = mutableListOf<TexPack>()

    fun initialize() {
        val tmpPacks = findPacks()

        packs.clear()
        val loadPacks = loadConfig()
        loadPacks.forEach { load ->
            val p = tmpPacks.firstOrNull { it.id == load.id }
            if (p != null) {
                tmpPacks.remove(p)
                p.enabled = load.enabled
                packs.add(p)
            }
        }
        packs.addAll(tmpPacks)
        saveConfig()
    }

    private fun findPacks(): MutableList<TexPack> {
        val tmpPacks = mutableListOf<TexPack>()

        // Local directory
        val texDir = TextureReplacerMod.getTexPackDir()
        texDir.list { _, name ->
            name != TextureReplacerMod.DUMP_DIRNAME
        }.forEach { dir ->
            val pack = TexPack(dir.name())
            Files.walk(Paths.get(dir.path()), FileVisitOption.FOLLOW_LINKS)
                .asSequence()
                .filter { it.toFile().isFile }
                .forEach {
                    val rel = Paths.get(dir.path()).relativize(it)
                    pack[rel] = it
                }
            tmpPacks.add(pack)
        }

        // Workshop
        val infos = Loader.getWorkshopInfos()
        infos.forEach { info ->
            if (info.hasTag("texture pack")) {
                val pack = TexPack(info.id, info.title)
                Files.walk(Paths.get(info.installPath), FileVisitOption.FOLLOW_LINKS)
                    .asSequence()
                    .filter { it.toFile().isFile }
                    .forEach {
                        val rel = Paths.get(info.installPath).relativize(it)
                        pack[rel] = it
                    }
                tmpPacks.add(pack)
            }
        }

        return tmpPacks
    }

    private fun loadConfig(): List<TexPack> {
        try {
            val gson = GsonBuilder()
                .create()
            val file = Gdx.files.absolute(configFilename)
            val type = object : TypeToken<List<TexPack>>() {}.type
            return gson.fromJson(file.reader(), type)
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    private fun saveConfig() {
        val gson = GsonBuilder()
            .setPrettyPrinting()
            .create()
        val file = Gdx.files.absolute(configFilename)
        val str = gson.toJson(packs)
        file.writeString(str, false)
    }

    internal fun swap(i: Int, j: Int) {
        Collections.swap(packs, i, j)
        val k = min(i, j)
        packs[k].reload()
        saveConfig()
    }

    internal fun addTexture(texture: Texture, data: TextureData) {
        if (data is FileTextureData) {
            val path = Paths.get(data.fileHandle.path()).toString()
            managedTextures[path] = texture
        }
    }

    internal fun disposeTexture(texture: Texture) {
        with (managedTextures.iterator()) {
            forEach {
                if (it.value == texture) {
                    remove()
                }
            }
        }
    }

    internal fun addOriginalRegion(region: TextureAtlas.AtlasRegion, data: TextureAtlas.TextureAtlasData) {
        val filename = TextureAtlasLoad.DataField.filename.get(data)
        val p = Paths.get(filename, "${region.name}.png")
        originalRegions[p.toString()] = TextureAtlas.AtlasRegion(region)
    }

    internal fun addRegion(region: TextureAtlas.AtlasRegion, data: TextureAtlas.TextureAtlasData) {
        val filename = TextureAtlasLoad.DataField.filename.get(data)
        val p = Paths.get(filename, "${region.name}.png")
        managedRegions[p.toString()] = region
    }

    internal fun disposeAtlas(atlas: TextureAtlas) {
        with (managedRegions.iterator()) {
            forEach {
                if (atlas.regions.contains(it.value)) {
                    remove()
                }
            }
        }
    }

    internal fun reloadTexture(path: String) {
        managedTextures[path]?.run {
            val data = TextureData.Factory.loadFromFile(getImgFile(path), false)
            load(data)
        }
    }

    internal fun reloadRegion(path: String) {
        managedRegions[path]?.run {
            val newRegion = getAtlasRegion(Paths.get(path)) ?: originalRegions[path]
            if (newRegion != null) {
                setRegion(newRegion)
                index = newRegion.index
                // not needed
                //name = newRegion.name
                offsetX = newRegion.offsetX
                offsetY = newRegion.offsetY
                packedWidth = newRegion.packedWidth
                packedHeight = newRegion.packedHeight
                originalWidth = newRegion.originalWidth
                originalHeight = newRegion.originalHeight
                rotate = newRegion.rotate
                splits = newRegion.splits
            }
        }
    }

    fun getImgFile(imgUrl: String): FileHandle {
        val p = Paths.get(imgUrl)
        packs.filter { it.enabled }
            .forEach { pack ->
                pack[p]?.let {
                    return Gdx.files.local(it.toString())
                }
            }
        return Gdx.files.internal(imgUrl)
    }

    fun getAtlasRegion(data: TextureAtlas.TextureAtlasData, name: String): TextureAtlas.AtlasRegion? {
        val filename = TextureAtlasLoad.DataField.filename.get(data)
        val p = Paths.get(filename, "${name}.png")
        return getAtlasRegion(p)
    }

    private fun getAtlasRegion(p: Path): TextureAtlas.AtlasRegion? {
        packs.filter { it.enabled }
            .forEach { pack ->
                pack[p]?.let {
                    val tex = Texture(Gdx.files.local(it.toString()))
                    val ret = tex.asAtlasRegion()
                    return ret
                }
            }
        return null
    }

    class TexPack(val id: String, val name: String = id) {
        var enabled = false
            set(value) {
                if (value != field) {
                    field = value
                    reload()
                    saveConfig()
                }
            }
        @Transient
        private val replacements = mutableMapOf<Path, Path>()

        fun reload() {
            replacements.forEach { (path, _) ->
                val p = path.toString()
                if (p.contains(".atlas")) {
                    reloadRegion(p)
                } else {
                    reloadTexture(p)
                }
            }
        }

        operator fun get(index: Path): Path? =
            replacements[index]

        operator fun set(index: Path, value: Path) {
            replacements[index] = value
        }
    }
}
