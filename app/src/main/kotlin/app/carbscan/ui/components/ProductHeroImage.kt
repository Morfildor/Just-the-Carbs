package app.carbscan.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.carbscan.domain.Product
import app.carbscan.domain.ProductImageSelector
import app.carbscan.ui.theme.Motion
import app.carbscan.ui.theme.Space
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/** Stable handle for the calculator's hero image, used by instrumented tests. */
const val PRODUCT_HERO_TAG = "product_hero_image"

/**
 * The calculator's product image (development-pass brief §4, §5).
 *
 * This exists to answer one question before the user trusts a number: **is this the package in my
 * hand?** A 56 dp thumbnail cannot answer it — products in a range often differ only by a colour
 * band or a flavour word — so the hero is large enough to recognise a package across a kitchen
 * counter, while the portion controls and the result stay on the same screen.
 *
 * Deliberate choices:
 *
 * - **[ContentScale.Fit], never a crop.** Cropping to fill a box is what removes the brand mark and
 *   the flavour name — precisely the parts the user is checking. A tall bottle or a wide box keeps
 *   its silhouette and gets letterboxed instead.
 * - **Compacts when the keyboard opens.** Identification matters before typing; while typing, the
 *   portion field and result matter more. One short transition, not a jump.
 * - **Never blocks the calculation.** The container occupies its space from the first frame and the
 *   image fades in behind it, so a slow or absent image cannot reflow the screen or delay a number
 *   (§33).
 * - **Monogram fallback**, the same one Recents uses — a grey box would read as broken.
 */
@Composable
fun ProductHeroImage(
    product: Product,
    /** True while the IME is open, so the image yields room to the portion controls. */
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val height by animateDpAsState(
        targetValue = if (compact) COMPACT_HEIGHT else FULL_HEIGHT,
        animationSpec = tween(Motion.STANDARD_MS),
        label = "heroHeight",
    )

    ProductHeroImageContent(product = product, height = height, onClick = onClick, modifier = modifier)
}

@Composable
private fun ProductHeroImageContent(
    product: Product,
    height: Dp,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Space.mediaRadius)
    // Same validator as everywhere else — a bigger image is still an untrusted URL (§5, §24).
    val imageUrl = remember(product.imageUrl, product.largeImageUrl) {
        ProductImageSelector.heroImageUrl(product)
    }
    var loaded by remember(imageUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            // White, not a tinted surface — and this is a correction made by actually looking at
            // the rendered screen rather than reasoning about it. Open Food Facts product photos
            // are shot on white and carry that background in the JPEG, so a warm-grey container
            // put a hard white rectangle inside a grey box: the frame fought the photo instead of
            // holding it. Matching the photos' own background makes the image sit on the page.
            //
            // In dark mode the same reasoning gives a light-but-not-white plate: a pure white slab
            // is a glare source at night, while a dark plate would still clash with the baked-in
            // white of the photo itself.
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .testTag(PRODUCT_HERO_TAG)
            // The product name is displayed directly above; announcing the image too would make a
            // screen reader say the same thing twice (§39).
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                text = product.monogram(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 40.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (imageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(Motion.STANDARD_MS)
                    .build(),
                contentDescription = null,
                // Fit, not Crop: see the class comment. Padding keeps an unusually narrow package
                // from touching the container's edges.
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                modifier = Modifier.fillMaxWidth().height(height).padding(Space.s),
            )
        }
    }
}

/** ~150 dp: large enough to identify a package, small enough to leave the result dominant (§5). */
private val FULL_HEIGHT = 150.dp

/** Yields ~60 dp to the portion controls while the keyboard is open (§5). */
private val COMPACT_HEIGHT = 92.dp

/**
 * Up to two initials — "Hagelslag puur" becomes "HP". Digits and punctuation are skipped so "7Up"
 * does not render as "7".
 */
private fun Product.monogram(): String =
    name.split(' ', '-', '/')
        .mapNotNull { word -> word.firstOrNull { it.isLetter() }?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifEmpty { "?" }
