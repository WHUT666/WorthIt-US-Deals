package com.example.ui.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * WorthIt Custom SF-Symbols–Inspired Vector Icon System.
 *
 * Design principles:
 * - Uniform 1.75dp optical stroke weight on a 24x24 viewport
 * - Rounded stroke caps & joins for Apple-grade geometric warmth
 * - Harmonized Filled (active) & Outlined (inactive) navigation icon pairs
 * - Zero allocations per recomposition via lazy cached ImageVectors
 */
object WorthItIcons {

    private const val STROKE = 1.75f
    private val INK = SolidColor(Color.Black)

    private inline fun icon(
        name: String,
        block: ImageVector.Builder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply(block).build()

    // =========================================================================
    // Bottom Navigation Tab Pairs (Filled Active / Outlined Inactive)
    // =========================================================================

    /** Deals Tab (Active) — Sculpted tilted price tag with crisp perforation cutout */
    val DealsFilled: ImageVector by lazy {
        icon("DealsFilled") {
            path(fill = INK, pathFillType = PathFillType.EvenOdd) {
                moveTo(3.5f, 6.2f)
                curveTo(3.5f, 4.71f, 4.71f, 3.5f, 6.2f, 3.5f)
                lineTo(11.1f, 3.5f)
                curveTo(11.9f, 3.5f, 12.66f, 3.82f, 13.22f, 4.38f)
                lineTo(19.95f, 11.11f)
                curveTo(21.12f, 12.28f, 21.12f, 14.18f, 19.95f, 15.35f)
                lineTo(15.35f, 19.95f)
                curveTo(14.18f, 21.12f, 12.28f, 21.12f, 11.11f, 19.95f)
                lineTo(4.38f, 13.22f)
                curveTo(3.82f, 12.66f, 3.5f, 11.9f, 3.5f, 11.1f)
                close()
                // Crisp circular hole cutout
                moveTo(7.9f, 6.4f)
                arcTo(1.5f, 1.5f, 0f, true, false, 7.9f, 9.4f)
                arcTo(1.5f, 1.5f, 0f, true, false, 7.9f, 6.4f)
                close()
                // Precision inner lightning notch
                moveTo(14.1f, 10.2f)
                lineTo(11.1f, 13.8f)
                lineTo(13.3f, 13.8f)
                lineTo(12.6f, 16.9f)
                lineTo(16.0f, 13.0f)
                lineTo(13.75f, 13.0f)
                close()
            }
        }
    }

    /** Deals Tab (Inactive) — Airy 1.75dp rounded-cap price tag */
    val DealsOutlined: ImageVector by lazy {
        icon("DealsOutlined") {
            path(
                stroke = INK,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3.75f, 6.5f)
                curveTo(3.75f, 4.98f, 4.98f, 3.75f, 6.5f, 3.75f)
                lineTo(11.1f, 3.75f)
                curveTo(11.83f, 3.75f, 12.53f, 4.04f, 13.05f, 4.56f)
                lineTo(19.65f, 11.16f)
                curveTo(20.72f, 12.23f, 20.72f, 13.97f, 19.65f, 15.04f)
                lineTo(15.04f, 19.65f)
                curveTo(13.97f, 20.72f, 12.23f, 20.72f, 11.16f, 19.65f)
                lineTo(4.56f, 13.05f)
                curveTo(4.04f, 12.53f, 3.75f, 11.83f, 3.75f, 11.1f)
                close()
            }
            path(fill = INK) {
                moveTo(8.0f, 6.75f)
                arcTo(1.25f, 1.25f, 0f, true, false, 8.0f, 9.25f)
                arcTo(1.25f, 1.25f, 0f, true, false, 8.0f, 6.75f)
                close()
            }
        }
    }

    /** Search Tab (Active) — Bold optical lens + crisp inner sparkle */
    val SearchFilled: ImageVector by lazy {
        icon("SearchFilled") {
            path(
                stroke = INK,
                strokeLineWidth = 2.25f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10.75f, 4.25f)
                arcTo(6.5f, 6.5f, 0f, true, false, 10.75f, 17.25f)
                arcTo(6.5f, 6.5f, 0f, true, false, 10.75f, 4.25f)
                close()
                moveTo(15.65f, 15.65f)
                lineTo(20.0f, 20.0f)
            }
            path(fill = INK) {
                moveTo(10.75f, 7.6f)
                lineTo(11.45f, 9.65f)
                lineTo(13.5f, 10.35f)
                lineTo(11.45f, 11.05f)
                lineTo(10.75f, 13.1f)
                lineTo(10.05f, 11.05f)
                lineTo(8.0f, 10.35f)
                lineTo(10.05f, 9.65f)
                close()
            }
        }
    }

    /** Search Tab (Inactive) & Search Fields — Sleek optical magnifying glass */
    val SearchOutlined: ImageVector by lazy {
        icon("SearchOutlined") {
            path(
                stroke = INK,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10.75f, 4.25f)
                arcTo(6.5f, 6.5f, 0f, true, false, 10.75f, 17.25f)
                arcTo(6.5f, 6.5f, 0f, true, false, 10.75f, 4.25f)
                close()
                moveTo(15.6f, 15.6f)
                lineTo(19.75f, 19.75f)
            }
        }
    }

    /** Radar Tab (Active) — Dual signal waves + bold core beacon */
    val RadarFilled: ImageVector by lazy {
        icon("RadarFilled") {
            path(
                stroke = INK,
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round
            ) {
                // Outer arcs
                moveTo(5.6f, 5.6f)
                arcTo(9.05f, 9.05f, 0f, false, false, 5.6f, 18.4f)
                moveTo(18.4f, 5.6f)
                arcTo(9.05f, 9.05f, 0f, false, true, 18.4f, 18.4f)
                // Inner arcs
                moveTo(8.45f, 8.45f)
                arcTo(5.0f, 5.0f, 0f, false, false, 8.45f, 15.55f)
                moveTo(15.55f, 8.45f)
                arcTo(5.0f, 5.0f, 0f, false, true, 15.55f, 15.55f)
            }
            path(fill = INK) {
                moveTo(12.0f, 9.5f)
                arcTo(2.5f, 2.5f, 0f, true, false, 12.0f, 14.5f)
                arcTo(2.5f, 2.5f, 0f, true, false, 12.0f, 9.5f)
                close()
            }
        }
    }

    /** Radar Tab (Inactive) — Airy live broadcast waves */
    val RadarOutlined: ImageVector by lazy {
        icon("RadarOutlined") {
            path(
                stroke = INK,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(5.8f, 5.8f)
                arcTo(8.75f, 8.75f, 0f, false, false, 5.8f, 18.2f)
                moveTo(18.2f, 5.8f)
                arcTo(8.75f, 8.75f, 0f, false, true, 18.2f, 18.2f)
                moveTo(8.6f, 8.6f)
                arcTo(4.8f, 4.8f, 0f, false, false, 8.6f, 15.4f)
                moveTo(15.4f, 8.6f)
                arcTo(4.8f, 4.8f, 0f, false, true, 15.4f, 15.4f)
            }
            path(
                stroke = INK,
                strokeLineWidth = STROKE
            ) {
                moveTo(12.0f, 10.25f)
                arcTo(1.75f, 1.75f, 0f, true, false, 12.0f, 13.75f)
                arcTo(1.75f, 1.75f, 0f, true, false, 12.0f, 10.25f)
                close()
            }
        }
    }

    /** Saved Tab & Bookmark Action (Filled) — Soft continuous-curve Apple ribbon */
    val BookmarkFilled: ImageVector by lazy {
        icon("BookmarkFilled") {
            path(fill = INK) {
                moveTo(6.0f, 5.5f)
                curveTo(6.0f, 4.12f, 7.12f, 3.0f, 8.5f, 3.0f)
                lineTo(15.5f, 3.0f)
                curveTo(16.88f, 3.0f, 18.0f, 4.12f, 18.0f, 5.5f)
                lineTo(18.0f, 19.8f)
                curveTo(18.0f, 20.65f, 17.02f, 21.13f, 16.34f, 20.62f)
                lineTo(12.0f, 17.35f)
                lineTo(7.66f, 20.62f)
                curveTo(6.98f, 21.13f, 6.0f, 20.65f, 6.0f, 19.8f)
                close()
            }
        }
    }

    /** Saved Tab & Bookmark Action (Outlined) — Airy continuous ribbon */
    val BookmarkOutlined: ImageVector by lazy {
        icon("BookmarkOutlined") {
            path(
                stroke = INK,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6.25f, 5.75f)
                curveTo(6.25f, 4.51f, 7.26f, 3.5f, 8.5f, 3.5f)
                lineTo(15.5f, 3.5f)
                curveTo(16.74f, 3.5f, 17.75f, 4.51f, 17.75f, 5.75f)
                lineTo(17.75f, 19.85f)
                lineTo(12.0f, 15.85f)
                lineTo(6.25f, 19.85f)
                close()
            }
        }
    }

    /** Engine Tab (Active) — Apple Control Center precision sliders (filled knobs) */
    val SlidersFilled: ImageVector by lazy {
        icon("SlidersFilled") {
            path(
                stroke = INK,
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(4.0f, 7.0f)
                lineTo(7.5f, 7.0f)
                moveTo(12.5f, 7.0f)
                lineTo(20.0f, 7.0f)
                moveTo(4.0f, 12.0f)
                lineTo(12.5f, 12.0f)
                moveTo(17.5f, 12.0f)
                lineTo(20.0f, 12.0f)
                moveTo(4.0f, 17.0f)
                lineTo(8.5f, 17.0f)
                moveTo(13.5f, 17.0f)
                lineTo(20.0f, 17.0f)
            }
            path(fill = INK) {
                moveTo(10.0f, 4.75f)
                arcTo(2.25f, 2.25f, 0f, true, false, 10.0f, 9.25f)
                arcTo(2.25f, 2.25f, 0f, true, false, 10.0f, 4.75f)
                close()
                moveTo(15.0f, 9.75f)
                arcTo(2.25f, 2.25f, 0f, true, false, 15.0f, 14.25f)
                arcTo(2.25f, 2.25f, 0f, true, false, 15.0f, 9.75f)
                close()
                moveTo(11.0f, 14.75f)
                arcTo(2.25f, 2.25f, 0f, true, false, 11.0f, 19.25f)
                arcTo(2.25f, 2.25f, 0f, true, false, 11.0f, 14.75f)
                close()
            }
        }
    }

    /** Engine Tab (Inactive) — Airy Apple Control Center sliders */
    val SlidersOutlined: ImageVector by lazy {
        icon("SlidersOutlined") {
            path(
                stroke = INK,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(4.0f, 7.0f)
                lineTo(7.75f, 7.0f)
                moveTo(12.25f, 7.0f)
                lineTo(20.0f, 7.0f)
                moveTo(4.0f, 12.0f)
                lineTo(12.75f, 12.0f)
                moveTo(17.25f, 12.0f)
                lineTo(20.0f, 12.0f)
                moveTo(4.0f, 17.0f)
                lineTo(8.75f, 17.0f)
                moveTo(13.25f, 17.0f)
                lineTo(20.0f, 17.0f)
                // Hollow circular knobs
                moveTo(10.0f, 5.0f)
                arcTo(2.0f, 2.0f, 0f, true, false, 10.0f, 9.0f)
                arcTo(2.0f, 2.0f, 0f, true, false, 10.0f, 5.0f)
                moveTo(15.0f, 10.0f)
                arcTo(2.0f, 2.0f, 0f, true, false, 15.0f, 14.0f)
                arcTo(2.0f, 2.0f, 0f, true, false, 15.0f, 10.0f)
                moveTo(11.0f, 15.0f)
                arcTo(2.0f, 2.0f, 0f, true, false, 11.0f, 19.0f)
                arcTo(2.0f, 2.0f, 0f, true, false, 11.0f, 15.0f)
            }
        }
    }

    // =========================================================================
    // Theme & Chrome Bar Action Icons
    // =========================================================================

    /** SF-style Sun with detached rounded rays (Light Mode) */
    val SunMode: ImageVector by lazy {
        icon("SunMode") {
            path(
                stroke = INK,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(12.0f, 8.0f)
                arcTo(4.0f, 4.0f, 0f, true, false, 12.0f, 16.0f)
                arcTo(4.0f, 4.0f, 0f, true, false, 12.0f, 8.0f)
                moveTo(12.0f, 3.25f)
                lineTo(12.0f, 4.75f)
                moveTo(12.0f, 19.25f)
                lineTo(12.0f, 20.75f)
                moveTo(3.25f, 12.0f)
                lineTo(4.75f, 12.0f)
                moveTo(19.25f, 12.0f)
                lineTo(20.75f, 12.0f)
                moveTo(5.8f, 5.8f)
                lineTo(6.9f, 6.9f)
                moveTo(17.1f, 17.1f)
                lineTo(18.2f, 18.2f)
                moveTo(18.2f, 5.8f)
                lineTo(17.1f, 6.9f)
                moveTo(6.9f, 17.1f)
                lineTo(5.8f, 18.2f)
            }
        }
    }

    /** SF-style Crescent Moon + subtle star (Dark Mode) */
    val MoonMode: ImageVector by lazy {
        icon("MoonMode") {
            path(
                stroke = INK,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19.5f, 14.3f)
                arcTo(7.5f, 7.5f, 0f, true, true, 9.7f, 4.5f)
                arcTo(5.8f, 5.8f, 0f, false, false, 19.5f, 14.3f)
                close()
            }
            path(fill = INK) {
                moveTo(17.5f, 4.2f)
                lineTo(18.0f, 5.7f)
                lineTo(19.5f, 6.2f)
                lineTo(18.0f, 6.7f)
                lineTo(17.5f, 8.2f)
                lineTo(17.0f, 6.7f)
                lineTo(15.5f, 6.2f)
                lineTo(17.0f, 5.7f)
                close()
            }
        }
    }

    /** Auto Theme — Half-filled precision contrast circle */
    val AutoContrast: ImageVector by lazy {
        icon("AutoContrast") {
            path(
                stroke = INK,
                strokeLineWidth = STROKE
            ) {
                moveTo(12.0f, 4.0f)
                arcTo(8.0f, 8.0f, 0f, true, false, 12.0f, 20.0f)
                arcTo(8.0f, 8.0f, 0f, true, false, 12.0f, 4.0f)
            }
            path(fill = INK) {
                moveTo(12.0f, 4.0f)
                arcTo(8.0f, 8.0f, 0f, false, true, 12.0f, 20.0f)
                close()
            }
        }
    }

    /** Sleek Apple Chevron Back (`chevron.left`) */
    val BackChevron: ImageVector by lazy {
        icon("BackChevron") {
            path(
                stroke = INK,
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(14.75f, 5.5f)
                lineTo(8.25f, 12.0f)
                lineTo(14.75f, 18.5f)
            }
        }
    }

    /** Iconic Apple Share (`square.and.arrow.up`) */
    val ShareArrow: ImageVector by lazy {
        icon("ShareArrow") {
            path(
                stroke = INK,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12.0f, 3.5f)
                lineTo(12.0f, 14.0f)
                moveTo(8.5f, 7.0f)
                lineTo(12.0f, 3.5f)
                lineTo(15.5f, 7.0f)
                moveTo(6.5f, 10.5f)
                lineTo(5.75f, 10.5f)
                curveTo(4.78f, 10.5f, 4.0f, 11.28f, 4.0f, 12.25f)
                lineTo(4.0f, 18.25f)
                curveTo(4.0f, 19.22f, 4.78f, 20.0f, 5.75f, 20.0f)
                lineTo(18.25f, 20.0f)
                curveTo(19.22f, 20.0f, 20.0f, 19.22f, 20.0f, 18.25f)
                lineTo(20.0f, 12.25f)
                curveTo(20.0f, 11.28f, 19.22f, 10.5f, 18.25f, 10.5f)
                lineTo(17.5f, 10.5f)
            }
        }
    }

    /** Precision Twin-Arc Refresh (`arrow.triangle.2.circlepath`) */
    val SyncRefresh: ImageVector by lazy {
        icon("SyncRefresh") {
            path(
                stroke = INK,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Upper arc clockwise
                moveTo(5.2f, 10.5f)
                arcTo(7.0f, 7.0f, 0f, false, true, 17.5f, 7.4f)
                moveTo(17.8f, 4.6f)
                lineTo(17.9f, 7.8f)
                lineTo(14.7f, 7.9f)
                // Lower arc clockwise
                moveTo(18.8f, 13.5f)
                arcTo(7.0f, 7.0f, 0f, false, true, 6.5f, 16.6f)
                moveTo(6.2f, 19.4f)
                lineTo(6.1f, 16.2f)
                lineTo(9.3f, 16.1f)
            }
        }
    }

    /** Apple Notification Bell with active chime arc (`bell.badge`) */
    val BellAlert: ImageVector by lazy {
        icon("BellAlert") {
            path(
                stroke = INK,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(17.5f, 10.2f)
                curveTo(17.5f, 7.1f, 15.05f, 4.75f, 12.0f, 4.75f)
                curveTo(8.95f, 4.75f, 6.5f, 7.1f, 6.5f, 10.2f)
                curveTo(6.5f, 13.6f, 4.75f, 15.4f, 4.75f, 16.2f)
                curveTo(4.75f, 16.65f, 5.1f, 17.0f, 5.55f, 17.0f)
                lineTo(18.45f, 17.0f)
                curveTo(18.9f, 17.0f, 19.25f, 16.65f, 19.25f, 16.2f)
                curveTo(19.25f, 15.4f, 17.5f, 13.6f, 17.5f, 10.2f)
                close()
                moveTo(9.8f, 17.4f)
                arcTo(2.3f, 2.3f, 0f, false, false, 14.2f, 17.4f)
            }
            path(fill = INK) {
                moveTo(18.5f, 4.2f)
                arcTo(1.8f, 1.8f, 0f, true, false, 18.5f, 7.8f)
                arcTo(1.8f, 1.8f, 0f, true, false, 18.5f, 4.2f)
                close()
            }
        }
    }

    // =========================================================================
    // Deal Card, Detail & Action Button Icons
    // =========================================================================

    /** Sleek External Link Arrow (`arrow.up.right`) */
    val ArrowUpRight: ImageVector by lazy {
        icon("ArrowUpRight") {
            path(
                stroke = INK,
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7.25f, 16.75f)
                lineTo(16.75f, 7.25f)
                moveTo(9.25f, 7.25f)
                lineTo(16.75f, 7.25f)
                lineTo(16.75f, 14.75f)
            }
        }
    }

    /** Aerodynamic Flash Lightning Bolt (`bolt.fill`) */
    val FlashBolt: ImageVector by lazy {
        icon("FlashBolt") {
            path(fill = INK) {
                moveTo(13.4f, 2.8f)
                curveTo(13.65f, 2.45f, 14.2f, 2.68f, 14.12f, 3.1f)
                lineTo(12.85f, 9.8f)
                lineTo(17.95f, 9.8f)
                curveTo(18.42f, 9.8f, 18.68f, 10.35f, 18.38f, 10.72f)
                lineTo(10.6f, 21.2f)
                curveTo(10.35f, 21.55f, 9.8f, 21.32f, 9.88f, 20.9f)
                lineTo(11.15f, 14.2f)
                lineTo(6.05f, 14.2f)
                curveTo(5.58f, 14.2f, 5.32f, 13.65f, 5.62f, 13.28f)
                close()
            }
        }
    }

    /** Apple Intelligence–style 4-point Sparkles (DealRank Algorithm) */
    val Sparkles: ImageVector by lazy {
        icon("Sparkles") {
            path(fill = INK) {
                moveTo(10.5f, 3.2f)
                lineTo(12.2f, 8.3f)
                lineTo(17.3f, 10.0f)
                lineTo(12.2f, 11.7f)
                lineTo(10.5f, 16.8f)
                lineTo(8.8f, 11.7f)
                lineTo(3.7f, 10.0f)
                lineTo(8.8f, 8.3f)
                close()
                moveTo(17.5f, 14.2f)
                lineTo(18.35f, 16.65f)
                lineTo(20.8f, 17.5f)
                lineTo(18.35f, 18.35f)
                lineTo(17.5f, 20.8f)
                lineTo(16.65f, 18.35f)
                lineTo(14.2f, 17.5f)
                lineTo(16.65f, 16.65f)
                close()
            }
        }
    }

    /** Apple Verified Shield Check (`checkmark.seal.fill`) */
    val VerifiedShield: ImageVector by lazy {
        icon("VerifiedShield") {
            path(fill = INK, pathFillType = PathFillType.EvenOdd) {
                moveTo(12.0f, 2.75f)
                lineTo(19.0f, 5.45f)
                curveTo(19.55f, 5.66f, 19.9f, 6.2f, 19.9f, 6.78f)
                lineTo(19.9f, 11.8f)
                curveTo(19.9f, 16.35f, 16.62f, 19.85f, 12.42f, 21.12f)
                curveTo(12.15f, 21.2f, 11.85f, 21.2f, 11.58f, 21.12f)
                curveTo(7.38f, 19.85f, 4.1f, 16.35f, 4.1f, 11.8f)
                lineTo(4.1f, 6.78f)
                curveTo(4.1f, 6.2f, 4.45f, 5.66f, 5.0f, 5.45f)
                close()
                // Crisp inner checkmark cutout
                moveTo(16.1f, 9.45f)
                lineTo(14.85f, 8.2f)
                lineTo(10.85f, 12.2f)
                lineTo(9.15f, 10.5f)
                lineTo(7.9f, 11.75f)
                lineTo(10.85f, 14.7f)
                close()
            }
        }
    }

    /** Perforated Retail Coupon Ticket (`ticket`) */
    val CouponTicket: ImageVector by lazy {
        icon("CouponTicket") {
            path(
                stroke = INK,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3.75f, 8.0f)
                curveTo(3.75f, 6.9f, 4.65f, 6.0f, 5.75f, 6.0f)
                lineTo(18.25f, 6.0f)
                curveTo(19.35f, 6.0f, 20.25f, 6.9f, 20.25f, 8.0f)
                lineTo(20.25f, 9.8f)
                arcTo(2.2f, 2.2f, 0f, false, false, 20.25f, 14.2f)
                lineTo(20.25f, 16.0f)
                curveTo(20.25f, 17.1f, 19.35f, 18.0f, 18.25f, 18.0f)
                lineTo(5.75f, 18.0f)
                curveTo(4.65f, 18.0f, 3.75f, 17.1f, 3.75f, 16.0f)
                lineTo(3.75f, 14.2f)
                arcTo(2.2f, 2.2f, 0f, false, false, 3.75f, 9.8f)
                close()
                moveTo(10.0f, 14.5f)
                lineTo(14.0f, 9.5f)
            }
            path(fill = INK) {
                moveTo(9.8f, 9.4f)
                arcTo(0.95f, 0.95f, 0f, true, false, 9.8f, 11.3f)
                arcTo(0.95f, 0.95f, 0f, true, false, 9.8f, 9.4f)
                close()
                moveTo(14.2f, 12.7f)
                arcTo(0.95f, 0.95f, 0f, true, false, 14.2f, 14.6f)
                arcTo(0.95f, 0.95f, 0f, true, false, 14.2f, 12.7f)
                close()
            }
        }
    }

    /** Soft-curved Worth / Upvote Thumb (Outlined) */
    val ThumbUpOutlined: ImageVector by lazy {
        icon("ThumbUpOutlined") {
            path(
                stroke = INK,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7.0f, 10.5f)
                lineTo(7.0f, 19.0f)
                lineTo(4.75f, 19.0f)
                curveTo(4.2f, 19.0f, 3.75f, 18.55f, 3.75f, 18.0f)
                lineTo(3.75f, 11.5f)
                curveTo(3.75f, 10.95f, 4.2f, 10.5f, 4.75f, 10.5f)
                close()
                moveTo(7.0f, 10.5f)
                lineTo(10.6f, 4.8f)
                curveTo(11.1f, 4.0f, 12.3f, 4.15f, 12.6f, 5.05f)
                curveTo(12.78f, 5.6f, 12.65f, 6.25f, 12.4f, 6.85f)
                lineTo(11.7f, 9.25f)
                lineTo(17.75f, 9.25f)
                curveTo(19.15f, 9.25f, 20.15f, 10.55f, 19.8f, 11.9f)
                lineTo(18.55f, 17.2f)
                curveTo(18.3f, 18.25f, 17.35f, 19.0f, 16.25f, 19.0f)
                lineTo(7.0f, 19.0f)
            }
        }
    }

    /** Soft-curved Worth / Upvote Thumb (Filled Active) */
    val ThumbUpFilled: ImageVector by lazy {
        icon("ThumbUpFilled") {
            path(fill = INK) {
                moveTo(4.75f, 10.0f)
                curveTo(3.92f, 10.0f, 3.25f, 10.67f, 3.25f, 11.5f)
                lineTo(3.25f, 18.0f)
                curveTo(3.25f, 18.83f, 3.92f, 19.5f, 4.75f, 19.5f)
                lineTo(6.5f, 19.5f)
                lineTo(6.5f, 10.0f)
                close()
                moveTo(8.0f, 10.1f)
                lineTo(8.0f, 19.5f)
                lineTo(16.25f, 19.5f)
                curveTo(17.6f, 19.5f, 18.75f, 18.58f, 19.08f, 17.28f)
                lineTo(20.3f, 12.0f)
                curveTo(20.72f, 10.22f, 19.37f, 8.5f, 17.55f, 8.5f)
                lineTo(12.75f, 8.5f)
                lineTo(13.3f, 6.6f)
                curveTo(13.68f, 5.3f, 12.8f, 3.8f, 11.45f, 3.8f)
                curveTo(10.8f, 3.8f, 10.2f, 4.15f, 9.85f, 4.72f)
                close()
            }
        }
    }

    /** Soft-curved Pass / Downvote Thumb (Outlined) */
    val ThumbDownOutlined: ImageVector by lazy {
        icon("ThumbDownOutlined") {
            path(
                stroke = INK,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(17.0f, 13.5f)
                lineTo(17.0f, 5.0f)
                lineTo(19.25f, 5.0f)
                curveTo(19.8f, 5.0f, 20.25f, 5.45f, 20.25f, 6.0f)
                lineTo(20.25f, 12.5f)
                curveTo(20.25f, 13.05f, 19.8f, 13.5f, 19.25f, 13.5f)
                close()
                moveTo(17.0f, 13.5f)
                lineTo(13.4f, 19.2f)
                curveTo(12.9f, 20.0f, 11.7f, 19.85f, 11.4f, 18.95f)
                curveTo(11.22f, 18.4f, 11.35f, 17.75f, 11.6f, 17.15f)
                lineTo(12.3f, 14.75f)
                lineTo(6.25f, 14.75f)
                curveTo(4.85f, 14.75f, 3.85f, 13.45f, 4.2f, 12.1f)
                lineTo(5.45f, 6.8f)
                curveTo(5.7f, 5.75f, 6.65f, 5.0f, 7.75f, 5.0f)
                lineTo(17.0f, 5.0f)
            }
        }
    }

    /** Overlapping Rounded Cards Copy (`doc.on.doc`) */
    val CopyLayers: ImageVector by lazy {
        icon("CopyLayers") {
            path(
                stroke = INK,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9.0f, 8.75f)
                curveTo(9.0f, 7.78f, 9.78f, 7.0f, 10.75f, 7.0f)
                lineTo(17.75f, 7.0f)
                curveTo(18.72f, 7.0f, 19.5f, 7.78f, 19.5f, 8.75f)
                lineTo(19.5f, 17.75f)
                curveTo(19.5f, 18.72f, 18.72f, 19.5f, 17.75f, 19.5f)
                lineTo(10.75f, 19.5f)
                curveTo(9.78f, 19.5f, 9.0f, 18.72f, 9.0f, 17.75f)
                close()
                moveTo(6.25f, 15.5f)
                curveTo(5.28f, 15.5f, 4.5f, 14.72f, 4.5f, 13.75f)
                lineTo(4.5f, 5.75f)
                curveTo(4.5f, 4.78f, 5.28f, 4.0f, 6.25f, 4.0f)
                lineTo(13.25f, 4.0f)
                curveTo(14.22f, 4.0f, 15.0f, 4.78f, 15.0f, 5.75f)
            }
        }
    }

    /** Smooth Curved Price Sparkline Chart (`chart.xyaxis.line`) */
    val PriceChart: ImageVector by lazy {
        icon("PriceChart") {
            path(
                stroke = INK,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4.0f, 19.25f)
                lineTo(20.0f, 19.25f)
                moveTo(4.5f, 15.5f)
                lineTo(9.0f, 10.5f)
                lineTo(13.2f, 13.3f)
                lineTo(19.25f, 6.25f)
            }
            path(fill = INK) {
                moveTo(19.25f, 4.5f)
                arcTo(1.75f, 1.75f, 0f, true, false, 19.25f, 8.0f)
                arcTo(1.75f, 1.75f, 0f, true, false, 19.25f, 4.5f)
                close()
            }
        }
    }

    /** Sleek Add Link (`link.badge.plus`) */
    val LinkAdd: ImageVector by lazy {
        icon("LinkAdd") {
            path(
                stroke = INK,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10.0f, 14.0f)
                lineTo(14.0f, 10.0f)
                moveTo(11.2f, 7.2f)
                lineTo(13.1f, 5.3f)
                arcTo(3.5f, 3.5f, 0f, true, true, 18.05f, 10.25f)
                lineTo(16.15f, 12.15f)
                moveTo(12.8f, 16.8f)
                lineTo(10.9f, 18.7f)
                arcTo(3.5f, 3.5f, 0f, true, true, 5.95f, 13.75f)
                lineTo(7.85f, 11.85f)
                moveTo(17.5f, 15.25f)
                lineTo(17.5f, 20.25f)
                moveTo(15.0f, 17.75f)
                lineTo(20.0f, 17.75f)
            }
        }
    }

    /** Minimalist Storefront (`storefront`) */
    val Storefront: ImageVector by lazy {
        icon("Storefront") {
            path(
                stroke = INK,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4.25f, 9.75f)
                lineTo(5.6f, 4.85f)
                curveTo(5.8f, 4.15f, 6.42f, 3.75f, 7.15f, 3.75f)
                lineTo(16.85f, 3.75f)
                curveTo(17.58f, 3.75f, 18.2f, 4.15f, 18.4f, 4.85f)
                lineTo(19.75f, 9.75f)
                curveTo(19.75f, 11.15f, 18.65f, 12.25f, 17.25f, 12.25f)
                curveTo(16.1f, 12.25f, 15.15f, 11.5f, 14.8f, 10.5f)
                curveTo(14.45f, 11.5f, 13.4f, 12.25f, 12.0f, 12.25f)
                curveTo(10.6f, 12.25f, 9.55f, 11.5f, 9.2f, 10.5f)
                curveTo(8.85f, 11.5f, 7.9f, 12.25f, 6.75f, 12.25f)
                curveTo(5.35f, 12.25f, 4.25f, 11.15f, 4.25f, 9.75f)
                close()
                moveTo(5.5f, 12.25f)
                lineTo(5.5f, 19.25f)
                curveTo(5.5f, 19.8f, 5.95f, 20.25f, 6.5f, 20.25f)
                lineTo(17.5f, 20.25f)
                curveTo(18.05f, 20.25f, 18.5f, 19.8f, 18.5f, 19.25f)
                lineTo(18.5f, 12.25f)
                moveTo(10.0f, 20.25f)
                lineTo(10.0f, 15.75f)
                curveTo(10.0f, 15.2f, 10.45f, 14.75f, 11.0f, 14.75f)
                lineTo(13.0f, 14.75f)
                curveTo(13.55f, 14.75f, 14.0f, 15.2f, 14.0f, 15.75f)
                lineTo(14.0f, 20.25f)
            }
        }
    }
}

/**
 * Miniature Apple Squircle App Icon mark used in the TopAppBar to unify
 * the home-screen launcher brand identity with the in-app navigation header.
 */
@Composable
fun WorthItBrandIconBadge(
    size: Dp = 28.dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.worthit_apple_icon_1795419429060),
        contentDescription = "WorthIt Brand Icon",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
    )
}
