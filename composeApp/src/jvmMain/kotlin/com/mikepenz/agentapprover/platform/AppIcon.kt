package com.mikepenz.agentapprover.platform

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Area
import java.awt.geom.GeneralPath
import java.awt.image.BufferedImage
import kotlin.math.*

/**
 * Generates the app/tray icon: white Claude logo filling the icon with a small
 * badge (Lucide badge-check outline + checkmark) in the bottom right. The badge
 * cuts out from the Claude logo with a small gap around it.
 *
 * When pendingCount > 0, the badge turns red and shows the count.
 */
object AppIcon {

    private val BADGE_GREEN = Color(0x4C, 0xAF, 0x50)
    private val BADGE_RED = Color(0xF4, 0x43, 0x36)

    /**
     * Creates a tray icon as a MultiResolutionImage for HiDPI (1x + 2x).
     * macOS template images must be black — the OS handles light/dark adaptation.
     */
    fun createTrayIconMultiRes(pendingCount: Int = 0): java.awt.Image {
        val img1x = create(22, pendingCount, trayMode = true)
        val img2x = create(44, pendingCount, trayMode = true)
        return java.awt.image.BaseMultiResolutionImage(img1x, img2x)
    }

    /**
     * Creates the app icon at the given pixel size.
     * @param trayMode If true, renders the logo in black (macOS template image)
     */
    fun create(size: Int, pendingCount: Int = 0, trayMode: Boolean = false): BufferedImage {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)

        val padding = size * 0.08f // Small padding for macOS alignment
        val logoSize = size - padding * 2

        // Build the Claude logo shape (filled area)
        val claudeShape = buildClaudeLogoShape(padding, padding, logoSize)

        // Badge geometry — diameter determines badge-check outline size
        val badgeDiameter = size * 0.46f
        val badgeRadius = badgeDiameter / 2f
        val badgeCenterX = size - badgeRadius - size * 0.04f
        val badgeCenterY = size - badgeRadius - size * 0.04f

        // Cut out the badge area from the Claude logo (circle cutout with gap)
        val cutoutRadius = badgeRadius + size * 0.04f
        val cutoutShape = buildBadgeOutline(badgeCenterX, badgeCenterY, (cutoutRadius * 2))
        val logoArea = Area(claudeShape)
        logoArea.subtract(Area(cutoutShape))

        // Draw Claude logo with cutout
        g.color = if (trayMode) Color.BLACK else Color.WHITE
        g.fill(logoArea)

        // Draw badge shape (filled badge-check outline)
        val badgeShape = buildBadgeOutline(badgeCenterX, badgeCenterY, badgeDiameter)
        val badgeColor = if (pendingCount > 0) BADGE_RED else BADGE_GREEN
        g.color = badgeColor
        g.fill(badgeShape)

        // Badge content
        g.color = Color.WHITE
        if (pendingCount > 0) {
            // Number
            val fontSize = (badgeRadius * 1.2f).toInt()
            g.font = Font(Font.SANS_SERIF, Font.BOLD, fontSize)
            val text = if (pendingCount > 9) "9+" else pendingCount.toString()
            val fm = g.fontMetrics
            val textX = badgeCenterX - fm.stringWidth(text) / 2f
            val textY = badgeCenterY + (fm.ascent - fm.descent) / 2f
            g.drawString(text, textX, textY)
        } else {
            // Checkmark (from the Lucide badge-check SVG inner path)
            val stroke = BasicStroke(
                badgeRadius * 0.22f,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND,
            )
            g.stroke = stroke
            // Checkmark scaled to badge: m9 12 2 2 4-4 in 24x24 viewBox
            val bs = badgeRadius / 12f // scale from viewBox center
            g.drawLine(
                (badgeCenterX - bs * 3f).toInt(), (badgeCenterY + bs * 0.5f).toInt(),
                (badgeCenterX - bs * 0.5f).toInt(), (badgeCenterY + bs * 2.5f).toInt(),
            )
            g.drawLine(
                (badgeCenterX - bs * 0.5f).toInt(), (badgeCenterY + bs * 2.5f).toInt(),
                (badgeCenterX + bs * 3.5f).toInt(), (badgeCenterY - bs * 2.5f).toInt(),
            )
        }

        g.dispose()
        return image
    }

    /**
     * Builds the Lucide badge-check outline shape centered at (cx, cy) with given diameter.
     * SVG path: M3.85 8.62 a4 4 0 0 1 4.78-4.77 ... Z (viewBox 0 0 24 24)
     */
    private fun buildBadgeOutline(cx: Float, cy: Float, diameter: Float): java.awt.Shape {
        val scale = diameter.toDouble() / 24.0
        val ox = cx.toDouble() - 12.0 * scale
        val oy = cy.toDouble() - 12.0 * scale
        val r = 4.0 * scale

        val path = GeneralPath()
        path.moveTo((ox + 3.85 * scale).toFloat(), (oy + 8.62 * scale).toFloat())

        // 8 arc segments — absolute endpoints in 24x24 viewBox coordinates
        // All: rx=4, ry=4, rotation=0, large-arc=false, sweep=true
        val endpoints = doubleArrayOf(
            8.63, 3.85,
            15.37, 3.85,
            20.15, 8.63,
            20.15, 15.37,
            15.38, 20.15,
            8.63, 20.15,
            3.85, 15.38,
            3.85, 8.62,
        )

        for (i in endpoints.indices step 2) {
            svgArcTo(path, r, r, 0.0, largeArc = false, sweep = true,
                ox + endpoints[i] * scale, oy + endpoints[i + 1] * scale)
        }

        path.closePath()
        return path
    }

    /**
     * Converts an SVG arc segment to cubic bezier curves and appends to path.
     * Implements the SVG spec endpoint-to-center arc parametrization (F.6.5–F.6.6).
     */
    private fun svgArcTo(
        path: GeneralPath,
        rx: Double, ry: Double,
        xRot: Double,
        largeArc: Boolean, sweep: Boolean,
        x2: Double, y2: Double,
    ) {
        val pt = path.currentPoint
        val x1 = pt.x
        val y1 = pt.y

        if (rx == 0.0 || ry == 0.0) {
            path.lineTo(x2.toFloat(), y2.toFloat())
            return
        }

        val phi = Math.toRadians(xRot)
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)

        val dx2 = (x1 - x2) / 2.0
        val dy2 = (y1 - y2) / 2.0
        val x1p = cosPhi * dx2 + sinPhi * dy2
        val y1p = -sinPhi * dx2 + cosPhi * dy2

        var rxA = abs(rx)
        var ryA = abs(ry)
        val x1pSq = x1p * x1p
        val y1pSq = y1p * y1p

        // Adjust radii if needed
        val lambdaSq = x1pSq / (rxA * rxA) + y1pSq / (ryA * ryA)
        if (lambdaSq > 1.0) {
            val lambda = sqrt(lambdaSq)
            rxA *= lambda
            ryA *= lambda
        }

        val rxSq = rxA * rxA
        val rySq = ryA * ryA

        // Center parametrization
        var sq = (rxSq * rySq - rxSq * y1pSq - rySq * x1pSq) /
                (rxSq * y1pSq + rySq * x1pSq)
        if (sq < 0) sq = 0.0
        val sign = if (largeArc == sweep) -1.0 else 1.0
        val coef = sign * sqrt(sq)
        val cxp = coef * rxA * y1p / ryA
        val cyp = coef * -ryA * x1p / rxA

        val cx = cosPhi * cxp - sinPhi * cyp + (x1 + x2) / 2.0
        val cy = sinPhi * cxp + cosPhi * cyp + (y1 + y2) / 2.0

        // Compute angles
        val theta1 = vecAngle(1.0, 0.0, (x1p - cxp) / rxA, (y1p - cyp) / ryA)
        var dtheta = vecAngle(
            (x1p - cxp) / rxA, (y1p - cyp) / ryA,
            (-x1p - cxp) / rxA, (-y1p - cyp) / ryA,
        )
        if (!sweep && dtheta > 0) dtheta -= 2 * PI
        if (sweep && dtheta < 0) dtheta += 2 * PI

        // Split into bezier segments (max 90° each)
        val segs = ceil(abs(dtheta) / (PI / 2)).toInt().coerceAtLeast(1)
        val segAngle = dtheta / segs
        val alpha = 4.0 / 3.0 * tan(segAngle / 4.0)

        var angle = theta1
        var curX = x1
        var curY = y1

        for (i in 0 until segs) {
            val nextAngle = angle + segAngle
            val cosA = cos(angle)
            val sinA = sin(angle)
            val cosNA = cos(nextAngle)
            val sinNA = sin(nextAngle)

            // Endpoint
            val epx = cosPhi * rxA * cosNA - sinPhi * ryA * sinNA + cx
            val epy = sinPhi * rxA * cosNA + cosPhi * ryA * sinNA + cy

            // Control point 1 (tangent at current)
            val dx1t = -rxA * sinA
            val dy1t = ryA * cosA
            val cp1x = curX + alpha * (cosPhi * dx1t - sinPhi * dy1t)
            val cp1y = curY + alpha * (sinPhi * dx1t + cosPhi * dy1t)

            // Control point 2 (tangent at end, reversed)
            val dx2t = -rxA * sinNA
            val dy2t = ryA * cosNA
            val cp2x = epx - alpha * (cosPhi * dx2t - sinPhi * dy2t)
            val cp2y = epy - alpha * (sinPhi * dx2t + cosPhi * dy2t)

            path.curveTo(
                cp1x.toFloat(), cp1y.toFloat(),
                cp2x.toFloat(), cp2y.toFloat(),
                epx.toFloat(), epy.toFloat(),
            )

            curX = epx
            curY = epy
            angle = nextAngle
        }
    }

    private fun vecAngle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
        val dot = ux * vx + uy * vy
        val len = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
        var ang = acos((dot / len).coerceIn(-1.0, 1.0))
        if (ux * vy - uy * vx < 0) ang = -ang
        return ang
    }

    /**
     * Builds the Claude logo as a filled Shape from the SVG path data.
     * The SVG viewBox is 0 0 16 16; we scale and translate to fit.
     */
    private fun buildClaudeLogoShape(x: Float, y: Float, size: Float): java.awt.Shape {
        val s = size / 16f // scale factor

        val path = GeneralPath()

        // Claude logo SVG path (from bootstrap-icons bi-claude)
        // Converted to moveTo/lineTo/curveTo calls
        path.moveTo(x + 3.127f * s, y + 10.604f * s)
        path.lineTo(x + 6.262f * s, y + 8.844f * s)
        path.lineTo(x + 6.315f * s, y + 8.691f * s)
        path.lineTo(x + 6.262f * s, y + 8.606f * s)
        path.lineTo(x + 6.11f * s, y + 8.606f * s)
        path.lineTo(x + 5.585f * s, y + 8.574f * s)
        path.lineTo(x + 3.794f * s, y + 8.526f * s)
        path.lineTo(x + 2.24f * s, y + 8.461f * s)
        path.lineTo(x + 0.735f * s, y + 8.381f * s)
        path.lineTo(x + 0.355f * s, y + 8.3f * s)
        path.lineTo(x + 0f * s, y + 7.832f * s)
        path.lineTo(x + 0.036f * s, y + 7.598f * s)
        path.lineTo(x + 0.356f * s, y + 7.384f * s)
        path.lineTo(x + 0.811f * s, y + 7.424f * s)
        path.lineTo(x + 1.82f * s, y + 7.493f * s)
        path.lineTo(x + 3.333f * s, y + 7.598f * s)
        path.lineTo(x + 4.43f * s, y + 7.662f * s)
        path.lineTo(x + 6.056f * s, y + 7.832f * s)
        path.lineTo(x + 6.315f * s, y + 7.832f * s)
        path.lineTo(x + 6.351f * s, y + 7.727f * s)
        path.lineTo(x + 6.262f * s, y + 7.662f * s)
        path.lineTo(x + 6.194f * s, y + 7.598f * s)
        path.lineTo(x + 4.628f * s, y + 6.536f * s)
        path.lineTo(x + 2.933f * s, y + 5.415f * s)
        path.lineTo(x + 2.046f * s, y + 4.769f * s)
        path.lineTo(x + 1.566f * s, y + 4.442f * s)
        path.lineTo(x + 1.323f * s, y + 4.136f * s)
        path.lineTo(x + 1.219f * s, y + 3.466f * s)
        path.lineTo(x + 1.654f * s, y + 2.986f * s)
        path.lineTo(x + 2.239f * s, y + 3.026f * s)
        path.lineTo(x + 2.389f * s, y + 3.066f * s)
        path.lineTo(x + 2.982f * s, y + 3.522f * s)
        path.lineTo(x + 4.249f * s, y + 4.503f * s)
        path.lineTo(x + 5.903f * s, y + 5.721f * s)
        path.lineTo(x + 6.145f * s, y + 5.923f * s)
        path.lineTo(x + 6.242f * s, y + 5.855f * s)
        path.lineTo(x + 6.254f * s, y + 5.806f * s)
        path.lineTo(x + 6.145f * s, y + 5.625f * s)
        path.lineTo(x + 5.245f * s, y + 3.999f * s)
        path.lineTo(x + 4.285f * s, y + 2.344f * s)
        path.lineTo(x + 3.857f * s, y + 1.658f * s)
        path.lineTo(x + 3.744f * s, y + 1.247f * s)

        // curve
        path.lineTo(x + 3.676f * s, y + 0.763f * s)
        path.lineTo(x + 4.172f * s, y + 0.089f * s)
        path.lineTo(x + 4.446f * s, y + 0f * s)
        path.lineTo(x + 5.108f * s, y + 0.089f * s)
        path.lineTo(x + 5.387f * s, y + 0.331f * s)
        path.lineTo(x + 5.798f * s, y + 1.271f * s)
        path.lineTo(x + 6.464f * s, y + 2.751f * s)
        path.lineTo(x + 7.497f * s, y + 4.765f * s)
        path.lineTo(x + 7.799f * s, y + 5.362f * s)
        path.lineTo(x + 7.961f * s, y + 5.915f * s)
        path.lineTo(x + 8.021f * s, y + 6.085f * s)
        path.lineTo(x + 8.126f * s, y + 6.085f * s)
        path.lineTo(x + 8.126f * s, y + 5.988f * s)
        path.lineTo(x + 8.211f * s, y + 4.854f * s)
        path.lineTo(x + 8.368f * s, y + 3.462f * s)
        path.lineTo(x + 8.522f * s, y + 1.67f * s)
        path.lineTo(x + 8.574f * s, y + 1.166f * s)
        path.lineTo(x + 8.824f * s, y + 0.561f * s)
        path.lineTo(x + 9.321f * s, y + 0.234f * s)
        path.lineTo(x + 9.708f * s, y + 0.42f * s)
        path.lineTo(x + 10.027f * s, y + 0.876f * s)
        path.lineTo(x + 9.982f * s, y + 1.17f * s)
        path.lineTo(x + 9.792f * s, y + 2.4f * s)
        path.lineTo(x + 9.422f * s, y + 4.33f * s)
        path.lineTo(x + 9.179f * s, y + 5.62f * s)
        path.lineTo(x + 9.321f * s, y + 5.62f * s)
        path.lineTo(x + 9.482f * s, y + 5.46f * s)
        path.lineTo(x + 10.136f * s, y + 4.592f * s)
        path.lineTo(x + 11.233f * s, y + 3.22f * s)
        path.lineTo(x + 11.717f * s, y + 2.675f * s)
        path.lineTo(x + 12.282f * s, y + 2.074f * s)
        path.lineTo(x + 12.645f * s, y + 1.787f * s)
        path.lineTo(x + 13.331f * s, y + 1.787f * s)
        path.lineTo(x + 13.836f * s, y + 2.538f * s)
        path.lineTo(x + 13.61f * s, y + 3.313f * s)
        path.lineTo(x + 12.903f * s, y + 4.208f * s)
        path.lineTo(x + 12.318f * s, y + 4.967f * s)
        path.lineTo(x + 11.479f * s, y + 6.097f * s)
        path.lineTo(x + 10.955f * s, y + 7.001f * s)
        path.lineTo(x + 11.003f * s, y + 7.073f * s)
        path.lineTo(x + 11.128f * s, y + 7.061f * s)
        path.lineTo(x + 13.025f * s, y + 6.658f * s)
        path.lineTo(x + 14.049f * s, y + 6.472f * s)
        path.lineTo(x + 15.272f * s, y + 6.262f * s)
        path.lineTo(x + 15.825f * s, y + 6.52f * s)
        path.lineTo(x + 15.885f * s, y + 6.783f * s)
        path.lineTo(x + 15.667f * s, y + 7.319f * s)
        path.lineTo(x + 14.36f * s, y + 7.642f * s)
        path.lineTo(x + 12.827f * s, y + 7.949f * s)
        path.lineTo(x + 10.543f * s, y + 8.489f * s)
        path.lineTo(x + 10.515f * s, y + 8.509f * s)
        path.lineTo(x + 10.547f * s, y + 8.549f * s)
        path.lineTo(x + 11.576f * s, y + 8.647f * s)
        path.lineTo(x + 12.016f * s, y + 8.671f * s)
        path.lineTo(x + 13.093f * s, y + 8.671f * s)
        path.lineTo(x + 15.098f * s, y + 8.821f * s)
        path.lineTo(x + 15.623f * s, y + 9.167f * s)
        path.lineTo(x + 15.938f * s, y + 9.591f * s)
        path.lineTo(x + 15.885f * s, y + 9.914f * s)
        path.lineTo(x + 15.078f * s, y + 10.325f * s)
        path.lineTo(x + 11.447f * s, y + 9.462f * s)
        path.lineTo(x + 10.575f * s, y + 9.244f * s)
        path.lineTo(x + 10.455f * s, y + 9.244f * s)
        path.lineTo(x + 10.455f * s, y + 9.317f * s)
        path.lineTo(x + 11.181f * s, y + 10.027f * s)
        path.lineTo(x + 12.512f * s, y + 11.229f * s)
        path.lineTo(x + 14.179f * s, y + 12.779f * s)
        path.lineTo(x + 14.263f * s, y + 13.162f * s)
        path.lineTo(x + 14.049f * s, y + 13.464f * s)
        path.lineTo(x + 13.823f * s, y + 13.432f * s)
        path.lineTo(x + 12.359f * s, y + 12.331f * s)
        path.lineTo(x + 11.794f * s, y + 11.834f * s)
        path.lineTo(x + 10.514f * s, y + 10.757f * s)
        path.lineTo(x + 10.43f * s, y + 10.757f * s)
        path.lineTo(x + 10.43f * s, y + 10.87f * s)
        path.lineTo(x + 10.725f * s, y + 11.302f * s)
        path.lineTo(x + 12.282f * s, y + 13.642f * s)
        path.lineTo(x + 12.362f * s, y + 14.36f * s)
        path.lineTo(x + 12.25f * s, y + 14.594f * s)
        path.lineTo(x + 11.846f * s, y + 14.735f * s)
        path.lineTo(x + 11.402f * s, y + 14.655f * s)
        path.lineTo(x + 10.491f * s, y + 13.375f * s)
        path.lineTo(x + 9.551f * s, y + 11.935f * s)
        path.lineTo(x + 8.792f * s, y + 10.644f * s)
        path.lineTo(x + 8.699f * s, y + 10.697f * s)
        path.lineTo(x + 8.251f * s, y + 15.518f * s)
        path.lineTo(x + 8.041f * s, y + 15.764f * s)
        path.lineTo(x + 7.557f * s, y + 15.95f * s)
        path.lineTo(x + 7.154f * s, y + 15.643f * s)
        path.lineTo(x + 6.94f * s, y + 15.147f * s)
        path.lineTo(x + 7.154f * s, y + 14.167f * s)
        path.lineTo(x + 7.412f * s, y + 12.887f * s)
        path.lineTo(x + 7.622f * s, y + 11.871f * s)
        path.lineTo(x + 7.812f * s, y + 10.608f * s)
        path.lineTo(x + 7.924f * s, y + 10.188f * s)
        path.lineTo(x + 7.916f * s, y + 10.16f * s)
        path.lineTo(x + 7.824f * s, y + 10.172f * s)
        path.lineTo(x + 6.871f * s, y + 11.479f * s)
        path.lineTo(x + 5.423f * s, y + 13.436f * s)
        path.lineTo(x + 4.277f * s, y + 14.663f * s)
        path.lineTo(x + 4.003f * s, y + 14.772f * s)
        path.lineTo(x + 3.526f * s, y + 14.525f * s)
        path.lineTo(x + 3.571f * s, y + 14.085f * s)
        path.lineTo(x + 3.837f * s, y + 13.695f * s)
        path.lineTo(x + 5.423f * s, y + 11.677f * s)
        path.lineTo(x + 6.379f * s, y + 10.427f * s)
        path.lineTo(x + 6.996f * s, y + 9.704f * s)
        path.lineTo(x + 6.992f * s, y + 9.599f * s)
        path.lineTo(x + 6.956f * s, y + 9.599f * s)
        path.lineTo(x + 2.744f * s, y + 12.335f * s)
        path.lineTo(x + 1.994f * s, y + 12.431f * s)
        path.lineTo(x + 1.67f * s, y + 12.129f * s)
        path.lineTo(x + 1.71f * s, y + 11.633f * s)
        path.lineTo(x + 1.864f * s, y + 11.471f * s)
        path.lineTo(x + 3.131f * s, y + 10.6f * s)
        path.closePath()

        return path
    }
}
