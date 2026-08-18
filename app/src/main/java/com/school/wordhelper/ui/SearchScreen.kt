package com.school.wordhelper.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.school.wordhelper.data.WordResult
import com.school.wordhelper.data.WordWithExample

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: WordHelperViewModel,
    onOpenOcr: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.word) {
        if (state.word.isNotEmpty()) input = state.word
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("单词助手") },
                actions = {
                    IconButton(onClick = onOpenOcr) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = "拍照识别")
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
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("输入英文单词") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search(input) })
            )
            Spacer(Modifier.height(12.dp))

            when {
                state.loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("正在查询…")
                        }
                    }
                }
                state.error != null -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                state.error ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.search(state.word) }) {
                                Text("重试")
                            }
                        }
                    }
                }
                state.result != null -> {
                    ResultContent(
                        result = state.result!!,
                        onWordClick = { viewModel.search(it) }
                    )
                }
                else -> {
                    HistoryContent(
                        history = state.history,
                        onWordClick = { viewModel.search(it) },
                        onOpenOcr = onOpenOcr
                    )
                }
            }
        }
    }
}

/** 查询结果：中文意思 / 同义词（含例句）/ 近义词（含例句）/ 英文释义 */
@Composable
internal fun ResultContent(
    result: WordResult,
    onWordClick: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            result.word,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        if (result.phonetic.isNotBlank()) {
            Text(
                result.phonetic,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "中文意思",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    result.chinese ?: "未获取到中文翻译（免费接口偶发失败，可重试）",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        if (result.synonyms.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "同义词",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    result.synonyms.forEach { item ->
                        SynonymExampleItem(item, onWordClick, result.examplesLoading)
                    }
                }
            }
        }

        if (result.nearSynonyms.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "近义词（意思相近）",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    result.nearSynonyms.forEach { item ->
                        SynonymExampleItem(item, onWordClick, result.examplesLoading)
                    }
                }
            }
        }

        if (result.definitions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "英文释义",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    result.definitions.forEach { d ->
                        val prefix = d.partOfSpeech?.let { "[" + it + "] " } ?: ""
                        Text(
                            "• " + prefix + d.text,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

/** 单个同义词/近义词：单词（可点击再查）+ 中文意思 + 英文例句 + 中文翻译 */
@Composable
private fun SynonymExampleItem(
    item: WordWithExample,
    onWordClick: (String) -> Unit,
    examplesLoading: Boolean
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            item.word,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onWordClick(item.word) }
        )
        if (!item.meaningZh.isNullOrBlank()) {
            Text(
                "意思：" + item.meaningZh,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (examplesLoading && item.exampleEn == null && item.meaningZh == null) {
            Text(
                "例句加载中…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (!item.exampleEn.isNullOrBlank()) {
            Text(
                "例句：" + item.exampleEn,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                item.exampleZh ?: "（暂无中文翻译）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "（未找到例句）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryContent(
    history: List<String>,
    onWordClick: (String) -> Unit,
    onOpenOcr: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("最近查询", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (history.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("输入单词后按回车，即可查询中文意思、英文释义、同义词和近义词（每个词都带中英文例句）。")
                    Spacer(Modifier.height(8.dp))
                    Text("如果单词在纸质书上，点右上角相机图标拍照识别，然后点击照片上的单词即可自动查询。")
                }
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                history.forEach { word ->
                    AssistChip(
                        onClick = { onWordClick(word) },
                        label = { Text(word) }
                    )
                }
            }
        }
    }
}
