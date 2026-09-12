package app.justthecarbs.ui.components

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductImageSelector
import app.justthecarbs.R
import app.justthecarbs.ui.theme.Motion
import app.justthecarbs.ui.theme.Space
import app.justthecarbs.ui.theme.extendedColors
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
    // A product with no photo has nothing to identify: the monogram is derived from the name shown
    // directly above it, so a full-height slab spends ~150 dp restating two letters the user has
    // already read. Found by running the app on a manually-entered product, where the hero was a
    // large empty blue rectangle sitting above a compressed portion zone. The photo case is
    // unchanged — that is where the height genuinely earns itself (§5).
    val hasImage = remember(product.imageUrl, product.largeImageUrl, product.images) {
        ProductImageSelector.heroImageUrl(product) != null
    }

    // A real photo is sized against the screen, not to a fixed dp (owner request: "much bigger, but
    // appropriately"). A flat 150 dp was the same size on a 5" phone and a tall modern one — small
    // on the second, and this image exists to be compared against a package held in the other hand,
    // so bigger genuinely helps. Proportional sizing gives a much larger picture on the phones people
    // actually have while staying self-limiting on short displays.
    //
    // The clamp is the "appropriately": [MAX_PHOTO_HEIGHT] stops it becoming a poster on a tall
    // device, and [MIN_PHOTO_HEIGHT] keeps it recognisable on a short one. The result panel is
    // pinned and the portion field sits above it, so the photo can never push either off-screen —
    // and it still yields to [COMPACT_HEIGHT] the moment the keyboard opens, which is when
    // identification stops mattering and typing starts.
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val photoHeight = (screenHeight * PHOTO_HEIGHT_FRACTION)
        .coerceIn(MIN_PHOTO_HEIGHT, MAX_PHOTO_HEIGHT)

    // Order matters: `compact` is checked first, so the monogram plate yields to the keyboard too.
    // Checking `!hasImage` first left the placeholder as the one thing on the screen that never gave
    // any height back while the user was typing — which is how the result and the equation ended up
    // pushed below the fold on a product with no photo.
    val height by animateDpAsState(
        targetValue = when {
            compact -> COMPACT_HEIGHT
            !hasImage -> MONOGRAM_HEIGHT
            else -> photoHeight
        },
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
    val imageUrl = remember(product.imageUrl, product.largeImageUrl, product.images) {
        ProductImageSelector.heroImageUrl(product)
    }
    var loaded by remember(imageUrl) { mutableStateOf(false) }
    val viewImagesDescription = stringResource(R.string.gallery_open)

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
            // `mediaSurface` (not surfaceContainerLowest, which is near-black in Dark) gives the
            // same treatment in dark mode: a light-but-not-white plate, soft enough to avoid
            // becoming a nighttime glare source while staying compatible with the photo's own
            // baked-in white background.
            .background(
                if (loaded) MaterialTheme.extendedColors.mediaSurface
                else MaterialTheme.colorScheme.primaryContainer,
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .testTag(PRODUCT_HERO_TAG)
            // The product name is displayed directly above; announcing the image too would make a
            // screen reader say the same thing twice (§39).
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
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                // Tracks the plate it sits on, so the short no-photo plate does not carry a
                // monogram scaled for the tall one.
                fontSize = if (height < FULL_HEIGHT) 32.sp else 44.sp,
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
                // Fit, not Crop: see the class comment. Padding keeps an unusually narrow package
                // from touching the container's edges.
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                modifier = Modifier.fillMaxWidth().height(height).padding(Space.s),
            )
        }
    }
}

/**
 * Share of the screen height a real product photo gets.
 *
 * 28% lands around 245 dp on a typical modern phone (~875 dp tall) — a genuinely large picture,
 * comfortably more than the flat 150 dp it replaces, while still leaving the portion controls and
 * the pinned result on the same screen without scrolling.
 */
private const val PHOTO_HEIGHT_FRACTION = 0.28f

/** Below this a package photo stops being comparable against the real thing. */
private val MIN_PHOTO_HEIGHT = 150.dp

/** Above this the photo starts competing with the result for the screen (§5). */
private val MAX_PHOTO_HEIGHT = 280.dp

/** The reference height the monogram sizing compares against. */
private val FULL_HEIGHT = 150.dp

/**
 * What the photo shrinks to while the keyboard is open (§5).
 *
 * Deliberately *smaller* than the 92 dp it used to be. With the resting photo enlarged, 92 dp left
 * the portion field clipped through its lower edge by the pinned result panel — seen on the
 * emulator with the keyboard open, and the same class of defect the
 * `panelTop >= fieldBottom` assertion in MealScreenTest exists to catch.
 *
 * The photo has already done its job by the time the user is typing, so this is the right place to
 * find the room: identification matters before the keyboard, the number matters during it.
 */
private val COMPACT_HEIGHT = 64.dp

/**
 * No photo exists, so the monogram is a placeholder rather than an identification aid. Tall enough
 * to stay a deliberate plate rather than a stripe, short enough that it stops dominating a screen
 * whose subject is the number below it.
 */
private val MONOGRAM_HEIGHT = 84.dp

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
