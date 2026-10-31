package android.graphics

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.InputStream
import javax.imageio.ImageIO

class Rect

/** Tiruan android.graphics.Bitmap di atas BufferedImage (cukup untuk memuat dan skala texture). */
class Bitmap internal constructor(internal val img: BufferedImage) {
    enum class Config { ARGB_8888 }

    val width: Int get() = img.width
    val height: Int get() = img.height

    /** Piksel format ARGB (sama dengan Bitmap.getPixels di Android). */
    fun getPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) {
        for (row in 0 until h) {
            img.getRGB(x, y + row, w, 1, pixels, offset + row * stride, w)
        }
    }

    fun recycle() {}

    companion object {
        /** Skala dengan interpolasi; turun skala dibagi dua bertahap supaya hasilnya halus (mirip box filter). */
        fun createScaledBitmap(src: Bitmap, dstW: Int, dstH: Int, filter: Boolean): Bitmap {
            if (src.width == dstW && src.height == dstH) return src
            var cur = src.img
            while (cur.width / 2 >= dstW && cur.height / 2 >= dstH) {
                cur = resize(cur, cur.width / 2, cur.height / 2)
            }
            if (cur.width != dstW || cur.height != dstH) cur = resize(cur, dstW, dstH)
            return Bitmap(cur)
        }

        private fun resize(src: BufferedImage, w: Int, h: Int): BufferedImage {
            val dst = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            val g = dst.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(src, 0, 0, w, h, null)
            g.dispose()
            return dst
        }
    }
}

object BitmapFactory {
    class Options {
        @JvmField var inPreferredConfig: Bitmap.Config? = null
    }

    fun decodeStream(input: InputStream, outPadding: Rect?, opts: Options?): Bitmap? {
        val img = ImageIO.read(input) ?: return null
        return Bitmap(img)
    }
}
