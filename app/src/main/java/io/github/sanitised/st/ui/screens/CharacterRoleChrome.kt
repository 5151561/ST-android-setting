package io.github.sanitised.st.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sanitised.st.R
import io.github.sanitised.st.ui.theme.STTheme

enum class CharacterLocalNav {
    CHARACTERS,
    LOREBOOK,
    PERSONA
}

private data class CharacterLocalNavAction(
    val destination: CharacterLocalNav,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun CharacterLocalBottomBar(
    active: CharacterLocalNav,
    onCharacters: () -> Unit,
    onLorebook: () -> Unit,
    onPersona: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = STTheme.colors
    val items = buildList {
        add(CharacterLocalNavAction(
            destination = CharacterLocalNav.CHARACTERS,
            label = stringResource(R.string.character_local_nav_characters),
            icon = Icons.Filled.Style,
            onClick = onCharacters
        ))
        add(CharacterLocalNavAction(
            destination = CharacterLocalNav.LOREBOOK,
            label = stringResource(R.string.character_local_nav_lorebook),
            icon = Icons.Filled.Book,
            onClick = onLorebook
        ))
        add(CharacterLocalNavAction(
            destination = CharacterLocalNav.PERSONA,
            label = stringResource(R.string.character_local_nav_persona),
            icon = Icons.Filled.Person,
            onClick = onPersona
        ))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface.copy(alpha = 0.96f))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val selected = item.destination == active
            val tint = if (selected) colors.accent else colors.muted
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .then(
                        if (selected) {
                            Modifier.background(colors.accent.copy(alpha = 0.12f))
                        } else {
                            Modifier
                        }
                    )
                    .clickable(onClick = item.onClick)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
                Text(text = item.label, color = tint, fontSize = 11.sp)
            }
        }
    }
}
