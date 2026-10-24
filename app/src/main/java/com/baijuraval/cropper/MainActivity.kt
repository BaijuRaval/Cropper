package com.baijuraval.cropper

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    private val selected = mutableListOf<Uri>()
    private val manualCrops = mutableMapOf<Uri, RectF>()
    private lateinit var list: LinearLayout
    private lateinit var count: TextView
    private val pickImages = 101
    private var openCvReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openCvReady = try { OpenCVLoader.initLocal() } catch (_: Throwable) { false }
        window.statusBarColor = Color.rgb(247, 248, 250)
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        buildUi()
    }

    private fun buildUi() {
        val pageBg = Color.rgb(246, 247, 252)
        window.statusBarColor = pageBg
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setBackgroundColor(pageBg)
        }

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(39, 43, 99), Color.rgb(94, 96, 232))
            ).apply { cornerRadius = dp(26).toFloat() }
            elevation = dp(3).toFloat()
        }
        val heroTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heroTop.addView(TextView(this).apply {
            text = "✂"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            background = roundedBackground(Color.argb(42, 255, 255, 255), 18)
        }, LinearLayout.LayoutParams(dp(62), dp(62)))
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        titleBlock.addView(TextView(this).apply {
            text = "DOCUMENT STUDIO"
            textSize = 10f
            letterSpacing = 0.13f
            setTextColor(Color.rgb(218, 221, 255))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        titleBlock.addView(TextView(this).apply {
            text = "Cropper"
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(0, dp(1), 0, 0)
        })
        heroTop.addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f))
        hero.addView(heroTop)
        hero.addView(TextView(this).apply {
            text = "Turn your photos into a clean, single-page A4 PDF."
            textSize = 14f
            setTextColor(Color.rgb(239, 240, 255))
            setPadding(0, dp(13), 0, dp(13))
        })
        hero.addView(TextView(this).apply {
            text = "AUTO CROP   •   MANUAL ADJUST   •   A4 PDF"
            textSize = 10f
            letterSpacing = 0.04f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            background = roundedBackground(Color.argb(38, 255, 255, 255), 10)
            setPadding(dp(11), dp(7), dp(11), dp(7))
        })
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(18)
        })

        root.addView(actionButton("＋   Add documents", Color.rgb(91, 92, 226), Color.WHITE, 16, 54).apply {
            textSize = 16f
            elevation = dp(2).toFloat()
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                startActivityForResult(intent, pickImages)
            }
        }, matchWrap())

        root.addView(TextView(this).apply {
            text = "Select several photos together — JPG or PNG."
            textSize = 12f
            setTextColor(Color.rgb(119, 125, 145))
            setPadding(dp(3), dp(8), dp(3), dp(17))
        })

        val section = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(1), 0, 0, dp(10))
        }
        section.addView(TextView(this).apply {
            text = "Your documents"
            textSize = 19f
            setTextColor(Color.rgb(29, 35, 58))
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        count = TextView(this).apply {
            text = "0 selected"
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.rgb(91, 92, 226))
            background = roundedBackground(Color.WHITE, 12, Color.rgb(229, 230, 245))
            setPadding(dp(12), dp(7), dp(12), dp(7))
        }
        section.addView(count)
        root.addView(section)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(6))
        }
        scroll.addView(list, ScrollView.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(actionButton("Create A4 PDF   →", Color.rgb(91, 92, 226), Color.WHITE, 17, 56).apply {
            textSize = 16f
            elevation = dp(3).toFloat()
            setOnClickListener {
                if (selected.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Add at least one image first", Toast.LENGTH_SHORT).show()
                } else {
                    printA4()
                }
            }
        }, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(10)
        })
        root.addView(TextView(this).apply {
            text = "All selected documents · one A4 page"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(132, 137, 155))
            setPadding(0, dp(8), 0, 0)
        }, matchWrap())

        setContentView(root)
        refresh()
    }

    @Deprecated("Uses the document picker result API for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickImages || resultCode != Activity.RESULT_OK || data == null) return

        // ACTION_OPEN_DOCUMENT with EXTRA_ALLOW_MULTIPLE returns the selection in clipData.
        // Keep data.data as well for providers that return a single URI alongside clipData.
        val found = linkedSetOf<Uri>()
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) found.add(clip.getItemAt(i).uri)
        }
        data.data?.let { found.add(it) }

        for (uri in found) {
            if    private fun refresh() {
        list.removeAllViews()
        count.text = "${selected.size} selected"

        if (selected.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(28), dp(20), dp(28))
                background = roundedBackground(Color.WHITE, 22, Color.rgb(232, 233, 244))
                elevation = dp(1).toFloat()
            }
            empty.addView(TextView(this).apply {
                text = "▧"
                textSize = 43f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(112, 111, 235))
                background = roundedBackground(Color.rgb(243, 243, 255), 20)
                setPadding(dp(17), dp(8), dp(17), dp(8))
            }, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(14) })
            empty.addView(TextView(this).apply {
                text = "Nothing here yet"
                textSize = 17f
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                setTextColor(Color.rgb(36, 42, 65))
            })
            empty.addView(TextView(this).apply {
                text = "Tap Add documents and select multiple photos at once. You can adjust each crop before creating your A4 PDF."
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(121, 126, 146))
                setPadding(0, dp(7), 0, 0)
            })
            list.addView(empty, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            })
            return
        }

        selected.forEachIndexed { index, uri ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(8), dp(10))
                background = roundedBackground(Color.WHITE, 18, Color.rgb(233, 234, 245))
                elevation = dp(1).toFloat()
            }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                background = roundedBackground(Color.rgb(240, 241, 248), 12)
                try {
                    val bitmap = loadBitmap(uri)
                    if (bitmap != null) {
                        val crop = manualCrops[uri]
                        if (crop != null) {
                            val preview = cropBitmap(bitmap, crop)
                            if (preview !== bitmap) bitmap.recycle()
                            setImageBitmap(preview)
                        } else {
                            setImageBitmap(bitmap)
                        }
                    } else {
                        setImageURI(uri)
                    }
                } catch (_: Exception) {
                    setImageURI(uri)
                }
            }
            card.addView(image, LinearLayout.LayoutParams(dp(66), dp(70)))

            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(11), 0, dp(4), 0)
            }
            labels.addView(TextView(this).apply {
                text = "Document ${(index + 1).toString().padStart(2, '0')}"
                textSize = 14f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(Color.rgb(36, 42, 65))
                maxLines = 1
            })
            labels.addView(TextView(this).apply {
                text = if (manualCrops.containsKey(uri)) "Manual crop ready" else "Auto-crop enabled"
                textSize = 11f
                setTextColor(if (manualCrops.containsKey(uri)) Color.rgb(42, 145, 119) else Color.rgb(124, 129, 149))
                setPadding(0, dp(4), 0, 0)
                maxLines = 1
            })
            card.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))

            card.addView(actionButton("Crop", Color.rgb(242, 242, 255), Color.rgb(85, 85, 207), 11, 38).apply {
                textSize = 12f
                minWidth = dp(54)
                setPadding(dp(7), 0, dp(7), 0)
                elevation = 0f
                setOnClickListener { openCropEditor(uri) }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(2) })

            card.addView(actionButton("×", Color.rgb(255, 241, 243), Color.rgb(189, 64, 85), 11, 38).apply {
                textSize = 19f
                minWidth = dp(38)
                setPadding(dp(4), 0, dp(4), 0)
                elevation = 0f
                contentDescription = "Remove document"
                setOnClickListener {
                    selected.remove(uri)
                    manualCrops.remove(uri)
                    refresh()
                }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(5) })

            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(9)
            })
        }
    }

    private fun roundedBackground(fill: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }
    }

    private fun actionButton(
        label: String,
        fill: Int,
        foreground: Int,
        radiusDp: Int = 16,
        heightDp: Int = 52
    ): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(foreground)
            background = roundedBackground(fill, radiusDp)
            minimumHeight = dp(heightDp)
            minHeight = dp(heightDp)
            setPadding(dp(12), 0, dp(12), 0)
            stateListAnimator = null
        }
    }

nClickListener {
                    selected.remove(uri)
                    manualCrops.remove(uri)
                    refresh()
                }
            })
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private fun openCropEditor(uri: Uri) {
        val bitmap = loadBitmap(uri)
        if (bitmap == null) {
            Toast.makeText(this, "Could not open this image", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(TextView(this).apply {
            text = "Adjust crop"
            textSize = 21f
            setTextColor(Color.rgb(24, 34, 48))
            paint.isFakeBoldText = true
        })
        root.addView(TextView(this).apply {
            text = "Drag inside to move. Drag a corner to resize, or drag outside to draw a new crop."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(5), 0, dp(10))
        })
        val initial = manualCrops[uri] ?: RectF(0.03f, 0.03f, 0.97f, 0.97f)
        val cropView = CropOverlayView(this, bitmap, initial)
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.rgb(30, 34, 40)) }
        frame.addView(cropView, FrameLayout.LayoutParams(-1, -1))
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        actions.addView(Button(this).apply {
            text = "Cancel"
            setOnClickListener {
                dialog.dismiss()
                bitmap.recycle()
            }
        })
        actions.addView(Button(this).apply {
            text = "Auto"
            contentDescription = "Clear manual crop and use automatic detection"
            setOnClickListener {
                manualCrops.remove(uri)
                dialog.dismiss()
                bitmap.recycle()
                refresh()
            }
        })
        actions.addView(Button(this).apply {
            text = "Use crop"
            setOnClickListener {
                manualCrops[uri] = cropView.selectedCrop
                dialog.dismiss()
                bitmap.recycle()
                refresh()
            }
        })
        root.addView(actions, matchWrap())
        dialog.setContentView(root)
        dialog.setOnDismissListener {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.94f).toInt(),
            (resources.displayMetrics.heightPixels * 0.82f).toInt())
    }

    private fun printA4() {
        val processed = mutableListOf<Bitmap>()
        try {
            selected.forEach { uri ->
                val source = loadBitmap(uri) ?: return@forEach
                val manual = manualCrops[uri]
                val result = if (manual != null) {
                    cropBitmap(source, manual)
                } else {
                    try { autoCropDocument(source) } catch (_: Exception) { source }
                }
                if (result !== source) source.recycle()
                processed.add(result)
            }
            if (processed.isEmpty()) {
                Toast.makeText(this, "Could not open the selected images", Toast.LENGTH_LONG).show()
                return
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
            val gap = 8f
            val contentWidth = pageWidth - margin * 2f
            val contentHeight = pageHeight - margin * 2f
            val safeGap = if (processed.size > 1) min(gap, contentHeight / (processed.size - 1) * 0.20f) else 0f

            // Base layout uses full page width, then uniformly scales the whole stack
            // down just enough for every selected document to fit on this single A4 page.
            val totalNaturalHeight = processed.sumOf { bitmap ->
                contentWidth.toDouble() * bitmap.height.toDouble() / bitmap.width.toDouble()
            }.toFloat()
            val totalGap = safeGap * max(0, processed.size - 1)
            val availableForImages = max(1f, contentHeight - totalGap)
            val uniformScale = min(1f, availableForImages / max(1f, totalNaturalHeight))

            try {
                val page = document.startPage(1)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                canvas.save()
                canvas.scale(canvas.width / pageWidth, canvas.height / pageHeight)

                val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(215, 219, 225)
                    style = Paint.Style.STROKE
                    strokeWidth = 0.65f
                }
                val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
                var y = margin
                processed.forEach { bitmap ->
                    val drawWidth = contentWidth * uniformScale
                    val drawHeight = drawWidth * bitmap.height.toFloat() / bitmap.width.toFloat()
                    val left = (pageWidth - drawWidth) / 2f
                    val dest = RectF(left, y, left + drawWidth, y + drawHeight)
                    canvas.drawBitmap(bitmap, null, dest, imagePaint)
                    canvas.drawRect(dest, border)
                    y = dest.bottom + safeGap
                }
                canvas.restore()
                document.finishPage(page)

                val output = File(cacheDir, "cropper-a4.pdf")
                FileOutputStream(output).use { document.writeTo(it) }

                // Open the native Android sharesheet with the generated PDF attached.
                // Recipients receive temporary read access through FileProvider.
                val pdfUri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "$packageName.fileprovider",
                    output
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, pdfUri)
                    clipData = ClipData.newUri(contentResolver, "Cropper A4 PDF", pdfUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share or print A4 PDF"))
            } finally {
                document.close()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not prepare print sheet: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        } finally {
            processed.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    private fun cropBitmap(source: Bitmap, normalized: RectF): Bitmap {
        val left = (normalized.left * source.width).roundToInt().coerceIn(0, source.width - 1)
        val top = (normalized.top * source.height).roundToInt().coerceIn(0, source.height - 1)
        val right = (normalized.right * source.width).roundToInt().coerceIn(left + 1, source.width)
        val bottom = (normalized.bottom * source.height).roundToInt().coerceIn(top + 1, source.height)
        if (left == 0 && top == 0 && right == source.width && bottom == source.height) return source
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    }

    private fun autoCropDocument(bitmap: Bitmap): Bitmap {
        if (!openCvReady) return bitmap
        val corners = detectDocumentCorners(bitmap) ?: return bitmap
        val ordered = orderCorners(corners)
        val outputWidth = max(distance(ordered[0], ordered[1]), distance(ordered[3], ordered[2]))
            .roundToInt().coerceIn(2, 3500)
        val outputHeight = max(distance(ordered[1], ordered[2]), distance(ordered[0], ordered[3]))
            .roundToInt().coerceIn(2, 3500)
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
            Imgproc.warpPerspective(sourceMat, corrected, transform, Size(outputWidth.toDouble(), outputHeight.toDouble()), Imgproc.INTER_CUBIC)
            Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888).also { Utils.matToBitmap(corrected, it) }
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
        val small = if (smallWidth != bitmap.width || smallHeight != bitmap.height)
            Bitmap.createScaledBitmap(bitmap, smallWidth, smallHeight, true) else bitmap

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
                    Imgproc.approxPolyDP(curve, approx, 0.02 * Imgproc.arcLength(curve, true), true)
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
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private inner class CropOverlayView(
        context: Context,
        private val bitmap: Bitmap,
        initial: RectF
    ) : View(context) {
        private val imageBounds = RectF()
        private var crop = RectF(initial)
        val selectedCrop: RectF get() = RectF(crop)
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x77000000 }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(117, 111, 255)
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
        }
        private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(117, 111, 255); style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat()
        }
        private var mode = MODE_NONE
        private var corner = -1
        private var startX = 0f
        private var startY = 0f
        private var startRect = RectF(crop)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.rgb(30, 34, 40))
            if (bitmap.width <= 0 || bitmap.height <= 0 || width == 0 || height == 0) return
            val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            val drawWidth = bitmap.width * scale
            val drawHeight = bitmap.height * scale
            imageBounds.set((width - drawWidth) / 2f, (height - drawHeight) / 2f,
                (width + drawWidth) / 2f, (height + drawHeight) / 2f)
            canvas.drawBitmap(bitmap, null, imageBounds, bitmapPaint)
            val selected = toScreen(crop)
            canvas.drawRect(imageBounds.left, imageBounds.top, imageBounds.right, selected.top, shadePaint)
            canvas.drawRect(imageBounds.left, selected.bottom, imageBounds.right, imageBounds.bottom, shadePaint)
            canvas.drawRect(imageBounds.left, selected.top, selected.left, selected.bottom, shadePaint)
            canvas.drawRect(selected.right, selected.top, imageBounds.right, selected.bottom, shadePaint)
            canvas.drawRect(selected, linePaint)
            val radius = dp(6).toFloat()
            val handleRadius = dp(7).toFloat()
            corners(selected).forEach { p ->
                canvas.drawCircle(p.x, p.y, handleRadius, handlePaint)
                canvas.drawCircle(p.x, p.y, handleRadius, cornerPaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (imageBounds.width() <= 0f || imageBounds.height() <= 0f) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    startRect = RectF(crop)
                    val selected = toScreen(crop)
                    corner = nearestCorner(event.x, event.y, selected)
                    mode = when {
                        corner >= 0 -> MODE_RESIZE
                        selected.contains(event.x, event.y) -> MODE_MOVE
                        else -> MODE_DRAW
                    }
                    if (mode == MODE_DRAW) {
                        val p = toNormalized(event.x, event.y)
                        crop = RectF(p.x, p.y, p.x, p.y)
                    }
                    parent?.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val p = toNormalized(event.x, event.y)
                    when (mode) {
                        MODE_MOVE -> {
                            val dx = (event.x - startX) / imageBounds.width()
                            val dy = (event.y - startY) / imageBounds.height()
                            val w = startRect.width()
                            val h = startRect.height()
                            val left = (startRect.left + dx).coerceIn(0f, 1f - w)
                            val top = (startRect.top + dy).coerceIn(0f, 1f - h)
                            crop = RectF(left, top, left + w, top + h)
                        }
                        MODE_RESIZE -> crop = resizeFromCorner(startRect, p, corner)
                        MODE_DRAW -> crop = RectF(
                            min(toNormalized(startX, startY).x, p.x),
                            min(toNormalized(startX, startY).y, p.y),
                            max(toNormalized(startX, startY).x, p.x),
                            max(toNormalized(startX, startY).y, p.y)
                        )
                    }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (crop.width() < 0.035f || crop.height() < 0.035f) crop = RectF(startRect)
                    crop = RectF(
                        crop.left.coerceIn(0f, 0.97f),
                        crop.top.coerceIn(0f, 0.97f),
                        crop.right.coerceIn(0.03f, 1f),
                        crop.bottom.coerceIn(0.03f, 1f)
                    )
                    mode = MODE_NONE
                    parent?.requestDisallowInterceptTouchEvent(false)
                    invalidate()
                    return true
                }
            }
            return true
        }

        private fun resizeFromCorner(base: RectF, p: PointF, which: Int): RectF {
            val minSide = 0.035f
            var left = base.left
            var top = base.top
            var right = base.right
            var bottom = base.bottom
            when (which) {
                0 -> { left = p.x.coerceIn(0f, right - minSide); top = p.y.coerceIn(0f, bottom - minSide) }
                1 -> { right = p.x.coerceIn(left + minSide, 1f); top = p.y.coerceIn(0f, bottom - minSide) }
                2 -> { right = p.x.coerceIn(left + minSide, 1f); bottom = p.y.coerceIn(top + minSide, 1f) }
                3 -> { left = p.x.coerceIn(0f, right - minSide); bottom = p.y.coerceIn(top + minSide, 1f) }
            }
            return RectF(left, top, right, bottom)
        }

        private fun nearestCorner(x: Float, y: Float, screen: RectF): Int {
            val all = corners(screen)
            var chosen = -1
            var best = dp(28).toFloat()
            all.forEachIndexed { index, p ->
                val dist = hypot(x - p.x, y - p.y)
                if (dist < best) { best = dist; chosen = index }
            }
            return chosen
        }

        private fun corners(rect: RectF) = listOf(
            PointF(rect.left, rect.top), PointF(rect.right, rect.top),
            PointF(rect.right, rect.bottom), PointF(rect.left, rect.bottom)
        )

        private fun toScreen(rect: RectF) = RectF(
            imageBounds.left + rect.left * imageBounds.width(),
            imageBounds.top + rect.top * imageBounds.height(),
            imageBounds.left + rect.right * imageBounds.width(),
            imageBounds.top + rect.bottom * imageBounds.height()
        )

        private fun toNormalized(x: Float, y: Float) = PointF(
            ((x - imageBounds.left) / imageBounds.width()).coerceIn(0f, 1f),
            ((y - imageBounds.top) / imageBounds.height()).coerceIn(0f, 1f)
        )

    }

    companion object {
        private const val MODE_NONE = 0
        private const val MODE_MOVE = 1
        private const val MODE_RESIZE = 2
        private const val MODE_DRAW = 3
    }

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
                .setPageCount(1)
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
                callback.onWriteFinished(arrayOf(android.print.PageRange(0, 0)))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }
}
