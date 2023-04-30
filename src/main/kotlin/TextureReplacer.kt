@file:JvmName("TextureReplacer")

package com.evacipated.cardcrawl.mod.texturereplacer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.TextureData
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.glutils.FileTextureData
import com.badlogic.gdx.graphics.glutils.PixmapTextureData
import com.evacipated.cardcrawl.mod.texturereplacer.extensions.asAtlasRegion
import com.evacipated.cardcrawl.mod.texturereplacer.patches.TextureAtlasLoad
import com.evacipated.cardcrawl.modthespire.Loader
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.sun.nio.zipfs.ZipPath
import java.io.File
import java.io.InputStream
import java.net.URI
import java.nio.file.*
import java.util.*
import kotlin.math.min
import kotlin.streams.asSequence
import kotlin.streams.toList

object TextureReplacer {
    private val configFilename = SpireConfig.makeFilePath(TextureReplacerMod.ID, "pack_order", "json")

    private val managedTextures: MutableMap<String, Texture> = mutableMapOf()
    private val managedRegions: MutableMap<String, TextureAtlas.AtlasRegion> = mutableMapOf()
    private val originalRegions: MutableMap<String, TextureAtlas.AtlasRegion> = mutableMapOf()

    internal val packs = mutableListOf<TexPack>()

    // public API
    @JvmStatic
    fun isPackEnabled(id: String): Boolean =
        packs.firstOrNull { it.id == id }?.enabled ?: false

    @JvmStatic
    fun isPackEnabled(workshopID: Long): Boolean =
        isPackEnabled(workshopID.toString(16))

    @JvmStatic
    fun isReplaced(texture: Texture): Boolean {
        val data = texture.textureData
        if (data is ZipFileTextureData) {
            return true
        }
        if (data is FileTextureData) {
            return data.fileHandle.type() != com.badlogic.gdx.Files.FileType.Internal &&
                    data.fileHandle.type() != com.badlogic.gdx.Files.FileType.Classpath
        }
        return false
    }

    @JvmStatic
    fun isReplaced(region: TextureAtlas.AtlasRegion): Boolean =
        isReplaced(region.texture)
    // public API end

    internal fun initialize() {
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

    internal fun refresh() {
        val tmpPacks = findPacks()

        // Remove packs that don't exist anymore
        packs.removeIf {
            if (tmpPacks.none { pack -> pack.id == it.id }) {
                it.enabled = false
                true
            } else {
                false
            }
        }

        // Add new packs
        tmpPacks.removeIf {
            packs.any { pack -> pack.id == it.id }
        }
        packs.addAll(tmpPacks)

        saveConfig()
    }

    private fun findPacks(): MutableList<TexPack> {
        val tmpPacks = mutableListOf<TexPack>()

        val initPack = { pack: TexPack, filename: String ->
            val path = Paths.get(filename)
            var newPath = if (path.toFile().extension.toLowerCase() == "zip") {
                val nameWithoutExtension = path.toFile().nameWithoutExtension
                val uri = URI.create("jar:${path.toUri()}")
                val fs = try {
                    FileSystems.newFileSystem(uri, mapOf<String, Any>("encoding" to "UTF-8"))
                } catch (e: FileSystemAlreadyExistsException) {
                    FileSystems.getFileSystem(uri)
                }
                val roots = Files.list(fs.getPath("/")).toList()
                var ret = fs.getPath("/")
                if (roots.size == 1 && roots[0].nameCount > 0) {
                    val root = roots[0].getName(0).toString().replace("/", "").replace(File.separator, "")
                    if (root == nameWithoutExtension) {
                        ret = roots[0]
                    }
                }
                ret.toString().trimEnd().let {
                    if (it.endsWith("/")) {
                        it.substring(0, it.length-1)
                    } else {
                        it
                    }
                }.let { fs.getPath(it) }
            } else {
                path
            }
            if (newPath.nameCount == 0) {
                newPath = newPath.fileSystem.getPath("/");
            }
            Files.walk(newPath, FileVisitOption.FOLLOW_LINKS)
                .asSequence()
                .filter {
                    try {
                        it.toFile().isFile
                    } catch (_: UnsupportedOperationException) {
                        !it.toString().endsWith(it.fileSystem.separator) && it != newPath
                    }
                }
                .forEach {
                    var rel = newPath.relativize(it)
                    rel = Paths.get(rel.toString())
                    pack[rel] = it
                }
            tmpPacks.add(pack)
        }

        // Local directory
        val texDir = TextureReplacerMod.getTexPackDir()
        texDir.list { _, name ->
            name != TextureReplacerMod.DUMP_DIRNAME
        }.filter { f -> f.isDirectory || f.name().endsWith(".zip", ignoreCase = true) }
        .forEach { dir ->
            val pack = TexPack(dir.name())
            initPack(pack, dir.path())
        }

        // Workshop
        val infos = Loader.getWorkshopInfos()
        infos.forEach { info ->
            if (info.hasTag("texture pack")) {
                val pack = TexPack(info.id, info.title)
                initPack(pack, info.installPath)
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

    internal fun addOriginalTexture(texture: Texture, imgUrl: String) {
        val path = Paths.get(imgUrl).toString()
        managedTextures[path] = texture
    }

    internal fun addTexture(texture: Texture, data: TextureData) {
        if (data is FileTextureData) {
            val path = Paths.get(data.fileHandle.path()).toString()
            managedTextures[path] = texture
        } else if (data is ZipFileTextureData) {
            val path = Paths.get(data.path).toString()
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

    internal fun getImgFile(imgUrl: String): FileHandle {
        val p = Paths.get(imgUrl)
        packs.filter { it.enabled }
            .forEach { pack ->
                pack[p]?.let {
                    return if (it is ZipPath) {
                        ZipFileHandle(it)
                    } else {
                        Gdx.files.absolute(it.toString())
                    }
                }
            }
        return Gdx.files.internal(imgUrl)
    }

    internal fun getAtlasRegion(data: TextureAtlas.TextureAtlasData, name: String): TextureAtlas.AtlasRegion? {
        val filename = TextureAtlasLoad.DataField.filename.get(data)
        val p = Paths.get(filename, "${name}.png")
        return getAtlasRegion(p)
    }

    private fun getAtlasRegion(p: Path): TextureAtlas.AtlasRegion? {
        packs.filter { it.enabled }
            .forEach { pack ->
                pack[p]?.let {
                    return if (it is ZipPath) {
                        val bytes = Files.readAllBytes(it)
                        val tex = Texture(ZipFileTextureData(p, bytes))
                        tex.asAtlasRegion()
                    } else {
                        val tex = Texture(Gdx.files.absolute(it.toString()))
                        tex.asAtlasRegion()
                    }
                }
            }
        return null
    }

    internal fun getFileHandle(handle: FileHandle): FileHandle {
        val p = handle.file().toPath()
        packs.filter { it.enabled }
            .forEach { pack ->
                pack[p]?.let {
                    return if (it is ZipPath) {
                        ZipFileHandle(it)
                    } else {
                        Gdx.files.absolute(it.toString())
                    }
                }
            }
        return handle
    }

    internal class ZipFileHandle(private val path: ZipPath) : FileHandle() {
        init {
            type = com.badlogic.gdx.Files.FileType.Classpath
        }

        override fun path(): String {
            return path.toString()
        }

        override fun name(): String {
            return path.fileName.toString()
        }

        override fun extension(): String {
            val name = name()
            val dotIndex = name.lastIndexOf('.')
            if (dotIndex == -1) {
                return ""
            }
            return name.substring(dotIndex + 1)
        }

        override fun pathWithoutExtension(): String {
            val path = path()
            val dotIndex = path.lastIndexOf('.')
            if (dotIndex == -1) {
                return path
            }
            return path.substring(0, dotIndex)
        }

        override fun nameWithoutExtension(): String {
            val name = name()
            val dotIndex = name.lastIndexOf('.')
            if (dotIndex == -1) {
                return name
            }
            return name.substring(0, dotIndex)
        }

        override fun read(): InputStream {
            return Files.newInputStream(path)
        }

        override fun toString(): String {
            return path.toString()
        }
    }

    internal class ZipFileTextureData(path: Path, bytes: ByteArray) : PixmapTextureData(
        Pixmap(bytes, 0, bytes.size),
        null,
        false,
        false
    ) {
        val path = path.toString()
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
