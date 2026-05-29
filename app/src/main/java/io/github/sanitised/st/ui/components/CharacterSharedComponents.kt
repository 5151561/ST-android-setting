package io.github.sanitised.st.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.R
import io.github.sanitised.st.api.STTag

@Composable
internal fun FavoriteIconButton(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: Dp? = null
) {
    IconButton(onClick = onToggleFavorite, enabled = enabled, modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = stringResource(R.string.character_filter_favorites),
            tint = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = iconSize?.let { Modifier.size(it) } ?: Modifier
        )
    }
}

@Composable
internal fun CharacterTagCheckboxList(
    tags: List<STTag>,
    selectedIds: Set<String>,
    onSelectedIdsChange: (Set<String>) -> Unit
) {
    tags.forEach { tag ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = tag.id in selectedIds,
                onCheckedChange = { checked ->
                    onSelectedIdsChange(
                        if (checked) {
                            selectedIds + tag.id
                        } else {
                            selectedIds - tag.id
                        }
                    )
                }
            )
            Text(if (tag.isFolder) stringResource(R.string.character_filter_folder, tag.name) else tag.name)
        }
    }
}
