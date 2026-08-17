package app.justthecarbs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.justthecarbs.R
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors

/**
 * The provenance badge (§23, §25).
 *
 * States what the value is and where it came from without alarming the user, and without implying
 * that crowd-sourced data has been medically validated. Never colour alone — the label always
 * carries the meaning in words (§39).
 */
@Composable
fun SourceBadge(
    product: Product,
    modifier: Modifier = Modifier,
    /**
     * Whether to show the plain-language line under an unverified online value.
     *
     * The calculator drops it while the keyboard is open: it is advice to act on before committing
     * to a portion, and during typing the field and the result need the height. The badge itself —
     * which carries the provenance in words — never goes away.
     */
    showHint: Boolean = true,
) {
    val verified = product.isUserVerified
    val label = when {
        verified -> stringResource(R.string.product_source_verified)
        product.dataSource == ProductDataOrigin.MANUAL -> stringResource(R.string.product_source_manual)
        product.dataSource == ProductDataOrigin.OCR -> stringResource(R.string.product_source_ocr)
        else -> stringResource(R.string.product_source_online)
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .background(
                    color = if (verified) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.extendedColors.orangeSoft
                    },
                    shape = RoundedCornerShape(50),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (verified) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.extendedColors.onOrangeSoft
                },
            )
        }

        // Unverified online data gets a plain-language nudge rather than a warning icon: the value
        // is usually right, and alarming the user every time would train them to ignore it (§25).
        if (showHint && !verified && product.dataSource == ProductDataOrigin.OPEN_FOOD_FACTS) {
            Text(
                text = stringResource(R.string.product_source_online_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs, start = Space.xs),
            )
        }
    }
}

@Composable
fun FavoriteButton(favorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add)
    IconButton(
        onClick = onToggle,
        modifier = modifier
            .size(Space.minTouchTarget)
            .semantics { contentDescription = description },
    ) {
        Icon(
            imageVector = if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
            contentDescription = null,
            tint = if (favorite) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * A recoverable dead end (§13, §26, §32, §36).
 *
 * Callers always pass at least one action. A message with no way forward is precisely the dead end
 * the brief forbids.
 *
 * **Scrollable, and that is load-bearing.** *Product not found* now offers four ways forward, and
 * this was a plain Column: at a large font scale on a short display the last action would have been
 * clipped with nothing to reveal it, which is not "below the fold" but *gone*. This repo has been
 * caught by that twice already — an action ordered after tall content that `LazyColumn` never
 * composed, and a control under the keyboard that swallowed its own clicks. An unreachable recovery
 * action on a dead-end screen is the same defect with worse consequences, since this screen exists
 * precisely because the user is already stuck.
 */
@Composable
fun RecoveryPanel(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Space.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s),
            content = { actions() },
        )
    }
}

/**
 * The two action shapes a [RecoveryPanel] offers: one obvious way forward, and the alternatives.
 *
 * They live here rather than beside the calculator because every recovery panel in the app uses the
 * same pair, and a second copy would be the point at which two dead-end screens start to drift
 * apart in size and emphasis.
 */
@Composable
fun PrimaryAction(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(Space.buttonRadius),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text(text) }
}

@Composable
fun SecondaryAction(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(Space.minTouchTarget),
    ) { Text(text) }
}

/** A section heading, announced as one so TalkBack can navigate by structure (§39). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * One search candidate (spec §9). Shared between the full search screen and Home's inline search
 * so the two never drift on what a hit shows.
 *
 * Says only what is known. A record with no carbohydrate value says so in words rather than showing
 * a zero — a "0 g carbs" card would be a confident wrong answer about food, which is exactly the
 * failure this app is built to avoid.
 */
@Composable
fun SearchResultRow(hit: ProductSearchHit, onClick: () -> Unit) {
    val carbsText = hit.carbsPer100?.let { carbs ->
        stringResource(
            R.string.search_carbs,
            carbs.stripTrailingZeros().toPlainString(),
            hit.basis.unitLabel,
        )
    } ?: stringResource(R.string.search_no_carbs)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Space.s)
            .semantics {
                contentDescription = "${hit.name}. ${hit.brand.orEmpty()} $carbsText"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchThumbnail(imageUrl = hit.imageUrl, name = hit.name)
        Spacer(Modifier.width(Space.m))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hit.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            // Brand and printed quantity together are usually what separates two otherwise
            // identical-looking cards on a shelf — a 390 g pack from a 600 g one.
            val subtitle = listOfNotNull(hit.brand, hit.packageQuantity).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                text = carbsText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
