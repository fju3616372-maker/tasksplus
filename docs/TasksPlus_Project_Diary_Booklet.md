# TASKS+ — 4-PAGE PROJECT DIARY BOOKLET

> **Vol. 01 · Product Field Guide, Engineering Journal & Social Showcase**
> **Crafted by:** frosty-sys · **Platform:** Android (API 23–36) · **Stack:** Kotlin, Jetpack Compose, Material 3, Room & Firebase

---

## PAGE 01 / 04 — COVER, VISION & EVERYDAY USES

### Tasks+
*A calm, tactile Android workspace for daily momentum, focus sprints, family accountability, and mindful screen-time habits.*

| Metric | Highlight |
| :--- | :--- |
| **Total Build Time** | **~17 Hours** across 4 iterative phases |
| **Android Reach** | **Android 6.0 (API 23) → Android 16 (API 36)** |
| **Architecture** | **Offline-First MVVM** (Room SQLite v8 + DataStore + Firestore Sync) |
| **Design Language** | **Material Design 3 Expressive** (Dynamic Color + 5 Tonal Palettes) |
| **Quality Assurance** | **Robolectric JVM Suites** + **9 Zero-Trust Firestore Rules Tests** |

---

### Why Tasks+ Exists (The Vision)
Most to-do apps fall into one of two extremes: bare-bones checklists that are easy to ignore, or cluttered enterprise trackers that feel exhausting to open. **Tasks+** was built to sit in the sweet spot—calm and joyful during daily planning, yet unmistakable and accountable when deadlines, focus sprints, or family screen-time rules actually matter.

### Everyday Uses
1. **Personal Daily Planning & Habit Routines**
   Capture one-time errands or repeating Daily, Weekly, Custom-Day, Monthly, and Annual routines. Color-coded categories keep Work, Personal, Shopping, Health, Study, and Home visually distinct without clutter, while a collapsible *Upcoming Recurring* drawer keeps tomorrow's habits out of today's focus list.
2. **Time-Boxed Focus Sprints**
   Attach one or more tasks to the built-in **Task Countdown Timer**. Watch live remaining time right from the notification shade or home-screen widget, and rely on a guaranteed high-priority **Time's Up** alert when your sprint ends.
3. **Family & Team Accountability**
   Create shared **Family** or **Organization** groups with role-based permissions (*Manager, Parent/Admin, Member, Child*). Assign chores or deliverables, require optional photo proof, and let **Gemini AI** verify completed tasks automatically or hand them off for parent approval.
4. **Mindful Screen-Time & Positive Motivation**
   Pair productivity with healthier phone habits: set hourly, daily, or weekly per-app usage limits, gate distracting apps behind required tasks, earn early-completion **Credit Points**, unlock custom profile emblems, and climb the opt-in **Global Leaderboard**.

---
`TASKS+ PROJECT DIARY  •  PAGE 01 / 04  •  COVER & EVERYDAY USES`

---

## PAGE 02 / 04 — COMPLETE FEATURES & CORE FUNCTIONS

Every module in **Tasks+** works offline-first on device and synchronizes smoothly to the cloud when signed in with Google.

### 1. Tactile Task Engine & Optional Color Categories
- **Smart Checklists:** Separate *To-Do* and *Completed* views, instant multi-field search (title, notes, category, priority, urgency, recurrence), and a subtle **radial ripple + spring-popped checkmark animation** whenever a task is completed.
- **Premade & Custom Categories:** Enable or disable categories anytime in *Settings → Task Categories*. Choose from 6 premade labels (*Work, Personal, Shopping, Health, Study, Home*) or create up to 30 custom categories across 10 curated color swatches.
- **Compact Top Filter Bar:** When enabled, a slim horizontal bar sits at the top of the To-Do screen to filter by category with one tap.

### 2. 1–5 Urgency Thermometer & Priority Reminders
- **Custom Urgency Thermometer Slider:** A full-width custom Canvas control gliding smoothly from **1/5 Calm (Emerald)** to **5/5 Critical (Crimson)** with zero dead space and tactile haptic detents.
- **Reliable Notifications & Alarm Bell:** Schedule interval reminders powered by Android's `AlarmManager`, with optional high-priority wake-up alarm bells and in-app permission helpers for Notifications, Exact Alarms, Overlay, and Battery Optimization.

### 3. Task Countdown Timer & Guaranteed Time's Up Alert
- Pick specific tasks for a timed sprint, pause/resume or mark tasks done mid-session, track the live countdown in persistent notifications and the **Task Timer Home Widget**, and receive an unmistakable **Time's Up** notification the second the clock hits `00:00`.

### 4. Daily Productivity Bar Chart Dashboard
- Visualize **Completed vs. Pending** tasks across *Today* or the past *7 Days* via a custom grouped bar chart available both as a toggleable in-app dashboard card and as a resizable **Android Home-Screen Widget**.

### 5. Family & Team Groups, Photo Proof & Screen-Time Locks
- **Groups & Roles:** Email-based invitations, live installed-app syncing for supervised members, and delegated Parent/Admin roles.
- **AI Photo Verification:** Optional camera/photo proof reviewed by **Firebase AI Logic (Gemini)** with clear approval feedback.
- **Screen-Time Enforcement:** Real-time `UsageStatsManager` tracking, per-app limits, instant or scheduled locks, and task-completion unlock rewards.

### 6. Credit Economy, Profile Badges, Themes & In-App Updater
- **Credit System & Leaderboard:** Configurable early-completion points, grace periods, late penalties, an immutable credit ledger, unlockable profile icons, and an opt-in **Global Leaderboard**.
- **Expressive Material 3 Theming:** System/Light/Dark modes, Android 12+ Dynamic Wallpaper Color, 5 tonal palettes (*Indigo, Emerald, Coral, Teal, Rose*), and an in-app GitHub Releases APK updater with automatic installer cleanup.

---
`TASKS+ PROJECT DIARY  •  PAGE 02 / 04  •  FEATURES & CORE FUNCTIONS`

---

## PAGE 03 / 04 — HOW IT WAS MADE & BUILD TIMELINE (~17 HOURS)

**Tasks+** was built iteratively over **~17 focused hours** across four distinct engineering and design phases—moving from an offline-first core to real-time cloud collaboration, gamification, and fine-grained visual craft.

### Development Timeline

| Phase | Time Spent | Milestone & What Was Built |
| :--- | :--- | :--- |
| **Phase 01 — Core Foundation** | **~4 Hours** | Designed the Material 3 Compose UI, adaptive icon, Room SQLite database, recurring task engine, `AlarmManager` priority reminders, dynamic theming, and home-screen task widgets. |
| **Phase 02 — Cloud, Groups & Screen Time** | **~6 Hours** | Integrated Google Sign-In via Jetpack Credential Manager, real-time Firebase Firestore sync with zero-trust security rules, Family/Team Groups, Gemini AI photo verification, and `UsageStatsManager` screen-time locks. |
| **Phase 03 — Gamification, Timer & Charts** | **~4 Hours** | Engineered the customizable Credit Economy, unlockable profile emblems, opt-in Global Leaderboard, live Task Countdown Timer (notification + widget + Time's Up alert), and the 7-day Daily Productivity Bar Chart. |
| **Phase 04 — Visual Polish & Hardening** | **~3 Hours** | Perfected the full-width Urgency Thermometer slider, built the radial ripple & stroke-drawn checkmark animation, added optional Premade & Custom Task Categories, fixed recurring cloud sync duplication, and verified with Robolectric + Firestore Rules tests. |

### Architecture & Technology Stack

| Layer | Technologies & Patterns Used |
| :--- | :--- |
| **Language & UI** | Kotlin, Jetpack Compose, Material Design 3, Custom `Canvas` Graphics, Spring & Keyframe Motion |
| **Architecture** | Offline-first **MVVM** (`TasksPlusViewModel`, `MutableStateFlow`, `collectAsStateWithLifecycle`, Kotlin Coroutines) |
| **Local Persistence** | **Room SQLite** (Schema v8 with lossless migrations `MIGRATION_1_2` → `MIGRATION_7_8`) + **Jetpack DataStore** |
| **Cloud & Security** | **Firebase Firestore** (named database + strict schema-validated `firestore.rules`), **Firebase Auth**, **Jetpack Credential Manager**, **Firebase App Check** |
| **AI & Android APIs** | **Firebase AI Logic (Gemini)**, `AlarmManager`, `NotificationManager`, `UsageStatsManager`, `AppWidgetProvider`, `FileProvider`, `OkHttp` |
| **Testing & QA** | **Robolectric** unit test suites (API 23 Marshmallow compatibility, timer alerts, recurring logic, credits, charts) + **9 automated `@firebase/rules-unit-testing` security tests** |

---
`TASKS+ PROJECT DIARY  •  PAGE 03 / 04  •  HOW IT WAS MADE & TIMELINE`

---

## PAGE 04 / 04 — ENGINEERING HIGHLIGHTS, LESSONS & COLOPHON

### Notable Engineering Challenges Solved

1. **Eliminating Daily Recurring Task Duplication Across Local & Cloud Sync**
   *The Problem:* Completing a daily task could spawn duplicate copies for the next day without moving the completed copy into the Completed tab.
   *The Fix:* Traced a subtle Kotlin-to-Firestore property mapping issue on `isCompleted` (`@PropertyName` / explicit snapshot parsing), synchronized both the completed record and next-day occurrence atomically across Room and Firestore, and isolated future occurrences inside a clean *Upcoming Recurring* drawer.

2. **Guaranteed "Time's Up" Timer Notifications**
   *The Problem:* Background process throttling can delay or drop timer alerts when an app isn't in the foreground.
   *The Fix:* Combined `AlarmManager` exact expiration triggers, synchronous broadcast execution with a dedicated high-priority alert channel, and synchronized notification/widget state updates so the **Time's Up** alert fires reliably every time.

3. **Pixel-Perfect Custom Controls From Android 6 to Android 16**
   *The Problem:* Standard sliders left trailing blank space at maximum urgency and showed distracting track artifacts; newer platform APIs could crash older Android 6.0 (API 23) devices.
   *The Fix:* Hand-crafted custom Compose `Canvas` visuals—the edge-to-edge **1–5 Urgency Thermometer**, the **Daily Productivity Grouped Bar Chart**, and the **Radial Ripple Checkmark Animation**—and backed them with automated API 23 Robolectric compatibility tests.

4. **Zero-Trust Cloud Security Parity**
   Every new feature (from Group roles and Global Leaderboard entries to Task Categories) was mirrored 1:1 across Room migrations, Firestore repositories, `firebase-blueprint.json`, and `firestore.rules`, verified by an automated 9-test emulator suite before deployment.

---

> *"Great productivity software respects your attention—gentle when you're in flow, unmistakable when it matters."*

### Colophon & Project Links
- **Project:** Tasks+ (`tasksplus`) — Android Edition
- **Author:** **frosty-sys**
- **Companion Projects:** `RNA. TV` · `navstack` · `Tasks+`
- **Built With:** Kotlin · Jetpack Compose · Material 3 · Room · Firebase Firestore · Firebase AI Logic (Gemini)

---
`TASKS+ PROJECT DIARY  •  PAGE 04 / 04  •  REFLECTIONS & COLOPHON`
