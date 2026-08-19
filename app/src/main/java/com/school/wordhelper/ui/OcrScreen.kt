package com.school.wordhelper.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.school.wordhelper.ocr.BitmapUtils
import com.school.wordhelper.ocr.OcrEngine
import com.school.wordhelper.ocr.OcrWord
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OcrScreen(
    viewModel: WordHelperViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ocrEngine = remember { OcrEngine(context) }
    val uiState by viewModel.uiState.collectAsState()

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var stage by remember { mutableStateOf("pick") } // pick → crop → ocr
    var cropRect by remember { mutableStateOf(RectF(0.05f, 0.05f, 0.95f, 0.95f)) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var words by remember { mutableStateOf<List<OcrWord>>(emptyList()) }
    var recognizing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("word_helper", Context.MODE_PRIVATE) }
    var engineChoice by remember { mutableStateOf(prefs.getString("ocr_engine", "baidu") ?: "baidu") }
    var apiKeyInput by remember { mutableStateOf(prefs.getString("baidu_api_key", "") ?: "") }
    var secretInput by remember { mutableStateOf(prefs.getString("baidu_secret_key", "") ?: "") }
    var tencentIdInput by remember { mutableStateOf(prefs.getString("tencent_secret_id", "") ?: "") }
    var tencentKeyInput by remember { mutableStateOf(prefs.getString("tencent_secret_key", "") ?: "") }
    var transAppIdInput by remember { mutableStateOf(prefs.getString("baidu_trans_appid", "") ?: "") }
    var transKeyInput by remember { mutableStateOf(prefs.getString("baidu_trans_key", "") ?: "") }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var keysConfigured by remember {
        mutableStateOf(
            when (prefs.getString("ocr_engine", "baidu") ?: "baidu") {
                "tencent" -> !prefs.getString("tencent_secret_id", "").orEmpty().isBlank() &&
                    !prefs.getString("tencent_secret_key", "").orEmpty().isBlank()
                "local" -> true
                else -> !prefs.getString("baidu_api_key", "").orEmpty().isBlank() &&
                    !prefs.getString("baidu_secret_key", "").orEmpty().isBlank()
            }
        )
    }
    var removeLines by remember {
        mutableStateOf(prefs.getBoolean("ocr_remove_lines", true))
    }

    val cacheFile = remember { File(context.cacheDir, "ocr_photo.jpg") }
    val photoUri = remember {
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", cacheFile)
    }

    /** 选择照片后：先进入裁剪 */
    fun loadPhoto(uri: Uri) {
        scope.launch {
            recognizing = true
            errorMessage = null
            words = emptyList()
            showResult = false
            try {
                val bmp = BitmapUtils.decodeSampledBitmapFromUri(context, uri)
                    ?: throw IOException("无法读取图片")
                originalBitmap = bmp
                cropRect = RectF(0.05f, 0.05f, 0.95f, 0.95f)
                stage = "crop"
            } catch (e: Exception) {
                errorMessage = "读取图片失败：" + (e.message ?: "未知错误")
                stage = "ocr"
            } finally {
                recognizing = false
            }
        }
    }

    /** 按裁剪框裁出区域并识别 */
    fun confirmCrop() {
        val src = originalBitmap ?: return
        scope.launch {
            recognizing = true
            errorMessage = null
            words = emptyList()
            showResult = false
            try {
                val left = (cropRect.left * src.width).toInt().coerceIn(0, src.width - 1)
                val top = (cropRect.top * src.height).toInt().coerceIn(0, src.height - 1)
                val right = (cropRect.right * src.width).toInt().coerceIn(left + 1, src.width)
                val bottom = (cropRect.bottom * src.height).toInt().coerceIn(top + 1, src.height)
                val cropped = Bitmap.createBitmap(src, left, top, right - left, bottom - top)
                bitmap = cropped
                val recognized = ocrEngine.recognize(cropped, removeLines)
                if (recognized.isEmpty()) {
                    errorMessage = "没有识别到英文单词，请重新裁剪对准单词后重试"
                } else {
                    words = recognized
                }
                stage = "ocr"
            } catch (e: Exception) {
                errorMessage = "识别失败：" + (e.message ?: "未知错误")
                stage = "ocr"
            } finally {
                recognizing = false
            }
        }
    }

    fun selectWord(word: String) {
        viewModel.onWordFromOcr(word)
        showResult = true
    }

    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) loadPhoto(photoUri) else errorMessage = "已取消拍照"
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) loadPhoto(uri) else errorMessage = "未选择图片"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拍照识别书本单词") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "百度 OCR 设置")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (stage) {
                "crop" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { stage = "pick" }) {
                            Text("取消")
                        }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { confirmCrop() }) {
                            Text("裁剪并识别")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val src = originalBitmap
                    if (src != null) {
                        Box(Modifier.weight(1f)) {
                            CropOverlay(
                                bitmap = src,
                                crop = cropRect,
                                onCropChange = { cropRect = it }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        QuickCropButtons(onCropChange = { cropRect = it })
                        Text(
                            "可点上方快捷选区快速定位，再拖动边框/四角/框内微调",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                "ocr" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { takePhoto.launch(photoUri) }) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("拍照")
                        }
                        OutlinedButton(onClick = {
                            pickImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }) {
                            Text("从相册选择")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = removeLines,
                            onClick = {
                                removeLines = !removeLines
                                prefs.edit().putBoolean("ocr_remove_lines", removeLines).apply()
                            },
                            label = { Text("去除线条（格子/横线）") }
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = { stage = "crop" },
                            enabled = originalBitmap != null
                        ) {
                            Text("重新裁剪")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    when {
                        showResult -> {
                            Column(Modifier.fillMaxSize()) {
                                TextButton(onClick = { showResult = false }) {
                                    Text("← 返回继续识别")
                                }
                                when {
                                    uiState.loading -> {
                                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator()
                                                Spacer(Modifier.height(8.dp))
                                                Text("正在查询…")
                                            }
                                        }
                                    }
                                    uiState.error != null -> {
                                        Box(Modifier.weight(1f)) {
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                                )
                                            ) {
                                                Column(Modifier.padding(16.dp)) {
                                                    Text(
                                                        uiState.error ?: "",
                                                        color = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                    Spacer(Modifier.height(8.dp))
                                                    Button(onClick = { viewModel.search(uiState.word) }) {
                                                        Text("重试")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    uiState.result != null -> {
                                        Box(Modifier.weight(1f)) {
                                            ResultContent(
                                                result = uiState.result!!,
                                                onWordClick = { viewModel.onWordFromOcr(it) }
                                            )
                                        }
                                    }
                                    else -> {
                                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                            Text("正在准备查询…")
                                        }
                                    }
                                }
                            }
                        }
                        recognizing -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(Modifier.height(8.dp))
                                    Text("正在识别文字…")
                                }
                            }
                        }
                        errorMessage != null -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        errorMessage ?: "",
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row {
                                        TextButton(onClick = { showSettings = true }) {
                                            Text("去设置")
                                        }
                                        TextButton(onClick = { stage = "crop" }) {
                                            Text("重新裁剪")
                                        }
                                    }
                                }
                            }
                        }
                        bitmap != null && words.isNotEmpty() -> {
                            Box(Modifier.weight(1f)) {
                                WordImageOverlay(
                                    bitmap = bitmap!!,
                                    words = words,
                                    onClick = { word -> selectWord(word) }
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "识别出的单词（点击也可查询）：",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                words.forEach { word ->
                                    AssistChip(
                                        onClick = { selectWord(word.text) },
                                        label = { Text(word.text) }
                                    )
                                }
                            }
                        }
                        else -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "还没有识别结果，请先拍照或从相册选择图片。",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { takePhoto.launch(photoUri) }) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("拍照")
                        }
                        OutlinedButton(onClick = {
                            pickImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }) {
                            Text("从相册选择")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "拍下课本或试卷上的单词，\n拍照后先裁剪对准目标单词，\n再点「裁剪并识别」。",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (keysConfigured) "OCR 已配置，可以拍照识别 ✓"
                                else "未配置 OCR，请点右上角「设置」填写密钥",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "⚠️ 提示：OCR 识别目前还不稳定（对带横线/格子的图片效果一般），正在优化中。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("OCR 识别设置") },
            text = {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                ) {
                    Text(
                        "选择识别引擎并填写对应密钥（只保存在本机）。填完可以先点「测试连接」验证。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = engineChoice == "baidu",
                            onClick = {
                                engineChoice = "baidu"
                                testResult = null
                                keysConfigured =
                                    !prefs.getString("baidu_api_key", "").orEmpty().isBlank() &&
                                        !prefs.getString("baidu_secret_key", "").orEmpty().isBlank()
                            },
                            label = { Text("百度 OCR") }
                        )
                        FilterChip(
                            selected = engineChoice == "tencent",
                            onClick = {
                                engineChoice = "tencent"
                                testResult = null
                                keysConfigured =
                                    !prefs.getString("tencent_secret_id", "").orEmpty().isBlank() &&
                                        !prefs.getString("tencent_secret_key", "").orEmpty().isBlank()
                            },
                            label = { Text("腾讯云 OCR") }
                        )
                        FilterChip(
                            selected = engineChoice == "local",
                            onClick = {
                                engineChoice = "local"
                                testResult = null
                                keysConfigured = true
                            },
                            label = { Text("本地离线") }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (engineChoice == "tencent") {
                        Text(
                            "腾讯云：https://console.cloud.tencent.com/ocr 开通文字识别，在「访问管理 → API 密钥管理」创建 SecretId / SecretKey。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = tencentIdInput,
                            onValueChange = {
                                tencentIdInput = it
                                testResult = null
                            },
                            label = { Text("SecretId") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = tencentKeyInput,
                            onValueChange = {
                                tencentKeyInput = it
                                testResult = null
                            },
                            label = { Text("SecretKey") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (engineChoice == "local") {
                        Text(
                            "本地 Tesseract OCR：无需密钥、完全离线。首次识别会自动复制约 4MB 模型，之后不再联网。精度略低于云端，适合无网环境。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "注意：本地引擎仅支持 arm64/armv7 手机（模拟器不可用）。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            "百度：https://console.bce.baidu.com 创建「文字识别」应用后复制 API Key / Secret Key。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = {
                                apiKeyInput = it
                                testResult = null
                            },
                            label = { Text("API Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = secretInput,
                            onValueChange = {
                                secretInput = it
                                testResult = null
                            },
                            label = { Text("Secret Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "例句翻译加速（可选）：开通百度翻译开放平台（免费额度）后填 APP ID 和密钥，例句翻译会更快更稳。申请：https://fanyi-api.baidu.com",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = transAppIdInput,
                        onValueChange = { transAppIdInput = it },
                        label = { Text("百度翻译 APP ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = transKeyInput,
                        onValueChange = { transKeyInput = it },
                        label = { Text("百度翻译密钥") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            if (testing) return@TextButton
                            scope.launch {
                                testing = true
                                testResult = null
                                try {
                                    testResult = if (engineChoice == "tencent") {
                                        ocrEngine.testKeys(tencentIdInput.trim(), tencentKeyInput.trim())
                                    } else {
                                        ocrEngine.testKeys(apiKeyInput.trim(), secretInput.trim())
                                    }
                                } catch (e: Exception) {
                                    testResult = "连接失败：" + (e.message ?: "未知错误")
                                } finally {
                                    testing = false
                                }
                            }
                        },
                        enabled = !testing
                    ) {
                        Text(if (testing) "测试中…" else "测试连接")
                    }
                    testResult?.let { msg ->
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (msg.startsWith("连接成功")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val editor = prefs.edit()
                        .putString("ocr_engine", engineChoice)
                        .putString("baidu_trans_appid", transAppIdInput.trim())
                        .putString("baidu_trans_key", transKeyInput.trim())
                    if (engineChoice == "tencent") {
                        editor
                            .putString("tencent_secret_id", tencentIdInput.trim())
                            .putString("tencent_secret_key", tencentKeyInput.trim())
                    } else {
                        editor
                            .putString("baidu_api_key", apiKeyInput.trim())
                            .putString("baidu_secret_key", secretInput.trim())
                    }
                    editor.apply()
                    com.school.wordhelper.data.TranslateConfig.appId = transAppIdInput.trim()
                    com.school.wordhelper.data.TranslateConfig.key = transKeyInput.trim()
                    keysConfigured = true
                    showSettings = false
                    Toast.makeText(context, "OCR 设置已保存", Toast.LENGTH_SHORT).show()
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/** 裁剪快捷选区：九宫格 + 全图，一键把裁剪框挪到对应区域 */
@Composable
private fun QuickCropButtons(onCropChange: (RectF) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(
                "左上" to RectF(0f, 0f, 0.34f, 0.34f),
                "上" to RectF(0.33f, 0f, 0.67f, 0.34f),
                "右上" to RectF(0.66f, 0f, 1f, 0.34f)
            ).forEach { (label, r) ->
                TextButton(onClick = { onCropChange(r) }, contentPadding = PaddingValues(horizontal = 2.dp)) {
                    Text(label, fontSize = 12.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(
                "左" to RectF(0f, 0.33f, 0.34f, 0.67f),
                "中" to RectF(0.33f, 0.33f, 0.67f, 0.67f),
                "右" to RectF(0.66f, 0.33f, 1f, 0.67f)
            ).forEach { (label, r) ->
                TextButton(onClick = { onCropChange(r) }, contentPadding = PaddingValues(horizontal = 2.dp)) {
                    Text(label, fontSize = 12.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(
                "左下" to RectF(0f, 0.66f, 0.34f, 1f),
                "下" to RectF(0.33f, 0.66f, 0.67f, 1f),
                "右下" to RectF(0.66f, 0.66f, 1f, 1f)
            ).forEach { (label, r) ->
                TextButton(onClick = { onCropChange(r) }, contentPadding = PaddingValues(horizontal = 2.dp)) {
                    Text(label, fontSize = 12.sp)
                }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(
                onClick = { onCropChange(RectF(0f, 0f, 1f, 1f)) },
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                Text("全图", fontSize = 12.sp)
            }
        }
    }
}

/** 裁剪界面：拖动四角/四边/框内移动调整裁剪范围（crop 为图片归一化坐标 0~1） */
@Composable
private fun CropOverlay(
    bitmap: Bitmap,
    crop: RectF,
    onCropChange: (RectF) -> Unit
) {
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        val scale = min(maxW / bitmap.width, maxH / bitmap.height)
        val offX = (maxW - bitmap.width * scale) / 2f
        val offY = (maxH - bitmap.height * scale) / 2f

        fun cropRectPx(): Rect = Rect(
            offX + crop.left * bitmap.width * scale,
            offY + crop.top * bitmap.height * scale,
            offX + crop.right * bitmap.width * scale,
            offY + crop.bottom * bitmap.height * scale
        )

        var mode by remember { mutableStateOf(-1) }
        var startCrop by remember { mutableStateOf(crop) }
        var accX by remember { mutableStateOf(0f) }
        var accY by remember { mutableStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(crop, bitmap) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val r = cropRectPx()
                            val tol = 44.dp.toPx()
                            accX = 0f
                            accY = 0f
                            startCrop = crop
                            mode = when {
                                (pos - r.topLeft).getDistance() < tol -> 0
                                (pos - Offset(r.right, r.top)).getDistance() < tol -> 1
                                (pos - Offset(r.left, r.bottom)).getDistance() < tol -> 2
                                (pos - r.bottomRight).getDistance() < tol -> 3
                                abs(pos.x - r.left) < tol && pos.y in r.top..r.bottom -> 5
                                abs(pos.x - r.right) < tol && pos.y in r.top..r.bottom -> 6
                                abs(pos.y - r.top) < tol && pos.x in r.left..r.right -> 7
                                abs(pos.y - r.bottom) < tol && pos.x in r.left..r.right -> 8
                                r.contains(pos) -> 4
                                else -> -1
                            }
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            if (mode < 0) return@detectDragGestures
                            accX += drag.x
                            accY += drag.y
                            val dX = accX / (bitmap.width * scale)
                            val dY = accY / (bitmap.height * scale)
                            val minSize = 0.05f
                            val l = startCrop.left
                            val t = startCrop.top
                            val rr = startCrop.right
                            val b = startCrop.bottom
                            when (mode) {
                                0 -> onCropChange(
                                    RectF(
                                        (l + dX).coerceIn(0f, rr - minSize),
                                        (t + dY).coerceIn(0f, b - minSize),
                                        rr,
                                        b
                                    )
                                )
                                1 -> onCropChange(
                                    RectF(
                                        l,
                                        (t + dY).coerceIn(0f, b - minSize),
                                        (rr + dX).coerceIn(l + minSize, 1f),
                                        b
                                    )
                                )
                                2 -> onCropChange(
                                    RectF(
                                        (l + dX).coerceIn(0f, rr - minSize),
                                        t,
                                        rr,
                                        (b + dY).coerceIn(t + minSize, 1f)
                                    )
                                )
                                3 -> onCropChange(
                                    RectF(
                                        l,
                                        t,
                                        (rr + dX).coerceIn(l + minSize, 1f),
                                        (b + dY).coerceIn(t + minSize, 1f)
                                    )
                                )
                                4 -> {
                                    val w = rr - l
                                    val hh = b - t
                                    val nl = (l + dX).coerceIn(0f, 1f - w)
                                    val nt = (t + dY).coerceIn(0f, 1f - hh)
                                    onCropChange(RectF(nl, nt, nl + w, nt + hh))
                                }
                                5 -> onCropChange(
                                    RectF((l + dX).coerceIn(0f, rr - minSize), t, rr, b)
                                )
                                6 -> onCropChange(
                                    RectF(l, t, (rr + dX).coerceIn(l + minSize, 1f), b)
                                )
                                7 -> onCropChange(
                                    RectF(l, (t + dY).coerceIn(0f, b - minSize), rr, b)
                                )
                                8 -> onCropChange(
                                    RectF(l, t, rr, (b + dY).coerceIn(t + minSize, 1f))
                                )
                            }
                        },
                        onDragEnd = { mode = -1 },
                        onDragCancel = { mode = -1 }
                    )
                }
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "待裁剪照片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            Canvas(Modifier.fillMaxSize()) {
                val r = cropRectPx()
                // 裁剪区外压暗
                drawRect(Color(0x99000000), topLeft = Offset(0f, 0f), size = Size(size.width, r.top))
                drawRect(
                    Color(0x99000000),
                    topLeft = Offset(0f, r.bottom),
                    size = Size(size.width, size.height - r.bottom)
                )
                drawRect(Color(0x99000000), topLeft = Offset(0f, r.top), size = Size(r.left, r.height))
                drawRect(
                    Color(0x99000000),
                    topLeft = Offset(r.right, r.top),
                    size = Size(size.width - r.right, r.height)
                )
                // 白色边框 + 四个角手柄
                drawRect(Color.White, topLeft = r.topLeft, size = r.size, style = Stroke(2.dp.toPx()))
                val hs = 22.dp.toPx()
                val corners = listOf(r.topLeft, Offset(r.right, r.top), Offset(r.left, r.bottom), r.bottomRight)
                corners.forEach { p ->
                    drawCircle(Color(0xFF3F51B5), hs / 2f, p)
                    drawCircle(Color.White, hs / 2f - 3.dp.toPx(), p)
                }
            }
        }
    }
}

/** 显示照片，并在识别出的单词上画框，点击单词触发查询 */
@Composable
private fun WordImageOverlay(
    bitmap: Bitmap,
    words: List<OcrWord>,
    onClick: (String) -> Unit
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontSize = 13.sp,
        color = Color.White,
        background = Color(0x993F51B5)
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(words, bitmap) {
                    detectTapGestures { offset ->
                        val scale = min(maxWidthPx / bitmap.width, maxHeightPx / bitmap.height)
                        val offX = (maxWidthPx - bitmap.width * scale) / 2f
                        val offY = (maxHeightPx - bitmap.height * scale) / 2f
                        val hit = words.firstOrNull { word ->
                            val left = offX + word.box.left * scale
                            val top = offY + word.box.top * scale
                            val right = offX + word.box.right * scale
                            val bottom = offY + word.box.bottom * scale
                            offset.x in left..right && offset.y in top..bottom
                        }
                        hit?.let { onClick(it.text) }
                    }
                }
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "书本照片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            Canvas(Modifier.fillMaxSize()) {
                val scale = min(maxWidthPx / bitmap.width, maxHeightPx / bitmap.height)
                val offX = (maxWidthPx - bitmap.width * scale) / 2f
                val offY = (maxHeightPx - bitmap.height * scale) / 2f

                words.forEach { word ->
                    val left = offX + word.box.left * scale
                    val top = offY + word.box.top * scale
                    val right = offX + word.box.right * scale
                    val bottom = offY + word.box.bottom * scale
                    val rectSize = Size(right - left, bottom - top)

                    drawRect(
                        color = Color(0x263F51B5),
                        topLeft = Offset(left, top),
                        size = rectSize
                    )
                    drawRect(
                        color = Color(0xFF3F51B5),
                        topLeft = Offset(left, top),
                        size = rectSize,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    if (bottom - top > 24.dp.toPx()) {
                        val layout = textMeasurer.measure(
                            AnnotatedString(word.text),
                            labelStyle
                        )
                        drawText(layout, topLeft = Offset(left, top))
                    }
                }
            }
        }
    }
}
