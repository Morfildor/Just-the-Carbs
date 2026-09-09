package app.justthecarbs.ui.onboarding

import app.justthecarbs.R

/**
 * Which preview sits behind the overlay for a given step.
 *
 * The tutorial teaches three different screens, so the backdrop has to change with the step. This is
 * a *drawing* instruction and nothing more: each preview is a deterministic, non-interactive
 * rendering built from constants in [TutorialPreview], never the real Home/Product/Meal composables
 * driven by real state. Nothing behind the scrim can start a camera, reach the network, read Room or
 * touch the user's actual meal.
 */
enum class TutorialBackdrop {
    /** The Home screen's two scan actions, search field and a stand-in recent row. */
    HOME,

    /** A product result: name, per-100 figure, portion field, result and *Add to meal*. */
    PRODUCT,

    /** The meal list with its pinned MEAL TOTAL panel. */
    MEAL,
}

/**
 * Which measured preview region a step illuminates.
 *
 * An anchor is looked up at draw time from the preview's own measured geometry (see
 * [TutorialAnchors]), never from a hardcoded coordinate, so the spotlight follows the real layout
 * across phone sizes, rotation, insets and font scale. [NONE] is reserved for a genuinely missing
 * or unavailable target; it is not used by the normal six-step sequence.
 */
enum class TutorialAnchor {
    NONE,
    RHYTHM,
    SCAN_BARCODE,
    SEARCH,
    SCAN_LABEL,
    ADD_TO_MEAL,
    MEAL_TOTAL,
}

/** The semantic feature supplying presentation colour. */
enum class TutorialAccent { PRIMARY, BARCODE, SEARCH, LABEL, PORTION, RESULT }

/** Rounded aperture categories, independent of measured target geometry. */
enum class TutorialFocusStyle { GROUP, CARD, CONTROL, RESULT }

/**
 * One instructional moment.
 *
 * Deliberately a plain data class over string *resource ids* rather than resolved text: the steps
 * are declared once, as a pure list, so the sequence and its boundaries are JVM-testable without a
 * Compose harness — while the words themselves stay in `strings.xml` and are resolved by the screen.
 */
data class TutorialStep(
    val titleRes: Int,
    val bodyRes: Int,
    val backdrop: TutorialBackdrop,
    val anchor: TutorialAnchor,
    val chapterRes: Int,
    val accent: TutorialAccent,
    val focusStyle: TutorialFocusStyle,
)

/**
 * The tutorial, in order (six moments, ~30-45s).
 *
 * The sequence mirrors the app's actual rhythm — find food, choose a portion, read the carbs — and
 * each teaching step points at the control the user will really tap, using that control's real
 * label. It is the single source of truth for both the screen and its tests, so a step cannot be
 * added to one without the other seeing it.
 */
val TUTORIAL_STEPS: List<TutorialStep> = listOf(
    // The orientation step illuminates the complete Find -> Portion -> Carbs rhythm.
    TutorialStep(
        titleRes = R.string.tutorial_title_orientation,
        bodyRes = R.string.tutorial_body_orientation,
        backdrop = TutorialBackdrop.HOME,
        anchor = TutorialAnchor.RHYTHM,
        chapterRes = R.string.tutorial_chapter_start,
        accent = TutorialAccent.PRIMARY,
        focusStyle = TutorialFocusStyle.GROUP,
    ),
    TutorialStep(
        titleRes = R.string.tutorial_title_barcode,
        bodyRes = R.string.tutorial_body_barcode,
        backdrop = TutorialBackdrop.HOME,
        anchor = TutorialAnchor.SCAN_BARCODE,
        chapterRes = R.string.tutorial_chapter_barcode,
        accent = TutorialAccent.BARCODE,
        focusStyle = TutorialFocusStyle.CARD,
    ),
    TutorialStep(
        titleRes = R.string.tutorial_title_search,
        bodyRes = R.string.tutorial_body_search,
        backdrop = TutorialBackdrop.HOME,
        anchor = TutorialAnchor.SEARCH,
        chapterRes = R.string.tutorial_chapter_search,
        accent = TutorialAccent.SEARCH,
        focusStyle = TutorialFocusStyle.CONTROL,
    ),
    TutorialStep(
        titleRes = R.string.tutorial_title_label,
        bodyRes = R.string.tutorial_body_label,
        backdrop = TutorialBackdrop.HOME,
        anchor = TutorialAnchor.SCAN_LABEL,
        chapterRes = R.string.tutorial_chapter_label,
        accent = TutorialAccent.LABEL,
        focusStyle = TutorialFocusStyle.CARD,
    ),
    TutorialStep(
        titleRes = R.string.tutorial_title_meal,
        bodyRes = R.string.tutorial_body_meal,
        backdrop = TutorialBackdrop.PRODUCT,
        anchor = TutorialAnchor.ADD_TO_MEAL,
        chapterRes = R.string.tutorial_chapter_meal,
        accent = TutorialAccent.PORTION,
        focusStyle = TutorialFocusStyle.CONTROL,
    ),
    TutorialStep(
        titleRes = R.string.tutorial_title_total,
        bodyRes = R.string.tutorial_body_total,
        backdrop = TutorialBackdrop.MEAL,
        anchor = TutorialAnchor.MEAL_TOTAL,
        chapterRes = R.string.tutorial_chapter_total,
        accent = TutorialAccent.RESULT,
        focusStyle = TutorialFocusStyle.RESULT,
    ),
)

/** Index of the final step. Derived, so adding a step cannot leave a stale constant behind. */
val TUTORIAL_LAST_STEP: Int = TUTORIAL_STEPS.lastIndex
