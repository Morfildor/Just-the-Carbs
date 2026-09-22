package app.justthecarbs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.justthecarbs.R
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductImageSelector
import app.justthecarbs.ui.theme.Motion
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Stable handle for the calculator's product thumbnail.
 *
 * Keeps the old `PRODUCT_HERO_TAG` spelling deliberately: the tag identifies *the tappable thing
 * that opens the gallery*, which is the same affordance in the same place in the reading order,
 * and every instrumented assertion about the gallery tap is about that affordance rather than
 * about how tall it is.
 */
const val PRODUCT_HERO_TAG = "product_hero_image"

/** The thumbnail's edge. Big enough to recognise a packet you are holding, small enough to stay secondary. */
private val THUMBNAIL_SIZE = 56.dp

/**
 * The calculator's identity row: a compact package thumbnail beside the per-100 figure and its
 * provenance.
 *
 * **Replaces a 245dp hero photo, and that is the point of the 2026-09-22 visual pass.** The hero
 * was sized as a share of the screen (28%, clamped 150–280dp) on the argument that the first
 * question a user has is "is this the package in my hand?" — which is true, and was answered at a
 * cost the screen could not pay. Measured on a 411×914dp phone: the photo, a three-line provenance
 * block and a result dock that had grown to carry a meal bar and a provenance sentence together
 * pushed the portion field — the one control this screen exists for — *underneath* the dock. At
 * 360×720dp or 1.3× text it left the screen entirely. A calculator whose input is off-screen has
 * stopped being a calculator, whatever its photo looks like.
 *
 * The identification argument survives at 56dp for a reason the hero's own KDoc missed: the user
 * is not identifying the product from nothing. They arrived here by scanning *this* barcode or
 * tapping *this* search result, the name is in the top bar, and the thumbnail is corroboration —
 * "yes, the red one" — not a lineup. When they genuinely need to compare artwork, the thumbnail
 * still opens the full gallery, which is a better answer than a large-ish inline picture was.
 *
 * Preserved from the hero verbatim: [ContentScale.Fit] so a tall bottle keeps its silhouette
 * rather than being cropped past its flavour word, the monogram fallback (a grey box reads as
 * broken), the `mediaSurface` plate that matches Open Food Facts' baked-in white photo
 * backgrounds, the gallery tap with its `Role.Button` semantics, and the cleared semantics so
 * TalkBack does not read the product name twice.
 *
 * Dropped from the hero: the compact/IME shrink, the `animateDpAsState`, the 1dp border. At 56dp
 * there is no height worth reclaiming when the keyboard opens, so there is nothing to animate —
 * which also removes a moving element from the screen at the moment the user starts typing.
 */
@Composable
fun ProductIdentityRow(
    product: Product,
    modifier: Modifier = Modifier,
    /** Null when the product has no safe gallery image, which is what removes the tap. */
    onOpenGallery: (() -> Unit)? = null,
    /**
     * The per-100 figure, its provenance badge and any verify affordance.
     *
     * A slot rather than parameters, so the identity row owns the thumbnail and the alignment
     * while the calculator keeps owning what it says about a product -- which is where the
     * `Product` -> string formatting and the `isRemoteRefreshable` decision already live.
     */
    details: @Composable () -> Unit,
) {
    Row(
        modifier = modifier,
        // Top, not CenterVertically: the details column can run to three lines (figure, badge,
        // verify link) and centring a 56dp square against it floated the thumbnail in the middle
        // of the text block instead of aligning with the figure it belongs to.
        verticalAlignment = Alignment.Top,
    ) {
        if (product.name.isNotEmpty()) {
            ProductIdentityThumbnail(product = product, onClick = onOpenGallery)
            Spacer(Modifier.width(Space.m))
        }
        Box(Modifier.weight(1f)) { details() }
    }
}

@Composable
private fun ProductIdentityThumbnail(product: Product, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(Space.mediaRadius)
    // Same validator as everywhere else — a smaller image is still an untrusted URL (§5, §24).
    val imageUrl = remember(product.imageUrl, product.largeImageUrl, product.images) {
        ProductImageSelector.heroImageUrl(product)
    }
    var loaded by remember(imageUrl) { mutableStateOf(false) }
    val viewImagesDescription = stringResource(R.string.gallery_open)

    Box(
        modifier = Modifier
            .size(THUMBNAIL_SIZE)
            .clip(shape)
            .background(
                if (loaded) MaterialTheme.extendedColors.mediaSurface
                else MaterialTheme.colorScheme.primaryContainer,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .testTag(PRODUCT_HERO_TAG)
            .clearAndSetSemantics {
                if (onClick != null) {
                    role = Role.Button
                    contentDescription = viewImagesDescription
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                text = product.monogram(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        if (imageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(Motion.STANDARD_MS)
                    .build(),
                contentDescription = null,
                // Fit, not Crop — a tall bottle keeps its silhouette. The 4dp inset keeps an
                // unusually narrow package off the plate's rounded edge.
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                modifier = Modifier.size(THUMBNAIL_SIZE).clip(shape).padding(Space.xs),
            )
        }
    }
}

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
