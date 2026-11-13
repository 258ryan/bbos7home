package ai.heretek.pwnapple.components.table

import ai.heretek.pwnapple.theme.PwnappleTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A section wrapper in an iOS 6 grouped table with letterpress debossed
 * header and footer captions.
 */
@Composable
fun IosTableSection(
    modifier: Modifier = Modifier,
    headerText: String? = null,
    footerText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val typography = PwnappleTheme.typography

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        if (!headerText.isNullOrEmpty()) {
            Text(
                text = headerText,
                style = typography.sectionHeader,
                modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 6.dp)
            )
        }

        IosGroupedTable(content = content)

        if (!footerText.isNullOrEmpty()) {
            Text(
                text = footerText,
                style = typography.sectionFooter,
                modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 8.dp)
            )
        }
    }
}
