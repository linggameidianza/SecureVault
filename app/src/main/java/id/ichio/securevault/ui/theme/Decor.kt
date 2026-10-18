package id.ichio.securevault.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Gradasi utama aplikasi: ungu → magenta → pink. */
val AppGradient: Brush = Brush.linearGradient(listOf(Violet, Purple, Pink))

private val avatarGradients = listOf(
    listOf(Violet, Pink),
    listOf(Coral, Sun),
    listOf(Teal, Sky),
    listOf(Color(0xFF3A7BD5), Color(0xFF00D2FF)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    listOf(Color(0xFFFF416C), Color(0xFFFF4B2B)),
    listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
    listOf(Color(0xFFF7971E), Color(0xFFFFD200))
)

private val pastels = listOf(
    Color(0xFFFFE1EA), Color(0xFFE1EEFF), Color(0xFFDDF8EF),
    Color(0xFFFFF1D1), Color(0xFFEDE4FF), Color(0xFFFFE6D9)
)
private val pastelAccents = listOf(Pink, Sky, Teal, Sun, Violet, Coral)

private fun indexFor(seed: String, size: Int) = abs(seed.lowercase().hashCode()) % size

fun avatarBrush(seed: String): Brush = Brush.linearGradient(avatarGradients[indexFor(seed, avatarGradients.size)])
fun pastelFor(seed: String): Color = pastels[indexFor(seed, pastels.size)]
fun accentFor(seed: String): Color = pastelAccents[indexFor(seed, pastelAccents.size)]

/** Header berlatar gradasi dengan sudut bawah membulat. Ikon & teks otomatis putih. */
@Composable
fun GradientHeader(
    title: String,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {}
) {
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(AppGradient)
                .statusBarsPadding()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(56.dp)) {
                if (navigationIcon != null) navigationIcon() else Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
            Column(Modifier.padding(horizontal = 12.dp)) { content() }
        }
    }
}

/** Kolom pencarian putih untuk diletakkan di dalam [GradientHeader]. */
@Composable
fun HeaderSearchField(value: String, onChange: (String) -> Unit, placeholder: String) {
    val textColor = Ink
    TextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Hapus pencarian")
                }
            }
        },
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = textColor,
            unfocusedTextColor = textColor,
            cursorColor = Violet,
            focusedPlaceholderColor = Color(0xFF8A84A0),
            unfocusedPlaceholderColor = Color(0xFF8A84A0),
            focusedLeadingIconColor = Violet,
            unfocusedLeadingIconColor = Violet,
            focusedTrailingIconColor = Color(0xFF8A84A0),
            unfocusedTrailingIconColor = Color(0xFF8A84A0)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = Color(0x55000000))
    )
}

/** Kotak statistik transparan di header. */
@Composable
fun StatPill(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = if (selected) 0.32f else 0.18f))
            .then(if (selected) Modifier.border(BorderStroke(1.5.dp, Color.White), shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f), maxLines = 1)
    }
}

/** Avatar huruf awal dengan gradasi warna sesuai nama. */
@Composable
fun GradientAvatar(text: String, size: Dp = 46.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(avatarBrush(text)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text.trim().take(1).uppercase().ifEmpty { "?" },
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Tombol aksi melayang bergradasi. */
@Composable
fun GradientFab(text: String, icon: ImageVector, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .shadow(12.dp, shape, spotColor = Violet, ambientColor = Violet)
            .clip(shape)
            .background(AppGradient)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

/** Ikon dalam kotak berwarna (untuk menu pengaturan). */
@Composable
fun IconTile(icon: ImageVector, color: Color, size: Dp = 40.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.75f)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

/** Kartu putih membulat untuk mengelompokkan isi. */
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = content
    )
}

/** Titik bulat kecil berwarna. */
@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}
