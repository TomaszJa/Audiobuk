# Audiobuk

Audiobuk is a modern, lightweight, and feature-rich audiobook player for Android. Built with Jetpack Compose and Media3, it focuses on providing a seamless experience for both single-file (M4B/M4A) and folder-based audiobook collections.

## 🌟 Key Features

- **Hybrid Library Support**: 
    - **Folder-based**: Every folder in your root directory is treated as a book, with its contents (MP3, etc.) as chapters.
    - **Single-file**: Individual audio files (M4B, M4A, MP3) in your root directory are automatically detected as standalone audiobooks.
- **Smart Metadata Extraction**: 
    - For single-file books, it extracts **Title**, **Artist**, and **Cover Art** directly from ID3/MP4 tags using `MediaMetadataRetriever`.
    - Falls back to sanitized filenames if tags are missing.
- **Robust Chapter Sorting**:
    - Ensures your audiobook plays in the correct order.
    - Priority: 1. Metadata Track Number -> 2. Filename/Title (Alphabetical).
- **Library Customization**:
    - **View Modes**: Toggle between a visual **Square Grid** (best for covers) and a detailed **List View**.
    - **Sort Order**: Quickly switch between **A-Z** and **Z-A** sorting using dynamic arrow icons.
    - **Visibility**: "Hide Finished" toggle to declutter your library by removing books at 100% progress.
- **Interactive Walkthrough**:
    - Built-in help system with animated overlays to guide you through the Library and Player features.
- **Rock-Solid Playback**:
    - Built on **Android Media3 (ExoPlayer)**.
    - Persistent playback state: Resumes exactly where you left off.
    - M4B/M4A internal chapter support (virtual tracks).

## 🛠️ Detailed App Logic

### 1. Library Scanning & Detection
The app uses a "Root Directory" model. When you select a folder:
- It looks at every sub-directory: If a folder contains audio files, it's a book.
- It looks at every loose file in the root: If it's an audio file, it's a book.
- **Database Sync**: To prevent "ghost" entries, the local database is cleared whenever you change the root directory or when the app is updated to a new version with schema changes.

### 2. Chapter Handling
- **Multi-file Books**: Chapters are individual files within a folder, sorted by metadata track number then title.
- **Single-file Books**: The app parses the file's internal structure. For M4B files, it uses `MediaExtractor` to identify chapter markers and creates "virtual tracks" in the database.
- **Playback**: When playing a "virtual chapter," the player uses clipping configurations to ensure seek bars and "Next/Prev" buttons work precisely for that segment.

### 3. Progress Tracking
- Progress is calculated as the sum of time listened across all chapters vs. total book duration.
- Books at 100% are marked with a "Check" icon and can be filtered out of the main view.

### 4. Permissions & Storage
- **Scoped Storage**: The app requests `READ_MEDIA_AUDIO` (Android 13+) or `READ_EXTERNAL_STORAGE` (older) to access your files securely.
- **Persistence**: It uses `takePersistableUriPermission` so it can continue accessing your audiobooks even after a device reboot without asking again.

## 🚀 Getting Started

1. **Permissions**: Accept the audio access request on first launch.
2. **Select Root**: Tap the folder icon in the top right and pick the folder where you store your audiobooks.
3. **Scan**: Wait a moment for the app to extract metadata.
4. **Learn**: Tap the **?** help icon to see an explanation of all library controls.

## 💻 Tech Stack

- **UI**: Jetpack Compose
- **Media**: Android Media3 (ExoPlayer & MediaSession)
- **Database**: Room (with destructive migration for stability)
- **Architecture**: MVVM with Kotlin Coroutines & Flow
- **State Management**: Jetpack ViewModel & SharedPreferences for persistence
