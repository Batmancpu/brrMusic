package com.music.zenith.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import com.music.zenith.data.lyrics.CharGrowth
import com.music.zenith.data.lyrics.LyricLine
import com.music.zenith.ui.replay.Fonts
import com.music.zenith.ui.replay.drawArtwork
import com.music.zenith.ui.replay.drawBackdrop
import com.music.zenith.ui.replay.drawLogo
import com.music.zenith.ui.replay.ellipsised
import com.music.zenith.ui.replay.loadBitmap
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The picked lines as one picture, drawn to a bitmap you can send.
 *
 * ## Why this is drawn rather than screenshotted
 *
 * The same reason the Replay is. A screenshot of the lyrics panel carries the
 * scrubber, the glow falloff, whichever lines happened to be on screen and the
 * status bar of that particular phone, so two people sharing the same verse
 * would send two different-looking pictures. A card laid out here is the verse,
 * the sleeve and this app's mark, and nothing else — and it is only as tall as
 * that verse needs, which holds it inside 1080×1920 (9:16): the shape a story
 * or a chat head expects. Keeping it there is the player's job, by refusing
 * more words at the pick rather than by shrinking them here.
 *
 * ## Why it isn't Compose
 *
 * `Bitmap` + `android.graphics.Canvas`, exactly as
 * [com.music.zenith.ui.replay.ReplayPoster] does it, and for the same
 * reasons written up there: Compose offscreen on minSdk 26 is a cascade of
 * hardware bitmaps and layers that fails differently per device, while a
 * software canvas draws identically from API 26 up. The backdrop, the logo, the
 * fonts and the sleeve are shared with the Replay by importing them rather than
 * by copying, so the two cards cannot drift apart.
 *
 * ## Fitting the type
 *
 * A passage can be one line or twenty, so both the size and the height are
 * chosen rather than fixed: the layout is retried down a fixed ladder of sizes
 * until the rows stop overflowing, and the card is then grown to exactly the
 * room those rows take. The bottom rung of that ladder is a floor rather than a
 * last resort — no card ever sets type smaller than it to squeeze a passage
 * in, so a passage too long for the frame lengthens the card instead of being
 * cut, or made small enough to hold a phone at arm's length to read.
 */
internal suspend fun renderLyricsShareCard(
    context: Context,
    card: LyricsShareRequest,
    /** Pad out to the full 1080×1920 frame, the passage centred in the room. */
    story: Boolean = false,
): Bitmap = lyricsCardPainter(context, card, story).render(null)

/**
 * The card laid out once, ready to be drawn at any moment of the song.
 *
 * Everything that never moves (backdrop, panel, song strip, brand) is drawn
 * here into one base picture; the passage is drawn over it per frame by
 * [LyricsCardPainter], so the share video and the sheet's preview can animate
 * the words without laying anything out again.
 */
internal suspend fun lyricsCardPainter(
    context: Context,
    card: LyricsShareRequest,
    story: Boolean = false,
    /** No backdrop: the card alone, to lay over a video. */
    transparent: Boolean = false,
): LyricsCardPainter = withContext(Dispatchers.Default) {
    val type = Fonts(context)
    val cover = card.artworkUrl?.let { loadBitmap(context, it) }

    // ── Sizing ──────────────────────────────────────────────────────────────
    val chrome = OUTER_TOP + PANEL_PAD_V * 2 + FOOTER_H + BRAND_BAND
    val frameRoom = MAX_CARD_H - chrome
    val plan = fittestPlan(type, card.lines, frameRoom)
    // A story pads the frame to 9:16 and keeps the whole panel centred in it.
    val naturalH = chrome + plan.content
    val cardH = if (story) maxOf(MAX_CARD_H, naturalH) else naturalH
    val spare = cardH - naturalH
    val panelTop = OUTER_TOP + spare / 2
    val bodyTop = panelTop + PANEL_PAD_V
    val bodyBottom = bodyTop + plan.content
    val footerTop = bodyBottom + PANEL_PAD_V
    val panelBottom = footerTop + FOOTER_H

    val bitmap = Bitmap.createBitmap(CARD_W, cardH.roundToInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    if (!transparent) drawBackdrop(canvas, cover, 0f, cardH)

    // ── Panel ───────────────────────────────────────────────────────────────
    val panel = RectF(CARD_MARGIN, panelTop, CARD_W - CARD_MARGIN, panelBottom)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    fill.color = 0x59000000
    canvas.drawRoundRect(panel, PANEL_RADIUS, PANEL_RADIUS, fill)
    fill.color = 0x0FFFFFFF
    canvas.drawRoundRect(panel, PANEL_RADIUS, PANEL_RADIUS, fill)
    // The song strip: a darker band whose bottom corners follow the panel.
    val footerPath = Path().apply {
        addRoundRect(
            RectF(panel.left, footerTop, panel.right, panelBottom),
            floatArrayOf(
                0f, 0f, 0f, 0f,
                PANEL_RADIUS, PANEL_RADIUS, PANEL_RADIUS, PANEL_RADIUS,
            ),
            Path.Direction.CW,
        )
    }
    fill.color = 0x33000000
    canvas.drawPath(footerPath, fill)
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0x26FFFFFF
    }
    canvas.drawRoundRect(
        RectF(panel.left + 1f, panel.top + 1f, panel.right - 1f, panel.bottom - 1f),
        PANEL_RADIUS,
        PANEL_RADIUS,
        edge,
    )

    // ── The passage ─────────────────────────────────────────────────────────
    // Only placed here; the painter draws each row, lit or not, per frame.
    val textLeft = CARD_MARGIN + PANEL_PAD
    val placed = ArrayList<PlacedRow>(plan.rows.size)
    var y = bodyTop
    for (row in plan.rows) {
        val baseline = y + row.gapBefore - row.paint.ascent() +
            if (row.isGap) row.paint.textSize * GAP_MARKER_OFFSET else 0f
        val x = if (row.centered) CARD_W / 2f else textLeft
        placed += PlacedRow(row, x, baseline)
        y += row.gapBefore + row.height
    }

    // ── Song strip ──────────────────────────────────────────────────────────
    val thumbY = footerTop + (FOOTER_H - FOOTER_THUMB) / 2f
    drawArtwork(canvas, cover, card.song.title, textLeft, thumbY, FOOTER_THUMB, false, 0.12f)
    val textX = textLeft + FOOTER_THUMB + 32f
    val room = panel.right - PANEL_PAD - textX
    val title = type.body(46f, Color.WHITE, bold = true)
    canvas.drawText(ellipsised(card.song.title, title, room), textX, thumbY + 54f, title)
    val artist = type.body(40f, 0x99FFFFFF.toInt())
    canvas.drawText(ellipsised(card.song.artist, artist, room), textX, thumbY + 104f, artist)

    // ── Brand ───────────────────────────────────────────────────────────────
    // Under the panel, centred, where Apple Music puts its own mark.
    val brand = type.heading(36f, 0xB3FFFFFF.toInt())
    val brandBaseline = panelBottom + 88f
    // The mark is drawn at the word's own cap height and sits on its baseline,
    // so the Z and "Zenith" read as one lockup.
    val capBounds = android.graphics.Rect().also { brand.getTextBounds("Z", 0, 1, it) }
    val logoH = capBounds.height().toFloat()
    val logoW = logoH * LOGO_ASPECT
    val brandWidth = logoW + LOGO_GAP + brand.measureText(BRAND)
    val brandLeft = (CARD_W - brandWidth) / 2f
    drawLogo(canvas, context, brandLeft + logoW, brandBaseline, logoW, logoH)
    canvas.drawText(BRAND, brandLeft + logoW + LOGO_GAP, brandBaseline, brand)

    LyricsCardPainter(CardLayout(bitmap.width, bitmap.height, bitmap, placed), card.lines)
}

/**
 * The card's own backdrop alone, [height] tall at the card's width: what the
 * share video sits on when it has no animated cover to play under the words.
 */
internal suspend fun renderLyricsShareBackdrop(
    context: Context,
    card: LyricsShareRequest,
    height: Int,
): Bitmap = withContext(Dispatchers.Default) {
    val cover = card.artworkUrl?.let { loadBitmap(context, it) }
    Bitmap.createBitmap(CARD_W, height, Bitmap.Config.ARGB_8888).also {
        drawBackdrop(Canvas(it), cover, 0f, height.toFloat())
    }
}

// ── Animation ──────────────────────────────────────────────────────────────

/** A row of the passage, and where it sits on the card. */
internal class PlacedRow internal constructor(val row: Row, val x: Float, val baseline: Float)

internal class CardLayout internal constructor(
    val width: Int,
    val height: Int,
    val base: Bitmap,
    val rows: List<PlacedRow>,
)

/**
 * Draws the card at a moment of the song, the way the player's lyrics panel
 * draws the same lines at that moment.
 *
 * The arithmetic is the panel's own, read off the same [LyricLine]:
 * [LyricLine.revealedChars] for how far the sweep has got, [LyricLine.wordLift]
 * for the word rising as it is sung, [LyricLine.growingAt] for a held word
 * swelling letter by letter. The panel's distances become ratios of the type
 * size, so they hold at whatever size the card's ladder landed on. With no
 * position (the still card) every line is drawn lit.
 *
 * Not thread-safe: it reuses its paints. One instance per drawing thread.
 */
internal class LyricsCardPainter internal constructor(
    private val layout: CardLayout,
    private val lines: List<LyricsShareLine>,
) {
    val width: Int get() = layout.width
    val height: Int get() = layout.height

    private val basePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val scratch = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fade = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val growth = CharGrowth()

    /** Each line's place among the sung lines (gaps don't count), for the falloff. */
    private val rank = IntArray(lines.size).also { ranks ->
        var next = 0
        lines.forEachIndexed { index, line -> ranks[index] = if (line.isGap) -1 else next++ }
    }

    /** The card at [positionMs] (track time; null for the still card), into [into] when given. */
    fun render(positionMs: Long?, into: Bitmap? = null): Bitmap {
        val bitmap = into ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        drawOn(Canvas(bitmap), positionMs)
        return bitmap
    }

    /** Draws the card onto [canvas] at the card's own pixel size. */
    fun drawOn(canvas: Canvas, positionMs: Long?) {
        canvas.drawBitmap(layout.base, 0f, 0f, basePaint)
        val active = positionMs?.let(::activeLine) ?: -1
        for (placed in layout.rows) drawRow(canvas, placed, positionMs, active)
    }

    /** The line being sung at [positionMs]: the last one to have started, or the first. */
    private fun activeLine(positionMs: Long): Int {
        var first = -1
        var last = -1
        lines.forEachIndexed { index, line ->
            if (line.isGap) return@forEachIndexed
            val start = line.startMs ?: line.timing?.timeMs ?: return@forEachIndexed
            if (first < 0) first = index
            if (positionMs >= start) last = index
        }
        return if (last >= 0) last else first
    }

    private fun drawRow(canvas: Canvas, placed: PlacedRow, positionMs: Long?, active: Int) {
        val row = placed.row
        val line = lines.getOrNull(row.line)
        if (positionMs == null || row.isGap || line == null || active < 0) {
            canvas.drawText(row.text, placed.x, placed.baseline, row.paint)
            return
        }
        val timing = line.timing
        val distance = kotlin.math.abs(rank[row.line] - rank[active]).coerceAtMost(LINE_FALLOFF.lastIndex)
        val alpha = row.paint.alpha * LINE_FALLOFF[distance]
        val start = line.startMs ?: timing?.timeMs ?: 0L
        val swept = timing != null && timing.isWordSynced && !row.rtl

        if (!swept) {
            // No word timings (or a script the sweep can't follow): the line
            // lights whole the moment it starts, as the panel's does.
            val lit = if (positionMs >= start) 1f else UNSUNG_ALPHA
            scratch.set(row.paint)
            scratch.alpha = (alpha * lit).roundToInt()
            canvas.drawText(row.text, placed.x, placed.baseline, scratch)
            return
        }
        timing!!
        // How far the sweep has got, in this row's own characters.
        val revealedInLine = if (row.isSub) {
            val lead = timing.text.length.coerceAtLeast(1)
            (timing.revealedChars(positionMs) / lead).coerceIn(0f, 1f) * (line.subText?.length ?: 0)
        } else {
            timing.revealedChars(positionMs)
        }
        val revealed = (revealedInLine - row.start).coerceIn(0f, row.text.length.toFloat())
        val sung = positionMs >= timing.endMs
        val dim = if (sung) 1f else UNSUNG_ALPHA
        val lifts = !row.isSub

        // The unsung copy underneath, then the lit one over it up to the sweep.
        scratch.set(row.paint)
        scratch.alpha = (alpha * dim).roundToInt()
        drawPieces(canvas, placed, scratch, positionMs, timing, lifts, bloom = false)
        if (revealed <= 0f || (sung && revealed >= row.text.length)) return

        scratch.alpha = alpha.roundToInt()
        if (revealed >= row.text.length) {
            drawPieces(canvas, placed, scratch, positionMs, timing, lifts, bloom = true)
            return
        }
        val size = row.paint.textSize
        val edge = placed.x + sweepX(row, revealed, if (row.isSub) emptyList() else timing.sweepSpans)
        // The feather runs ahead of the sweep, into what is still to come.
        val feather = minOf(size * FEATHER_RATIO, edge - placed.x)
        val top = placed.baseline + row.paint.ascent() - size * HEADROOM_RATIO
        val bottom = placed.baseline + row.paint.descent() + size * 0.1f
        val save = canvas.saveLayer(0f, top, layout.width.toFloat(), bottom, null)
        canvas.clipRect(0f, top, edge + feather, bottom)
        drawPieces(canvas, placed, scratch, positionMs, timing, lifts, bloom = true)
        if (feather > 0.5f) {
            fade.shader = LinearGradient(
                edge, 0f, edge + feather, 0f,
                Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, top, layout.width.toFloat(), bottom, fade)
        }
        canvas.restoreToCount(save)
    }

    /**
     * The row's text with each word at its lift, and a held word letter by
     * letter at its swell, with (when [bloom]) the glow its letters give off.
     */
    private fun drawPieces(
        canvas: Canvas,
        placed: PlacedRow,
        paint: Paint,
        positionMs: Long,
        timing: LyricLine,
        lifts: Boolean,
        bloom: Boolean,
    ) {
        val row = placed.row
        val text = row.text
        if (!lifts) {
            canvas.drawText(text, placed.x, placed.baseline, paint)
            return
        }
        val size = paint.textSize
        val rise = size * RISE_RATIO
        val rowStart = row.start
        val rowEnd = rowStart + text.length
        fun run(from: Int, to: Int, dy: Float) {
            if (to <= from) return
            canvas.drawText(text, from, to, placed.x + paint.measureText(text, 0, from), placed.baseline + dy, paint)
        }
        var cursor = 0
        timing.wordSpans.forEachIndexed { index, span ->
            val from = maxOf(span.first, rowStart) - rowStart
            val to = minOf(span.last + 1, rowEnd) - rowStart
            if (to <= from) return@forEachIndexed
            run(cursor, from, 0f)
            val grow = timing.growingAt(index)
            if (grow != null && positionMs >= grow.startMs && positionMs <= grow.restsAtMs) {
                for (k in from until to) {
                    val letter = rowStart + k - span.first
                    grow.sampleInto(letter, positionMs, growth)
                    val left = placed.x + paint.measureText(text, 0, k)
                    val w = paint.measureText(text, k, k + 1)
                    val cx = left + w / 2f + growth.shift * size
                    val y = placed.baseline - growth.rise * rise
                    canvas.save()
                    canvas.scale(growth.scale, growth.scale, cx, y)
                    if (bloom && growth.bloom > 0.01f) {
                        glow.set(paint)
                        glow.alpha = (paint.alpha * growth.bloom.coerceAtMost(1f)).roundToInt()
                        glow.maskFilter = BlurMaskFilter(size * GLOW_RATIO, BlurMaskFilter.Blur.NORMAL)
                        canvas.drawText(text, k, k + 1, cx - w / 2f, y, glow)
                    }
                    canvas.drawText(text, k, k + 1, cx - w / 2f, y, paint)
                    canvas.restore()
                }
            } else {
                run(from, to, -timing.wordLift(index, positionMs) * rise)
            }
            cursor = to
        }
        run(cursor, text.length, 0f)
    }

    /** Where [chars] characters into [row] falls, from the row's left edge. */
    private fun sweepX(row: Row, chars: Float, spans: List<IntRange>): Float {
        val text = row.text
        val paint = row.paint
        val at = row.start + chars
        // Within a timed piece the edge crosses its width at a steady pace.
        val piece = spans.firstOrNull { at >= it.first && at < it.last + 1 }
        if (piece != null && piece.first >= row.start && piece.last + 1 <= row.start + text.length) {
            val from = paint.measureText(text, 0, piece.first - row.start)
            val to = paint.measureText(text, 0, piece.last + 1 - row.start)
            return from + (to - from) * (at - piece.first) / (piece.last + 1 - piece.first)
        }
        // A space between words, or a piece broken over a wrap: letter by letter.
        val k = chars.toInt().coerceIn(0, text.length)
        val here = paint.measureText(text, 0, k)
        val next = paint.measureText(text, 0, (k + 1).coerceAtMost(text.length))
        return here + (next - here) * (chars - k)
    }
}

// The panel's animation numbers as ratios of the type size (its 34sp headline).
private const val UNSUNG_ALPHA = 0.45f
private val LINE_FALLOFF = floatArrayOf(1f, 0.8f, 0.7f, 0.58f, 0.46f)
private const val FEATHER_RATIO = 30f / 34f
private const val RISE_RATIO = 2f / 34f
private const val HEADROOM_RATIO = 0.35f
private const val GLOW_RATIO = 6f / 34f

private fun isRtl(text: String): Boolean =
    !java.text.Bidi(text, java.text.Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()

// ── Fitting ────────────────────────────────────────────────────────────────

internal class Row internal constructor(
    val text: String,
    val paint: Paint,
    val height: Float,
    val gapBefore: Float,
    /** Drawn centred rather than off the left edge — the gap marker. */
    val centered: Boolean = false,
    val isGap: Boolean = false,
    /** Which of the request's lines this row belongs to. */
    val line: Int = -1,
    /** Where the row starts in its line's text (or in its sub-line's, for [isSub]). */
    val start: Int = 0,
    /** The translation or romanization under the line, rather than the line itself. */
    val isSub: Boolean = false,
    /** Right-to-left text, which lights whole rather than sweeping. */
    val rtl: Boolean = false,
)

/** The rows at one size, with everything needed to place them. */
private class Plan(val rows: List<Row>, val ascent: Float) {
    /** Distance from the top of the first line to the bottom of the last. */
    val content: Float
        get() = ascent + rows.fold(0f) { total, row -> total + row.gapBefore + row.height }
}

/**
 * The largest size on [FIT_LADDER] whose rows still fit in [room], or the
 * bottom rung when none of them do.
 *
 * The bottom rung is a floor rather than a last resort: under it the type stops
 * being worth sharing, so the frame grows to hold the words instead of the
 * words shrinking to hold the frame. That the player budgets characters at the
 * pick is what keeps this branch from ever being reached in practice.
 */
private fun fittestPlan(
    type: Fonts,
    lines: List<LyricsShareLine>,
    room: Float,
): Plan {
    for (size in FIT_LADDER) {
        val plan = planAt(type, lines, size)
        if (plan.content <= room) return plan
    }
    return planAt(type, lines, FIT_LADDER.last())
}

/**
 * The rows at one [size], set in the face the lyrics panel draws with.
 *
 * SF Pro Display Heavy with the panel's tight tracking and its line advance,
 * carried over as the same ratios the panel's 34sp headline uses — so the card
 * reads as a still of that panel rather than as a different app quoting it.
 * Compose's tracking is absolute and `Paint`'s is a fraction of the size, hence
 * the divisions: -0.7 over 34 for the words, -0.7 over 20 for the smaller line
 * the panel hangs underneath them.
 */
private fun planAt(type: Fonts, lines: List<LyricsShareLine>, size: Float): Plan {
    val primary = type.heading(size, Color.WHITE).apply { letterSpacing = TRACKING }
    val subSize = (size * SUB_RATIO).coerceAtLeast(SUB_FLOOR)
    val secondary = type.heading(subSize, SUB_COLOR).apply { letterSpacing = SUB_TRACKING }

    val rows = mutableListOf<Row>()
    lines.forEachIndexed { index, line ->
        if (line.isGap) {
            // The skipped lines, as "(...)" — the conventional editorial mark
            // for an omission. Hung off the left edge with the words, smaller
            // and a dim grey. Keep its row compact so the marker follows the
            // preceding lyric closely; the regular gap on the next lyric line
            // provides the separation below it.
            val marker = type.body(size * 0.65f, 0x66FFFFFF.toInt())
            val markerHeight = marker.descent() - marker.ascent()
            rows += Row("(...)", marker, markerHeight, 0f, isGap = true, line = index)
            return@forEachIndexed
        }
        var firstOfLine = true
        fun add(text: String, paint: Paint, height: Float, start: Int, isSub: Boolean, rtl: Boolean) {
            // One verse line is one paragraph: its first row carries the space
            // above it and its wraps sit tight underneath, so a phrase reads as
            // a block rather than as a list of separate sentences.
            val gap = when {
                rows.isEmpty() -> 0f
                firstOfLine && index > 0 -> size * 0.55f
                else -> 0f
            }
            rows += Row(text, paint, height, gap, line = index, start = start, isSub = isSub, rtl = rtl)
            firstOfLine = false
        }
        // Each row remembers where it starts in its line, so the sweep (which
        // counts characters of the whole line) can be found within it.
        fun addRows(source: String, paint: Paint, height: Float, isSub: Boolean) {
            val rtl = isRtl(source)
            var cursor = 0
            wrap(source, paint, CARD_CONTENT_W).forEach { piece ->
                val at = source.indexOf(piece, cursor).takeIf { it >= 0 } ?: cursor
                cursor = at + piece.length
                add(piece, paint, height, at, isSub, rtl)
            }
        }
        addRows(line.text, primary, size * LEAD, isSub = false)
        val sub = line.subText
        if (!sub.isNullOrBlank() && sub != line.text) {
            addRows(sub, secondary, subSize * SUB_LEAD, isSub = true)
        }
    }
    return Plan(rows, -primary.ascent())
}

// ── Text ───────────────────────────────────────────────────────────────────

/**
 * Greedy wrap at [width], breaking wherever the line has to.
 *
 * Not a plain word-wrap: Japanese, Chinese and Korean lyrics carry no spaces at
 * all, and a wrapper that only split on spaces would hand the whole verse to
 * `drawText` and let it run off the card. `breakText` finds the character that
 * fits, and only then is the break walked back to a space — so scripts that keep
 * their words apart lose nothing, and scripts that don't still get several lines.
 */
private fun wrap(text: String, paint: Paint, width: Float): List<String> {
    val source = text.trim()
    if (source.isEmpty()) return listOf("")

    val out = mutableListOf<String>()
    var rest = source
    while (rest.isNotEmpty()) {
        val fit = paint.breakText(rest, true, width, null)
        if (fit <= 0) {
            out += rest
            break
        }
        // Walk back to the last space inside the fit, but only when something
        // still has to follow it — otherwise the fit is the whole string and
        // cutting at the space would drop the final word.
        val space = rest.lastIndexOf(' ', fit - 1)
        val take = if (space > 0 && fit < rest.length) space else fit
        if (take <= 0) {
            out += rest
            break
        }
        out += rest.substring(0, take).trimEnd()
        rest = rest.substring(take).trimStart()
    }
    return out.filter { it.isNotEmpty() }.ifEmpty { listOf("") }
}

// ── Card geometry ──────────────────────────────────────────────────────────

private const val CARD_W = 1080

/**
 * 9:16 — a ceiling on the height, not the height itself. The card is grown to
 * its passage and only ever comes out this tall when the passage demands it.
 */
private const val MAX_CARD_H = 1920f

/** Side margin around the panel, and the padding inside it. */
private const val CARD_MARGIN = 56f
private const val PANEL_PAD = 56f
private const val PANEL_PAD_V = 64f
private const val PANEL_RADIUS = 56f
private const val CARD_CONTENT_W = CARD_W - (CARD_MARGIN + PANEL_PAD) * 2

private const val LOGO_GAP = 20f
private const val BRAND = "Music"
private const val LOGO_ASPECT = 730f / 484f

/** Air above the panel, and the band under it that holds the brand. */
private const val OUTER_TOP = 56f
private const val BRAND_BAND = 132f

/** The song strip at the foot of the panel, and the sleeve square in it. */
private const val FOOTER_H = 216f
private const val FOOTER_THUMB = 128f

/** Smallest the translation line is allowed to get before it stops being legible. */
private const val SUB_FLOOR = 24f

/** Small visual correction so the omission marker sits below the line above. */
private const val GAP_MARKER_OFFSET = 0.18f

/**
 * The sizes tried, largest first.
 *
 * A ladder rather than a search: a dozen candidates is nothing to lay out, and
 * stepping by a visible amount keeps two cards with adjacent line counts from
 * coming out nearly the same size and reading as a mistake.
 *
 * The last rung doubles as the floor — see [fittestPlan] for why the frame is
 * what gives way under it rather than the type.
 */
private val FIT_LADDER = floatArrayOf(80f, 72f, 64f, 56f, 50f, 44f, 38f, 32f)

// ── The panel's type, as ratios ────────────────────────────────────────────
// Every number here is one the lyrics panel uses, divided by the size it uses
// it at so it holds at whatever size the ladder lands on.

/** Tracking of the panel's headline: -0.7sp against its 34sp. */
private const val TRACKING = -0.7f / 34f

/** The same -0.7sp, against the 20sp it sets the line underneath in. */
private const val SUB_TRACKING = -0.7f / 20f

/** Line advance: 41sp over 34sp for the words, 25sp over 20sp for the sub. */
private const val LEAD = 41f / 34f
private const val SUB_LEAD = 25f / 20f

/** The sub-line against the words above it, and the white it is drawn in. */
private const val SUB_RATIO = 20f / 34f
private val SUB_COLOR = 0xD9FFFFFF.toInt()
