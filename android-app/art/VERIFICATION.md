# Metronome reconstruction — candidate 2.2.2 / 33

Implemented: tapered beveled navy body, silver bezel/base, recessed graduated dial, teal accents, bottom pivot, separate moving rod and weight, fixed three-quarter camera, local prefiltered studio cubemap, fill light, static fallback from the model, live power/motion policy observation.

Verified locally:
- Blender model exported and rendered at center and both swing extremes.
- GLB hierarchy, 17,232 triangles, six materials, fixed pivot and panel clearance.
- Pure Java motion tests: +/-18-degree bounds, 450ms startup, exact 600ms stop, restart.
- Java compilation, D8, bundletool validation and signed universal APK construction.
- No public APIs or project storage formats changed.

Not verified: actual Android rendering, upgrade/data-preservation smoke test, 15-minute physical-device performance test. The software emulator is installed but had not brought up Android's package/activity services at handoff; hardware acceleration is unavailable. Blender renders must not be presented as Android screenshots.

Release gate: do not upload this candidate until Android visual/functional checks succeed. Code 33 is local only and must be checked against Play before use. Internal testing currently remains on the prior release.

UI integration pass: 156dp scene beneath project identity/rate, 36sp main timer, duplicate compact time hidden when expanded, edit/delete in a compact bottom row. Bloom lowered to .035, key light to 65,000 lux, softbox peak radiance reduced, chrome roughness raised to .18. Fallback rerendered with the softer lighting. Android emulator exposes package/activity services but APK installs timed out; this is not an on-device validation pass.

Artifacts: build/Freelancer-Time-Tracker-UI3D.aab (unsigned), build/metronome-test.apk (local test signature), app/src/main/assets/metronome-static.png, build/metronome-left.png, build/metronome-right.png. The test signature does not replace Google Play's app-signing certificate.

Polish pass: embedded brushed-metal roughness texture, darker navy/inset panel, polished steel, narrower studio reflections, increased teal emission, Blender glare and Android bloom. A gray-floor presentation render is saved to build/metronome-polished.png. Its contact shadow is part of the Blender presentation scene; an equivalent floor shadow has not yet been added to Android. Android rendering remains a separate acceptance gate.
