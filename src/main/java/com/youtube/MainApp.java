package com.youtube;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItem;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional; 
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MainApp {
    private static Properties appProps = new Properties();

    private static final String CLIENT_SECRETS = "/client_secret.json"; // Ensure this file is in src/main/resources
    private static final List<String> SCOPES = Collections.singletonList("https://www.googleapis.com/auth/youtube");
    private static final String APPLICATION_NAME = "YouTube Playlist Migrator";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_BASE_DIRECTORY_PATH = "tokens";
    private static final String SOURCE_TOKENS_SUBDIRECTORY = "source";
    private static final String TARGET_TOKENS_SUBDIRECTORY = "target";

    // Read from config.properties or use defaults
    private static String MIGRATED_PLAYLIST_PREFIX;
    private static String DEFAULT_NEW_PLAYLIST_PRIVACY;
    private static long API_CALL_DELAY_MS;

    private static final Logger LOGGER = LoggerFactory.getLogger(MainApp.class);
    static {
        try (InputStream input = MainApp.class.getResourceAsStream("/config.properties")) {
            if (input == null) {
                LOGGER.warn("Sorry, unable to find config.properties, using defaults.");
            } else {
                appProps.load(input);
            }
        } catch (IOException ex) {
            LOGGER.error("Error loading config.properties", ex);
        }
                // Initialize properties with defaults if not found in file or if file loading failed
        MIGRATED_PLAYLIST_PREFIX = appProps.getProperty("migrated.playlist.prefix", "Migrated - ");
        DEFAULT_NEW_PLAYLIST_PRIVACY = appProps.getProperty("default.new.playlist.privacy", "private");
        API_CALL_DELAY_MS = Long.parseLong(appProps.getProperty("api.call.delay.ms", "0")); // Default to 0ms (no delay)
    }

    /**
     * Authorizes the installed application to access user's protected data.
     * @param httpTransport The NetHttpTransport.
     * @param tokenDirectorySuffix Suffix for the token directory (e.g., "source" or "target").
     * @param userId A unique ID for the user credentials being stored (e.g., "user-source", "user-target").
     */
    private static Credential authorize(final NetHttpTransport httpTransport, String tokenDirectorySuffix, String userId) throws IOException {
        InputStream in = MainApp.class.getResourceAsStream(CLIENT_SECRETS);
        if (in == null) {
            LOGGER.error("Error: Resource not found: {}. Please ensure that 'client_secret.json' is in the 'src/main/resources' directory.", CLIENT_SECRETS);
            // LOGGER.error("Please ensure that 'client_secret.json' is in the 'src/main/resources' directory."); // This line is redundant
            System.exit(1);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        java.io.File tokenDir = new java.io.File(TOKENS_BASE_DIRECTORY_PATH, tokenDirectorySuffix);

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(tokenDir))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize(userId);
    }

    // Overloaded for source/target distinction
    public static YouTube getYouTubeService(String tokenDirectorySuffix, String userId) throws GeneralSecurityException, IOException {
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = authorize(httpTransport, tokenDirectorySuffix, userId);
        return new YouTube.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public static void main(String[] args) {
        try {
            LOGGER.info("Welcome to the YouTube Playlist Migrator!");

            // --- Setup ---
            LOGGER.info("--- Authorizing Source YouTube Account ---");
            LOGGER.info("Please follow the browser prompts to authorize the SOURCE YouTube account.");
            YouTubeServiceManager sourceServiceManager = new YouTubeServiceManager(getYouTubeService(SOURCE_TOKENS_SUBDIRECTORY, "user-source"));
            LOGGER.info("Authorization successful for SOURCE account.");

            LOGGER.info("--- Authorizing Target YouTube Account ---");
            LOGGER.info("Please follow the browser prompts to authorize the TARGET YouTube account.");
            YouTubeServiceManager targetServiceManager = new YouTubeServiceManager(getYouTubeService(TARGET_TOKENS_SUBDIRECTORY, "user-target"));
            LOGGER.info("Authorization successful for TARGET account.");

            MigrationProgressTracker progressTracker = new MigrationProgressTracker("migration_status.txt");
            LOGGER.info("Fetching playlists from the source account...");
            List<Playlist> sourcePlaylists = sourceServiceManager.getMyPlaylists();
            LOGGER.info("Found {} playlists in the source account.", sourcePlaylists.size());

            // Example: Record source data info (you'd get total items more accurately)
            if (progressTracker.getProgress().exportDate() == null) {
                progressTracker.setExportDate(LocalDate.now());
                // You would iterate through playlists to get total video counts for a more accurate totalVideosInSource
                sourcePlaylists.forEach(p -> {
                    long itemCount = p.getContentDetails() != null && p.getContentDetails().getItemCount() != null ?
                                     p.getContentDetails().getItemCount() : 0;
                    progressTracker.recordSourcePlaylist(p.getId(), p.getSnippet().getTitle(), (int) itemCount);
                });
                progressTracker.saveProgressToFile();
            }

            LOGGER.info("--- Migration Control ---");
            // System.out.println("You can now implement logic to select which playlists to migrate."); // Keep as System.out for direct user interaction
            System.out.print("Number of playlists to migrate (e.g., 1, 2, or 'all' for all " + sourcePlaylists.size() + " playlists): ");
            java.util.Scanner scanner = new java.util.Scanner(System.in);
            String input = scanner.nextLine().trim();

            List<Playlist> playlistsToMigrate = new ArrayList<>();
            if ("all".equalsIgnoreCase(input)) {
                playlistsToMigrate.addAll(sourcePlaylists);
            } else {
                try {
                    int count = Integer.parseInt(input);
                    if (count > 0 && count <= sourcePlaylists.size()) {
                        playlistsToMigrate.addAll(sourcePlaylists.subList(0, count));
                    } else if (count > sourcePlaylists.size()) {
                        LOGGER.warn("Number exceeds available playlists. Migrating all {} playlists.", sourcePlaylists.size());
                        playlistsToMigrate.addAll(sourcePlaylists);
                    } else {
                        LOGGER.warn("Invalid number. No playlists will be migrated.");
                    }
                } catch (NumberFormatException e) {
                    LOGGER.warn("Invalid input. No playlists will be migrated.");
                }
            }

            if (!playlistsToMigrate.isEmpty()) {
                LOGGER.info("Starting migration for {} playlist(s)...", playlistsToMigrate.size());

                boolean quotaErrorOccurred = false;
                for (Playlist sourcePlaylist : playlistsToMigrate) {
                    String sourcePlaylistId = sourcePlaylist.getId();
                    String sourcePlaylistTitle = sourcePlaylist.getSnippet().getTitle();
                    LOGGER.info("Processing source playlist: '{}' (ID: {})", sourcePlaylistTitle, sourcePlaylistId);

                    // Check if this playlist is already fully migrated
                    PlaylistDetail detail = progressTracker.getProgress().playlistDetails().get(sourcePlaylistId);
                    if (detail != null && detail.importedVideos() >= detail.totalVideos() && detail.totalVideos() > 0) {
                        LOGGER.info("Playlist '{}' seems to be already fully migrated. Skipping.", sourcePlaylistTitle);
                        continue;
                    }

                    if (quotaErrorOccurred) {
                        LOGGER.warn("Skipping playlist '{}' due to previous quota error.", sourcePlaylistTitle);
                        break; // Stop processing further playlists if quota was hit
                    }

                    try {
                        // 1. Create a new playlist in the target account
                        String targetPlaylistTitle = MIGRATED_PLAYLIST_PREFIX + sourcePlaylistTitle;
                        Optional<Playlist> existingTargetPlaylistOpt = targetServiceManager.findPlaylistByTitle(targetPlaylistTitle);
                        
                        Playlist targetPlaylist;
                        Set<String> existingTargetVideoIds = Collections.emptySet();

                        if (existingTargetPlaylistOpt.isPresent()) {
                            targetPlaylist = existingTargetPlaylistOpt.get();
                            LOGGER.info("Found existing target playlist: '{}' (ID: {}). Will add missing videos.",
                                    targetPlaylist.getSnippet().getTitle(), targetPlaylist.getId());
                            // Get video IDs already in the target playlist to avoid duplicates
                            List<PlaylistItem> targetItems = targetServiceManager.getPlaylistItems(targetPlaylist.getId());
                            existingTargetVideoIds = targetItems.stream()
                                    .map(item -> item.getSnippet().getResourceId().getVideoId())
                                    .collect(Collectors.toSet());
                            LOGGER.info("Target playlist '{}' already contains {} videos.", targetPlaylist.getSnippet().getTitle(), existingTargetVideoIds.size());
                        } else {
                            LOGGER.info("Creating new playlist: '{}' in TARGET account", targetPlaylistTitle);
                            targetPlaylist = targetServiceManager.createPlaylist(
                                    targetPlaylistTitle,
                                    sourcePlaylist.getSnippet().getDescription(),
                                    DEFAULT_NEW_PLAYLIST_PRIVACY
                            );
                            LOGGER.info("New playlist created with ID: {}", targetPlaylist.getId());
                        }

                        // 2. Get items from the source playlist
                        LOGGER.info("Fetching items from source playlist '{}'...", sourcePlaylistTitle);
                        List<PlaylistItem> sourceItems = sourceServiceManager.getPlaylistItems(sourcePlaylistId);
                        LOGGER.info("Found {} items in source playlist.", sourceItems.size());

                        // 3. Add missing items to the target playlist
                        int videosAddedToTarget = 0;
                        for (PlaylistItem item : sourceItems) {
                            String videoId = item.getSnippet().getResourceId().getVideoId();
                            if (!existingTargetVideoIds.contains(videoId)) {
                                try {
                                    LOGGER.debug("Attempting to add video ID: {} to target playlist '{}'", videoId, targetPlaylist.getSnippet().getTitle());
                                    targetServiceManager.addVideoToPlaylist(targetPlaylist.getId(), videoId);
                                    videosAddedToTarget++;
                                    LOGGER.info("Successfully added video ID: {} to target playlist '{}'", videoId, targetPlaylist.getSnippet().getTitle());
                                } catch (VideoNotFoundException vnfe) {
                                    LOGGER.warn("Skipping video addition to playlist '{}'. Reason: {}. Video ID: {}. Investigate: https://www.youtube.com/watch?v={}",
                                            targetPlaylist.getSnippet().getTitle(), vnfe.getMessage(), videoId, videoId);
                                } catch (VideoPreconditionFailedException vpfe) {
                                    LOGGER.warn("Skipping video addition to playlist '{}' due to precondition failure. Reason: {}. Video ID: {}. Investigate: https://www.youtube.com/watch?v={}",
                                            targetPlaylist.getSnippet().getTitle(), vpfe.getMessage(), videoId, videoId);

                                }
                            } else {
                                LOGGER.debug("Video ID: {} already exists in target playlist '{}'. Skipping addition.", videoId, targetPlaylist.getSnippet().getTitle());
                            }
                                    if (API_CALL_DELAY_MS > 0) {
                                try {
                                    Thread.sleep(API_CALL_DELAY_MS);
                                } catch (InterruptedException ie) {
                                    LOGGER.warn("Thread sleep interrupted during video adding.", ie);
                                    Thread.currentThread().interrupt(); // Restore interrupted status
                                }
                            }
                        }
                    if (videosAddedToTarget > 0) {
                            LOGGER.info("{} new videos added to target playlist '{}'.", videosAddedToTarget, targetPlaylist.getSnippet().getTitle());
                            // Update progress only if new videos were actually added
                            progressTracker.recordVideosImportedFromPlaylist(sourcePlaylistId, videosAddedToTarget);
                            progressTracker.saveProgressToFile();
                            LOGGER.info("Progress updated and saved for playlist '{}'.", sourcePlaylistTitle);
                        } else {
                            LOGGER.info("No new videos to add to target playlist '{}'. It seems up-to-date.", targetPlaylist.getSnippet().getTitle());
                        }

                    } catch (IOException e) {
                        if (e instanceof GoogleJsonResponseException) {
                            GoogleJsonResponseException gjre = (GoogleJsonResponseException) e;
                            if (gjre.getDetails() != null && gjre.getDetails().getErrors() != null && !gjre.getDetails().getErrors().isEmpty()) {
                                String reason = gjre.getDetails().getErrors().get(0).getReason();
                                if ("quotaExceeded".equals(reason)) {
                                    LOGGER.error("FATAL: YouTube API quota exceeded while processing playlist '{}'. Halting further migration attempts.", sourcePlaylistTitle, e);
                                    quotaErrorOccurred = true; // This will stop processing subsequent playlists
                                } else if ("failedPrecondition".equals(reason)) {
                                    LOGGER.error("Failed precondition migrating playlist '{}'. Details: {}", sourcePlaylistTitle, gjre.getDetails().toPrettyString(), e);
                                    // Consider if this error should also halt all migrations:
                                    // quotaErrorOccurred = true;
                                } else {
                                    LOGGER.error("Google API Error migrating playlist '{}' (Reason: {}): {}", sourcePlaylistTitle, reason, e.getMessage(), e);
                                }
                            } else {
                                LOGGER.error("Google API Error (no specific details) migrating playlist '{}': {}", sourcePlaylistTitle, e.getMessage(), e);
                            }
                        } else {
                            LOGGER.error("IO Error migrating playlist '{}': {}", sourcePlaylistTitle, e.getMessage(), e);
                        }
                    } catch (Exception e) { // Catch other potential exceptions like InterruptedException
                        LOGGER.error("An unexpected error occurred during migration of playlist '{}': {}", sourcePlaylistTitle, e.getMessage(), e);
                    }
                }
                if (quotaErrorOccurred) {
                    LOGGER.info("Migration process for selected playlists INTERRUPTED due to quota error.");
                } else {
                    LOGGER.info("Migration process for selected playlists finished.");
                }

                // --- Verification Step ---
                MigrationVerifier verifier = new MigrationVerifier(sourceServiceManager, targetServiceManager, MIGRATED_PLAYLIST_PREFIX);
                verifier.generateVerificationReport(playlistsToMigrate, "."); // Output to current directory
            } else {
                LOGGER.info("No playlists selected for migration.");
            }
            scanner.close();

        } catch (GeneralSecurityException e) {
            LOGGER.error("Security exception: {}", e.getMessage(), e);
        } catch (IOException e) {
            LOGGER.error("IO exception: {}", e.getMessage(), e);
        }
        LOGGER.info("Application finished.");
    }
}