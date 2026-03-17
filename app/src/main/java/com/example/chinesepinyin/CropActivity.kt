package com.example.chinesepinyin

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.chinesepinyin.databinding.ActivityCropBinding
import java.io.File
import java.io.FileOutputStream

class CropActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCropBinding

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val RESULT_CROPPED_URI = "result_cropped_uri"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCropBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = "認識範囲を選択"
            setDisplayHomeAsUpEnabled(true)
        }

        val uriString = intent.getStringExtra(EXTRA_IMAGE_URI)
        if (uriString == null) {
            Toast.makeText(this, "画像が見つかりません", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val uri = Uri.parse(uriString)
        binding.cropImageView.setImageUri(uri)

        // リセットボタン
        binding.btnResetCrop.setOnClickListener {
            binding.cropImageView.resetCrop()
        }

        // 決定ボタン
        binding.btnConfirmCrop.setOnClickListener {
            val cropped = binding.cropImageView.getCroppedBitmap()
            if (cropped == null) {
                Toast.makeText(this, "クロップ範囲が無効です", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.progressCrop.visibility = View.VISIBLE
            binding.btnConfirmCrop.isEnabled = false

            // クロップ済み画像をキャッシュに保存してURIを返す
            val file = File(cacheDir, "cropped_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val resultUri = Uri.fromFile(file)
            val resultIntent = Intent().apply {
                putExtra(RESULT_CROPPED_URI, resultUri.toString())
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        setResult(Activity.RESULT_CANCELED)
        finish()
        return true
    }
}
