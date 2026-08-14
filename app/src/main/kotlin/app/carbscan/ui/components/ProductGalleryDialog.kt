package app.carbscan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.carbscan.R
import app.carbscan.domain.ProductImage
import app.carbscan.domain.ProductImageType
import app.carbscan.ui.theme.Motion
import app.carbscan.ui.theme.Space
import coil3.compose.AsyncImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch
import java.util.Locale

const val PRODUCT_GALLERY_TAG = "product_gallery"
const val PRODUCT_GALLERY_NEXT_TAG = "product_gallery_next"
const val PRODUCT_GALLERY_PREVIOUS_TAG = "product_gallery_previous"
const val PRODUCT_GALLERY_ERROR_TAG = "product_gallery_error"

/** Modal package inspection. It owns only pager state; product/calculation state stays outside. */
@Composable
fun ProductGalleryDialog(
    productName: String,
    images: List<ProductImage>,
    onDismiss: () -> Unit,
    imageLoader: ImageLoader = SingletonImageLoader.get(LocalContext.current),
    imageModel: (ProductImage) -> Any? = { it.displayUrl },
) {
    require(images.isNotEmpty()) { "A product gallery needs at least one validated image" }

    val pagerState = rememberPagerState(pageCount = images::size)
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = Space.s)
                .testTag(PRODUCT_GALLERY_TAG),
            shape = RoundedCornerShape(Space.cardRadius),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                GalleryHeader(productName, onDismiss)

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = if (images.size > 1) 1 else 0,
                        pageSpacing = Space.s,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        GalleryImagePage(productName, images[page], imageLoader, imageModel(images[page]))
                    }

                    if (images.size > 1) {
                        GalleryArrow(
                            previous = true,
                            enabled = pagerState.currentPage > 0,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            },
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                        GalleryArrow(
                            previous = false,
                            enabled = pagerState.currentPage < images.lastIndex,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            },
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }

                val currentIndex = pagerState.currentPage.coerceIn(images.indices)
                val current = images[currentIndex]
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.s),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    Text(
                        text = current.type.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    current.language?.takeIf(String::isNotBlank)?.let { language ->
                        Text(
                            language.uppercase(Locale.ROOT),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (images.size > 1) {
                        Text(
                            stringResource(R.string.gallery_page, currentIndex + 1, images.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryHeader(productName: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Space.m, top = Space.s, end = Space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.gallery_title), style = MaterialTheme.typography.titleLarge)
            Text(
                productName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(Space.minTouchTarget)) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.gallery_close))
        }
    }
}

@Composable
private fun GalleryImagePage(
    productName: String,
    image: ProductImage,
    imageLoader: ImageLoader,
    imageModel: Any?,
) {
    var state by remember(image.displayUrl) { mutableStateOf(GalleryLoadState.LOADING) }
    var retry by remember(image.displayUrl) { mutableIntStateOf(0) }
    val description = stringResource(R.string.gallery_image_description, image.type.displayName(), productName)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 60.dp, vertical = Space.s)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(Space.mediaRadius)),
        contentAlignment = Alignment.Center,
    ) {
        key(retry) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageModel)
                    .crossfade(Motion.STANDARD_MS)
                    .build(),
                contentDescription = description,
                imageLoader = imageLoader,
                contentScale = ContentScale.Fit,
                onLoading = { state = GalleryLoadState.LOADING },
                onSuccess = { state = GalleryLoadState.LOADED },
                onError = { state = GalleryLoadState.ERROR },
                modifier = Modifier.fillMaxSize().padding(Space.s),
            )
        }

        when (state) {
            GalleryLoadState.LOADING -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.s),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                Text(stringResource(R.string.gallery_loading), style = MaterialTheme.typography.bodyMedium)
            }
            GalleryLoadState.ERROR -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.xs),
                modifier = Modifier.testTag(PRODUCT_GALLERY_ERROR_TAG).semantics {
                    liveRegion = LiveRegionMode.Polite
                },
            ) {
                Text(
                    stringResource(R.string.gallery_unavailable),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.gallery_retry)) }
            }
            GalleryLoadState.LOADED -> Unit
        }
    }
}

@Composable
private fun GalleryArrow(
    previous: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val description = stringResource(if (previous) R.string.gallery_previous else R.string.gallery_next)
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .padding(Space.xs)
            .size(Space.minTouchTarget)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(50))
            .testTag(if (previous) PRODUCT_GALLERY_PREVIOUS_TAG else PRODUCT_GALLERY_NEXT_TAG)
            .semantics { contentDescription = description },
    ) {
        Icon(
            if (previous) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
        )
    }
}

@Composable
private fun ProductImageType.displayName(): String = stringResource(
    when (this) {
        ProductImageType.FRONT -> R.string.gallery_front
        ProductImageType.NUTRITION -> R.string.gallery_nutrition
        ProductImageType.INGREDIENTS -> R.string.gallery_ingredients
        ProductImageType.PACKAGING -> R.string.gallery_packaging
    },
)

private enum class GalleryLoadState { LOADING, LOADED, ERROR }
