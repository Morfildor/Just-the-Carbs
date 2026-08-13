package app.carbscan.domain

import java.math.BigDecimal
import java.time.Instant

/**
 * Where a product's carbohydrate figure came from, which is what decides whether the app is allowed
 * to replace it (brief §10, §23).
 *
 * The brief (§33) lists `source` and `userVerified` as separate fields. They are collapsed into one
 * here on purpose: two fields that encode the same fact can disagree, and a record claiming to be
 * both "remote" and "verified" has no safe interpretation. §33 explicitly permits improving the
 * model where the architecture warrants it.
 */
enum class ProductSource {
    /** Cached Open Food Facts data. Refreshable. Shown as *Online value* (§25). */
    REMOTE,

    /** The user checked this against the physical package. Shown as *✓ Verified by you* (§23). */
    USER_VERIFIED,

    /** The user created this, typically after an unknown barcode (§26, §27). */
    MANUAL,
    ;

    /**
     * True when the value belongs to the user rather than to a database. Remote data may never
     * silently replace it — the single rule the whole §23 reliability story rests on.
     */
    val isUserOwned: Boolean get() = this != REMOTE
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
    val source: ProductSource,
    val brand: String? = null,
    /** Declared package size, used only for the ½ pack / Full pack shortcuts (§14). */
    val packageAmount: BigDecimal? = null,
    val servingAmount: BigDecimal? = null,
    val imageUrl: String? = null,
    /** Retained when the user overrides a remote value, so *Reset to online value* stays possible (§23). */
    val originalRemoteCarbs: BigDecimal? = null,
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
}
