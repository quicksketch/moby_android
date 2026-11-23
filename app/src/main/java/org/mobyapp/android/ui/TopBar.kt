package org.mobyapp.android.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The top bar, laid out as in the iOS app: a bold, centered title, and a back
 * button with an arrow and the name of the screen it returns to, such as
 * "← Sites". See SPECIFICATIONS.md section 14.
 *
 * @param backLabel The screen the back button returns to, or null for none.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobyTopBar(
    title: String,
    backLabel: String? = null,
    onBack: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = { if (backLabel != null) BackButton(backLabel, onBack) },
        actions = actions,
    )
}

/** An arrow and the name of the screen it returns to. */
@Composable
private fun BackButton(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(start = 8.dp, end = 12.dp),
        modifier = Modifier.heightIn(min = 48.dp).testTag("back"),
    ) {
        // The arrow is decorative: the button is read as its label.
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
    }
}
