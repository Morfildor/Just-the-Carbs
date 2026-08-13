package app.carbscan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.carbscan.R
import app.carbscan.domain.Product
import app.carbscan.domain.ProductDataOrigin
import app.carbscan.ui.theme.Space

/**
 * The provenance badge (§23, §25).
 *
 * States what the value is and where it came from without alarming the user, and without implying
 * that crowd-sourced data has been medically validated. Never colour alone — the label always
 * carries the meaning in words (§39).
 */
@Composable
fun SourceBadge(product: Product, modifier: Modifier = Modifier) {
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
                        MaterialTheme.colorScheme.surfaceVariant
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
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        // Unverified online data gets a plain-language nudge rather than a warning icon: the value
        // is usually right, and alarming the user every time would train them to ignore it (§25).
        if (!verified && product.dataSource == ProductDataOrigin.OPEN_FOOD_FACTS) {
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
 */
@Composable
fun RecoveryPanel(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Space.l),
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
