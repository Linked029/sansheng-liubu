package com.lyihub.archiveassistant.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyihub.archiveassistant.R
import com.lyihub.archiveassistant.data.copyUriToFile
import com.lyihub.archiveassistant.data.importFileName
import com.lyihub.archiveassistant.data.resolveDisplayName
import com.lyihub.archiveassistant.data.uniqueImportFile
import com.lyihub.archiveassistant.domain.ContentType
import com.lyihub.archiveassistant.domain.DocumentFormat
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.domain.Topic
import com.lyihub.archiveassistant.state.AddItemDialogPrefill
import com.lyihub.archiveassistant.ui.components.ActionButton
import com.lyihub.archiveassistant.ui.components.ArchiveChip
import com.lyihub.archiveassistant.ui.components.ArchiveDialog
import com.lyihub.archiveassistant.ui.components.ArchiveDialogAction
import com.lyihub.archiveassistant.ui.components.PaneContainer
import com.lyihub.archiveassistant.ui.components.XuanPaperBackground
import com.lyihub.archiveassistant.ui.theme.ImperialCinnabar
import com.lyihub.archiveassistant.ui.theme.ImperialDisplayFont
import com.lyihub.archiveassistant.ui.theme.ImperialParchment
import com.lyihub.archiveassistant.util.toChineseCount
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val DetailBorder = Color.Black
private val DetailPaperDeep = ImperialParchment
private val DetailInk = Color.Black
private val DetailCinnabar = ImperialCinnabar
private val DetailCardCorner = 9.dp
private val DetailTagChipShape = ArchiveFlatCutShape

private const val FileProviderAuthoritySuffix = ".fileprovider"

private fun markdownFileName(title: String): String {
  val baseName =
    title
      .lineSequence()
      .firstOrNull()
      .orEmpty()
      .trim()
      .take(48)
      .replace(Regex("[\\\\/:*?\"<>|]+"), "-")
      .ifBlank { "clipboard-note" }
  return if (baseName.endsWith(".md", ignoreCase = true)) baseName else "$baseName.md"
}

private fun writeMarkdownPrefillFile(context: Context, title: String, content: String): File? {
  return try {
    val itemsDir = File(context.filesDir, "items").also { it.mkdirs() }
    val dest = uniqueImportFile(itemsDir, markdownFileName(title))
    dest.writeText(content)
    dest
  } catch (_: Exception) {
    null
  }
}

@Composable
fun DetailPane(
  topic: Topic,
  items: List<KnowledgeItem>,
  searchQuery: String,
  onBack: () -> Unit,
  onItemClick: (String) -> Unit,
  modifier: Modifier = Modifier,
  showBackButton: Boolean = true,
) {
  val horizontalPadding = 24.dp
  val topPadding = 56.dp
  val headerOverlayHeight = if (items.isEmpty()) 100.dp else 124.dp
  val listState = rememberLazyListState()
  val density = LocalDensity.current
  val headerFadeDistancePx = with(density) { 58.dp.toPx() }
  val headerAlpha =
    if (listState.firstVisibleItemIndex > 0) {
      0f
    } else {
      (1f - listState.firstVisibleItemScrollOffset / headerFadeDistancePx).coerceIn(0f, 1f)
    }
  val availableTags =
    remember(items) {
      items.flatMap(::articleTags).distinct()
    }
  var activeTags by remember(availableTags) { mutableStateOf(availableTags.toSet()) }
  val filteredItems =
    remember(items, activeTags) {
      if (availableTags.isEmpty() || activeTags.isEmpty()) {
        emptyList()
      } else {
        items.filter { item -> articleTags(item).any { tag -> tag in activeTags } }
      }
    }

  PaneContainer(modifier = modifier.testTag("detail-pane")) {
    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        overscrollEffect = null,
        contentPadding =
          PaddingValues(
            start = horizontalPadding,
            top = topPadding + headerOverlayHeight,
            end = horizontalPadding,
            bottom = 28.dp,
          ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (items.isEmpty()) {
          item {
            EmptyMemorialShelf(modifier = Modifier.fillMaxWidth())
          }
        } else {
          item {
            if (filteredItems.isEmpty()) {
              EmptyFilteredShelf(modifier = Modifier.fillMaxWidth())
            } else {
              ArticleMasonryGrid(
                items = filteredItems,
                searchQuery = searchQuery,
                onItemClick = onItemClick,
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }
      }
      if (headerAlpha > 0.02f) {
        Column(
          modifier =
            Modifier.align(Alignment.TopStart)
              .padding(start = horizontalPadding, top = topPadding, end = horizontalPadding)
              .fillMaxWidth()
              .graphicsLayer { alpha = headerAlpha },
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          DetailCourtHeader(
            topic = topic,
            itemCount = items.size,
            onBack = onBack,
            modifier = Modifier.fillMaxWidth(),
            showBackButton = showBackButton,
          )
          if (items.isNotEmpty()) {
            ArticleFilterBar(
              tags = availableTags,
              activeTags = activeTags,
              onToggleTag = { tag ->
                activeTags =
                  if (tag in activeTags) {
                    activeTags - tag
                  } else {
                    activeTags + tag
                  }
              },
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ArticleFilterBar(
  tags: List<String>,
  activeTags: Set<String>,
  onToggleTag: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = "筛选：",
      style = MaterialTheme.typography.labelLarge,
      color = DetailInk,
      maxLines = 1,
    )
    LazyRow(
      modifier = Modifier.weight(1f),
      overscrollEffect = null,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      items(tags) { tag ->
        ArticleTagChip(
          text = tag,
          selected = tag in activeTags,
          fixedColor = tagAccentColor(tag),
          onClick = { onToggleTag(tag) },
        )
      }
    }
  }
}

@Composable
private fun EmptyFilteredShelf(modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .height(92.dp)
        .clip(RoundedCornerShape(DetailCardCorner))
        .background(Color.White.copy(alpha = 0.38f)),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = "请选择至少一个标签",
      style = MaterialTheme.typography.bodyMedium,
      color = DetailInk.copy(alpha = 0.62f),
    )
  }
}

@Composable
private fun ArticleMasonryGrid(
  items: List<KnowledgeItem>,
  searchQuery: String,
  onItemClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val columns = distributeArticleCards(items)
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    repeat(2) { column ->
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        columns[column].forEach { card ->
          MemorialArticleCard(
            item = card.item,
            searchQuery = searchQuery,
            onClick = { onItemClick(card.item.id) },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}

private data class MasonryArticleCard(val item: KnowledgeItem)

private fun distributeArticleCards(items: List<KnowledgeItem>): List<List<MasonryArticleCard>> {
  val columns = List(2) { mutableListOf<MasonryArticleCard>() }
  val heights = FloatArray(2)
  items.forEach { item ->
    val column = if (heights[0] <= heights[1]) 0 else 1
    columns[column] += MasonryArticleCard(item)
    heights[column] += estimateArticleHeight(item)
  }
  return columns
}

private fun estimateArticleHeight(item: KnowledgeItem): Float {
  val textWeight =
    1f +
      (item.title.length / 18f).coerceAtMost(2.2f) +
      (item.summary.ifBlank { item.fullText }.length / 72f).coerceAtMost(3.6f)
  val imageWeight =
    if (item.imageResName != null) 1.45f / articleImageAspectRatio(item).coerceAtLeast(0.58f)
    else 0f
  return textWeight + imageWeight
}

private fun articleImageAspectRatio(item: KnowledgeItem): Float {
  return when (positiveMod(item.id.hashCode(), 5)) {
    0 -> 1.18f
    1 -> 0.92f
    2 -> 1.34f
    3 -> 0.78f
    else -> 1.05f
  }
}

@Composable
private fun DetailCourtHeader(
  topic: Topic,
  itemCount: Int,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  showBackButton: Boolean,
) {
  PaneHeroHeader(
    title = topic.title,
    description = "${itemCount.toChineseCount()}篇 · ${folderDescription(topic)}",
    showBackButton = showBackButton,
    onBack = onBack,
    modifier = modifier.testTag("detail-summary"),
  )
}

private fun folderDescription(topic: Topic): String {
  return folderVisualForTopicId(topic.id).description
}

@Composable
private fun MemorialArticleCard(
  item: KnowledgeItem,
  searchQuery: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val cardShape = ArchiveFlatCutShape
  val imageShape = ArchiveFlatCutShape
  val tags = articleTags(item)
  val imageResId = localArticleImageResId(item.imageResName)
  val imageLayout =
    if (imageResId != null) {
      localArticleImageLayout(imageResId)
    } else {
      null
    }
  Box(
    modifier =
      modifier
        .shadow(8.dp, cardShape, clip = false)
        .clip(cardShape)
        .background(DetailPaperDeep, cardShape)
        .clickable(onClick = onClick)
        .testTag("knowledge-card-${item.id}")
  ) {
    Image(
      painter = painterResource(id = R.drawable.home_search_tile),
      contentDescription = null,
      modifier = Modifier.matchParentSize(),
      contentScale = ContentScale.Crop,
    )
    Box(modifier = Modifier.matchParentSize().background(Color.White.copy(alpha = 0.2f)))
    Column(modifier = Modifier.fillMaxWidth()) {
      if (imageResId != null) {
        Box(modifier = Modifier.fillMaxWidth().clip(imageShape)) {
          Image(
            painter = painterResource(id = imageResId),
            contentDescription = null,
            modifier =
              Modifier.fillMaxWidth()
                .aspectRatio(imageLayout?.aspectRatio ?: articleImageAspectRatio(item))
                .clip(imageShape),
            contentScale =
              if (imageLayout?.cropToMaxHeight == true) ContentScale.Crop else ContentScale.Fit,
          )
        }
      }
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .padding(
              start = 7.dp,
              top = if (imageResId != null) 6.dp else 8.dp,
              end = 7.dp,
              bottom = 8.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        Text(
          text =
            buildHighlightedText(
              text = item.title,
              query = searchQuery,
              highlightColor = DetailInk,
              highlightBgColor = Color(0xFFF2D88A).copy(alpha = 0.84f),
            ),
          style = MaterialTheme.typography.titleSmall,
          color = DetailInk,
          fontWeight = FontWeight.Normal,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 3.dp, bottom = 2.dp),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          tags.forEachIndexed { index, tag ->
            ArticleTagChip(
              text = tag,
              selected = true,
              fixedColor = tagAccentColor(tag),
              onClick = null,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun localArticleImageResId(imageResName: String?): Int? {
  val context = LocalContext.current
  return remember(imageResName, context) {
    imageResName
      ?.let { context.resources.getIdentifier(it, "drawable", context.packageName) }
      ?.takeIf { it != 0 }
  }
}

@Composable
private fun localArticleImageLayout(resId: Int): ArticleImageLayout {
  val context = LocalContext.current
  return remember(resId, context) {
    val options =
      BitmapFactory.Options().apply {
        inJustDecodeBounds = true
      }
    context.resources.openRawResource(resId).use { input ->
      BitmapFactory.decodeStream(input, null, options)
    }
    val width = options.outWidth.takeIf { it > 0 } ?: return@remember ArticleImageLayout(1f, false)
    val height =
      options.outHeight.takeIf { it > 0 } ?: return@remember ArticleImageLayout(1f, false)
    val originalRatio = width.toFloat() / height.toFloat()
    val minRatio = 9f / 16f
    ArticleImageLayout(
      aspectRatio = originalRatio.coerceAtLeast(minRatio),
      cropToMaxHeight = originalRatio < minRatio,
    )
  }
}

private data class ArticleImageLayout(
  val aspectRatio: Float,
  val cropToMaxHeight: Boolean,
)

private fun articleTags(item: KnowledgeItem): List<String> {
  item.fullText
    .lineSequence()
    .firstOrNull { it.startsWith("标签：") }
    ?.removePrefix("标签：")
    ?.split("·", "、", ",", "，")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.distinct()
    ?.take(3)
    ?.takeIf { it.isNotEmpty() }
    ?.let {
      return it
    }

  val seed = (item.title.length + item.summary.length + item.id.length).coerceAtLeast(0)
  return listOf(item.documentFormat?.label ?: item.contentType.label)
    .plus(fallbackTagLabels(seed))
    .distinct()
    .take(3)
}

@Composable
private fun ArticleTagChip(
  text: String,
  modifier: Modifier = Modifier,
  selected: Boolean,
  fixedColor: Color,
  onClick: (() -> Unit)?,
) {
  ArchiveChip(
    label = text,
    selected = selected,
    accentColor = fixedColor,
    modifier = modifier,
    shape = DetailTagChipShape,
    textStyle =
      MaterialTheme.typography.labelSmall.copy(
        fontFamily = ImperialDisplayFont,
        fontSize = 10.sp,
        lineHeight = 10.sp,
      ),
    onClick = onClick,
  )
}

@Composable
private fun EmptyMemorialShelf(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.background(DetailPaperDeep).border(1.dp, DetailBorder.copy(alpha = 0.78f)),
    contentAlignment = Alignment.Center,
  ) {
    Image(
      painter = painterResource(id = R.drawable.memorial_xuan_paper),
      contentDescription = null,
      modifier = Modifier.matchParentSize(),
      contentScale = ContentScale.Crop,
      alpha = 0.68f,
    )
    XuanPaperBackground(
      modifier = Modifier.matchParentSize(),
      textureAlpha = 0.68f,
      veilAlpha = 0.9f,
    ) {}
    Column(
      modifier = Modifier.padding(36.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Default.Add,
        contentDescription = null,
        tint = DetailCinnabar.copy(alpha = 0.76f),
      )
      Text(
        text = "此档暂无奏折",
        style = MaterialTheme.typography.headlineSmall,
        color = DetailInk,
        fontWeight = FontWeight.Normal,
      )
      Text(
        text = "可由中书录入，门下筛选后归入尚书档案。",
        style = MaterialTheme.typography.bodyMedium,
        color = DetailInk.copy(alpha = 0.68f),
      )
    }
  }
}

@Composable
private fun DecorativeCorner(
  modifier: Modifier = Modifier,
  rotation: Float,
) {
  Image(
    painter = painterResource(id = R.drawable.memorial_cover_corner),
    contentDescription = null,
    modifier = modifier.padding(8.dp).size(30.dp).graphicsLayer(rotationZ = rotation),
    contentScale = ContentScale.Fit,
    alpha = 0.58f,
    colorFilter = ColorFilter.tint(DetailCinnabar.copy(alpha = 0.76f)),
  )
}

@Composable
private fun DecorativeDetailImage(
  modifier: Modifier = Modifier,
  alpha: Float,
  tint: Color,
) {
  Image(
    painter = painterResource(id = R.drawable.imperial_ornament_pattern),
    contentDescription = null,
    modifier = modifier,
    contentScale = ContentScale.Crop,
    alpha = alpha,
    colorFilter = ColorFilter.tint(tint),
  )
}

@Composable
private fun FilterChip(
  label: String,
  selected: Boolean,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  val accent = tagAccentColor(label)
  ArchiveChip(
    label = label,
    selected = selected,
    enabled = enabled,
    accentColor = accent,
    shape = MaterialTheme.shapes.small,
    height = 32.dp,
    textStyle = MaterialTheme.typography.labelMedium,
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    selectedBorderAlpha = 0.68f,
    unselectedBackgroundColor = Color(0xFFE3E0D8).copy(alpha = 0.46f),
    unselectedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
    selectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
    onClick = onClick,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemDialog(
  onDismiss: () -> Unit,
  onConfirm:
    (String, String, ContentType, String?, String, Boolean, DocumentFormat?, String?) -> Unit,
  validationMessage: String?,
  initialItem: KnowledgeItem? = null,
  prefill: AddItemDialogPrefill? = null,
  topics: List<Topic> = emptyList(),
  initialTopicId: String? = null,
) {
  val isEditMode = initialItem != null
  val effectivePrefill = prefill?.takeIf { initialItem == null }
  val lockContentType = effectivePrefill?.lockContentType == true
  val hideSourceFilePicker = effectivePrefill != null
  val availableContentTypes =
    effectivePrefill?.availableContentTypes
      ?: listOf(ContentType.WEB_ARTICLE, ContentType.IMAGE_SCREENSHOT, ContentType.DOCUMENT)
  var topicMenuExpanded by remember { mutableStateOf(false) }
  var selectedDialogTopicId by
    remember(initialItem, initialTopicId, topics) {
      mutableStateOf(
        when {
          initialItem != null -> initialItem.topicId
          initialTopicId != null && topics.any { it.id == initialTopicId } -> initialTopicId
          else -> topics.firstOrNull()?.id.orEmpty()
        }
      )
    }
  val selectedDialogTopic = topics.firstOrNull { it.id == selectedDialogTopicId }
  var title by
    remember(initialItem, effectivePrefill) {
      mutableStateOf(initialItem?.title ?: effectivePrefill?.title ?: "")
    }
  var selectedContentType by
    remember(initialItem, effectivePrefill) {
      mutableStateOf(
        initialItem?.contentType ?: effectivePrefill?.contentType ?: ContentType.WEB_ARTICLE
      )
    }
  var url by
    remember(initialItem, effectivePrefill) {
      mutableStateOf(
        when {
          initialItem?.contentType == ContentType.WEB_ARTICLE -> initialItem.sourceUrl ?: ""
          effectivePrefill?.contentType == ContentType.WEB_ARTICLE ->
            effectivePrefill.sourceUrl ?: ""
          else -> ""
        }
      )
    }
  var textContent by
    remember(effectivePrefill) { mutableStateOf(effectivePrefill?.textContent.orEmpty()) }
  var selectedFileUri by
    remember(initialItem, effectivePrefill) {
      mutableStateOf(
        when {
          initialItem != null &&
            initialItem.contentType != ContentType.WEB_ARTICLE &&
            initialItem.sourceUrl != null -> Uri.parse(initialItem.sourceUrl)
          effectivePrefill != null &&
            effectivePrefill.contentType != ContentType.WEB_ARTICLE &&
            effectivePrefill.sourceUrl != null -> Uri.parse(effectivePrefill.sourceUrl)
          else -> null
        }
      )
    }
  var summary by remember(initialItem) { mutableStateOf(initialItem?.summary ?: "") }
  var selectedDocumentFormat by
    remember(initialItem, effectivePrefill) {
      mutableStateOf(initialItem?.documentFormat ?: effectivePrefill?.documentFormat)
    }
  var selectedFileName by
    remember(initialItem, effectivePrefill) {
      mutableStateOf(initialItem?.fileName ?: effectivePrefill?.fileName)
    }
  var selectedImageBitmap by remember {
    mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
  }
  var selectedLocalFilePath by
    remember(initialItem) {
      mutableStateOf(
        if (initialItem != null && initialItem.contentType != ContentType.WEB_ARTICLE)
          initialItem.sourceUrl
        else null
      )
    }

  val context = androidx.compose.ui.platform.LocalContext.current
  LaunchedEffect(selectedFileUri) {
    val uri = selectedFileUri
    if (uri != null && (uri.scheme == "content" || uri.scheme == "file")) {
      val file =
        withContext(Dispatchers.IO) {
          try {
            val itemsDir = File(context.filesDir, "items").also { it.mkdirs() }
            val ext =
              selectedFileName
                ?.substringAfterLast('.', "")
                ?.takeIf { it.isNotBlank() }
                ?.let { ".$it" } ?: ""
            val dest = uniqueImportFile(itemsDir, importFileName(selectedFileName, ext))
            if (copyUriToFile(context, uri, dest)) dest else null
          } catch (_: Exception) {
            null
          }
        }
      selectedLocalFilePath = file?.absolutePath
      selectedImageBitmap =
        if (file != null && selectedContentType == ContentType.IMAGE_SCREENSHOT) {
          withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
          }
        } else {
          null
        }
    } else {
      selectedLocalFilePath = null
      selectedImageBitmap = null
    }
  }

  fun detectDocumentFormat(fileName: String?): DocumentFormat? {
    val ext = fileName?.substringAfterLast('.', "")?.lowercase() ?: return null
    return DocumentFormat.entries
      .firstOrNull { it.extension.equals(".$ext", ignoreCase = true) }
      ?.takeUnless { it == DocumentFormat.UNKNOWN }
  }

  val filePickerLauncher =
    rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri?
      ->
      selectedFileUri = uri
      selectedFileName = uri?.let { resolveDisplayName(context, it) }
      if (selectedContentType == ContentType.DOCUMENT) {
        selectedDocumentFormat = detectDocumentFormat(selectedFileName)
      }
    }

  fun selectType(type: ContentType) {
    if (lockContentType && type != selectedContentType) return
    selectedContentType = type
    url =
      if (type == ContentType.WEB_ARTICLE) {
        url.ifBlank { effectivePrefill?.sourceUrl.orEmpty() }
      } else if (effectivePrefill?.sourceUrl != null) {
        url
      } else {
        ""
      }
    selectedFileUri = null
    selectedLocalFilePath = null
    selectedDocumentFormat = null
    selectedFileName = null
    if (
      type == ContentType.DOCUMENT &&
        (effectivePrefill?.title?.isNotBlank() == true || textContent.isNotBlank())
    ) {
      selectedDocumentFormat = DocumentFormat.MARKDOWN
      selectedFileName = markdownFileName(title)
    }
  }

  ArchiveDialog(
    title = if (isEditMode) "修改资料" else "新增资料",
    onDismissRequest = onDismiss,
    testTag = "add-item-dialog",
    actions = {
      ArchiveDialogAction(
        label = "取消",
        onClick = onDismiss,
      )
      ArchiveDialogAction(
        label = if (isEditMode) "保存" else "确认",
        onClick = {
          val textDocumentContent =
            textContent.takeIf { it.isNotBlank() }
              ?: effectivePrefill?.takeIf { it.sourceUrl == null && it.title.isNotBlank() }?.title
          val textDocumentFile =
            if (
              selectedContentType == ContentType.DOCUMENT &&
                selectedFileUri == null &&
                textDocumentContent != null
            ) {
              writeMarkdownPrefillFile(context, title, textDocumentContent)
            } else {
              null
            }
          val sourceUrl =
            when (selectedContentType) {
              ContentType.WEB_ARTICLE -> url.takeIf { it.isNotBlank() }
              else -> textDocumentFile?.absolutePath ?: selectedLocalFilePath
            }
          val docFormat =
            if (selectedContentType == ContentType.DOCUMENT) {
              if (textDocumentFile != null) DocumentFormat.MARKDOWN
              else selectedDocumentFormat ?: DocumentFormat.UNKNOWN
            } else null
          val fileName = if (textDocumentFile != null) textDocumentFile.name else selectedFileName
          val topicId = if (isEditMode) initialItem?.topicId.orEmpty() else selectedDialogTopicId
          onConfirm(
            topicId,
            title,
            selectedContentType,
            sourceUrl,
            summary,
            false,
            docFormat,
            fileName,
          )
        },
        primary = true,
        testTag = "add-item-confirm",
      )
    },
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      if (!isEditMode) {
        ExposedDropdownMenuBox(
          expanded = topicMenuExpanded,
          onExpandedChange = { topicMenuExpanded = it },
          modifier = Modifier.fillMaxWidth().testTag("add-item-topic-selector"),
        ) {
          OutlinedTextField(
            value = selectedDialogTopic?.title ?: "选择主题",
            onValueChange = {},
            readOnly = true,
            label = { Text("归属主题") },
            trailingIcon = {
              ExposedDropdownMenuDefaults.TrailingIcon(expanded = topicMenuExpanded)
            },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
          )
          ExposedDropdownMenu(
            expanded = topicMenuExpanded,
            onDismissRequest = { topicMenuExpanded = false },
          ) {
            topics.forEach { topic ->
              DropdownMenuItem(
                text = { Text(topic.title) },
                onClick = {
                  selectedDialogTopicId = topic.id
                  topicMenuExpanded = false
                },
              )
            }
          }
        }
      }

      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        modifier = Modifier.fillMaxWidth().testTag("add-item-title"),
        label = { Text("标题") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
          ),
      )

      OutlinedTextField(
        value = summary,
        onValueChange = { summary = it },
        modifier = Modifier.fillMaxWidth().testTag("add-item-summary"),
        label = { Text("摘要（选填）") },
        minLines = 3,
        maxLines = 6,
        shape = MaterialTheme.shapes.medium,
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
          ),
      )

      Text(
        text = "类型",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        availableContentTypes.forEach { type ->
          FilterChip(
            label = type.label,
            selected = selectedContentType == type,
            enabled = !lockContentType,
            onClick = { selectType(type) },
          )
        }
      }

      when (selectedContentType) {
        ContentType.WEB_ARTICLE -> {
          OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth().testTag("add-item-url"),
            label = { Text("链接") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
              ),
          )
        }

        ContentType.IMAGE_SCREENSHOT -> {
          selectedImageBitmap?.let { bitmap ->
            Image(
              bitmap = bitmap,
              contentDescription = "已选图片预览",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(modifier = Modifier.height(12.dp))
          }
          if (!hideSourceFilePicker) {
            ActionButton(
              label = "选择图像文件",
              onClick = { filePickerLauncher.launch("image/*") },
              testTag = "add-item-pick-image",
            )
          }
          selectedFileUri?.let { uri ->
            Text(
              text = "已选择: ${selectedFileName ?: resolveDisplayName(context, uri)}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        ContentType.DOCUMENT -> {
          if (!hideSourceFilePicker) {
            ActionButton(
              label = "选择文档",
              onClick = { filePickerLauncher.launch("*/*") },
              testTag = "add-item-pick-document",
            )
          }
          selectedFileUri?.let { uri ->
            Text(
              text = "已选择: ${selectedFileName ?: resolveDisplayName(context, uri)}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (effectivePrefill?.textContent != null) {
            OutlinedTextField(
              value = textContent,
              onValueChange = { textContent = it },
              modifier = Modifier.fillMaxWidth().testTag("add-item-text-content"),
              label = { Text("文本内容") },
              minLines = 3,
              maxLines = 8,
              shape = MaterialTheme.shapes.medium,
              colors =
                OutlinedTextFieldDefaults.colors(
                  focusedContainerColor = MaterialTheme.colorScheme.surface,
                  unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
          }
          if (selectedDocumentFormat != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "识别格式：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Box(
                modifier =
                  Modifier.background(
                    ImperialCinnabar.copy(alpha = 0.1f),
                    RoundedCornerShape(4.dp),
                  )
              ) {
                Text(
                  text = selectedDocumentFormat!!.label,
                  style = MaterialTheme.typography.labelMedium,
                  color = DetailInk,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
              }
            }
          }
        }

        else -> {}
      }

      if (validationMessage != null) {
        Text(
          text = validationMessage,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
fun ClipboardDialog(
  content: String,
  imageUri: String? = null,
  sourceLabel: String? = null,
  sourceContentType: ContentType? = null,
  sourceDocumentFormat: DocumentFormat? = null,
  sourceFileName: String? = null,
  onSummarize: () -> Unit,
  onManualCreate: () -> Unit,
  onDismiss: () -> Unit,
  isSmartSummarizing: Boolean = false,
  smartSummarizationMessage: String? = null,
) {
  val scrollState = rememberScrollState()
  val context = LocalContext.current
  var imageBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
  val hasContent = content.isNotBlank()

  LaunchedEffect(imageUri) {
    val uriStr = imageUri
    if (uriStr != null) {
      val bitmap =
        withContext(Dispatchers.IO) {
          try {
            val uri = Uri.parse(uriStr)
            context.contentResolver.openInputStream(uri)?.use { input ->
              BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
          } catch (_: Exception) {
            null
          }
        }
      imageBitmap = bitmap
    }
  }

  ArchiveDialog(
    title = if (sourceLabel != null) "检测到${sourceLabel}内容" else "检测到剪切板内容",
    onDismissRequest = onDismiss,
    actions = {
      ArchiveDialogAction(
        label = "忽略",
        onClick = onDismiss,
      )
      ArchiveDialogAction(
        label = "手动归纳",
        onClick = onManualCreate,
      )
      ArchiveDialogAction(
        onClick = onSummarize,
        enabled = !isSmartSummarizing,
        primary = true,
      ) { contentColor ->
        if (isSmartSummarizing) {
          CircularProgressIndicator(
            modifier = Modifier.size(14.dp).testTag("clipboard-smart-summarize-loading"),
            strokeWidth = 1.8.dp,
            color = contentColor,
          )
        }
        Text(
          text = if (isSmartSummarizing) "归纳中…" else "智能归纳",
          style = MaterialTheme.typography.labelLarge,
          color = contentColor,
          maxLines = 1,
        )
      }
    },
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(scrollState),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      imageBitmap?.let { bmp ->
        Image(
          bitmap = bmp,
          contentDescription = "剪切板图片",
          modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
          contentScale = ContentScale.Fit,
        )
      }
      if (hasContent) {
        Text(
          text = content,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (
        !hasContent &&
          imageBitmap == null &&
          (sourceFileName != null || sourceDocumentFormat != null)
      ) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .background(Color.White.copy(alpha = 0.34f), RoundedCornerShape(4.dp))
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            sourceFileName?.let {
              Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = DetailInk,
              )
            }
            val typeLabel =
              when {
                sourceDocumentFormat != null && sourceDocumentFormat != DocumentFormat.UNKNOWN ->
                  "${sourceDocumentFormat.label} ${ContentType.DOCUMENT.label}"
                sourceContentType != null -> sourceContentType.label
                else -> null
              }
            typeLabel?.let {
              Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = DetailCinnabar,
              )
            }
          }
        }
      }
      if (smartSummarizationMessage != null) {
        Text(
          text = smartSummarizationMessage,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
fun DeleteItemConfirmDialog(
  itemTitle: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  ArchiveDialog(
    title = "确认删除",
    onDismissRequest = onDismiss,
    actions = {
      ArchiveDialogAction(label = "取消", onClick = onDismiss)
      ArchiveDialogAction(label = "删除", onClick = onConfirm, destructive = true)
    },
  ) {
    Text(
      text = "确定要删除资料 \"$itemTitle\" 吗？",
      style = MaterialTheme.typography.bodyMedium,
      color = DetailInk,
    )
  }
}
