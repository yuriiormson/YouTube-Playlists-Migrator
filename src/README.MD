--- /dev/null
+++ b/Users/Yurii_Ormson/Library/Mobile Documents/com~apple~CloudDocs/IdeaProjects/YouTube-Playlist-Migrator/README.md
@@ -0,0 +1,159 @@
+# YouTube Playlist Migrator
+
+A Java-based command-line application to migrate YouTube playlists from one Google account to another. It handles OAuth 2.0 authentication for both source and target accounts, fetches playlists, and copies videos, while also tracking migration progress.
+
+## Features
+
+*   **Dual Account Authorization:** Securely authenticates with both the source and target YouTube accounts using OAuth 2.0.
+*   **Playlist Discovery:** Fetches all playlists from the source account.
+*   **Selective Migration:** Allows the user to specify how many playlists to migrate.
+*   **Playlist Creation:** Creates new playlists in the target account, optionally with a prefix.
+*   **Video Transfer:** Copies video items from source playlists to the corresponding new target playlists.
+*   **Duplicate Prevention:** Checks for existing playlists in the target account and existing videos within those playlists to avoid duplicates.
+*   **Progress Tracking:** Saves migration status to a local file (`migration_status.txt`), allowing for potential resumability and overview.
+*   **Configurable:** Uses a `config.properties` file for settings like playlist prefix, default privacy, and API call delays.
+*   **API Rate Limit Consideration:** Includes an optional delay between API calls to help avoid hitting YouTube Data API quotas.
+
+## Prerequisites
+
+1.  **Java Development Kit (JDK):** Version 8 or higher.
2.  **Gradle:** For building and running the project (or your preferred build tool like Maven).
3.  **Google Cloud Project:**
    *   A project in the Google Cloud Console.
    *   **YouTube Data API v3 enabled** for your project.
4.  **OAuth 2.0 Credentials (`client_secret.json`):**
    *   From the Google Cloud Console, create OAuth 2.0 client ID credentials for a "Desktop app".
    *   Download the credentials JSON file.
+
+## Setup
+
+1.  **Clone the Repository (if applicable):**
+    ```bash
+    git clone <your-repository-url>
+    cd YouTube-Playlist-Migrator
+    ```
+
+2.  **Configure Google Cloud Project & API:**
+    *   Go to the Google Cloud Console.
+    *   Create a new project or select an existing one.
+    *   Navigate to "APIs & Services" > "Enabled APIs & services".
+    *   Click "+ ENABLE APIS AND SERVICES" and search for "YouTube Data API v3". Enable it.
+
+3.  **Create and Place `client_secret.json`:**
+    *   In the Google Cloud Console, go to "APIs & Services" > "Credentials".
+    *   Click "+ CREATE CREDENTIALS" and choose "OAuth client ID".
+    *   If prompted, configure the consent screen first. For "User type", you can choose "External". Fill in the required app information.
+    *   For "Application type", select "Desktop app".
+    *   Give it a name (e.g., "YouTube Playlist Migrator Desktop").
+    *   Click "Create". You'll see your client ID and client secret.
+    *   Click "DOWNLOAD JSON" to download the credentials file.
+    *   **Rename this downloaded file to `client_secret.json`.**
+    *   **Place this `client_secret.json` file into the `src/main/resources` directory of your project.**
+
+4.  **Configure `config.properties` (Optional but Recommended):**
+    *   Create a file named `config.properties` in the `src/main/resources` directory.
+    *   You can add the following properties (adjust values as needed):
+        ```properties
+        # Prefix for the migrated playlist titles in the target account
+        migrated.playlist.prefix=Migrated - 
+
+        # Default privacy status for newly created playlists (public, private, unlisted)
+        default.new.playlist.privacy=private
+
+        # Delay in milliseconds between API calls (e.g., when adding videos)
+        # Helps to avoid hitting API rate limits. 0 means no delay.
+        # A value like 500 (0.5 seconds) or 1000 (1 second) is a good starting point.
+        api.call.delay.ms=500
+        ```
+    *   If this file is not found, or a property is missing, the application will use default values.
+
+## Building and Running with Gradle
+
+1.  **Build the project (optional, `run` usually builds first):**
+    ```bash
+    ./gradlew build 
+    ```
+    (Use `gradlew.bat build` on Windows)
+
+2.  **Run the application:**
+    ```bash
+    ./gradlew run
+    ```
+    (Use `gradlew.bat run` on Windows)
+
+## How It Works
+
+1.  **Authorization:**
+    *   On the first run, the application will open your web browser twice:
+        *   First, to authorize access to the **SOURCE** YouTube account.
+        *   Second, to authorize access to the **TARGET** YouTube account.
+    *   You'll need to log in and grant the requested permissions.
+    *   OAuth 2.0 tokens are stored locally in a `tokens/` directory (specifically `tokens/source` and `tokens/target`) in the project root. Subsequent runs will use these stored tokens, so you won't need to re-authorize unless the tokens expire or are revoked.
+
+2.  **Playlist Fetching:**
+    *   The application fetches all playlists from the authorized source account.
+
+3.  **User Input:**
+    *   You'll be prompted to enter the number of playlists you wish to migrate from the source list, or type 'all'.
+
+4.  **Migration Process:**
+    *   For each selected source playlist:
+        *   It checks if a playlist with the configured prefix and same title already exists in the target account. If so, it uses the existing one.
+        *   Otherwise, it creates a new playlist in the target account with the title `[PREFIX] + Source Playlist Title` and the configured privacy status.
+        *   It fetches all video items from the source playlist.
+        *   It adds each video item to the target playlist, skipping any videos already present in the target playlist.
+        *   An API call delay (if configured) is applied between adding videos to respect API quotas.
+
+5.  **Progress Tracking:**
+    *   Migration progress, including which playlists and videos have been processed, is logged to `migration_status.txt` in the project root. This file helps in understanding what has been done and can be useful if the process is interrupted.
+
+## Configuration Details (`config.properties`)
+
+*   `migrated.playlist.prefix`: (Default: `Migrated - `) A string prepended to the titles of playlists created in the target account.
+*   `default.new.playlist.privacy`: (Default: `private`) The privacy status for newly created playlists. Valid values: `public`, `private`, `unlisted`.
+*   `api.call.delay.ms`: (Default: `0`) The delay in milliseconds between consecutive API calls (like adding videos). A value of `500` (0.5 seconds) or `1000` (1 second) is recommended for larger migrations to avoid hitting API rate limits.
+
+## API Quotas
+
+The YouTube Data API has usage quotas. Migrating a large number of playlists or videos can consume a significant portion of your daily quota.
+*   Using the `api.call.delay.ms` setting can help mitigate rapid quota consumption.
+*   If you encounter quota errors, you may need to wait until your quota resets (typically daily) or request a quota increase from Google.
+
+## Logging
+
+The application uses SLF4J for logging. Output will be displayed in the console, providing information about the migration process and any errors encountered.
+
+## Contributing
+
+Contributions are welcome! Please feel free to submit pull requests or open issues for bugs, feature requests, or improvements.
+
+## License
+
+This project is open-source. You can specify a license here (e.g., MIT, Apache 2.0). If no license is specified, default copyright laws apply.
+
+---
+
+*This README was generated based on the project structure and code.*
+

