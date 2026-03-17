package com.example.chinesepinyin

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.chinesepinyin.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedImageUri: Uri? = null
    private var cameraImageUri: Uri? = null

    // ML Kit Chinese Text Recognizer
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    // ギャラリー選択 → クロップ画面へ
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { launchCropActivity(it) }
    }

    // カメラ撮影 → クロップ画面へ
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            cameraImageUri?.let { launchCropActivity(it) }
        }
    }

    // クロップ画面からの結果受け取り
    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val croppedUriString = result.data?.getStringExtra(CropActivity.RESULT_CROPPED_URI)
            if (croppedUriString != null) {
                val croppedUri = Uri.parse(croppedUriString)
                selectedImageUri = croppedUri
                binding.imagePreview.setImageURI(croppedUri)
                resetResults()
                // クロップ後は自動的に認識開始
                recognizeText(croppedUri)
            }
        }
        // RESULT_CANCELED の場合は何もしない（元の画像を維持）
    }

    // カメラ権限リクエスト
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    // ストレージ権限リクエスト
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        galleryLauncher.launch("image/*")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupButtons()
    }

    private fun setupButtons() {
        // ギャラリー選択ボタン
        binding.btnSelectImage.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                galleryLauncher.launch("image/*")
            } else {
                val permission = Manifest.permission.READ_EXTERNAL_STORAGE
                if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                    galleryLauncher.launch("image/*")
                } else {
                    storagePermissionLauncher.launch(permission)
                }
            }
        }

        // カメラ撮影ボタン
        binding.btnTakePhoto.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
                launchCamera()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // 認識ボタン（プレビュー画像を再クロップ or 再認識）
        binding.btnRecognize.setOnClickListener {
            val uri = selectedImageUri
            if (uri == null) {
                Toast.makeText(this, getString(R.string.select_image_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            recognizeText(uri)
        }

        // クロップし直しボタン
        binding.btnCrop.setOnClickListener {
            val uri = selectedImageUri
            if (uri == null) {
                Toast.makeText(this, getString(R.string.select_image_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 元の画像URIを保持してクロップ画面へ
            launchCropActivity(uri)
        }

        // テキストコピーボタン
        binding.btnCopyText.setOnClickListener {
            val text = binding.tvRecognizedText.text.toString()
            if (text.isNotEmpty()) copyToClipboard("Chinese Text", text)
        }

        // ピンインコピーボタン
        binding.btnCopyPinyin.setOnClickListener {
            val pinyin = binding.tvPinyin.text.toString()
            if (pinyin.isNotEmpty()) copyToClipboard("Pinyin", pinyin)
        }
    }

    private fun launchCropActivity(uri: Uri) {
        val intent = Intent(this, CropActivity::class.java).apply {
            putExtra(CropActivity.EXTRA_IMAGE_URI, uri.toString())
        }
        cropLauncher.launch(intent)
    }

    private fun launchCamera() {
        val imageFile = createImageFile()
        cameraImageUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            imageFile
        )
        cameraLauncher.launch(cameraImageUri)
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = externalCacheDir ?: cacheDir
        return File.createTempFile("IMG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun recognizeText(uri: Uri) {
        showLoading(true)
        resetResults()

        lifecycleScope.launch {
            try {
                val image = withContext(Dispatchers.IO) {
                    InputImage.fromFilePath(this@MainActivity, uri)
                }
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val recognizedText = visionText.text
                        if (recognizedText.isBlank()) {
                            showLoading(false)
                            showStatus(getString(R.string.no_text_found))
                        } else {
                            processRecognizedText(recognizedText)
                        }
                    }
                    .addOnFailureListener { e ->
                        showLoading(false)
                        showStatus("エラー: ${e.message}")
                    }
            } catch (e: Exception) {
                showLoading(false)
                showStatus("エラー: ${e.message}")
            }
        }
    }

    private fun processRecognizedText(text: String) {
        lifecycleScope.launch(Dispatchers.Default) {
            val pinyin = PinyinConverter.convertToPinyin(text)
            val pairs = PinyinConverter.convertToCharPinyinPairs(text)

            withContext(Dispatchers.Main) {
                showLoading(false)

                binding.tvRecognizedText.text = text
                binding.cardRecognized.visibility = View.VISIBLE

                binding.tvPinyin.text = pinyin
                binding.cardPinyin.visibility = View.VISIBLE

                binding.rubyTextView.setCharPinyinPairs(pairs)
                binding.cardRuby.visibility = View.VISIBLE

                // ピンインを自動的にクリップボードにコピー
                copyToClipboard("Pinyin", pinyin, showToast = true)
            }
        }
    }

    private fun copyToClipboard(label: String, text: String, showToast: Boolean = true) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        if (showToast) {
            Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnRecognize.isEnabled = !show
        binding.btnCrop.isEnabled = !show
        if (show) {
            binding.tvStatus.text = getString(R.string.recognizing)
            binding.tvStatus.visibility = View.VISIBLE
        } else {
            binding.tvStatus.visibility = View.GONE
        }
    }

    private fun showStatus(message: String) {
        binding.tvStatus.text = message
        binding.tvStatus.visibility = View.VISIBLE
    }

    private fun resetResults() {
        binding.cardRecognized.visibility = View.GONE
        binding.cardPinyin.visibility = View.GONE
        binding.cardRuby.visibility = View.GONE
        binding.tvStatus.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer.close()
    }
}
