package com.lyihub.archiveassistant.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lyihub.archiveassistant.ui.theme.ImperialDisplayFont

@Composable
internal fun PaneHeroHeader(
  title: String,
  description: String,
  modifier: Modifier = Modifier,
  showBackButton: Boolean = false,
  onBack: (() -> Unit)? = null,
  onTitleClick: (() -> Unit)? = null,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (showBackButton && onBack != null) {
        IconButton(onClick = onBack) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            tint = Color.Black,
          )
        }
      }
      Text(
        text = title,
        style = MaterialTheme.typography.displayMedium,
        color = Color.Black,
        fontWeight = FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
          Modifier.weight(1f)
            .then(
              if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier
            ),
      )
    }
    Text(
      text = description,
      style = MaterialTheme.typography.titleSmall.copy(fontFamily = ImperialDisplayFont),
      color = Color.Black.copy(alpha = 0.78f),
      modifier = Modifier.fillMaxWidth().testTag("pane-hero-summary"),
    )
  }
}
