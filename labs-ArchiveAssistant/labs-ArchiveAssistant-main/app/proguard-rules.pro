# LiteRT-LM SDK (no consumer rules shipped)
-keep class com.google.ai.edge.litertlm.** { *; }

# Local LLM engine interface (protect from R8 renaming)
-keep class com.lyihub.archiveassistant.domain.LocalLlmEngine { *; }
-keep class com.lyihub.archiveassistant.data.LiteRtLmEngineAdapter { *; }