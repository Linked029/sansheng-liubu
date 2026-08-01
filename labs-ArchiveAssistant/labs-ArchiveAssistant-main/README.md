# 聚合拾遗

聚合拾遗是一个面向个人资料归档和知识整理的 Android 应用。项目使用 Kotlin 与 Jetpack Compose 构建，包名为 `com.lyihub.archiveassistant`，当前以“六部”作为固定分类体系，把网页、文档和手动录入内容整理到不同主题下。

项目仍处在原型和功能验证阶段，README 中列出的限制不是使用说明的补充，而是当前实现状态的一部分。

## 当前能力

- 固定六部分类：应用内置吏部、户部、礼部、兵部、刑部、工部等主题，当前分类不可新增、重命名或删除。
- 资料归档：支持维护知识条目标题、摘要、正文、来源链接、文档格式和本地文件信息。
- 网页与文档输入：代码中包含网页抓取、文档内容提取、URI 导入、FileProvider 和本地文件处理链路。
- 智能归纳基础链路：支持配置远程 AI 引擎，并包含 OpenAI-compatible、OpenAI Responses、Gemini 等请求形态；也包含本地模型推理服务与 LiteRT LM 适配代码。
- 本地持久化：通过 DataStore Preferences 保存应用数据和 AI 引擎设置。
- Compose 界面：包含首页、分类详情、设置页、条目弹窗和折页式奏折阅读/审阅界面。

## 尚未完成与已知问题

- AI 三省六部推荐尚未实现：当前项目有 AI 归纳与分类提示词基础，但还没有完成面向“三省六部”体系的自动推荐、排序或决策流。不要把现有智能归纳视为完整推荐系统。
- 页面滑动响应偏慢：部分页面，尤其是复杂折页阅读/审阅界面，存在滑动、翻页或手势响应不够跟手的问题，还需要继续做渲染、手势处理和重组性能优化。
- 六部分类当前固定：主题管理入口存在，但实际分类体系被固定，不能在应用内自由新增、改名或删除。
- AI 能力依赖配置：远程 AI 需要用户自行配置 Endpoint、模型和 API Key；本地 AI 需要设备、模型文件和推理后端满足运行条件。
- 项目尚未按正式产品标准收尾：仍需要补齐异常态、性能优化、更多真机测试和发布流程。

## 技术栈

- Kotlin
- Android Gradle Plugin
- Jetpack Compose / Material 3
- Android DataStore Preferences
- OkHttp
- Jsoup
- PDFBox Android
- LiteRT LM Android

## 环境要求

- Android Studio 或可用的 Android SDK/Gradle 环境
- JDK 11 兼容环境
- Android SDK：项目 `compileSdk` 为 36，`minSdk` 为 31

## 构建与运行

克隆项目后，在仓库根目录执行：

```bash
./gradlew assembleDebug
```

安装到已连接设备或模拟器：

```bash
./gradlew installDebug
```

运行单元测试：

```bash
./gradlew testDebugUnitTest
```

运行 Android Instrumentation 测试需要连接设备或启动模拟器：

```bash
./gradlew connectedDebugAndroidTest
```

## 项目结构

```text
app/src/main/java/com/lyihub/archiveassistant/
  app/        应用入口与整体组装
  data/       数据存取、网页抓取、文档提取、AI 请求与模型下载
  domain/     知识条目、六部主题、AI 设置和归纳接口等领域模型
  service/    本地推理前台服务与连接封装
  state/      应用状态管理与业务动作
  ui/         Compose 界面、主题、组件和自定义阅读视图
  util/       通用工具
```

## 开发重点

当前优先级建议如下：

1. 完成 AI 三省六部推荐：明确输入、推荐目标、解释信息、失败态和人工确认流程。
2. 优化滑动与翻页性能：重点检查复杂自绘视图、动画、触摸事件处理和 Compose 重组边界。
3. 稳定导入与归纳流程：覆盖网页、Markdown、PDF、本地文件和剪贴板输入的异常处理。
4. 补齐发布前验证：增加真机性能测试、端到端用例、权限说明和发布配置。

## 许可

项目源代码按 GNU General Public License v3.0 or later 授权发布，详见 [LICENSE](LICENSE)。

第三方依赖、字体、图片、PDF 和 mock 数据保留各自原始授权。已知依赖与资源核验状态记录在 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。其中部分打包资源仍缺少完整来源和授权记录，正式发布前需要完成素材来源核验或替换。
