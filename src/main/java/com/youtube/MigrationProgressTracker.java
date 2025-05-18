package com.youtube;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap; // To preserve insertion order for playlist details in file
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MigrationProgressTracker {
    // Keys for the progress file
    private static final String HEADER = "# Playlist Migration Progress";
    private static final String EXPORT_DATE_KEY = "Export Date";
    private static final String TOTAL_PLAYLISTS_IN_SOURCE_KEY = "Total Playlists in Source Account";
    private static final String TOTAL_VIDEOS_IN_SOURCE_KEY = "Total Videos in Source Account";
    private static final String LAST_IMPORT_DATE_KEY = "Last Import Date";
    private static final String TOTAL_PLAYLISTS_MIGRATED_KEY = "Total Playlists Migrated";
    private static final String TOTAL_VIDEOS_MIGRATED_KEY = "Total Videos Migrated";
    private static final String DAILY_VIDEOS_IMPORTED_KEY = "Daily Videos Imported";

    private static final String PLAYLIST_DETAILS_HEADER = "# Playlist Details";
    // Regex to parse playlist detail lines: e.g., "[playlistId] Key: Value"
    private static final Pattern PLAYLIST_DETAIL_PATTERN = Pattern.compile("\\[(.*?)\\]\\s*(.*?):\\s*(.*)");
    private static final String PD_NAME_KEY = "Name";
    private static final String PD_TOTAL_VIDEOS_KEY = "Total Videos";
    private static final String PD_IMPORTED_VIDEOS_KEY = "Imported Videos";

    private final Path progressFilePath;
    private MigrationProgress currentProgress;

    public MigrationProgressTracker(String filePath) {
        this.progressFilePath = Paths.get(filePath);
        this.currentProgress = loadProgressFromFile();
    }

    public MigrationProgress getProgress() {
        return currentProgress;
    }

    /**
     * Sets the export date. Other source totals are derived from recorded playlists.
     * @param exportDate The date the source data was fetched/exported.
     */
    public void setExportDate(LocalDate exportDate) {
        this.currentProgress = new MigrationProgress(
                exportDate,
                this.currentProgress.totalPlaylistsInSource(),
                this.currentProgress.totalVideosInSource(),
                this.currentProgress.lastImportDate(),
                this.currentProgress.totalPlaylistsSuccessfullyMigrated(),
                this.currentProgress.totalVideosSuccessfullyMigrated(),
                this.currentProgress.dailyVideosImported(),
                this.currentProgress.playlistDetails()
        );
    }

    /**
     * Records information about a playlist from the source.
     * If the playlist ID already exists, its details (name, total videos) will be updated.
     * @param playlistId The unique ID of the playlist.
     * @param playlistName The name of the playlist.
     * @param totalVideosInPlaylist The total number of videos in this playlist.
     */
    public void recordSourcePlaylist(String playlistId, String playlistName, int totalVideosInPlaylist) {
        Map<String, PlaylistDetail> newDetails = new HashMap<>(this.currentProgress.playlistDetails());
        PlaylistDetail existingDetail = newDetails.get(playlistId);

        int importedVideosCount = (existingDetail != null) ? existingDetail.importedVideos() : 0;
        newDetails.put(playlistId, new PlaylistDetail(playlistName, totalVideosInPlaylist, importedVideosCount));

        // Recalculate total videos in source
        int newTotalVideosInSource = newDetails.values().stream().mapToInt(PlaylistDetail::totalVideos).sum();

        this.currentProgress = new MigrationProgress(
                this.currentProgress.exportDate(),
                newDetails.size(), // Total playlists is the size of the map
                newTotalVideosInSource,
                this.currentProgress.lastImportDate(),
                this.currentProgress.totalPlaylistsSuccessfullyMigrated(),
                this.currentProgress.totalVideosSuccessfullyMigrated(),
                this.currentProgress.dailyVideosImported(),
                Collections.unmodifiableMap(newDetails)
        );
    }

    /**
     * Records that a certain number of videos have been imported for a specific playlist.
     * @param playlistId The ID of the playlist for which videos were imported.
     * @param videosImportedThisBatch Number of videos imported in this batch for this playlist.
     */
    public void recordVideosImportedFromPlaylist(String playlistId, int videosImportedThisBatch) {
        if (videosImportedThisBatch <= 0) {
            return; // No change if no items were imported
        }

        Map<String, PlaylistDetail> newPlaylistDetails = new HashMap<>(this.currentProgress.playlistDetails());
        PlaylistDetail detail = newPlaylistDetails.get(playlistId);

        if (detail == null) {
            System.err.println("Warning: Attempting to record imported videos for an unknown playlist ID: " + playlistId);
            return;
        }

        PlaylistDetail updatedDetail = detail.withVideosImported(videosImportedThisBatch);
        newPlaylistDetails.put(playlistId, updatedDetail);

        LocalDate today = LocalDate.now();
        int newDailyVideosCount = this.currentProgress.dailyVideosImported();

        if (this.currentProgress.lastImportDate() != null && this.currentProgress.lastImportDate().equals(today)) {
            newDailyVideosCount += videosImportedThisBatch;
        } else {
            newDailyVideosCount = videosImportedThisBatch;
        }

        int newTotalVideosMigrated = this.currentProgress.totalVideosSuccessfullyMigrated() + videosImportedThisBatch;

        // Update count of fully migrated playlists
        long fullyMigratedPlaylistsCount = newPlaylistDetails.values().stream()
                .filter(pd -> pd.importedVideos() >= pd.totalVideos() && pd.totalVideos() > 0) // Ensure totalVideos > 0 to count non-empty playlists
                .count();

        this.currentProgress = new MigrationProgress(
                this.currentProgress.exportDate(),
                this.currentProgress.totalPlaylistsInSource(),
                this.currentProgress.totalVideosInSource(),
                today,
                (int) fullyMigratedPlaylistsCount,
                newTotalVideosMigrated,
                newDailyVideosCount,
                Collections.unmodifiableMap(newPlaylistDetails)
        );
    }

    private MigrationProgress loadProgressFromFile() {
        if (!Files.exists(progressFilePath)) {
            return new MigrationProgress(); // Return default/empty progress if file doesn't exist
        }
        Map<String, String> globalData = new HashMap<>();
        Map<String, Map<String, String>> rawPlaylistDetails = new LinkedHashMap<>(); // Preserve order for writing
        boolean inPlaylistDetailsSection = false;

        try (BufferedReader reader = Files.newBufferedReader(progressFilePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    if (line.trim().equals(PLAYLIST_DETAILS_HEADER)) {
                        inPlaylistDetailsSection = true;
                    } else if (line.trim().equals(HEADER)) {
                        inPlaylistDetailsSection = false;
                    }
                    continue;
                }

                if (inPlaylistDetailsSection) {
                    Matcher matcher = PLAYLIST_DETAIL_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        String id = matcher.group(1).trim();
                        String key = matcher.group(2).trim();
                        String value = matcher.group(3).trim();
                        rawPlaylistDetails.computeIfAbsent(id, k -> new HashMap<>()).put(key, value);
                    }
                } else {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        globalData.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading migration progress from " + progressFilePath + ": " + e.getMessage());
            // Fallback to default progress or consider throwing a custom exception
            return new MigrationProgress();
        }

        Map<String, PlaylistDetail> playlistDetailsMap = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : rawPlaylistDetails.entrySet()) {
            String id = entry.getKey();
            Map<String, String> details = entry.getValue();
            try {
                String name = details.getOrDefault(PD_NAME_KEY, "Unknown Playlist");
                int total = Integer.parseInt(details.getOrDefault(PD_TOTAL_VIDEOS_KEY, "0"));
                int imported = Integer.parseInt(details.getOrDefault(PD_IMPORTED_VIDEOS_KEY, "0"));
                playlistDetailsMap.put(id, new PlaylistDetail(name, total, imported));
            } catch (NumberFormatException e) {
                System.err.println("Error parsing playlist detail for ID " + id + ": " + e.getMessage());
            }
        }

        try {
            LocalDate exportDate = globalData.containsKey(EXPORT_DATE_KEY) ? LocalDate.parse(globalData.get(EXPORT_DATE_KEY), MigrationProgress.DATE_FORMATTER) : null;
            int totalPlaylistsInSource = Integer.parseInt(globalData.getOrDefault(TOTAL_PLAYLISTS_IN_SOURCE_KEY, "0"));
            int totalVideosInSource = Integer.parseInt(globalData.getOrDefault(TOTAL_VIDEOS_IN_SOURCE_KEY, "0"));
            LocalDate lastImportDate = globalData.containsKey(LAST_IMPORT_DATE_KEY) ? LocalDate.parse(globalData.get(LAST_IMPORT_DATE_KEY), MigrationProgress.DATE_FORMATTER) : null;
            int totalPlaylistsMigrated = Integer.parseInt(globalData.getOrDefault(TOTAL_PLAYLISTS_MIGRATED_KEY, "0"));
            int totalVideosMigrated = Integer.parseInt(globalData.getOrDefault(TOTAL_VIDEOS_MIGRATED_KEY, "0"));
            int dailyVideosImported = Integer.parseInt(globalData.getOrDefault(DAILY_VIDEOS_IMPORTED_KEY, "0"));

            return new MigrationProgress(exportDate, totalPlaylistsInSource, totalVideosInSource, lastImportDate, totalPlaylistsMigrated, totalVideosMigrated, dailyVideosImported, Collections.unmodifiableMap(playlistDetailsMap));
        } catch (Exception e) {
            System.err.println("Error parsing migration progress data: " + e.getMessage());
            return new MigrationProgress(); // Fallback on parsing error
        }
    }

    public void saveProgressToFile() throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(progressFilePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write(HEADER);
            writer.newLine();

            if (currentProgress.exportDate() != null) {
                writer.write(EXPORT_DATE_KEY + ": " + currentProgress.exportDate().format(MigrationProgress.DATE_FORMATTER));
                writer.newLine();
            }
            writer.write(TOTAL_PLAYLISTS_IN_SOURCE_KEY + ": " + currentProgress.totalPlaylistsInSource());
            writer.newLine();
            writer.write(TOTAL_VIDEOS_IN_SOURCE_KEY + ": " + currentProgress.totalVideosInSource());
            writer.newLine();

            if (currentProgress.lastImportDate() != null) {
                writer.write(LAST_IMPORT_DATE_KEY + ": " + currentProgress.lastImportDate().format(MigrationProgress.DATE_FORMATTER));
                writer.newLine();
            }
            writer.write(TOTAL_PLAYLISTS_MIGRATED_KEY + ": " + currentProgress.totalPlaylistsSuccessfullyMigrated());
            writer.newLine();
            writer.write(TOTAL_VIDEOS_MIGRATED_KEY + ": " + currentProgress.totalVideosSuccessfullyMigrated());
            writer.newLine();
            writer.write(DAILY_VIDEOS_IMPORTED_KEY + ": " + currentProgress.dailyVideosImported());
            writer.newLine();

            if (!currentProgress.playlistDetails().isEmpty()) {
                writer.newLine();
                writer.write(PLAYLIST_DETAILS_HEADER);
                writer.newLine();
                // Sort by playlist ID for consistent output, if desired, or use LinkedHashMap for insertion order
                currentProgress.playlistDetails().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()) // Optional: for consistent ordering in the file
                    .forEach(entry -> {
                    try {
                        writer.write(String.format("[%s] %s: %s%n", entry.getKey(), PD_NAME_KEY, entry.getValue().playlistName()));
                        writer.write(String.format("[%s] %s: %d%n", entry.getKey(), PD_TOTAL_VIDEOS_KEY, entry.getValue().totalVideos()));
                        writer.write(String.format("[%s] %s: %d%n", entry.getKey(), PD_IMPORTED_VIDEOS_KEY, entry.getValue().importedVideos()));
                    } catch (IOException e) {
                        // This inner try-catch is tricky with lambdas. Consider alternative or rethrow.
                        // For simplicity here, we'll print an error. A better solution might involve a custom Consumer.
                        System.err.println("Error writing playlist detail for " + entry.getKey() + ": " + e.getMessage());
                    }
                });
            }
        }
    }
}