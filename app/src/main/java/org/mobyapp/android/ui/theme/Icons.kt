package org.mobyapp.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icons the core Material icon set doesn't have.
 */
object MobyIcons {

    /**
     * A location arrow, for "Use current location", like the iOS app's
     * (Material's "Near Me" icon, Apache License 2.0).
     */
    val NearMe: ImageVector by lazy {
        ImageVector.Builder(name = "NearMe", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .path(fill = SolidColor(Color.Black)) {
                // M21 3L3 10.53v.98l6.84 2.65L12.48 21h.98L21 3z
                moveTo(21f, 3f)
                lineTo(3f, 10.53f)
                verticalLineToRelative(0.98f)
                lineToRelative(6.84f, 2.65f)
                lineTo(12.48f, 21f)
                horizontalLineToRelative(0.98f)
                lineTo(21f, 3f)
                close()
            }
            .build()
    }
}
