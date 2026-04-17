/*
Font Awesome Free License
-------------------------

Font Awesome Free is free, open source, and GPL friendly. You can use it for
commercial projects, open source projects, or really almost whatever you want.
Full Font Awesome Free license: https://fontawesome.com/license/free.

# Icons: CC BY 4.0 License (https://creativecommons.org/licenses/by/4.0/)
In the Font Awesome Free download, the CC BY 4.0 license applies to all icons
packaged as SVG and JS file types.

# Fonts: SIL OFL 1.1 License (https://scripts.sil.org/OFL)
In the Font Awesome Free download, the SIL OFL license applies to all icons
packaged as web and desktop font files.

# Code: MIT License (https://opensource.org/licenses/MIT)
In the Font Awesome Free download, the MIT license applies to all non-font and
non-icon files.

# Attribution
Attribution is required by MIT, SIL OFL, and CC BY licenses. Downloaded Font
Awesome Free files already contain embedded comments with sufficient
attribution, so you shouldn't need to do anything additional when using these
files normally.

We've kept attribution comments terse, so we ask that you do not actively work
to remove them from files, especially code. They're a great way for folks to
learn about Font Awesome.

# Brand Icons
All brand icons are trademarks of their respective owners. The use of these
trademarks does not indicate endorsement of the trademark holder by Font
Awesome, nor vice versa. **Please do not use brand logos for any purpose except
to represent the company, product, or service to which they refer.**

*/
package com.mikepenz.agentbuddy.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val FontAwesomeCaretDown: ImageVector
    get() {
        if (_FontAwesomeCaretDown != null) return _FontAwesomeCaretDown!!

        _FontAwesomeCaretDown = ImageVector.Builder(
            name = "caret-down",
            defaultWidth = 15.dp,
            defaultHeight = 24.dp,
            viewportWidth = 320f,
            viewportHeight = 512f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
            ) {
                moveTo(31.3f, 192f)
                horizontalLineToRelative(257.3f)
                curveToRelative(17.8f, 0f, 26.7f, 21.5f, 14.1f, 34.1f)
                lineTo(174.1f, 354.8f)
                curveToRelative(-7.8f, 7.8f, -20.5f, 7.8f, -28.3f, 0f)
                lineTo(17.2f, 226.1f)
                curveTo(4.6f, 213.5f, 13.5f, 192f, 31.3f, 192f)
                close()
            }
        }.build()

        return _FontAwesomeCaretDown!!
    }

private var _FontAwesomeCaretDown: ImageVector? = null
