package com.evacipated.cardcrawl.mod.texturereplacer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.glutils.FileTextureData
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.ScreenUtils
import com.evacipated.cardcrawl.mod.texturereplacer.patches.TextureAtlasImageReplace
import com.evacipated.cardcrawl.mod.texturereplacer.patches.TextureAtlasLoad
import com.evacipated.cardcrawl.modthespire.Loader
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.streams.asSequence

class TextureDumper {
    companion object {
        private val all = FileSystems.getDefault().getPathMatcher("glob:*.{png,jpg,atlas}")
        private val img = FileSystems.getDefault().getPathMatcher("glob:*.{png,jpg}")
        private val atlas = FileSystems.getDefault().getPathMatcher("glob:*.atlas")
    }

    private val allTextures = mutableSetOf<Path>()
    private val atlasTextureBlackList = mutableSetOf<Path>()

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    fun dumpAllTextures(finished: () -> Unit) {
        Thread {
            //println("Dumping all textures to file...")
            val fs = FileSystems.newFileSystem(Paths.get(Loader.STS_JAR), null)
            fs.rootDirectories.forEach { root ->
                val (atlases, imgs) = Files.walk(root)
                    .asSequence()
                    .filterNotNull()
                    .filter(Files::isRegularFile)
                    .filter(all::matches)
                    .partition(atlas::matches)

                atlases.forEach(::dumpAtlas)
                imgs.forEach(::dumpTexture)
            }

            Gdx.app.postRunnable { finished() }
        }.start()
    }

    private fun dumpTexture(path: Path) {
        //print("${path}...")
        TextureReplacerMod.currentDump.set(path.toString())
        val outPath = Paths.get(TextureReplacerMod.TEXPACKS_PATH, TextureReplacerMod.DUMP_DIRNAME, path.toString())
        val outFile = Gdx.files.local(outPath.toString())
        val output = outFile.write(false)
        val input = Files.newInputStream(path)
        input.copyTo(output)
        output.close()
        input.close()
        //println("Dumped")
    }

    private fun dumpAtlas(path: Path) {
        //println("${path}...")
        TextureReplacerMod.currentDump.set(path.toString())
        val outPath = Paths.get(TextureReplacerMod.TEXPACKS_PATH, TextureReplacerMod.DUMP_DIRNAME, path.toString())
        Gdx.files.local(outPath.toString()).mkdirs()

        val atlas = glContext {
            // disable texture replacing so dump uses default textures
            TextureAtlasImageReplace.disable = true
            TextureAtlasLoad.ReplaceRegions.disable = true
            TextureAtlas(path.toString().substring(1))
                .also {
                    TextureAtlasImageReplace.disable = false
                    TextureAtlasLoad.ReplaceRegions.disable = false
                }
        }
        // blacklist all the atlas textures,
        // so they don't get dumped by the texture dumper
        // Note: blacklist now does nothing, all textures are dumped
        atlas.textures.forEach {
            val data = it.textureData as? FileTextureData
            if (data != null) {
                atlasTextureBlackList.add(path.fileSystem.getPath(path.parent.toString(), data.fileHandle.name()))
            }
        }
        // dump all regions
        val png = PixmapIO.PNG()
        atlas.regions.forEach { dumpAtlasRegion(path, it, outPath, png) }
        glContext { atlas.dispose() }
        //println("  Dumped")
    }

    private fun dumpAtlasRegion(atlasPath: Path, region: TextureAtlas.AtlasRegion, outPath: Path, png: PixmapIO.PNG) {
        //println("  ${region.name}")
        TextureReplacerMod.currentDump.set(atlasPath.resolve(region.name).toString())
        glContext {
            val fbo = FrameBuffer(Pixmap.Format.RGBA8888, region.originalWidth, region.originalHeight, false)
            fbo.begin()
            try {
                Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
                val sb = SpriteBatch()
                val matrix = Matrix4()
                matrix.setToOrtho2D(0f, 0f, region.originalWidth.toFloat(), region.originalHeight.toFloat())
                sb.projectionMatrix = matrix

                sb.begin()
                try {
                    if (region.rotate) {
                        sb.draw(
                            region,
                            region.offsetX,
                            region.offsetY,
                            0f, 0f,
                            region.rotatedPackedWidth,
                            region.rotatedPackedHeight,
                            1f, 1f,
                            0f,
                            true
                        )
                    } else {
                        sb.draw(
                            region,
                            region.offsetX,
                            region.offsetY,
                            region.packedWidth.toFloat(),
                            region.packedHeight.toFloat()
                        )
                    }
                } finally {
                    sb.end()
                    sb.dispose()
                }

                val pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, region.originalWidth, region.originalHeight)
                try {
                    val filename = region.name + if (region.index >= 0) {
                        region.index.toString()
                    } else {
                        ""
                    } + ".png"
                    val file = Gdx.files.local(outPath.resolve(filename).toString())
                    png.write(file, pixmap)
                } finally {
                    pixmap.dispose()
                }
            } finally {
                fbo.end()
                fbo.dispose()
            }
        }
    }

    private fun <T : Any> glContext(block: () -> T): T {
        lateinit var ret: T
        Gdx.app.postRunnable {
            ret = block()
            lock.withLock {
                condition.signal()
            }
        }
        lock.withLock {
            condition.await()
        }
        return ret
    }
}
