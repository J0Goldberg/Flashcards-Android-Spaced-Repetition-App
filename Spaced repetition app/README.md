# Flashcards — Android Spaced Repetition App

A Kotlin/Jetpack Compose Android app implementing Confidence-Based Repetition (CBR)
with Brainscape-style 1–5 confidence grading.

---

## Project structure

```
app/src/main/java/com/flashcards/
├── data/
│   ├── local/
│   │   ├── Daos.kt               # Room DAOs (Deck, Card, CardState, ReviewEvent)
│   │   └── FlashcardsDatabase.kt # Room DB + first-run seed data
│   ├── model/
│   │   └── Entities.kt           # @Entity data classes
│   └── repository/
│       └── FlashcardsRepository.kt  # Single source of truth
├── di/
│   └── AppModule.kt              # Hilt DI bindings
├── domain/
│   └── algorithm/
│       └── CbrAlgorithm.kt       # Pure CBR scheduling logic
├── ui/
│   ├── home/
│   │   ├── HomeViewModel.kt
│   │   └── HomeScreen.kt         # Deck list with due counts
│   └── study/
│       ├── StudyViewModel.kt     # Session state machine
│       └── StudyScreen.kt        # Card flip + 1–5 confidence rater
├── FlashcardsApplication.kt      # @HiltAndroidApp
└── MainActivity.kt               # Nav graph

app/src/test/
└── .../algorithm/CbrAlgorithmTest.kt   # Unit tests for scheduler
```

---

## Setup

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 35

### Steps
1. Clone the repo and open the root folder in Android Studio.
2. Let Gradle sync complete (all deps pulled automatically via `libs.versions.toml`).
3. Run on an emulator or device running API 26+.

---

## The CBR algorithm (`CbrAlgorithm.kt`)

| Rating | Meaning     | Effect |
|--------|-------------|--------|
| 1      | Not at all  | Reset → interval = 1 day, repetitions = 0 |
| 2      | Barely      | Reset → interval = 1 day, repetitions = 0 |
| 3      | Somewhat    | Grow by ×1.5 |
| 4      | Mostly      | Grow by ×2.0 |
| 5      | Perfectly   | Grow by ×2.5 |

Additional rules:
- First rep → 1 day, second rep → 3 days, third+ → formula above.
- Cards overdue by >2 days get an 85% staleness penalty on the new interval.
- Interval hard-capped at 180 days.

---

## Tech stack

| Layer | Library |
|-------|---------|
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| ViewModel | Lifecycle ViewModel + StateFlow |
| Database | Room (SQLite) |
| DI | Hilt |
| Build | Gradle 8 + version catalog |

---

## Extending the app

- **Stats screen** — `StatsViewModel` should call `repository.getGlobalStats()` and
  `getDeckStats(id)` per deck; `ReviewEventDao.dailyCounts()` gives the heatmap data.
- **Deck editor screen** — create/update `DeckEntity` and its `CardEntity` list, then
  call `repository.saveDeck()` / `repository.saveCard()`.
- **Notifications** — use `WorkManager` with a daily `CoroutineWorker` that queries
  `CardStateDao.countDue()` across all decks and posts a notification if > 0.
- **Import/export** — parse CSV into `CardEntity` objects and call `cardDao.upsertAll()`.
