# Final Tutorial V2 implementation plan

1. Add focus-emphasis and pointer metadata to `TutorialStep`; pin the six-step mapping in JVM tests.
2. Add pure pointer geometry and safety tests for finite coordinates, outside-target endpoints, outside-teaching starts, collision omission, and constrained layouts.
3. Separate scrim, tonal edge, bloom, and pointer Canvas rendering while preserving the authoritative animated spotlight rectangle.
4. Coordinate the finite acquisition phase and measured narration/target geometry in `OnboardingScreen`; keep Home preview identity stable.
5. Refine compact progress and typography only where rendered inspection shows competition with the target or headline.
6. Run focused JVM tests, then the requested unit, lint, debug, Android-test assembly, instrumentation, screenshot, and walkthrough checks supported by the attached emulator/device environment.
7. Inspect the final diff and report only fresh evidence, including any physical-device or visual configurations that remain unverified.
