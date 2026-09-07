package app.justthecarbs

/**
 * Marks an instrumented test class as a measurement, calibration, or rejected experiment rather
 * than a regression gate (P1 §11).
 *
 * These tests print evidence and, by their own KDoc, assert little or nothing about product
 * behaviour — several document a decision already made and rejected (e.g. `RawElementClustering-
 * DiagnosticTest`, `BlockedDeclarationDiagnosticTest`), others calibrate a threshold that is already
 * set (`TextResolutionCalibrationTest`), and others compare configurations this app does not ship
 * side by side (`OcrEngineBakeOffTest`). Running them on every push spends emulator time — some
 * individually take minutes against the real ML Kit recognizer — without gating anything, since a
 * red assertion in one of them was never meant to block a merge.
 *
 * The mandatory nine-photograph regression corpus (`RealImageOcrTest`, `ProductionStillPipelineTest`,
 * `SelectedTableProductionTest`, `EvidencePipelineProductionTest`) is deliberately NOT annotated with
 * this marker — those tests assert concrete expected values and fail hard on a missing fixture by
 * design (see their own KDocs), and must keep running on every push.
 *
 * **This must be applied directly on the class as `@ExploratoryExperiment`, never wrapped in JUnit's
 * `@Category(ExploratoryExperiment::class)`.** AndroidX Test's `-e notAnnotation` filter
 * (`AnnotationExclusionFilter.evaluateTest`, `androidx.test:runner`) excludes a class only when
 * `testClass.isAnnotationPresent(ExploratoryExperiment::class.java)` is true — i.e. only when this
 * type is itself present as a runtime annotation on the class. `@Category` is JUnit's own,
 * unrelated mechanism (`org.junit.experimental.categories.Category`, read by JUnit's `Categories`
 * runner, which this project does not use); wrapping the marker in it compiles cleanly, the
 * annotation is genuinely retained in the class file, and `notAnnotation` silently excludes nothing
 * at all — confirmed by decompiling `androidx.test:runner:1.7.0` and by `am instrument -e log true`
 * enumerating an annotated class's tests identically with and without the filter. `@Retention(RUNTIME)`
 * is required for the same reason: the default Kotlin annotation retention is already `RUNTIME`, but
 * it is stated explicitly here because the filter reads it via reflection at instrumentation time,
 * not at compile time.
 *
 * CI excludes this marker via `-Pandroid.testInstrumentationRunnerArguments.notAnnotation=` (see
 * `.github/workflows/ci.yml` and `release-gate.yml`); run an excluded class directly with
 * `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`
 * (with no `notAnnotation` argument) when deliberately re-measuring it.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ExploratoryExperiment
