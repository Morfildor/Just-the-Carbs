package app.carbscan.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import app.carbscan.domain.Product
import app.carbscan.domain.ProductImageSelector
import app.carbscan.domain.ProductImageUrlValidator
import app.carbscan.ui.theme.Motion
import app.carbscan.ui.theme.Space
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * A product thumbnail (§7, §31).
 *
 * Falls back to a monogram tile rather than a grey box or a generic placeholder icon. Most Open
 * Food Facts entries have no image, and a screen of identical grey squares looks broken; initials
 * differ per product, so the list still reads as a list of distinct things.
 *
 * **Nothing ever waits for this.** The image loads asynchronously and its absence changes no
 * layout: the monogram occupies the same box from the first frame, so a late-arriving image cannot
 * reflow the row under the user's thumb (§31, §62).
 */
@Composable
fun ProductThumbnail(
    product: Product,
    modifier: Modifier = Modifier,
    size: Dp = Space.thumbnail,
) {
    val shape = RoundedCornerShape(Space.cardRadius)
    // A malicious or corrupt remote record must not make the app load an image from an arbitrary
    // third-party host (§15) — an untrusted URL is treated exactly like a missing one.
    //
    // Routed through ProductImageSelector so a list of 52 dp tiles keeps using the 200 px image
    // even though a 400 px one now exists: decoding the larger bitmap for every row would cost
    // memory for detail nobody can see at this size (§33). Validation is unchanged.
    val safeImageUrl = remember(product.imageUrl, product.largeImageUrl) {
        ProductImageSelector.thumbnailUrl(product)
    }
    var imageLoaded by remember(safeImageUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            // Purely decorative: the product name sits next to it, so announcing it again would
            // make a screen reader say everything twice (§39).
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = imageLoaded,
            animationSpec = tween(Motion.STANDARD_MS),
            label = "thumbnail",
        ) { loaded ->
            if (!loaded) {
                Text(
                    text = product.monogram(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value * 0.34f).sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        if (safeImageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(safeImageUrl)
                    .crossfade(Motion.STANDARD_MS)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { imageLoaded = true },
                modifier = Modifier.size(size).clip(shape),
            )
        }
    }
}

/**
 * The same tile for a search hit, which is not a [Product] and deliberately never becomes one until
 * the user picks it (spec §9).
 *
 * The URL is validated by the identical [ProductImageUrlValidator] rule, not a relaxed one: a
 * search result is *more* untrusted than a cached product, not less, since nothing about it has
 * been through a lookup yet.
 */
@Composable
fun SearchThumbnail(
    imageUrl: String?,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = Space.thumbnail,
) {
    val shape = RoundedCornerShape(Space.cardRadius)
    val safeImageUrl = remember(imageUrl) { ProductImageUrlValidator.validate(imageUrl) }
    var imageLoaded by remember(safeImageUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = imageLoaded,
            animationSpec = tween(Motion.STANDARD_MS),
            label = "searchThumbnail",
        ) { loaded ->
            if (!loaded) {
                Text(
                    text = monogramOf(name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value * 0.34f).sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        if (safeImageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(safeImageUrl)
                    .crossfade(Motion.STANDARD_MS)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { imageLoaded = true },
                modifier = Modifier.size(size).clip(shape),
            )
        }
    }
}

/**
 * Up to two initials from the product name — "Hagelslag puur" becomes "HP".
 * Digits and punctuation are skipped so a name like "7Up" does not render as "7".
 */
private fun Product.monogram(): String = monogramOf(name)

private fun monogramOf(name: String): String =
    name.split(' ', '-', '/')
        .mapNotNull { word -> word.firstOrNull { it.isLetter() }?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifEmpty { "?" }
