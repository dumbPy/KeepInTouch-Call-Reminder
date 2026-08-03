package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TagCategory
import com.example.data.model.TagEntity

@Composable
fun TagChip(
    tag: TagEntity,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val tagColor = try {
        Color(android.graphics.Color.parseColor(tag.colorHex))
    } catch (e: Exception) {
        Color(android.graphics.Color.parseColor(tag.category.defaultColorHex))
    }

    val categoryBadgeText = when (tag.category) {
        TagCategory.GROUPING -> "Group"
        TagCategory.FREQUENCY -> "${tag.singleValue}d"
        TagCategory.SNOOZE_DEFAULT -> "Snooze ${tag.singleValue}d"
        TagCategory.PRIORITY -> "P${tag.singleValue}"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tagColor.copy(alpha = 0.15f))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(tagColor)
            )
            Text(
                text = "${tag.name} ($categoryBadgeText)",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = tagColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
