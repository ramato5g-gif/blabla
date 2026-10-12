package it.tesi.caa

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration

/** Schermo stretto (telefono in verticale): layout a una colonna, tessere più grandi. */
@Composable
fun isCompact(): Boolean = LocalConfiguration.current.screenWidthDp < 600

val Ink = Color(0xFF1B1B1B)
val Muted = Color(0xFF616161)
val Panel = Color(0xFFF1F3F4)
val Accent = Color(0xFF1565C0)

/** Tessera CAA: immagine (foto, pittogramma o emoji) + parola, colore della categoria. */
@Composable
fun CardTile(
    card: Card,
    showLabel: Boolean,
    uppercase: Boolean,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val img = rememberImage(card.imagePath)
    val shape = RoundedCornerShape(14.dp)
    BoxWithConstraints(
        modifier
            .padding(3.dp)
            .clip(shape)
            .background(card.wordClass.color)
            .border(if (selected) 4.dp else 1.5.dp, if (selected) Accent else Color(0x55000000), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        val base = minOf(maxWidth.value, maxHeight.value)
        val labelSp = (base * 0.14f).coerceIn(10f, 30f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val imgSize = base * if (showLabel) 0.68f else 0.85f
            if (img == null && card.emoji.isBlank()) {
                // Parola scritta con la tastiera: solo testo, grande
                Text(
                    if (uppercase) card.label.uppercase() else card.label,
                    fontSize = (base * 0.24f).coerceIn(14f, 40f).sp, fontWeight = FontWeight.Bold, color = Ink,
                    fontFamily = Legible, textAlign = TextAlign.Center, maxLines = 2
                )
            } else {
                if (img != null) {
                    Image(
                        img, contentDescription = card.label, contentScale = ContentScale.Fit,
                        modifier = Modifier.size(imgSize.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
                    )
                } else {
                    Text(card.emoji, fontSize = (imgSize * 0.72f).sp, textAlign = TextAlign.Center)
                }
                if (showLabel) {
                    Text(
                        if (uppercase) card.label.uppercase() else card.label,
                        fontSize = labelSp.sp, fontWeight = FontWeight.Bold, color = Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Voce dell'elenco argomenti. */
@Composable
fun TopicTile(
    emoji: String,
    imagePath: String?,
    name: String,
    selected: Boolean,
    uppercase: Boolean,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    val img = rememberImage(imagePath)
    Row(
        modifier
            .padding(3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Accent else Color.White)
            .border(1.dp, Color(0x33000000), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(if (small) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (img != null) Image(img, null, contentScale = ContentScale.Fit, modifier = Modifier.size(if (small) 28.dp else 40.dp).clip(RoundedCornerShape(6.dp)))
        else Text(emoji.ifBlank { "📁" }, fontSize = if (small) 22.sp else 30.sp)
        Text(
            if (uppercase) name.uppercase() else name,
            fontSize = if (small) 15.sp else 17.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else Ink,
            maxLines = 2, modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    if (isCompact()) {
        // Telefono: titolo sopra, pulsanti sotto (scorrevoli)
        Column(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 6.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) TextButton(onClick = onBack) { Text("←", fontSize = 22.sp) }
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { actions() }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onBack != null) TextButton(onClick = onBack) { Text("← Indietro") }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, fontWeight = FontWeight.Bold, color = Muted, modifier = Modifier.padding(top = 6.dp))
}

@Composable
fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            if (o == selected) Button(onClick = { onSelect(o) }) { Text(label(o)) }
            else OutlinedButton(onClick = { onSelect(o) }) { Text(label(o)) }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = Ink)
        Switch(checked, onChange)
    }
}

/** Barra orizzontale per le statistiche. */
@Composable
fun BarRow(label: String, value: Int, max: Int, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(if (isCompact()) 110.dp else 150.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Ink)
        Box(Modifier.weight(1f).height(20.dp)) {
            val f = if (max <= 0) 0f else value.toFloat() / max
            Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceIn(0.01f, 1f)).clip(RoundedCornerShape(4.dp)).background(color))
        }
        Text("$value", modifier = Modifier.width(44.dp).padding(start = 6.dp), fontWeight = FontWeight.Bold, color = Ink)
    }
}

@Composable
fun StatBox(title: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Panel).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(title, color = Muted, textAlign = TextAlign.Center)
    }
}
