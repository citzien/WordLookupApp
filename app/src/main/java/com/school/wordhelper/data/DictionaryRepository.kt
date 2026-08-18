package com.school.wordhelper.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class WordResult(
    val word: String,
    val phonetic: String = "",
    val chinese: String? = null,
    val definitions: List<Definition> = emptyList(),
    val synonyms: List<WordWithExample> = emptyList(),
    val nearSynonyms: List<WordWithExample> = emptyList(),
    /** true 表示例句/中文意思还在后台加载 */
    val examplesLoading: Boolean = false
)

data class Definition(
    val partOfSpeech: String?,
    val text: String
)

/** 一个同义词/近义词，以及它的中文意思、英文例句 + 中文翻译 */
data class WordWithExample(
    val word: String,
    val exampleEn: String? = null,
    val exampleZh: String? = null,
    val meaningZh: String? = null
)

private data class ExampleSentence(
    val word: String,
    val en: String,
    val zh: String?,
    val zhSimplified: Boolean
)

private data class WordDetail(
    val word: String,
    val en: String?,
    val zh: String?,
    val meaningZh: String?
)

private data class ZhPick(
    val text: String,
    val simplified: Boolean
)

private data class DefinitionsBundle(
    val phonetic: String,
    val definitions: List<Definition>
)

/**
 * 免费 API 组合：
 *  - 同义词：Datamuse rel_syn
 *  - 近义词：Datamuse ml（意思相近的词）
 *  - 例句：Tatoeba 语料库（限流参数 + 简体优先）
 *  - 中文翻译：MyMemory（带内存缓存）
 *  - 英文释义/音标：Free Dictionary API (dictionaryapi.dev)
 *
 * 采用两段式加载：
 *  1. lookup() 只取主体结果（翻译/释义/同义词/近义词），约 1~2 秒
 *  2. enrichExamples() 后台补充每个词的例句和中文意思
 */
class DictionaryRepository {

    private val cache = ConcurrentHashMap<String, WordResult>()
    private val translationCache = ConcurrentHashMap<String, String>()

    /** 第一阶段：主体结果。重复查询直接返回缓存（含例句） */
    suspend fun lookup(word: String): WordResult {
        cache[word]?.let { return it }
        val base = queryBase(word)
        return base.copy(examplesLoading = true)
    }

    /** 第二阶段：补充每个同义词/近义词的例句和中文意思 */
    suspend fun enrichExamples(word: String, base: WordResult): WordResult {
        if (!base.examplesLoading) return base
        val words = (base.synonyms + base.nearSynonyms).map { it.word }.distinct()
        val details = fetchDetails(words)
        val enriched = base.copy(
            synonyms = base.synonyms.map { w ->
                val d = details[w.word]
                WordWithExample(w.word, d?.en, d?.zh, d?.meaningZh)
            },
            nearSynonyms = base.nearSynonyms.map { w ->
                val d = details[w.word]
                WordWithExample(w.word, d?.en, d?.zh, d?.meaningZh)
            },
            examplesLoading = false
        )
        cache[word] = enriched
        return enriched
    }

    private suspend fun queryBase(word: String): WordResult = coroutineScope {
        val encoded = URLEncoder.encode(word, "UTF-8")
        val chineseDeferred = async { fetchChinese(word) }
        val defsDeferred = async { fetchDefinitions(encoded) }
        val synonymsDeferred = async { fetchSynonyms(encoded) }
        val nearDeferred = async { fetchNearSynonyms(encoded) }

        val synonyms = synonymsDeferred.await().take(5)
        val nearAll = nearDeferred.await()
            .filter { it != word && it !in synonyms }
            .take(5)
        val definitionsBundle = defsDeferred.await()

        WordResult(
            word = word,
            chinese = chineseDeferred.await(),
            phonetic = definitionsBundle.phonetic,
            definitions = definitionsBundle.definitions,
            synonyms = synonyms.map { WordWithExample(it) },
            nearSynonyms = nearAll.map { WordWithExample(it) }
        )
    }

    /** MyMemory 翻译（带缓存，返回简体中文） */
    private suspend fun translateZh(text: String): String? {
        translationCache[text]?.let { return it }
        val result = try {
            val url = "https://api.mymemory.translated.net/get?q=" +
                URLEncoder.encode(text, "UTF-8") + "&langpair=en|zh-CN"
            val json = httpGet(url)
            val obj = JSONObject(json)
            val t = obj.optJSONObject("responseData")?.optString("translatedText").orEmpty()
            t.takeIf { it.isNotBlank() && !it.contains("MYMEMORY WARNING", ignoreCase = true) }
        } catch (e: Exception) {
            null
        }
        if (!result.isNullOrBlank()) translationCache[text] = result
        return result
    }

    /** 中文意思：国内词典接口（快）→ MyMemory 兜底 */
    private suspend fun fetchChinese(word: String): String? = fetchChineseMeaning(word)

    /** 中文意思/释义，带缓存：金山词霸 → 有道词典 → MyMemory */
    private suspend fun fetchChineseMeaning(text: String): String? {
        translationCache[text]?.let { return it }
        val result = try {
            icibaMeaning(text)
        } catch (e: Exception) {
            null
        } ?: try {
            youdaoMeaning(text)
        } catch (e: Exception) {
            null
        } ?: translateZh(text)
        if (!result.isNullOrBlank()) translationCache[text] = result
        return result
    }

    /** 金山词霸词典接口（国内直连，约 0.1 秒） */
    private suspend fun icibaMeaning(word: String): String? {
        val url = "https://dict-mobile.iciba.com/interface/index.php?c=word&m=getsuggest&nums=10&is_need_mean=1&word=" +
            URLEncoder.encode(word, "UTF-8")
        val json = httpGet(url)
        val obj = JSONObject(json)
        val arr = obj.optJSONArray("message") ?: return null
        val first = arr.optJSONObject(0) ?: return null
        return first.optString("paraphrase").takeIf { it.isNotBlank() }
    }

    /** 有道词典 suggest 接口（国内直连，约 0.1 秒） */
    private suspend fun youdaoMeaning(word: String): String? {
        val url = "https://dict.youdao.com/suggest?num=5&ver=2.0&doctype=json&q=" +
            URLEncoder.encode(word, "UTF-8")
        val json = httpGet(url)
        val obj = JSONObject(json)
        val entries = obj.optJSONObject("data")?.optJSONArray("entries") ?: return null
        val first = entries.optJSONObject(0) ?: return null
        return first.optString("explain").takeIf { it.isNotBlank() }
    }

    private suspend fun fetchDefinitions(encoded: String): DefinitionsBundle = try {
        val json = httpGet("https://api.dictionaryapi.dev/api/v2/entries/en/" + encoded)
        val arr = JSONArray(json)
        val entry = arr.optJSONObject(0) ?: return DefinitionsBundle("", emptyList())

        val phonetic = entry.optString("phonetic").ifBlank {
            entry.optJSONArray("phonetics")?.let { phonetics ->
                (0 until phonetics.length()).firstNotNullOfOrNull { i ->
                    phonetics.optJSONObject(i)?.optString("text")?.takeIf { it.isNotBlank() }
                }
            }.orEmpty()
        }

        val definitions = mutableListOf<Definition>()
        val meanings = entry.optJSONArray("meanings")
        if (meanings != null) {
            for (i in 0 until meanings.length()) {
                if (definitions.size >= 4) break
                val meaning = meanings.optJSONObject(i) ?: continue
                val pos = meaning.optString("partOfSpeech").ifBlank { null }
                val defs = meaning.optJSONArray("definitions") ?: continue
                for (j in 0 until defs.length()) {
                    if (definitions.size >= 4) break
                    val text = defs.optJSONObject(j)?.optString("definition").orEmpty()
                    if (text.isNotBlank()) definitions.add(Definition(pos, text))
                }
            }
        }
        DefinitionsBundle(phonetic, definitions)
    } catch (e: Exception) {
        DefinitionsBundle("", emptyList())
    }

    private suspend fun fetchSynonyms(encoded: String): List<String> = try {
        val json = httpGet("https://api.datamuse.com/words?rel_syn=" + encoded + "&max=15")
        parseWordList(json)
    } catch (e: Exception) {
        emptyList()
    }

    private suspend fun fetchNearSynonyms(encoded: String): List<String> = try {
        val json = httpGet("https://api.datamuse.com/words?ml=" + encoded + "&max=15")
        parseWordList(json)
    } catch (e: Exception) {
        emptyList()
    }

    private fun parseWordList(json: String): List<String> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.optString("word")?.takeIf { it.isNotBlank() }
        }
    }

    /** 全部词并发抓取例句 + 中文意思（最多 10 路） */
    private suspend fun fetchDetails(words: List<String>): Map<String, WordDetail> = coroutineScope {
        val semaphore = Semaphore(10)
        val deferred = words.map { w ->
            async { semaphore.withPermit { fetchWordDetail(w) } }
        }
        deferred.mapNotNull { it.await() }.associateBy { it.word }
    }

    private suspend fun fetchWordDetail(word: String): WordDetail? {
        val example = fetchExample(word)
        var zh = example?.zh
        if (example != null && example.en != null && !example.zhSimplified) {
            // 例句翻译不是简体时，用翻译接口强制转成简体
            val t = translateZh(example.en)
            if (!t.isNullOrBlank()) zh = t
        }
        val meaning = fetchChineseMeaning(word)
        if (example == null && meaning == null) return null
        return WordDetail(word, example?.en, zh, meaning)
    }

    private suspend fun fetchExample(word: String): ExampleSentence? {
        return try {
            val url = "https://tatoeba.org/en/api_v0/search?from=eng&query=" +
                URLEncoder.encode(word, "UTF-8") + "&to=cmn&trans_filter=limit&limit=1"
            val json = httpGet(url)
            val results = JSONObject(json).optJSONArray("results") ?: return null
            for (i in 0 until results.length()) {
                val s = results.optJSONObject(i) ?: continue
                val text = s.optString("text").trim()
                if (text.isBlank() || !text.contains(word, ignoreCase = true)) continue
                val pick = findChineseTranslation(s.optJSONArray("translations"))
                if (pick != null) {
                    return ExampleSentence(word, text, pick.text, pick.simplified)
                }
                return ExampleSentence(word, text, null, false)
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** 提取中文翻译：简体（Hans）优先，繁体兜底 */
    private fun findChineseTranslation(translations: JSONArray?): ZhPick? {
        if (translations == null) return null
        var fallback: ZhPick? = null
        for (i in 0 until translations.length()) {
            val group = translations.optJSONArray(i) ?: continue
            for (j in 0 until group.length()) {
                val t = group.optJSONObject(j) ?: continue
                val lang = t.optString("lang")
                val langTag = t.optString("lang_tag")
                val script = t.optString("script")
                if (lang != "cmn" && langTag != "cmn") continue
                val text = t.optString("text").trim()
                if (text.isBlank()) continue
                if (langTag == "zh-Hans" || script == "Hans") return ZhPick(text, true)
                if (fallback == null) fallback = ZhPick(text, false)
            }
        }
        return fallback
    }
}
