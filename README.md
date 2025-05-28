# YouTube Playlists Migrator

A Java-based command-line application to migrate YouTube playlists from one Google account to another. It handles OAuth 2.0 authentication for both source and target accounts, fetches playlists, copies videos, tracks migration progress in detail, and generates a verification report.

## Features

* **Dual Account Authorization:** Securely authenticates with both the source and target YouTube accounts using OAuth 2.0.
* **Playlist Discovery:** Fetches all playlists from the source account.
* **Selective Migration:** Allows the user to specify how many playlists to migrate or migrate all.
* **Playlist Creation:** Creates new playlists in the target account, optionally with a prefix.
* **Video Transfer:** Copies video items from source playlists to the corresponding new target playlists.
* **Duplicate Prevention:**
    * Checks for existing playlists in the target account (based on prefixed title) and uses them if found.
    * Checks for existing videos within target playlists to avoid adding duplicates.
* **Enhanced Progress Tracking:**
    * Saves detailed migration status to a local file (`migration_status.txt`). This includes source data overview (total playlists/videos, export date), import progress (last import date, daily imported videos, total migrated playlists/videos), and per-playlist details (name, total videos, imported videos).
    * Allows the application to identify and skip playlists that are already fully migrated based on the progress file.
* **Migration Verification & Reporting:**
    * Generates an Excel (`.xlsx`) report after migration attempts, detailing the success for each playlist.
    * The report includes a summary, per-playlist comparison of source and target video counts, lists of missing or extra videos, and direct links to missing YouTube videos.
* **Robust Error Handling:**
    * Specifically handles and logs common issues like "video not found" or "video precondition failed" allowing the migration to continue with other videos where possible.
    * Halts migration of subsequent playlists if a YouTube API quota error is encountered during a run.
* **Configurable:** Uses a `config.properties` file for settings like playlist prefix, default privacy, and API call delays.
* **API Rate Limit Consideration:** Includes an optional delay between API calls (e.g., when adding videos) to help avoid hitting YouTube Data API quotas.

## Prerequisites

1.  **Java Development Kit (JDK):** Version 8 or higher.
2.  **Gradle:** For building and running the project (or your preferred build tool like Maven).
3.  **Google Cloud Project:**
    * A project in the Google Cloud Console.
    * **YouTube Data API v3 enabled** for your project.
4.  **OAuth 2.0 Credentials (`client_secret.json`):**
    * From the Google Cloud Console, create OAuth 2.0 client ID credentials for a "Desktop app".
    * Download the credentials JSON file.

## Setup

1.  **Clone the Repository (if applicable):**
    ```bash
    git clone <your-repository-url>
    cd YouTube-Playlist-Migrator
    ```

2.  **Configure Google Cloud Project & API:**
    * Go to the Google Cloud Console.
    * Create a new project or select an existing one.
    * Navigate to "APIs & Services" > "Enabled APIs & services".
    * Click "+ ENABLE APIS AND SERVICES" and search for "YouTube Data API v3". Enable it.

3.  **Create and Place `client_secret.json`:**
    * In the Google Cloud Console, go to "APIs & Services" > "Credentials".
    * Click "+ CREATE CREDENTIALS" and choose "OAuth client ID".
    * If prompted, configure the consent screen first. For "User type", you can choose "External". Fill in the required app information.
    * For "Application type", select "Desktop app".
    * Give it a name (e.g., "YouTube Playlist Migrator Desktop").
    * Click "Create". You'll see your client ID and client secret.
    * Click "DOWNLOAD JSON" to download the credentials file.
    * **Rename this downloaded file to `client_secret.json`.**
    * **Place this `client_secret.json` file into the `src/main/resources` directory of your project.**

4.  **Configure `config.properties` (Optional but Recommended):**
    * Create a file named `config.properties` in the `src/main/resources` directory.
    * You can add the following properties (adjust values as needed):
        ```properties
        # Prefix for the migrated playlist titles in the target account
        migrated.playlist.prefix=Migrated - 

        # Default privacy status for newly created playlists (public, private, unlisted)
        default.new.playlist.privacy=private

        # Delay in milliseconds between API calls (e.g., when adding videos)
        # Helps to avoid hitting API rate limits. 0 means no delay.
        # A value like 500 (0.5 seconds) or 1000 (1 second) is a good starting point.
        api.call.delay.ms=500
        ```
    * If this file is not found, or a property is missing, the application will use default values.

## Building and Running with Gradle

1.  **Build the project (optional, `run` usually builds first):**
    ```bash
    ./gradlew build 
    ```
    (Use `gradlew.bat build` on Windows)

2.  **Run the application:**
    ```bash
    ./gradlew run
    ```
    (Use `gradlew.bat run` on Windows)

## How It Works

1.  **Authorization:**
    * On the first run, the application will open your web browser twice:
        * First, to authorize access to the **SOURCE** YouTube account.
        * Second, to authorize access to the **TARGET** YouTube account.
    * You'll need to log in and grant the requested permissions.
    * OAuth 2.0 tokens are stored locally in a `tokens/` directory (specifically `tokens/source` and `tokens/target`) in the project root. Subsequent runs will use these stored tokens, so you won't need to re-authorize unless the tokens expire or are revoked.

2.  **Playlist Fetching & Initial Progress Recording:**
    * The application fetches all playlists from the authorized source account.
    * Initial details about source playlists (ID, title, total video count) are recorded in `migration_status.txt`.

3.  **User Input:**
    * You'll be prompted to enter the number of playlists you wish to migrate from the source list, or type 'all'.

4.  **Migration Process:**
    * For each selected source playlist:
        * It checks the `migration_status.txt` to see if the playlist is already fully migrated. If so, it's skipped.
        * It checks if a playlist with the configured prefix and same title already exists in the target account. If so, it uses the existing one and identifies videos already present.
        * Otherwise, it creates a new playlist in the target account with the title `[PREFIX] + Source Playlist Title` and the configured privacy status.
        * It fetches all video items from the source playlist.
        * It adds each video item to the target playlist, skipping any videos already present or videos that cause specific errors (e.g., "video not found," logged with a warning).
        * An API call delay (if configured) is applied between adding videos to respect API quotas.
        * If an API quota error occurs, migration of subsequent playlists in this run is halted.

5.  **Progress Tracking (`migration_status.txt`):**
    * Detailed migration progress is logged to `migration_status.txt` in the project root. This includes:
        * Overall source account summary (total playlists, total videos, date of fetching).
        * Overall migration summary (last import date, total playlists fully migrated, total videos migrated across all playlists, videos imported on the last import date).
        * Per-playlist details: Source playlist ID, name, total videos, and number of videos successfully imported to the target.
    * This file helps in understanding what has been done, what's pending, and can be useful if the process is interrupted.

6.  **Migration Verification & Report Generation:**
    * After attempting to migrate the selected playlists, a verification step is performed.
    * An Excel report (e.g., `Migration_Verification_Report_YYYYMMDD_HHMMSS.xlsx`) is generated in the project root.
    * This report provides:
        * **Summary Sheet:** Overall statistics of the verification.
        * **Playlist Details Sheet:** For each processed source playlist, it shows the source playlist name/ID, source video count, expected target playlist name, actual target playlist ID (if found), target video count, migration status (e.g., Complete, Partial, Target Playlist Not Found), counts of missing/extra videos in the target, and notes on issues.
        * **Missing Videos Details Sheet:** Lists each video ID that was found in a source playlist but not in its corresponding target playlist, along with the source playlist name and a direct YouTube link to the missing video.

## Configuration Details (`config.properties`)

* `migrated.playlist.prefix`: (Default: `Migrated - `) A string prepended to the titles of playlists created in the target account.
* `default.new.playlist.privacy`: (Default: `private`) The privacy status for newly created playlists. Valid values: `public`, `private`, `unlisted`.
* `api.call.delay.ms`: (Default: `0`) The delay in milliseconds between consecutive API calls (like adding videos). A value of `500` (0.5 seconds) or `1000` (1 second) is recommended for larger migrations to avoid hitting API rate limits. If not set, defaults to 0 (no delay).

## `migration_status.txt` Details

This file provides a persistent record of the migration process. Key sections include:

* **Global Summary:**
    * `Export Date`: When source playlist data was last fetched.
    * `Total Playlists in Source Account`: Count of unique playlists found in the source.
    * `Total Videos in Source Account`: Sum of videos across all identified source playlists.
    * `Last Import Date`: The most recent date any videos were imported.
    * `Total Playlists Migrated`: Count of source playlists considered fully migrated (all videos imported).
    * `Total Videos Migrated`: Grand total of video items successfully copied to target playlists.
    * `Daily Videos Imported`: Number of videos imported on the `Last Import Date`.
* **Playlist Details Section:** For each source playlist processed:
    * `[playlistId] Name`: The title of the source playlist.
    * `[playlistId] Total Videos`: Number of videos in this source playlist.
    * `[playlistId] Imported Videos`: Number of videos from this source playlist successfully copied to the target.

## API Quotas

The YouTube Data API has usage quotas. Migrating a large number of playlists or videos can consume a significant portion of your daily quota.
* Using the `api.call.delay.ms` setting can help mitigate rapid quota consumption.
* If you encounter quota errors (the application will log this and halt further playlist migrations for the current run), you may need to wait until your quota resets (typically daily) or request a quota increase from Google. The `migration_status.txt` and verification report can help you understand what was completed before the quota was hit.

## Logging

The application uses SLF4J for logging. Output will be displayed in the console, providing information about the migration process, detailed step-by-step actions, warnings (e.g., for unmigratable videos), and any errors encountered. For certain video-related issues (like "video not found"), the log may include a direct URL to investigate the problematic video (e.g., `https://www.youtube.com/watch?v={videoId}`).

## License

Default copyright laws apply.
