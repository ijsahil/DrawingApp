package com.example.drawingapp

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private var drawingView: DrawingView? = null
    private var customProgressDialog: Dialog? = null
    private var mImageButtonCurrentPaint: ImageButton? = null
    private val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val imageBackGround: ImageView = findViewById(R.id.iv_background)
                imageBackGround.setImageURI(result.data?.data)
            }
        }
    private val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                val permissionsName = it.key
                val isGranted = it.value

                if (isGranted) {
                    val pickIntent =
                        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLauncher.launch(pickIntent)
                } else {
                    if (permissionsName == Manifest.permission.READ_EXTERNAL_STORAGE) {
                        Toast.makeText(
                            this@MainActivity, "oops you denied the permission", Toast.LENGTH_LONG
                        ).show()
                    }

                }
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawingView = findViewById(R.id.drawing_view)
        val linearLayoutPaintColors = findViewById<LinearLayout>(R.id.ll_paint_colors)
        mImageButtonCurrentPaint = linearLayoutPaintColors[3] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.drawing_pallet_selected)
        )
        drawingView?.setSizeForBrush(10.toFloat())
        val brushImg: ImageButton = findViewById(R.id.image_brush)
        brushImg.setOnClickListener {
            showBrushSizeDialog()
        }
        val ibGallery: ImageButton = findViewById(R.id.image_gallery)
        ibGallery.setOnClickListener {
            requestStoragePermission()
        }
        val ibUndo: ImageButton = findViewById(R.id.image_undo)
        ibUndo.setOnClickListener {
            drawingView?.onClickUndo()
        }
        val ibRedo: ImageButton = findViewById(R.id.image_redo)
        ibRedo.setOnClickListener {
            drawingView?.onClickRedo()
        }
        val ibSave: ImageButton = findViewById(R.id.image_save)
        ibSave.setOnClickListener {
            showRationalDialog(
                "Save Image Confirmation",
                "Click 'Save' to save the image to your device. Click 'Cancel' if you do not want to save the image."
            )
            {
                if (isWriteStorageAllowed()) {
                    showCustomProgress()
                    lifecycleScope.launch {
                        val flDrawingView: FrameLayout =
                            findViewById(R.id.fl_drawing_view_container)
                        savedBitmapFile(getBitmapFromView(flDrawingView))
                    }
                }
            }
        }
    }

    private fun isWriteStorageAllowed(): Boolean {
        val result =
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        return result == PackageManager.PERMISSION_GRANTED
    }
    private fun requestStoragePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            )
        ) {
            showRationalDialog("Kids drawing app", "Needs to access your external storage")
        } else {

            /*
             * A launch method is used to launch the contract
             */
            requestPermission.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun showBrushSizeDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush Size :")
        val smlBtn: ImageButton = brushDialog.findViewById(R.id.ib_small_brush)
        smlBtn.setOnClickListener {
            drawingView?.setSizeForBrush(5.toFloat())
            brushDialog.dismiss()
        }
        val mediumBtn: ImageButton = brushDialog.findViewById(R.id.ib_medium_eraser)
        mediumBtn.setOnClickListener {
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }
        val largeBtn: ImageButton = brushDialog.findViewById(R.id.ib_large_eraser)
        largeBtn.setOnClickListener {
            drawingView?.setSizeForBrush(15.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    fun colorClicked(view: View) {
        if (view !== mImageButtonCurrentPaint) {
            val color = view.tag.toString()
            drawingView?.setPaintColor(color)
            val colorBtn = view as ImageButton
            colorBtn.setImageDrawable(
                ContextCompat.getDrawable(
                    this, R.drawable.drawing_pallet_selected
                )
            )
            mImageButtonCurrentPaint!!.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.drawing_pallet_normal)
            )
            mImageButtonCurrentPaint = view
        }
    }

    private fun getBitmapFromView(view: View): Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if (bgDrawable != null) {
            bgDrawable.draw(canvas)
        } else {
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return returnedBitmap
    }

    private suspend fun savedBitmapFile(mBitmap: Bitmap?): String {
        var result = ""
        withContext(Dispatchers.IO) {
            if (mBitmap != null) {
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)
                    val f =
                        File(externalCacheDir?.absoluteFile.toString() + File.separator + "KidsDrawingApp_" + System.currentTimeMillis() / 1000 + ".png")
                    val fos = FileOutputStream(f)
                    fos.write(bytes.toByteArray())
                    fos.close()
                    result = f.absolutePath
                    runOnUiThread {
                        cancelCustomProgress()
                        if (result.isNotEmpty()) {
                            Toast.makeText(
                                this@MainActivity,
                                "File saved successfully :$result",
                                Toast.LENGTH_LONG
                            ).show()
                            shareImage(result)
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Something went wrong while saving the picture",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }


    private fun showRationalDialog(
        title: String, message: String, function: () -> Unit
    ) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("Save") { dialogInterface, _ ->
            dialogInterface.dismiss()
            function.invoke()
        }
        builder.setNegativeButton("Cancel") { dialogInterface, _ ->
            dialogInterface.dismiss()
        }
        builder.create()
        builder.setCancelable(false)
        builder.show()
    }

    private fun showRationalDialog(title: String, message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("Save") { dialogInterface, _ ->
            dialogInterface.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialogInterface, _ ->
            dialogInterface.dismiss()
        }
        builder.create()
        builder.setCancelable(false)
        builder.show()
    }

    private fun cancelCustomProgress() {
        if (customProgressDialog != null) {
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    private fun showCustomProgress() {
        customProgressDialog = Dialog(this)
        customProgressDialog?.setContentView(R.layout.custom_progress_bar)
        customProgressDialog?.show()
    }
    private fun shareImage(result : String){
        MediaScannerConnection.scanFile(this, arrayOf(result),null){
                _, uri ->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            intent.putExtra(Intent.EXTRA_STREAM,uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent,"Share"))
        }
    }

}