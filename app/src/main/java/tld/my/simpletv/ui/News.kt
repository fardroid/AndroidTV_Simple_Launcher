package tld.my.simpletv.ui

// The news card, the quote of the day and a headline over the home screen (with the QR code of its link). Sizes follow
// src/theme/global.css of the webOS app (.news, .quote, .news-detail).

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import tld.my.simpletv.FeedItem
import tld.my.simpletv.Feeds
import tld.my.simpletv.Quote
import tld.my.simpletv.R
import tld.my.simpletv.plural
import tld.my.simpletv.str

/** "12 min ago", "3 h ago" */
fun ageText(at: Long, now: Long): String {
    val age = Feeds.ageOf(at, now) ?: return ""
    return when (age.second) {
        Feeds.Unit.MIN -> str(R.string.n_min_ago, age.first)
        Feeds.Unit.H -> str(R.string.n_h_ago, age.first)
        Feeds.Unit.D -> str(R.string.n_d_ago, age.first)
    }
}

/** The page of headlines the card shows, and how many pages there are (the card turns by pages: ← → turn them, ↑ ↓ choose). */
fun newsPages(count: Int, height: Float, k: Float): Pair<Int, Int> {
    val per = Feeds.headlinesPerPage(height, k)
    return per to maxOf(1, (count + per - 1) / per)
}

/** The news: a page of headlines, each with its outlet and age. */
@Composable
fun NewsCard(items: List<FeedItem>?, loading: Boolean, page: Int, selected: Int, focused: Boolean, height: Float, k: Float, nowMs: Long) {
    val list = items.orEmpty()
    val (per, pages) = newsPages(list.size, height, k)
    val p = minOf(page, pages - 1)
    val shown = list.drop(p * per).take(per)
    Column(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp)) {
        Row(Modifier.fillMaxWidth().height((44 * k).dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(str(R.string.news), Modifier.weight(1f), style = wtext(26f * k, 500, lineHeight = 1.2f), maxLines = 1)
            if (pages > 1) BasicText("${p + 1} / $pages", style = wtext(17f * k, 400, Hue.textSecondary))
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp).clipToBounds()) {
            shown.forEachIndexed { i, item ->
                val index = p * per + i
                val sel = focused && index == selected
                Column(Modifier.fillMaxWidth().background(if (sel) Color(0x29FFFFFF) else Color.Transparent, RoundedCornerShape(18.dp)).padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 14.dp)) {
                    BasicText(listOf(item.source, ageText(item.at, nowMs)).filter { it.isNotEmpty() }.joinToString(" · "), style = wtext(14f * k, 400, Hue.textSecondary, 1.35f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(item.title, Modifier.padding(top = 6.dp), style = wtext(22f * k, 500, lineHeight = 1.3f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (list.isEmpty()) {
                BasicText(
                    if (loading) str(R.string.loading_the_news) else if (items != null) str(R.string.no_fresh_headlines) else str(R.string.the_news_has_not_loaded_yet),
                    Modifier.fillMaxWidth().padding(top = 70.dp), style = wtext(22f * k, 400, Hue.textSecondary).copy(textAlign = TextAlign.Center),
                )
            }
        }
    }
}

private const val QUOTE_MAX_FONT = 44f
private const val QUOTE_MIN_FONT = 16f

/** The quote of the day: the text as big as the card allows (a short saying is set large, a long one small), the author under
 *  it. The size is found by measuring: from the largest down, two pixels at a time, until it fits. */
@Composable
fun QuoteCard(quote: Quote?, loading: Boolean, height: Float, k: Float) {
    val measurer = rememberTextMeasurer(8)
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 20.dp)) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val w = with(density) { maxWidth.roundToPx() }
        val byLine = 8 + 17f * k * 1.2f
        val room = with(density) { maxHeight.toPx() } - byLine * density.density
        Column(Modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            if (quote == null) {
                BasicText(
                    if (loading) str(R.string.loading_the_quote_of_the_day) else str(R.string.the_quote_of_the_day_has_not_loaded_yet),
                    style = wtext(22f * k, 400, Hue.textSecondary, 1.3f),
                )
            } else {
                val size = remember(quote.text, height, k, w, room) {
                    var font = Math.round(minOf(QUOTE_MAX_FONT * k, height * 0.34f)).toFloat()
                    fun style(f: Float) = wtext(f, 300, lineHeight = 1.3f).copy(fontStyle = FontStyle.Italic)
                    fun fits(f: Float) = measurer.measure(quote.text, style(f), constraints = Constraints(maxWidth = w), density = density).size.height <= room
                    while (!fits(font) && font > QUOTE_MIN_FONT) font -= 2f
                    font
                }
                BasicText(quote.text, style = wtext(size, 300, lineHeight = 1.3f).copy(fontStyle = FontStyle.Italic), maxLines = if (size <= QUOTE_MIN_FONT) 3 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
                BasicText("${quote.author} · Wikiquote", Modifier.padding(top = 8.dp), style = wtext(17f * k, 400, Hue.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The squares of a QR code of `text` (null when it cannot be made: too long). */
fun qrMatrix(text: String): Array<BooleanArray>? = runCatching {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0))
    Array(m.height) { y -> BooleanArray(m.width) { x -> m.get(x, y) } }
}.getOrNull()

/** A headline over the home screen: its outlet and age, the headline, the description from the feed, and a QR code of its link
 *  to read the article on a phone. OK or Back closes it (Home handles the keys). */
@Composable
fun NewsDetail(item: FeedItem, nowMs: Long) {
    val code = remember(item.link) { if (item.link.isNotEmpty()) qrMatrix(item.link) else null }
    Box(Modifier.fillMaxSize().background(Color(0xB8040612)), contentAlignment = Alignment.Center) {
        Row(Modifier.width(1320.dp).background(Color(0xFF161A30), RoundedCornerShape(36.dp)).padding(horizontal = 56.dp, vertical = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(listOf(item.source, ageText(item.at, nowMs)).filter { it.isNotEmpty() }.joinToString(" · "), style = wtext(22f, 400, Hue.textSecondary))
                BasicText(item.title, Modifier.padding(top = 14.dp), style = wtext(44f, 500, lineHeight = 1.25f))
                if (item.summary.isNotEmpty()) BasicText(item.summary, Modifier.padding(top = 22.dp), style = wtext(24f, 400, Hue.textSecondary, 1.45f), maxLines = 6, overflow = TextOverflow.Ellipsis)
                BasicText(str(R.string.ok_or_back_closes), Modifier.padding(top = 30.dp), style = wtext(20f, 400, Hue.textMuted))
            }
            if (code != null) {
                Column(Modifier.padding(start = 56.dp).width(300.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.size(300.dp)) {
                        // a white square with a quiet border of two modules, the dark modules on it
                        val n = code.size
                        val cell = size.width / (n + 4)
                        drawRoundRect(Color.White, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f * density))
                        for (y in 0 until n) for (x in 0 until n) if (code[y][x]) {
                            drawRect(Color.Black, Offset((x + 2) * cell, (y + 2) * cell), Size(cell + 0.5f, cell + 0.5f))
                        }
                    }
                    BasicText(str(R.string.scan_to_read_it_on_your_phone), Modifier.padding(top = 16.dp), style = wtext(20f, 400, Hue.textSecondary, 1.35f).copy(textAlign = TextAlign.Center))
                }
            }
        }
    }
}
