package app.carbscan.domain

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

/**
 * A product the app can calculate with. Field set follows brief §33.
 *
 * Carbohydrate figures are [BigDecimal] rather than [Double] because they are displayed to the
 * user and drive a calculation whose result gets typed into a bolus calculator by hand.
 */
data class Product(
    val barcode: String,
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
    val imageUrl: String? = null,
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
    /** When the user last confirmed this against the package (§24). Never blocks calculation. */
    val verifiedAt: Instant? = null,
    val remoteUpdatedAt: Instant? = null,
    val lastUsedAt: Instant? = null,
    /** Pre-filled on next use so a repeat product is one tap away (§20). */
    val lastPortion: BigDecimal? = null,
    val favorite: Boolean = false,
) {
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
    val canResetToOnlineValue: Boolean get() = originalRemoteCarbs != null

    /**
     * The remote provider now reports a different figure from the one in use.
     *
     * Surfaced as an unobtrusive notice, never as a block: a changed online value is information,
     * not an error, and the user may well be holding the older packaging (§24).
     */
    val remoteValueDiffers: Boolean
        get() = latestRemoteCarbs?.let { it.compareTo(carbsPer100) != 0 } == true
}
