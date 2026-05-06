# Gallery App

A production-ready Android Gallery application built with modern Android architecture.

## Tech Stack

- **Kotlin** + **Jetpack Compose**
- **Material 3** design system
- **MVVM + Repository** pattern
- **Hilt** dependency injection
- **Room** database
- **Coil** image loading with video thumbnail support
- **Media3 / ExoPlayer** video playback
- **Navigation Compose**
- **Kotlin Coroutines + Flow**
- **DataStore** for preferences
- **BiometricPrompt** for vault security
- minSdk: 26 | targetSdk: 35

## Screens (37 total)

1. Splash Screen
2. Onboarding (3 pages)
3. Home / Timeline (grouped by date)
4. Albums + Album Detail
5. Photo Viewer (pinch-zoom, swipe, double-tap zoom)
6. AI Editor (crop, adjust, filters + premium AI tools)
7. Smart Cleaner (screenshots, similar, large videos)
8. Secure Vault Locked (PIN pad)
9. Secure Vault Unlocked
10. AI Search (filter by type, date, name)
11. Map View (shell — requires Google Maps SDK)
12. Memories (On This Day + Best Of)
13. Premium / Paywall (plans, feature list)
14. Settings
15. Video Player (ExoPlayer with custom controls)
16. Video Trimmer (Media3 Transformer ready)
17. Recently Deleted / Trash (30-day retention)
18. Storage Manager
19. Backup & Sync
20. Collage Maker
21. Slideshow
22. Multi-select mode
23. Sort & Filter sheet
24. Photo Info sheet
25. Set As sheet
26. Delete confirmation dialog
27. Create album dialog
28. App Lock setup
29. Cleaner Running
30. Cleaner Result
31. Premium Nudge card

## Architecture

```
com.grow.gallery
├── app/                    # Application, MainActivity, DI, Navigation
├── core/
│   ├── designsystem/       # Theme, Colors, Typography, Shapes, Spacing, Components
│   ├── permissions/        # Android permission manager (API 26-35)
│   ├── media/              # MediaStore repository (images + videos)
│   ├── database/           # Room — trash, vault, custom albums
│   ├── security/           # VaultManager (Android Keystore + DataStore)
│   ├── billing/            # Billing abstraction interface
│   └── common/             # Extensions, DataStoreManager
└── feature/
    ├── splash / onboarding / home / albums / viewer
    ├── editor / cleaner / vault / search / map
    ├── memories / premium / settings / video
    └── trash / storage / collage / slideshow
```

## Permission Handling

| Android Version | Permission |
|----------------|------------|
| 14+ (API 34+)  | `READ_MEDIA_VISUAL_USER_SELECTED` (partial) |
| 13 (API 33)    | `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO` |
| ≤ 12 (API ≤ 32)| `READ_EXTERNAL_STORAGE` |

- Never crashes on denial — shows empty state + CTA
- Partial access (Android 14+) gracefully handled

## Design System

- Primary: `#0066FF` (Brand Blue)
- Material 3 dynamic color on Android 12+
- Light + Dark mode
- Edge-to-edge with WindowInsets padding
- 48dp minimum touch targets
- Smooth Compose animations throughout

## TODO (Future)

- [ ] Google Maps SDK for Map View
- [ ] Google Play Billing 7.x
- [ ] Cloud backup (Google Drive / Firebase)
- [ ] On-device ML for duplicate/similarity detection
- [ ] Face grouping
- [ ] Media3 Transformer for video export
- [ ] Shared element transitions
