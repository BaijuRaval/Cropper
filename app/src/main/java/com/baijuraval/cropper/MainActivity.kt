package com.baijuraval.cropper

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    private val selected = mutableListOf<Uri>()
    private lateinit var list: LinearLayout
    private lateinit var count: TextView
    private val pickImages = 101
    private var openCvReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openCvReady = try {
            OpenCVLoader.initLocal()
        } catch (_: Throwable) {
            false
        }
        window.statusBarColor = Color.rgb(247, 248, 250)
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(16))
            setBackgroundColor(Color.rgb(247, 248, 250))
        }
        root.addView(TextView(this).apply {
            text = "Cropper"
            textSize = 30f
            setTextColor(Color.rgb(24, 34, 48))
            paint.isFakeBoldText = true
        })
        root.addView(TextView(this).apply {
            text = "Auto-crop documents and stack them vertically on A4."
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(5), 0, dp(18))
        })
        root.addView(Button(this).apply {
            text = "＋  Select multiple images"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                startActivityForResult(intent, pickImages)
            }
        }, matchWrap())

        count = TextView(this).apply {
            text = "No images selected"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(14), 0, dp(8))
        }
        root.addView(count)
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply {
            text = "Crop & Print A4  →"
            textSize = 16f
            setOnClickListener {
                if (selected.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Select at least one image first", Toast.LENGTH_SHORT).show()
                } else {
                    printA4()
                }
            }
        }, matchWrap().apply { topMargin = dp(12) })
        setContentView(root)
        refresh()
    }

    @Deprecated("Uses the document picker result API for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickImages || resultCode != Activity.RESULT_OK || data == null) return
        val found = mutableListOf<Uri>()
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) found.add(clip.getItemAt(i).uri)
        } ?: data.data?.let { found.add(it) }
        for (uri in found) {
            if (uri !in selected) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {
                }
                selected.add(uri)
            }
        }
        refresh()
    }

    private fun refresh() {
        list.removeAllViews()
        count.text = if (selected.isEmpty()) {
            "No images selected"
        } else {
            "${selected.size} image(s) • auto-crop, then vertical A4 layout"
        }
        selected.forEachIndexed { index, uri ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setBackgroundColor(Color.WHITE)
            }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                try {
                    setImageURI(uri)
                } catch (_: Exception) {
                }
            }
            row.addView(image, LinearLayout.LayoutParams(dp(76), dp(56)))
            row.addView(TextView(this).apply {
                text = "Document ${index + 1}"
                textSize = 15f
                setTextColor(Color.rgb(35, 45, 60))
                setPadding(dp(12), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Button(this).apply {
                text = "Remove"
                setOnClickListener {
                    selected.remove(uri)
                    refresh()
                }
            })
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private fun printA4() {
        val processed = mutableListOf<Bitmap>()
        try {
            selected.forEach { uri ->
                val source = loadBitmap(uri) ?: return@forEach
                val cropped = try {
                    autoCropDocument(source)
                } catch (_: Exception) {
                    source
                }
                if (cropped !== source) source.recycle()
                processed.add(cropped)
            }
            if (processed.isEmpty()) {
                Toast.makeText(this, "Could not open the selected images", Toast.LENGTH_LONG).show()
                return
            }
            if (!openCvReady) {
                Toast.makeText(this, "Auto-crop is unavailable on this device; images will still be arranged on A4.", Toast.LENGTH_LONG).show()
            }

            val attrs = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(PrintAttributes.Resolution("cropper", "Cropper", 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()
            val document = PrintedPdfDocument(this, attrs)
            val pageWidth = 595f
            val pageHeight = 842f
            val margin = 24f
            val gap = 14f
            val contentWidth = pageWidth - margin * 2f
            val contentHeight = pageHeight - margin * 2f
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(215, 219, 225)
                style = Paint.Style.STROKE
                strokeWidth = 0.65f
            }
            val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

            try {
                var pageNumber = 1
                var page = document.startPage(pageNumber)
                var canvas = page.canvas
                preparePageCanvas(canvas, pageWidth, pageHeight)
                var y = margin

                processed.forEach { bitmap ->
                    val scale = min(
                        contentWidth / bitmap.width.toFloat(),
                        contentHeight / bitmap.height.toFloat()
                    )
                    val drawWidth = bitmap.width * scale
                    val drawHeight = bitmap.height * scale

                    if (y > margin && y + drawHeight > pageHeight - margin) {
                        canvas.restore()
                        document.finishPage(page)
                        pageNumber++
                        page = document.startPage(pageNumber)
                        canvas = page.canvas
                        preparePageCanvas(canvas, pageWidth, pageHeight)
                        y = margin
                    }

                    val left = (pageWidth - drawWidth) / 2f
                    val dest = RectF(left, y, left + drawWidth, y + drawHeight)
                    canvas.drawBitmap(bitmap, null, dest, imagePaint)
                    canvas.drawRect(dest, border)
                    y = dest.bottom + gap
                }

                canvas.restore()
                document.finishPage(page)
                val output = File(cacheDir, "cropper-a4.pdf")
                FileOutputStream(output).use { document.writeTo(it) }
                val printManager = getSystemService(PRINT_SERVICE) as PrintManager
                printManager.print("Cropper - vertical A4 documents", PdfPrintAdapter(output), attrs)
            } finally {
                document.close()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not prepare print sheet: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        } finally {
            processed.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    private fun preparePageCanvas(canvas: Canvas, pageWidth: Float, pageHeight: Float) {
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.scale(canvas.width / pageWidth, canvas.height / pageHeight)
    }

    /**
     * Finds the largest clear four-corner document contour and perspective-corrects it.
     * If a clear boundary isn't detected, the original image is preserved rather than
     * accidentally cutting off document content.
     */
    private fun autoCropDocument(bitmap: Bitmap): Bitmap {
        if (!openCvReady) return bitmap
        val corners = detectDocumentCorners(bitmap) ?: return bitmap
        val ordered = orderCorners(corners)

        val widthTop = distance(ordered[0], ordered[1])
        val widthBottom = distance(ordered[3], ordered[2])
        val heightRight = distance(ordered[1], ordered[2])
        val heightLeft = distance(ordered[0], ordered[3])
        val outputWidth = max(widthTop, widthBottom).roundToInt().coerceIn(2, 3500)
        val outputHeight = max(heightRight, heightLeft).roundToInt().coerceIn(2, 3500)
        if (outputWidth < 2 || outputHeight < 2) return bitmap

        val sourcePoints = MatOfPoint2f().apply { fromArray(*ordered) }
        val destinationPoints = MatOfPoint2f().apply {
            fromArray(
                Point(0.0, 0.0),
                Point((outputWidth - 1).toDouble(), 0.0),
                Point((outputWidth - 1).toDouble(), (outputHeight - 1).toDouble()),
                Point(0.0, (outputHeight - 1).toDouble())
            )
        }
        val sourceMat = Mat()
        val corrected = Mat()
        var transform: Mat? = null
        return try {
            Utils.bitmapToMat(bitmap, sourceMat)
            transform = Imgproc.getPerspectiveTransform(sourcePoints, destinationPoints)
            Imgproc.warpPerspective(
                sourceMat,
                corrected,
                transform,
                Size(outputWidth.toDouble(), outputHeight.toDouble()),
                Imgproc.INTER_CUBIC
            )
            Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888).also {
                Utils.matToBitmap(corrected, it)
            }
        } catch (_: Exception) {
            bitmap
        } finally {
            sourcePoints.release()
            destinationPoints.release()
            sourceMat.release()
            corrected.release()
            transform?.release()
        }
    }

    private fun detectDocumentCorners(bitmap: Bitmap): Array<Point>? {
        val ratio = min(1.0, 1200.0 / max(bitmap.width, bitmap.height).toDouble())
        val smallWidth = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val smallHeight = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        val small = if (smallWidth != bitmap.width || smallHeight != bitmap.height) {
            Bitmap.createScaledBitmap(bitmap, smallWidth, smallHeight, true)
        } else {
            bitmap
        }

        val rgba = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        try {
            Utils.bitmapToMat(small, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 45.0, 140.0)
            Imgproc.dilate(edges, edges, kernel)
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            val imageArea = small.width.toDouble() * small.height.toDouble()
            val scaleX = bitmap.width.toDouble() / small.width
            val scaleY = bitmap.height.toDouble() / small.height
            for (contour in contours.sortedByDescending { Imgproc.contourArea(it) }.take(40)) {
                val areaRatio = Imgproc.contourArea(contour) / imageArea
                if (areaRatio < 0.08 || areaRatio > 0.96) continue

                val curve = MatOfPoint2f()
                val approx = MatOfPoint2f()
                var polygon: MatOfPoint? = null
                try {
                    curve.fromArray(*contour.toArray())
                    val perimeter = Imgproc.arcLength(curve, true)
                    Imgproc.approxPolyDP(curve, approx, 0.02 * perimeter, true)
                    val points = approx.toArray()
                    if (points.size == 4) {
                        polygon = MatOfPoint(*points)
                        if (Imgproc.isContourConvex(polygon)) {
                            return points.map { Point(it.x * scaleX, it.y * scaleY) }.toTypedArray()
                        }
                    }
                } finally {
                    curve.release()
                    approx.release()
                    polygon?.release()
                }
            }
            return null
        } finally {
            contours.forEach { it.release() }
            kernel.release()
            rgba.release()
            gray.release()
            blurred.release()
            edges.release()
            hierarchy.release()
            if (small !== bitmap) small.recycle()
        }
    }

    private fun orderCorners(points: Array<Point>): Array<Point> {
        val topLeft = points.minByOrNull { it.x + it.y }!!
        val bottomRight = points.maxByOrNull { it.x + it.y }!!
        val topRight = points.minByOrNull { it.y - it.x }!!
        val bottomLeft = points.maxByOrNull { it.y - it.x }!!
        return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun distance(a: Point, b: Point): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / sampleSize > 2800) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private inner class PdfPrintAdapter(private val file: File) : android.print.PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: android.os.CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = android.print.PrintDocumentInfo.Builder(file.name)
                .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(android.print.PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out android.print.PageRange>?,
            destination: android.os.ParcelFileDescriptor,
            cancellationSignal: android.os.CancellationSignal?,
            callback: WriteResultCallback
        ) {
            try {
                java.io.FileInputStream(file).use { input ->
                    java.io.FileOutputStream(destination.fileDescriptor).use { output -> input.copyTo(output) }
                }
                callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }
}
