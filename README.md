# 单词助手（Word Lookup App）

一个面向安卓手机的单词查询应用，专门解决「纸质书上的单词」：
拍照 → 裁剪 → OCR 识别 → 点击照片上的单词 → 自动查询中文意思、英文释义、同义词（含例句）、近义词（含例句）。

## 下载安装

最新版 APK（v1.10）在 GitHub Release 页面：
https://github.com/citzien/WordLookupApp/releases

## 功能

- 手动输入单词查询：中文意思、英文释义 + 音标
- 同义词 / 近义词分别列出，每个词都带**中文意思 + 英文例句 + 中文翻译**
- 拍照 / 相册 → **裁剪**（九宫格快捷选区 + 四角四边拖拽）→ OCR
- OCR 双引擎可选：**百度智能云** / **腾讯云**（都在设置页填写密钥）
- **去除线条**预处理：可清除格子边框、以及从文字中间穿过的横线
- 点词即查：识别出的单词在原图上画框，点击照片上的单词直接查询，可返回继续识别
- 例句**边查边显示**：每个词的例句查完一条显示一条
- 例句翻译加速：可选配置**百度翻译开放平台**（更快、保证简体）
- 最近查询历史（最近 12 个词）
- ⚠️ **OCR 识别目前还不稳定**（对带横线/格子的图片效果一般），正在优化中

## 技术栈

| 部分 | 方案 |
| --- | --- |
| 语言/UI | Kotlin + Jetpack Compose (Material 3) |
| OCR | 百度智能云（高精度含位置版，额度用完自动降级） / 腾讯云（通用印刷体） |
| 同义词 | Datamuse API（rel_syn） |
| 近义词 | Datamuse API（ml，意思相近的词） |
| 例句 | Tatoeba 语料库（含中文翻译，简体优先） |
| 中文释义 | 金山词霸 → 有道词典 → 百度翻译 → MyMemory（按顺序回退） |
| 英文释义/音标 | Free Dictionary API（dictionaryapi.dev） |

除云端 OCR 和可选的百度翻译外，其余数据源全部免费且无需注册。

## 配置（App 内 ⚙️ 设置）

### 百度 OCR（推荐先试）
1. https://console.bce.baidu.com 注册登录并**实名认证**
2. 搜索进入「文字识别」→「立即开通」
3. 「应用列表」→「创建应用」→ 勾选文字识别接口 → 创建
4. 复制 **API Key / Secret Key** 填进 App 设置

### 腾讯云 OCR（备用引擎）
1. https://console.cloud.tencent.com 注册登录并实名认证
2. 搜索「文字识别」→ 开通「通用印刷体识别」（免费额度每月约 1000 次）
3. 搜索「访问管理 CAM」→「API 密钥管理」→ 新建密钥
4. 复制 **SecretId / SecretKey** 填进 App 设置，并把引擎切到「腾讯云 OCR」

### 百度翻译（可选，例句翻译加速）
1. https://fanyi-api.baidu.com 开通百度翻译开放平台（免费额度）
2. 创建应用，拿到 **APP ID** 和**密钥**
3. 填到 App 设置页底部「例句翻译加速」

## 打开与运行

1. 安装 Android Studio（Ladybug 或更新）
2. File → Open，选择 WordLookupApp 文件夹
3. 等待 Gradle 同步完成（已配置 Gradle 8.10.2 wrapper + 国内镜像；JDK 17 已写入 gradle.properties）
4. 用安卓真机运行（Run ▶），或 构建 → 构建 APK(s) 直接出安装包
5. 产物位置：app/build/outputs/apk/debug/app-debug.apk

## 权限说明

- 只需要 INTERNET 权限（查询 API + 云端 OCR）
- 拍照调用系统相机 App，不需要相机权限；相册使用系统照片选择器，不需要存储权限

## 常见问题

- **OCR 识别还不稳定**：当前版本对带横线/格子的图片效果一般，正在优化中；可尝试「裁剪对准单词 + 去除线条 + 切换引擎」
- 识别报「还没有配置密钥」：去 OCR 页右上角 ⚙️ 设置填写所选引擎的密钥
- 百度报错 6：应用没勾选文字识别接口，去控制台「应用列表 → 编辑应用」勾选并保存
- 腾讯云报 language 不支持：已修复（v1.8 起不再传语言参数）
- 中文意思空白：词典接口偶发失败，点「重试」即可
- 某几个词没有例句：Tatoeba 语料库里没有该词的例句，属正常现象
- 生僻词没有英文释义：dictionaryapi.dev 查不到，只显示中文意思和同义词

## 目录结构

    WordLookupApp/
    ├── app/
    │   ├── build.gradle.kts
    │   └── src/main/
    │       ├── AndroidManifest.xml
    │       ├── res/                 # 图标、主题、FileProvider 配置
    │       └── java/com/school/wordhelper/
    │           ├── MainActivity.kt
    │           ├── data/            # Datamuse、Tatoeba、词典/翻译、百度翻译（TranslateConfig）
    │           ├── ocr/             # 百度/腾讯 OCR 客户端、去线（LineRemover）、裁剪
    │           └── ui/              # Compose 界面（搜索页、OCR 页、ViewModel）
    └── gradle/libs.versions.toml    # 依赖版本统一管理

## 版本历史

- v1.10 百度翻译例句加速（可选）、例句边查边显示、OCR 标注「暂时不稳定」
- v1.9  新增去除「从文字中间穿过的横线」
- v1.8  新增腾讯云 OCR 引擎、识别词标点过滤修复
- v1.6  新增裁剪功能
- v1.5  新增去除格子线、识别分辨率提升
- v1.1  国产 OCR（百度）替换 ML Kit
