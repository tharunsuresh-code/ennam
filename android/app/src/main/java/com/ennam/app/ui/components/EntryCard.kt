package com.ennam.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ennam.app.data.model.Entry
import com.ennam.app.data.model.PendingEntry

private val categoryEmojis = mapOf(
    "todo" to "\u2705", "idea" to "\uD83D\uDCA1", "receipt" to "\uD83E\uDDFE",
    "journal" to "\uD83D\uDCDD", "bookmark" to "\uD83D\uDCD6",
    "question" to "\u2753", "screenshot" to "\uD83D\uDCF8"
)

// ──────────────────────────────────────────────
// Category-specific card layouts
// ──────────────────────────────────────────────

data class CardCallbacks(
    val onArchive: (String) -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val onToggleDone: (String) -> Unit = {},
    val onTogglePin: (String) -> Unit = {},
    val onAnswer: (String, String) -> Unit = { _, _ -> },
    val onToggleLocked: (String) -> Unit = {},
    val onOpenUrl: (String) -> Unit = {}
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryCard(
    entry: Entry,
    callbacks: CardCallbacks = CardCallbacks(),
    modifier: Modifier = Modifier
) {
    when (entry.category) {
        "todo" -> TodoCard(entry, callbacks, modifier)
        "idea" -> IdeaCard(entry, callbacks, modifier)
        "receipt" -> ReceiptCard(entry, callbacks, modifier)
        "journal" -> JournalCard(entry, callbacks, modifier)
        "bookmark" -> BookmarkCard(entry, callbacks, modifier)
        "question" -> QuestionCard(entry, callbacks, modifier)
        "screenshot" -> ScreenshotCard(entry, callbacks, modifier)
        else -> GenericCard(entry, callbacks, modifier)
    }
}

/** Show rawText as primary, summary as gray subtitle if different */
@Composable
private fun EntryBody(entry: Entry, maxLines: Int = 3) {
    val raw = entry.rawText
    val summ = entry.summary

    Text(
        text = raw,
        fontWeight = FontWeight.Medium,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
    if (summ.isNotBlank() && summ != raw) {
        Spacer(Modifier.height(2.dp))
        Text(
            text = summ,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ────────── TODO ──────────

@Composable
private fun TodoCard(entry: Entry, callbacks: CardCallbacks, modifier: Modifier) {
    val priorityColor = when (entry.priority) {
        "high" -> Color(0xFFE53935)
        "low" -> Color(0xFF43A047)
        else -> Color(0xFFFDD835)
    }
    val priorityLabel = when (entry.priority) {
        "high" -> "\uD83D\uDD34 High"
        "low" -> "\uD83D\uDFE2 Low"
        else -> "\uD83D\uDFE1 Medium"
    }
    val bgColor = if (entry.isDone)
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    else
        MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = entry.isDone,
                onCheckedChange = { callbacks.onToggleDone(entry.id) }
            )
            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.rawText,
                    fontWeight = if (entry.isDone) FontWeight.Normal else FontWeight.Medium,
                    textDecoration = if (entry.isDone) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (entry.isDone)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = priorityColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = priorityLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = priorityColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatTimestamp(entry.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ────────── IDEA ──────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IdeaCard(entry: Entry, callbacks: CardCallbacks, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .combinedClickable(onClick = { expanded = !expanded }),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\uD83D\uDCA1", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.rawText,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Always show full raw text + summary on expansion
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    // Show summary as a subtitle if it differs
                    if (entry.summary.isNotBlank() && entry.summary != entry.rawText) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = entry.summary.take(300),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Show tags
                    if (entry.tags.isNotBlank() && entry.tags != "[]") {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "\uD83C\uDFF7\uFE0F " + entry.tags
                                .removeSurrounding("[", "]")
                                .replace("\"", "")
                                .replace(",", " \u00B7 "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formatTimestamp(entry.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ────────── RECEIPT ──────────

private val amountRegex = Regex("""[$€£¥]?\s*(\d+(?:[.,]\d{1,2})?)""")
private val storeKeywords = listOf("at ", "from ", "store:", "shop:", "@")

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReceiptCard(entry: Entry, callbacks: CardCallbacks, modifier: Modifier) {
    val amount = remember(entry.rawText) {
        amountRegex.find(entry.rawText)?.groupValues?.get(0) ?: ""
    }
    val storeName = remember(entry.rawText) {
        val lower = entry.rawText.lowercase()
        storeKeywords.firstNotNullOfOrNull { kw ->
            val idx = lower.indexOf(kw)
            if (idx >= 0) {
                entry.rawText.substring(idx + kw.length).trim()
                    .split(Regex("""[,.\n]""")).firstOrNull()?.trim()
            } else null
        } ?: entry.rawText.take(40)
    }

    val showMenu = remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .combinedClickable(onClick = {}, onLongClick = { showMenu.value = true }),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            if (amount.isNotBlank()) {
                Text(
                    text = amount,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = storeName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            // Show summary as a note if different from raw
            if (entry.summary.isNotBlank() && entry.summary != entry.rawText) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = entry.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "\uD83E\uDDFE Receipt",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formatTimestamp(entry.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    DropdownMenu(
        expanded = showMenu.value,
        onDismissRequest = { showMenu.value = false }
    ) {
        DropdownMenuItem(
            text = { Text("Archive") },
            onClick = { showMenu.value = false; callbacks.onArchive(entry.id) }
        )
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            onClick = { showMenu.value = false; callbacks.onDelete(entry.id) }
        )
    }
}

// ────────── JOURNAL ──────────

// mood detection via Regex in the card function — no set needed here

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JournalCard(entry: Entry, callbacks: CardCallbacks, modifier: Modifier) {
    val moodEmoji = remember(entry.rawText) {
        val m = Regex("""[:;][)D(]|😀|😁|😂|😊|😍|😢|😭|😡|🤔|😎|🥰|🤩""").find(entry.rawText)
        m?.value ?: "\uD83D\uDCDD"
    }
    val firstLines = remember(entry.rawText) {
        entry.rawText.lines().take(3).joinToString("\n")
    }
    val locked by remember { mutableStateOf(entry.isLocked) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = {},
                onLongClick = { callbacks.onToggleLocked(entry.id) }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(moodEmoji, fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (locked) "\uD83D\uDD12 Locked entry" else formatTimestamp(entry.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatDate(entry.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            if (locked) {
                Text(
                    "Tap and authenticate to view",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            } else {
                Text(
                    text = firstLines,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "\uD83D\uDCDD Journal \u00B7 long-press to ${if (locked) "unlock" else "lock"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ────────── BOOKMARK ──────────

private val urlRegex = Regex("""https?://[^\s,;!?)]+""")

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkCard(entry: Entry, callbacks: CardCallbacks, modifier: Modifier) {
    val context = LocalContext.current
    val url = remember(entry.rawText) { urlRegex.find(entry.rawText)?.value ?: "" }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .combinedClickable(onClick = {
                if (url.isNotBlank()) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\uD83D\uDCD6", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.rawText,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.summary.isNotBlank() && entry.summary != entry.rawText) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = entry.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (url.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formatTimestamp(entry.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ────────── QUESTION ──────────

@Composable
private fun QuestionCard(entry: Entry, callbacks: CardCallbacks, modifier: Modifier) {
    var answerText by remember { mutableStateOf(entry.answer) }
    val resolved = entry.answer.isNotBlank()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (resolved)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(if (resolved) "\u2705" else "\u2753", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.rawText,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (resolved) 2 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            // Show summary if it differs
            if (entry.summary.isNotBlank() && entry.summary != entry.rawText && !resolved) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            if (resolved) {
                Text(
                    text = "\uD83D\uDCAC $answerText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic
                )
            } else {
                OutlinedTextField(
                    value = answerText,
                    onValueChange = { answerText = it },
                    placeholder = { Text("Type your answer...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (answerText.isNotBlank()) {
                                callbacks.onAnswer(entry.id, answerText)
                            }
                        }
                    )
                )
            }
        }
    }
}

// ────────── SCREENSHOT ──────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScreenshotCard(entry: Entry, callbacks: CardCallbacks, modifier: Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .combinedClickable(onClick = { callbacks.onTogglePin(entry.id) }),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.isPinned)
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\uD83D\uDCF8", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text("Screenshot", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleSmall)
                if (entry.isPinned) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.PushPin, contentDescription = "Pinned",
                        modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatTimestamp(entry.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "\uD83D\uDDBC\uFE0F Image preview",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            if (entry.rawText.isNotBlank() && entry.rawText != "[Image captured - OCR pending]") {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = entry.rawText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (entry.isPinned) "\uD83D\uDCCC Pinned \u00B7 tap to unpin" else "Tap to pin to top",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ────────── GENERIC FALLBACK ──────────

@Composable
private fun GenericCard(entry: Entry, callbacks: CardCallbacks, modifier: Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text("\uD83D\uDCCC", fontSize = 20.sp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.rawText,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.summary.isNotBlank() && entry.summary != entry.rawText) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = entry.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formatTimestamp(entry.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ────────── PENDING ──────────

@Composable
fun PendingEntryCard(pending: PendingEntry, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.7f).height(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {}
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(0.4f).height(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {}
            Spacer(Modifier.height(4.dp))
            Text(
                text = pending.rawText.take(50),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

// ────────── FORMAT HELPERS ──────────

private fun formatTimestamp(ms: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ms
    return when {
        diff < 60_000 -> "just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}

private fun formatDate(ms: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return "${cal.get(java.util.Calendar.MONTH) + 1}/${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
}
