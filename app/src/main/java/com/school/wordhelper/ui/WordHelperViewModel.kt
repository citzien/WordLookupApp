package com.school.wordhelper.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.school.wordhelper.data.DictionaryRepository
import com.school.wordhelper.data.TranslateConfig
import com.school.wordhelper.data.WordResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray

data class SearchUiState(
    val word: String = "",
    val loading: Boolean = false,
    val result: WordResult? = null,
    val error: String? = null,
    val history: List<String> = emptyList()
)

class WordHelperViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = DictionaryRepository()
    private val prefs = app.getSharedPreferences("word_helper", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        SearchUiState(history = loadHistory())
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        // 把设置页保存的百度翻译密钥加载到内存
        TranslateConfig.appId = prefs.getString("baidu_trans_appid", "").orEmpty()
        TranslateConfig.key = prefs.getString("baidu_trans_key", "").orEmpty()
    }

    fun search(raw: String) {
        val word = raw.trim().lowercase()
        if (word.isEmpty()) return
        _uiState.update { it.copy(word = word, loading = true, result = null, error = null) }
        viewModelScope.launch {
            try {
                // 第一段：主体结果先显示（约 1~2 秒）
                val result = repository.lookup(word)
                _uiState.update { it.copy(loading = false, result = result) }
                saveHistory(word)
                // 第二段：后台补充例句和中文意思（逐个完成即刷新，边查边显示）
                val enriched = repository.enrichExamples(word, result) { updated ->
                    _uiState.update { it.copy(result = updated) }
                }
                _uiState.update { it.copy(result = enriched) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, error = "查询失败：" + (e.message ?: "未知错误"))
                }
            }
        }
    }

    /** OCR 点选单词后调用：直接搜索 */
    fun onWordFromOcr(word: String) = search(word)

    private fun loadHistory(): List<String> = try {
        val raw = prefs.getString("history", null) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun saveHistory(word: String) {
        val updated = (listOf(word) + _uiState.value.history).distinct().take(12)
        prefs.edit().putString("history", JSONArray(updated).toString()).apply()
        _uiState.update { it.copy(history = updated) }
    }
}
