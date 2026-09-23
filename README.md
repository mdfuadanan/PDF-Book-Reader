# PDF Book Reader 📚

A modern, high-performance desktop PDF reader and personal library management application built with **Java 17+**, **JavaFX 21**, **Apache PDFBox 3.0**, and **Google Drive integration** — no Google sign-in required for public shared folders.

Designed with a clean, layered architecture that separates platform-agnostic business logic from the JavaFX presentation layer, making it straightforward to port the core to **Android** without rewriting service logic.

---

## ✨ Key Features

### 🧭 Navigation & Layout
- **Collapsible Sidebar** with three sections: **Home**, **Local Library**, **Drive Library**.
- **Home Panel**: Dashboard cards showing local book count, Drive book count, and Google Account status. Quick-action buttons to open each section.
- **Status Bar** at the bottom: live status messages, indeterminate/determinate progress bar for uploads and downloads, local books folder path display.
- **Top App Bar**: App title, "Library Manager" badge, and **Refresh Libraries** button.

### 📂 Local Library
- **Auto-scan**: Automatically discovers all PDF files inside the configured `Books/` directory.
- **Import**: Bring in any PDF from anywhere on your system via a file-picker dialog.
- **Rename**: Rename any local PDF — the file on disk is renamed in-place.
- **Delete**: Remove one or more selected books from local storage with a confirmation dialog (bulk-delete supported).
- **Upload to Drive**: Upload selected local books to a linked Google Drive folder (requires Editor access + sign-in).
- **Open Folder**: Jump to the local `Books/` folder in Windows Explorer / OS file manager (via `java.awt.Desktop`).
- **Selected Book Preview Bar**: Shows thumbnail, title, size and date of the currently selected book.
- **Multi-select**: Ctrl+click or Shift+click to select multiple books for batch upload or delete.
- **Starter Guide**: Auto-generates a welcome PDF on first run so the library is never empty.

### ☁️ Google Drive Shared Folder Library (No OAuth Required)
- **Zero-Sign-In**: Paste any publicly shared Google Drive folder link — no API key or browser login needed.
- **Automatic Permission Detection Engine**: Probes Google Drive's internal HTML data payload (`anyoneWithLink` role codes) to detect access level in priority order:
  - `EDITOR` → full read & write (upload enabled)
  - `COMMENTER` → read & download only
  - `VIEWER` → read & download only (default for neutral links)
- **Auto-detection** runs on every folder load and on every new link paste.
- **Manual Override**: Click the auto-detected role badge to toggle between Editor and Viewer if auto-detection returns a wrong result.
- **Collapsible Folder Config Card**: Hides the configuration box for more table/card space; a compact summary bar remains visible showing the shortened folder ID and role badge with Unlink and Open in Browser actions.
- **Read Online (Streaming)**: Double-click any Drive book or click **"Read Online"** to stream the PDF into a temporary cache and open it instantly in the embedded reader — no permanent local copy created.
- **Download**: Save a permanent local copy with buffered 64 KB streaming and real-time progress reporting. Downloaded books are immediately selected in the local panel.
- **Rename Drive Book**: Rename a Drive PDF via the Google Drive API (requires OAuth sign-in + Editor access).
- **Delete Drive Book**: Delete a Drive PDF via the Google Drive API (requires OAuth sign-in + Editor access).
- **Unlink**: Dedicated **Unlink** button (in both expanded card and collapsed summary bar) to disconnect the shared folder, clear the cache, and reset configuration.
- **File Sizes**: Resolves exact remote file sizes in the background using HTTP `Range: bytes=0-0` header probing.

### 📋 Library View Modes (both panels)
- **Details View**: Sortable table showing PDF cover thumbnail (32×44), title, file size, and date (local: "Modified", Drive: "Cloud Date").
- **Icons View**: Visual bookshelf card grid (80×108 per card) with cover thumbnails loaded asynchronously, title, and size.
- **View Mode Dropdown**: Per-panel `MenuButton` with radio items to switch between Details and Icons view without leaving the panel.
- **Real-Time Search**: Instant case-insensitive title filtering in both panels, updates both table and icons grid views.
- **Multi-select in Icons View**: Ctrl+click / Shift+click to select multiple book cards; consistent selection state between Details and Icons views.

### 🖼️ PDF Cover Thumbnails
- **Local books**: Rendered asynchronously via PDFBox at 60 DPI; cached in a `ConcurrentHashMap` by file path.
- **Drive books (downloaded/cached locally)**: Same PDFBox thumbnail rendering used when a local file is found.
- **Drive books (cloud-only)**: Fetches a Google Drive thumbnail via `https://drive.google.com/thumbnail?id=<fileId>&sz=w320` asynchronously; cached in a separate `driveThumbCache`; falls back to placeholder on error.
- **Placeholder**: Dark-gradient book icon + red "PDF" badge shown while thumbnail loads or if unavailable.

### 📖 Embedded PDF Reader
- **Rendering Engine**: High-fidelity page rendering via **Apache PDFBox 3.0** with a 10-page LRU cache.
- **Zoom**:
  - Preset levels: `50%`, `75%`, `100%`, `125%`, `150%`, `200%`.
  - Smart fitting: **Fit Width** and **Fit Page**.
  - Dynamic DPI scaling (36–300 DPI, clamped to avoid OOM).
  - Dark-styled dropdown with high-contrast cell selection.
- **Two-Page Spread**: Toggle a realistic book spread with a center spine divider.
- **Navigation**:
  - Toolbar: First (`⇤`), Previous (`◀`), Page-Jump input, Total-Pages label, Next (`▶`), Last (`⇥`).
  - Floating on-screen side buttons for intuitive page flipping.
  - Animated slide-and-fade page transitions.
- **Keyboard Shortcuts**:

  | Action | Keys |
  |---|---|
  | Next page | `→`, `Page Down`, `Space`, `>`, `.` |
  | Previous page | `←`, `Page Up`, `<`, `,` |
  | First / Last page | `Home` / `End` |
  | Zoom in / out | `Ctrl +` / `Ctrl -` |
  | Reset zoom | `Ctrl 0` |
  | Smooth zoom | `Ctrl + Mouse Wheel` |
  | Fullscreen | `F11` |
  | Exit fullscreen / Back | `Esc` |
  | Mouse side buttons | Navigate pages |

### 🔐 Google OAuth Drive Integration (Signed-In Mode)
- Place a `credentials.json` (Google Cloud **OAuth 2.0 Desktop App** credential) in the project root to enable authenticated access.
- The app validates the credential file on import — detects Web OAuth vs Desktop OAuth, missing fields, and placeholder example files.
- Enables listing and downloading from your **private** `PDFBookReader` Drive folder.
- Enables **Rename** and **Delete** of Drive books from within the app.
- Separate `CredentialsDialog` UI for sign-in / sign-out / credential file management.
- OAuth tokens stored locally in the `tokens/` directory — no password stored.
- OAuth scope: `DriveScopes.DRIVE` (full Drive access for private folder management).

---

## 🏗️ Project Architecture

```
src/main/java/com/pdfreader/
├── Main.java                     # JVM entry point (launcher wrapper for module compatibility)
├── PdfBookReaderApp.java          # JavaFX Application lifecycle, scene setup, CSS loading
│
├── model/                         # Pure Java — 100% Android-portable
│   ├── Book.java                  # PDF entity: id, title, path, size, date, source, driveFileId
│   └── BookSource.java            # Enum: LOCAL | CLOUD
│
├── config/                        # Configuration — 100% Android-portable
│   ├── AppConfig.java             # Configuration POJO (JSON-serializable)
│   └── ConfigManager.java         # Singleton JSON persistence via Gson
│
├── service/                       # Business Logic — decoupled from JavaFX
│   ├── BookService.java           # Local file I/O: scan, import, rename, delete, starter guide
│   ├── DriveService.java          # Google Drive: public folder browsing, streaming,
│   │                              #   thumbnail URL, rename/delete via API, credential validation,
│   │                              #   permission auto-detection engine, OAuth upload/download
│   └── PdfService.java            # PDFBox: load, render, 10-page LRU page cache, cover thumbnails
│
└── ui/                            # JavaFX Presentation Layer only
    ├── AppIcons.java              # SVG vector icon factory (Home, Account, Folder, PDF, etc.)
    ├── CredentialsDialog.java     # OAuth credential management and sign-in dialog
    ├── DriveIcon.java             # Authentic Google Drive tri-color icon (Canvas-drawn)
    ├── FirstRunWizard.java        # First-launch onboarding dialog
    ├── MainView.java              # Main window: sidebar navigation, home panel, dual-panel
    │                              #   local + drive libraries, view mode switching, thumbnails
    └── PdfReaderView.java         # Embedded reader: toolbar, viewport, two-page spread

src/main/resources/
└── css/modern-theme.css           # Full JavaFX CSS stylesheet (light theme)

src/test/java/com/pdfreader/
├── BookModelTest.java             # Book entity: size formatting, date formatting, equality
├── BookServiceAndPdfTest.java     # Local scan, import, starter guide, thumbnail rendering
├── ConfigManagerTest.java         # JSON config serialization / deserialization round-trip
└── DriveLinkTest.java             # URL parsing, ID extraction, permission detection,
                                   #   anyoneWithLink payload decoding, size cache
```

### Android Portability
| Layer | What to replace |
|---|---|
| `model/` | Drop-in — no changes needed |
| `config/` | Drop-in — no changes needed |
| `BookService` | Map to `MediaStore` or Storage Access Framework (`DocumentFile`) |
| `DriveService` | Use OkHttp; replace OAuth with Google Play Services auth |
| `PdfService` | Replace `BufferedImage` with Android `android.graphics.pdf.PdfRenderer` |
| `ui/` | Replace JavaFX with Jetpack Compose composables |

---

## 📦 Dependencies

| Library | Version | Purpose |
|---|---|---|
| JavaFX Controls + FXML + Swing | 21.0.6 | Desktop UI framework |
| Apache PDFBox | 3.0.4 | PDF loading, rendering, thumbnail generation |
| Google API Client | 2.7.0 | Google Drive API v3 (authenticated mode) |
| Google OAuth Client Jetty | 1.36.0 | OAuth 2.0 desktop authentication |
| Google API Services Drive | v3-rev20240509-2.0.0 | Drive file/folder/rename/delete operations |
| Google HTTP Client Gson | 1.45.0 | HTTP JSON transport layer |
| Gson | 2.11.0 | Configuration JSON serialization |
| SLF4J Simple | 2.0.16 | Logging |
| JUnit Jupiter | 5.10.2 | Unit testing |

---

## 📋 System Requirements

| Requirement | Details |
|---|---|
| **OS** | Windows 10/11, macOS 12+, or Linux |
| **Java** | JDK 17 or newer |
| **Maven** | Bundled via Maven Wrapper (`mvnw.cmd` / `mvnw`) — no global Maven install required |
| **Internet** | Required only for Google Drive features |

---

## 🚀 Getting Started

### Option 1 — Install via Windows Installer (Recommended for Users)

Run the installer inside the `installer/` directory:
```powershell
installer/PDFBookReader-1.0.0.exe
```
- **Self-contained**: Bundles the required runtime — no Java installation or setup required.
- **Convenient**: Automatically adds Start Menu and Desktop shortcuts.
- **Configurable**: Choose custom installation location, and uninstall cleanly via Windows Settings / Control Panel.

### Option 2 — Run from Source (Development)

**Windows:**
```powershell
.\run.bat
```
or directly:
```powershell
.\mvnw.cmd javafx:run
```

**macOS / Linux:**
```bash
./mvnw javafx:run
```

### Option 3 — Build & Run Standalone JAR

```powershell
.\mvnw.cmd package -DskipTests
java -jar target/pdf-book-reader-1.0.0.jar
```

---

## ☁️ Using Google Drive (Public Folders — No Sign-In)

1. Open **Google Drive** in your browser.
2. Right-click any folder → **Share** → set access to **"Anyone with the link"** → **Copy link**.
3. In PDF Book Reader, navigate to **Drive Library** and paste the link into the **Shared Folder Configuration** card, then click **Link Folder**.
4. The app will:
   - Auto-detect the permission level (Editor / Commenter / Viewer).
   - Fetch and list all PDF books inside the folder.
   - Resolve file sizes in the background.
   - Show Drive cover thumbnails fetched from Google's thumbnail API.
5. **Read Online**: Stream and open any book instantly. **Download**: Save a permanent local copy.

---

## 🔐 Using Google Drive (Private Folders — OAuth Sign-In)

1. Go to [Google Cloud Console](https://console.cloud.google.com/) and create a project.
2. Enable the **Google Drive API**.
3. Create an **OAuth 2.0 Desktop App** credential and download the `credentials.json` file.
4. Place `credentials.json` in the project root directory.
5. Launch the app → click **Google Account** in the sidebar → import credentials and sign in.
6. Your private `PDFBookReader` folder in Drive will be created automatically on first sign-in.
7. With Editor access + sign-in, you can also **Rename** and **Delete** Drive books from within the app.

> See [`credentials.json.example`](credentials.json.example) for the expected file structure.

---

## 🧪 Running Tests

```powershell
.\mvnw.cmd test
```

| Test Class | What It Validates |
|---|---|
| `BookModelTest` | File size formatting, date formatting, equality/hash logic |
| `BookServiceAndPdfTest` | Directory scanning, PDF import, rename, starter guide generation, thumbnail rendering |
| `ConfigManagerTest` | Config JSON serialization and deserialization round-trip |
| `DriveLinkTest` | Share URL parsing, file ID extraction, `anyoneWithLink` payload decoding, permission detection priority, size caching |

---

## 📁 Configuration File

The app persists settings in `pdf_book_reader_config.json` (auto-created on first run):

```json
{
  "booksDirectory": "Books",
  "driveFolderName": "PDFBookReader",
  "driveFolderId": "",
  "sharedFolderUrl": "",
  "folderPermission": "VIEWER",
  "driveCardCollapsed": false,
  "credentialsPath": "credentials.json",
  "tokensDirectory": "tokens",
  "firstRunCompleted": false,
  "defaultZoom": 1.25,
  "lastOpenedBookPath": ""
}
```

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
