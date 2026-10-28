# Freelancer Heaven — Premium 3D preview

Classic interface is the default. Settings → Appearance enables Premium 3D or restores Classic. The same Activity, SharedPreferences keys, timer and project actions serve both presentations. No data migration. App ID stays com.freelancerheaven.timetracker.

## Rebuilt metronome

The new model is generated with Blender by scripts/model_metronome.py. Editable source: art/metronome.blend. It exports timer-metronome.glb and metronome-static.png. scripts/render_metronome_checks.py renders the two extreme positions into build/. The GLB has 17,232 triangles and six materials. PendulumPivot is at the lower front of the case; the runtime multiplies its rest transform by a local Z rotation, keeping the pivot fixed.

MetronomeMotion uses monotonic time, a 1.2-second period, 18-degree amplitude, 450ms startup envelope and exact centering after 600ms. scripts/MotionTest.java verifies motion independently of Android. StudioEnvironment generates a local softbox cubemap and asks Filament to prefilter its specular mipmaps. No network is needed at runtime.

Source renders are Blender captures, not Android screenshots. Android and physical-device acceptance must be recorded separately before publication.

## Build
Run scripts/build-preview.sh in the supplied Android workspace. Dependencies are pinned to Filament / gltfio 1.56.0 from Maven Central. scripts/make_pendulum.py deterministically generates a local indexed GLB with a separately named PendulumArm node. Build output is an unsigned AAB; signing/publishing is a separate operation.

Version 31 is a local candidate, not a reserved Play version. Check Play before uploading. Never commit signing keys or service-account credentials. Gradle dependency declarations are also included; the verified build path is the manual build script.

## Interaction
Vertical projects, shared timer actions, white/teal cards. A real 3D pendulum uses Filament lighting at 30fps maximum. Only the rod and its weight swing left-right while the timer runs; the frame stays fixed. The pendulum eases to center after stopping. Decorative geometry is excluded from TalkBack.

Reduced motion: Settings → Appearance → Reduce motion. Android animator disable and battery saver are checked on activity resume and timer state changes. The native renderer shows a static frame; initialization failure shows a painted ring. Rendering stops in background and when scrolled out of view. Logo animation is suppressed in modern mode.

## Verification performed
Java compilation; D8; bundletool validation; embedded GLB, native ABI libraries and resources checked. No device is connected in the execution environment. No claim of on-device install/render/performance validation.

## Device acceptance gate before release
- Install via intended signing/distribution route over the classic app, without uninstalling.
- Confirm classic default; record version from Help & privacy.
- Create projects with EUR/USD, long names and different rates.
- Enable Premium 3D; start timer, background for 60 seconds, return, stop and verify duration.
- Toggle Classic/Premium ten times while running; no duplicate ticks or lost records.
- Exercise create, edit, switch, end, delete, report export and backup/restore.
- Verify 320dp width, enlarged fonts, keyboard, TalkBack and vertical scrolling.
- Disable animations; enable battery saver; verify static scene and functional controls.
- Run 15 minutes, inspect memory/frame times and background rendering with Android profiler.
- Exercise unavailable renderer fallback.
- Only distribute after testing; leave Closed testing unchanged.
