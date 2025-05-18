package com.youtube;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;

public record MigrationProgress(
    LocalDate exportDate,
    int totalPlaylistsInSource,         // Total unique playlists identified from source
    int totalVideosInSource,            // Sum of videos across all source playlists
    LocalDate lastImportDate,
    int totalPlaylistsSuccessfullyMigrated, // Playlists where all items have been processed
    int totalVideosSuccessfullyMigrated,    // Total videos migrated across all playlists
    int dailyVideosImported,            // Videos imported on the lastImportDate
    Map<String, PlaylistDetail> playlistDetails // Key: Playlist ID, Value: Details for that playlist
) {
    // Formatter for reading/writing dates consistently
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE; // YYYY-MM-DD

    // Constructor for initial state or when no progress file exists
    public MigrationProgress() {
        this(null, 0, 0, null, 0, 0, 0, Collections.emptyMap());
    }
}