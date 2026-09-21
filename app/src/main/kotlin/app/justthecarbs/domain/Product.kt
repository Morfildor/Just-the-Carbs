package app.justthecarbs.domain

import java.math.BigDecimal
import java.time.Instant

/**
 * Where a product's data originally came from. Provenance, and nothing else.
 *
 * This is permanent: a product that arrived from Open Food Facts stays [OPEN_FOOD_FACTS] forever,
 * even after the user checks it against the package. Whether the user has checked it is a separate
 * fact — see [VerificationStatus] — because the two answer different questions and a product can
 * change one without changing the other.
 */
enum class ProductDataOrigin {
    /** Fetched from the Open Food Facts database (§12). Displayed as *Online value* (§25). */
    OPEN_FOOD_FACTS,

    /** Typed in by the user, usually after an unknown barcode (§26, §27). */
    MANUAL,

    /** Read off a nutrition label by OCR and confirmed by the user — never auto-accepted (§29). */
    OCR,
    ;

    /**
     * True when the value was authored on this device rather than downloaded. Remote data may never
     * replace it, regardless of verification status: a manual product the user has not yet
     * double-checked is still *their* number, not one for a sync to silently correct.
     */
    val isUserAuthored: Boolean get() = this != OPEN_FOOD_FACTS
}

/** Whether the user has personally checked this value against the physical package (§23). */
enum class VerificationStatus {
    /** Not checked by the user. For OFF data this drives *Online value · Check package if needed*. */
    UNVERIFIED,

    /** The user confirmed it against the package. Displayed as *✓ Verified by you* (§23). */
    USER_VERIFIED,
}

/** The four package-image roles Open Food Facts exposes in `selected_images`. */
enum class ProductImageType {
    FRONT,
    NUTRITION,
    INGREDIENTS,
    PACKAGING,
}

/** One language-selected, allowlisted display image for a package-image role. */
data class ProductImage(
    val type: ProductImageType,
    val language: String?,
    val displayUrl: String,
)

/**
 * A product the app can calculate with. Field set follows brief §33.
 *
 * Carbohydrate figures are [BigDecimal] rather than [Double] because they are displayed to the
 * user and drive a calculation whose result gets typed into a bolus calculator by hand.
 */
data class Product(
    val barcode: String,
    /**
     * The canonical product name — the provider's, or the one the user authored.
     *
     * This belongs to the product, not to this device: for an Open Food Facts record it may
     * legitimately change when the provider corrects or re-titles the record, and a refresh is
     * allowed to replace it. A personal name for the same product is [localAlias], stored beside
     * this one rather than over it, so renaming can never destroy the identity a lookup resolves by.
     */
    val name: String,
    val carbsPer100: BigDecimal,
    val basis: NutritionBasis,
    /** Provenance. Permanent — verifying a product does not change where it came from. */
    val dataSource: ProductDataOrigin,
    /** Whether the user has checked it. Orthogonal to [dataSource]. */
    val verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
    val brand: String? = null,
    /** Declared package size, used only for the ½ pack / Full pack shortcuts (§14). */
    val packageAmount: BigDecimal? = null,
    val servingAmount: BigDecimal? = null,
    /** Small (200 px) front image — thumbnails in Recents and search results. */
    val imageUrl: String? = null,
    /**
     * Larger (400 px) front image for the calculator's hero (§5).
     *
     * Kept as a separate field rather than replacing [imageUrl]: the two serve different jobs, and
     * a list of 52 dp tiles should not decode 400 px bitmaps. Nullable because older cached rows
     * predate it and many OFF records have no image at all.
     */
    val largeImageUrl: String? = null,
    /** Validated OFF display images, with no more than one language variant per image role. */
    val images: List<ProductImage> = emptyList(),
    /** Retained when the user overrides a remote value, so *Reset to online value* stays possible (§23). */
    val originalRemoteCarbs: BigDecimal? = null,
    /**
     * The most recent value the remote provider reported, recorded even when it is NOT applied.
     *
     * Products get reformulated while keeping the same barcode. Storing the latest remote figure
     * next to the user's own lets the app notice the difference and mention it, without ever
     * overwriting what the user verified against the package (§24, correction #10).
     */
    val latestRemoteCarbs: BigDecimal? = null,
    /** Null for historical rows whose remote denominator was never stored. Never infer it. */
    val originalRemoteBasis: NutritionBasis? = null,
    val latestRemoteBasis: NutritionBasis? = null,
    /** When the user last confirmed this against the package (§24). Never blocks calculation. */
    val verifiedAt: Instant? = null,
    val remoteUpdatedAt: Instant? = null,
    val lastUsedAt: Instant? = null,
    /** Pre-filled on next use so a repeat product is one tap away (§20). */
    val lastPortion: BigDecimal? = null,
    val favorite: Boolean = false,
    /** Which amount field was last used — grams/ml, or a countable portion unit (§11). */
    val lastInputMode: InputMode? = null,
    /** The [PortionUnit.id] last selected, so switching back to it is immediate (§11, §12). */
    val lastSelectedPortionUnitId: Long? = null,
    /** The last countable count entered, e.g. `2` for "2 slices" (§11). */
    val lastCount: BigDecimal? = null,
    /**
     * A personal name for this product on this device, or null for none.
     *
     * Presentation metadata and nothing else. It is **not** provenance, **not** verification and
     * **not** a product edit: [name], [dataSource] and [verificationStatus] are untouched by
     * setting one, a remote refresh preserves it rather than overwriting it, and it is never sent
     * anywhere — there is no upload path in this app for any field, and this one least of all.
     *
     * Kept separate from [name] rather than replacing it because the two answer different
     * questions. [name] is what the product *is*, and the provider may correct it; this is what one
     * user chose to call it. Storing the alias over the canonical name would make a legitimate
     * remote correction look like the user's rename being silently reverted, and would leave the
     * app unable to say what the product is actually called.
     *
     * Always null or non-blank — blank is normalised to null at the repository boundary, so
     * "an alias of spaces" is not a state the rest of the app has to consider.
     */
    val localAlias: String? = null,
) {
    /**
     * The one name to show the user for this product.
     *
     * Every new user-facing presentation of a live [Product] reads this rather than [name], so that
     * "what do we call this product on screen" is answered in one place. Scattering
     * `localAlias ?: name` through the UI would make it a rule someone has to remember at each new
     * call site, and the first site that forgot would show the canonical name back to a user who
     * had renamed it — with nothing on screen to explain why.
     *
     * A blank alias resolves to [name]. The repository normalises blank to null on write, so this
     * is belt-and-braces against a row stored before that rule existed rather than a live case.
     *
     * Deliberately **not** applied to values already stored elsewhere. A `MealItem.displayName` is
     * an immutable snapshot of what was added; renaming a product later does not rewrite history.
     */
    val displayName: String get() = localAlias?.takeIf { it.isNotBlank() } ?: name

    /** True when the user has given this product a personal name on this device. */
    val hasLocalAlias: Boolean get() = !localAlias.isNullOrBlank()

    /** The unit the portion field is locked to. Never converted (§17, design decision 3.1). */
    val portionUnit: String get() = basis.unitLabel

    val isUserVerified: Boolean get() = verificationStatus == VerificationStatus.USER_VERIFIED

    /**
     * Whether a remote refresh is allowed to replace this record's product facts.
     *
     * Both conditions are required, and they exclude different things:
     * - user-authored data (manual, OCR) is never remote-refreshable, verified or not;
     * - verified data is never remote-refreshable, even though its provenance is Open Food Facts.
     *
     * This single property is the whole of §23's "sync must NEVER silently replace it".
     */
    val isRemoteRefreshable: Boolean
        get() = !dataSource.isUserAuthored && verificationStatus == VerificationStatus.UNVERIFIED

    /** True once the user overrode an online figure, so *Reset to online value* can be offered (§23). */
    val canResetToOnlineValue: Boolean get() = originalRemoteCarbs != null && originalRemoteBasis != null

    /**
     * The remote provider now reports a different figure from the one in use.
     *
     * Surfaced as an unobtrusive notice, never as a block: a changed online value is information,
     * not an error, and the user may well be holding the older packaging (§24).
     */
    val remoteValueDiffers: Boolean
        get() = latestRemoteBasis != null && latestRemoteCarbs?.let { it.compareTo(carbsPer100) != 0 || latestRemoteBasis != basis } == true

    companion object {
        /**
         * The longest personal name a product may be given ([localAlias]).
         *
         * Generous rather than tight: the point of a limit here is to keep one field from becoming
         * a place to store a paragraph, not to make the user count characters. Real aliases are two
         * or three words ("Breakfast bread"), and 60 leaves ample room for a long one while staying
         * inside what the product title and a Home card can render at a large font scale.
         *
         * Lives on the domain type rather than in the repository so the text field that enforces it
         * on screen and the write that enforces it on save read the same number. Two copies of a
         * limit is how a field that accepts 60 characters ends up silently storing 40.
         */
        const val MAX_LOCAL_ALIAS_LENGTH: Int = 60
    }
}
