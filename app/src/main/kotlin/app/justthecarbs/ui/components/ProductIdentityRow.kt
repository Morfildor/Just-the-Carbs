package app.justthecarbs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Stable handle for the calculator's product image container.
 *
 * Keeps the old `PRODUCT_HERO_TAG` spelling deliberately: the tag identifies *the tappable thing
 * that opens the gallery*, which is the same affordance in the same place in the reading order,
 * and every instrumented assertion about the gallery tap is about that affordance rather than
 * about how tall it is. It is on the fixed container, never on the picture inside it, so what it
 * measures is the space the layout reserved.
 */
const val PRODUCT_HERO_TAG = "product_hero_image"

/**
 * The thumbnail's edge in the row arrangement: big enough to recognise the packet in your hand at
 * arm's length, small enough to stay subordinate to the portion field and the result. 56dp (the
 * 2026-09-22 visual pass) corroborated nothing; 96dp and then 112dp since, which is now the row's
 * floor: what the calculator reserves before the portion controls take their height.
 */
internal val THUMBNAIL_SIZE = 112.dp

/**
 * The row's thumbnail sizes, the largest that fits used (2026-09-23 hero redesign). A remembered
 * product with Usual portions and a meal in progress leaves about 80dp on a 412dp phone, too little
 * for a hero with its caption but enough for a noticeably larger thumbnail, so the row steps up
 * rather than leaving that height empty.
 */
internal val ROW_THUMBNAIL_SIZES = listOf(144.dp, 128.dp, THUMBNAIL_SIZE)

/**
 * The thumbnail on a genuinely height-constrained screen (a 360x720dp phone at 1.3x text), where
 * the full size would spend height the portion controls need. The only row size there.
 */
internal val COMPACT_THUMBNAIL_SIZE = 72.dp

/**
 * At or below this window height the row uses [COMPACT_THUMBNAIL_SIZE]; 720dp-tall phones keep the
 * full size. *At or below*, because CI's release-gate emulator is exactly 320x640dp.
 */
private val COMPACT_HEIGHT_THRESHOLD = 640.dp

/**
 * The hero photo container's possible heights (2026-09-23 hero redesign). Owner feedback on a
 * 412dp phone: a 112dp thumbnail over roughly 250dp of empty page. The spare height now goes to the
 * picture, but only in these fixed steps: the largest that fits after the portion controls and the
 * answer have taken theirs. 240dp is the cap, a little over twice the thumbnail's edge: large
 * enough to read a label and a brand, not so large that a packet outweighs the answer it is there
 * to corroborate. The smallest is still above the row's largest step, since a hero is stacked
 * over its caption and costs more height than a row for any given photo size.
 */
internal val HERO_PHOTO_HEIGHTS = listOf(240.dp, 200.dp, 160.dp)

/** Between the hero photo and the facts line under it: the caption belongs to the photo. */
private val HERO_CAPTION_GAP = Space.xs

/**
 * The initials never outgrow the answer's type. Scaled to the plate (a third of its edge) as
 * before, which leaves the row's 112dp and 72dp plates exactly as they were, and capped for the
 * hero, where a third of 240dp would be larger than the 72sp carbohydrate result.
 */
private const val MONOGRAM_MAX_SP = 40f

/**
 * The calculator's identity header: the product image and, beside or under it, the per-100 figure
 * and its provenance.
 *
 * Two arrangements, chosen at layout time by [identityLayoutFor]:
 *
 * - **Hero**, when [allowHero] and the header has room: a photo container across the full width,
 *   one of the fixed [HERO_PHOTO_HEIGHTS], with the facts on a line under it.
 * - **Row** otherwise: a square thumbnail beside the facts, the arrangement this screen had before
 *   the hero redesign, at the largest of [ROW_THUMBNAIL_SIZES] that fits ([COMPACT_THUMBNAIL_SIZE]
 *   on a short window). With [keyboardOpen] the smallest row, or nothing when even that does not
 *   fit.
 *
 * **The container is the space, and nothing about the image changes it.** Which arrangement and
 * which size are decided from the room the calculator leaves, never from whether a photo exists,
 * has loaded or failed, so a photo, a loading placeholder, a broken image and the initials fallback
 * all occupy one box from the first layout, and a photo that arrives late moves nothing. Inside the
 * hero container the picture is drawn at its own shape ([ContentScale.Fit], unchanged), centred,
 * on the same near-white plate with the same 12dp corners: at 240dp a letterboxed plate across the
 * whole width was a pale slab around a narrow bottle, which in Dark was the brightest thing on the
 * screen. Until the shape is known the plate is square.
 *
 * Preserved from the thumbnail: the monogram fallback (a grey box reads as broken), the gallery tap
 * with its `Role.Button` semantics, and the cleared semantics so TalkBack does not read the product
 * name twice. A nameless quick calculation has no image at all: its title says what it is.
 */
@Composable
fun ProductIdentityRow(
    product: Product,
    modifier: Modifier = Modifier,
    /** Null when the product has no safe gallery image, which is what removes the tap. */
    onOpenGallery: (() -> Unit)? = null,
    /** True where the screen can give the header a large photo when there is room for one. */
    allowHero: Boolean = false,
    /** True while the soft keyboard is open: no hero, and the header may hide rather than overflow. */
    keyboardOpen: Boolean = false,
    /**
     * The per-100 figure, its provenance badge and any verify affordance.
     *
     * A slot rather than parameters, so the identity header owns the image and the arrangement
     * while the calculator keeps owning what it says about a product -- which is where the
     * `Product` -> string formatting and the `isRemoteRefreshable` decision already live. It must
     * support intrinsic measurement, and truthfully: the header asks how tall it is at a given
     * width, and a slot that measures taller than it answered is drawn over whatever sits above
     * the header. `FlowRow` answers as if its items shared one line; use `WrappingRow`.
     */
    details: @Composable () -> Unit,
) {
    val hasImage = product.name.isNotEmpty()
    // Sized against the window rather than a flag threaded from the screen: the constraint is "is
    // there room", which is a property of the device, not of any decision the calculator makes.
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val compact = screenHeight <= COMPACT_HEIGHT_THRESHOLD
    val measurePolicy = remember(hasImage, allowHero, keyboardOpen, compact) {
        IdentityMeasurePolicy(
            hasImage = hasImage,
            heroCapable = allowHero && hasImage,
            keyboardOpen = keyboardOpen,
            // Only the calculator steps the row up; anywhere else the row is the fixed thumbnail.
            rowThumbnails = when {
                compact -> listOf(COMPACT_THUMBNAIL_SIZE)
                allowHero -> ROW_THUMBNAIL_SIZES
                else -> listOf(THUMBNAIL_SIZE)
            },
        )
    }
    Layout(
        // Clips only when the calculator has less height than the smallest row (see measure).
        modifier = modifier.clipToBounds(),
        content = {
            if (hasImage) ProductImagePlate(product = product, onClick = onOpenGallery)
            Box { details() }
        },
        measurePolicy = measurePolicy,
    )
}

private class IdentityMeasurePolicy(
    private val hasImage: Boolean,
    private val heroCapable: Boolean,
    private val keyboardOpen: Boolean,
    private val rowThumbnails: List<Dp>,
) : MeasurePolicy {

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val width = constraints.maxWidth
        val image = if (hasImage) measurables.first() else null
        val details = measurables.last()
        val layout = identityLayoutFor(
            heroCapable = heroCapable,
            keyboardOpen = keyboardOpen,
            available = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE,
            heroCaption = if (heroCapable) details.minIntrinsicHeight(width) else 0,
            captionGap = HERO_CAPTION_GAP.roundToPx(),
            heroHeights = HERO_PHOTO_HEIGHTS.map { it.roundToPx() },
            rowOptions = rowThumbnails.map { RowOption(it.roundToPx(), rowHeight(details, width, it.roundToPx())) },
        )
        return when (layout) {
            is IdentityLayout.Hero -> {
                val photo = image!!.measure(Constraints.fixed(width, layout.photoHeight))
                val facts = details.measure(Constraints(maxWidth = width))
                val factsTop = photo.height + HERO_CAPTION_GAP.roundToPx()
                layout(width, constraints.constrainHeight(factsTop + facts.height)) {
                    photo.place(0, 0)
                    facts.place(0, factsTop)
                }
            }
            is IdentityLayout.Row -> {
                val side = layout.thumbnail
                val photo = image?.measure(Constraints.fixed(side, side))
                val facts = details.measure(Constraints(maxWidth = rowFactsWidth(width, side)))
                // Within the height given, never beyond it. With the keyboard closed the row is the
                // floor even when it does not fit, which happens only when the whole space between
                // the meal bar and the dock is shorter than the smallest row: 1.8x text on a 320x640dp
                // window with a meal and an answer. A layout taller than its constraints is centred
                // on them by Compose, which drew the header over the meal bar; reported at the
                // height given, it stays at the top and is cut at its lower edge instead.
                layout(width, constraints.constrainHeight(maxOf(photo?.height ?: 0, facts.height))) {
                    photo?.place(0, 0)
                    facts.place(if (photo != null) side + Space.m.roundToPx() else 0, 0)
                }
            }
            IdentityLayout.Hidden -> layout(width, 0) {}
        }
    }

    /**
     * The header's floor: the smallest row. The calculator reserves this much before the portion
     * controls take theirs, so the image and the facts are never squeezed out while the keyboard is
     * closed.
     */
    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        if (keyboardOpen) 0 else rowHeight(measurables.last(), width, smallestThumbnail())

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        if (keyboardOpen) 0 else rowHeight(measurables.last(), width, smallestThumbnail())

    // Width intrinsics are answered from the facts alone. The image container is a
    // BoxWithConstraints, which cannot be asked for intrinsics at all, and its width is the
    // header's to decide rather than the other way round.
    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.last().minIntrinsicWidth(height) + rowImageWidth(smallestThumbnail())

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.last().maxIntrinsicWidth(height) + rowImageWidth(smallestThumbnail())

    private fun IntrinsicMeasureScope.smallestThumbnail(): Int = rowThumbnails.minOf { it.roundToPx() }

    private fun IntrinsicMeasureScope.rowImageWidth(thumbnail: Int): Int =
        if (hasImage) thumbnail + Space.m.roundToPx() else 0

    private fun IntrinsicMeasureScope.rowFactsWidth(width: Int, thumbnail: Int): Int =
        (width - rowImageWidth(thumbnail)).coerceAtLeast(0)

    /** The row's height with a [thumbnail]-sized image: the taller of the image and the facts. */
    private fun IntrinsicMeasureScope.rowHeight(details: IntrinsicMeasurable, width: Int, thumbnail: Int): Int =
        maxOf(if (hasImage) thumbnail else 0, details.minIntrinsicHeight(rowFactsWidth(width, thumbnail)))
}

/**
 * The fixed image container. Its size comes from the header and is the same whatever the image is
 * doing; only the plate drawn inside it knows about the photo.
 */
@Composable
private fun ProductImagePlate(product: Product, onClick: (() -> Unit)?) {
    // Same validator as everywhere else -- a smaller image is still an untrusted URL (§5, §24).
    val imageUrl = remember(product.imageUrl, product.largeImageUrl, product.images) {
        ProductImageSelector.heroImageUrl(product)
    }
    // The photo's width over its height, known once it has loaded. Null means nothing to show yet
    // (loading, failed or no photo at all), which is when the initials are drawn.
    var photoAspect by remember(imageUrl) { mutableStateOf<Float?>(null) }
    val loaded = photoAspect != null
    val viewImagesDescription = stringResource(R.string.gallery_open)
    // The whole container takes the tap, so a narrow bottle is as easy to hit as a wide box; the
    // press is drawn on the plate, where the picture is, rather than across empty page.
    val interactions = remember { MutableInteractionSource() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interactions, indication = null, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .testTag(PRODUCT_HERO_TAG)
            .clearAndSetSemantics {
                if (onClick != null) {
                    role = Role.Button
                    contentDescription = viewImagesDescription
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // A hero container is at least the smallest hero height; every row size is below it.
        val hero = maxHeight >= HERO_PHOTO_HEIGHTS.min()
        val edge = minOf(maxWidth, maxHeight)
        val shape = RoundedCornerShape(Space.mediaRadius)
        Box(
            modifier = Modifier
                .then(
                    if (hero) {
                        // The photo's own shape, as large as the container allows; square until
                        // the shape is known.
                        Modifier.aspectRatio(photoAspect ?: 1f, matchHeightConstraintsFirst = true)
                    } else {
                        Modifier.size(edge)
                    },
                )
                .clip(shape)
                .indication(interactions, ripple())
                // Neutral while there is no photo (2026-09-23 calculator refinement): the lavender
                // `primaryContainer` it replaced made two initials the most coloured object in the
                // header. The near-white media surface matches Open Food Facts' baked-in white
                // photo backgrounds once there is a photo on it.
                .background(
                    if (loaded) {
                        MaterialTheme.extendedColors.mediaSurface
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (!loaded) {
                Text(
                    text = product.monogram(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = minOf(edge.value * 0.34f, MONOGRAM_MAX_SP).sp,
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
                    // Fit, not Crop -- a tall bottle keeps its silhouette. The 4dp inset keeps an
                    // unusually narrow package off the plate's rounded edge.
                    contentScale = ContentScale.Fit,
                    onSuccess = { state ->
                        val size = state.painter.intrinsicSize
                        photoAspect = if (size.isSpecified && size.width > 0f && size.height > 0f) {
                            size.width / size.height
                        } else {
                            1f
                        }
                    },
                    modifier = Modifier.matchParentSize().padding(Space.xs),
                )
            }
        }
    }
}

/**
 * Up to two initials -- "Hagelslag puur" becomes "HP". Digits and punctuation are skipped so "7Up"
 * does not render as "7".
 */
private fun Product.monogram(): String =
    name.split(' ', '-', '/')
        .mapNotNull { word -> word.firstOrNull { it.isLetter() }?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifEmpty { "?" }
