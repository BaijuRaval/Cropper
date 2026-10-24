package com.baijuraval.cropper

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
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
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

class MainActivity : ComponentActivity() {
    private val selected = mutableListOf<Uri>()
    private lateinit var list: LinearLayout
    private lateinit var count: TextView
    private val pickImages = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            text = "Your photos. One A4 sheet. Ready to print."
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(5), 0, dp(18))
        })
        val choose = Button(this).apply {
            text = "＋  Select multiple images"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                startActivityForResult(intent, pickImages)
            }
        }
        root.addView(choose, matchWrap())
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
            text = "Print A4 sheet  →"
            textSize = 16f
            setOnClickListener {
                if (selected.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Select at least one image first", Toast.LENGTH_SHORT).show()
                } else printA4()
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
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                selected.add(uri)
            }
        }
        refresh()
    }

    private fun refresh() {
        list.removeAllViews()
        count.text = if (selected.isEmpty()) "No images selected" else "${selected.size} image(s) • arranged automatically on A4"
        selected.forEachIndexed { index, uri ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setBackgroundColor(Color.WHITE)
            }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                try { setImageURI(uri) } catch (_: Exception) {}
            }
            row.addView(image, LinearLayout.LayoutParams(dp(76), dp(56)))
            row.addView(TextView(this).apply {
                text = "Image ${index + 1}"
                textSize = 15f
                setTextColor(Color.rgb(35, 45, 60))
                setPadding(dp(12), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Button(this).apply {
                text = "Remove"
                setOnClickListener { selected.remove(uri); refresh() }
            })
            val lp = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            list.addView(row, lp)
        }
    }

    private fun printA4() {
        try {
            val attrs = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(android.print.PrintAttributes.Resolution("cropper", "Cropper", 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()
            val document = PrintedPdfDocument(this, attrs)
            val pageWidth = 595f
            val pageHeight = 842f
            val margin = 28f
            val gap = 14f
            val columns = 2
            val cellWidth = (pageWidth - margin * 2 - gap) / columns
            val cellHeight = 150f
            val perPage = 10
            val pages = selected.chunked(perPage)
            pages.forEachIndexed { pageIndex, uris ->
                val page = document.startPage(pageIndex + 1)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.LTGRAY
                    style = Paint.Style.STROKE
                    strokeWidth = 0.7f
                }
                uris.forEachIndexed { itemIndex, uri ->
                    val bitmap = loadBitmap(uri) ?: return@forEachIndexed
                    val col = itemIndex % columns
                    val row = itemIndex / columns
                    val left = margin + col * (cellWidth + gap)
                    val top = margin + row * (cellHeight + gap)
                    val scale = min(cellWidth / bitmap.width.toFloat(), cellHeight / bitmap.height.toFloat())
                    val w = bitmap.width * scale
                    val h = bitmap.height * scale
                    val dest = RectF(left + (cellWidth - w) / 2f, top + (cellHeight - h) / 2f,
                        left + (cellWidth + w) / 2f, top + (cellHeight + h) / 2f)
                    canvas.drawBitmap(bitmap, null, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    canvas.drawRect(dest, paint)
                    bitmap.recycle()
                }
                document.finishPage(page)
            }
            val output = File(cacheDir, "cropper-a4.pdf")
            FileOutputStream(output).use { document.writeTo(it) }
            document.close()
            val printManager = getSystemService(PRINT_SERVICE) as PrintManager
            printManager.print("Cropper A4 sheet", PdfPrintAdapter(output), attrs)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not prepare print sheet: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? = try {
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) { null }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private inner class PdfPrintAdapter(private val file: File) : android.print.PrintDocumentAdapter() {
        override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, cancellationSignal: android.os.CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
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
        override fun onWrite(pages: Array<out android.print.PageRange>?, destination: android.os.ParcelFileDescriptor, cancellationSignal: android.os.CancellationSignal?, callback: WriteResultCallback) {
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
