package com.youtube;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.PlaylistItemSnippet;
import com.google.api.services.youtube.model.PlaylistListResponse;
import com.google.api.services.youtube.model.PlaylistSnippet;
import com.google.api.services.youtube.model.PlaylistStatus;
import com.google.api.services.youtube.model.ResourceId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class YouTubeServiceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(YouTubeServiceManager.class);
    private final YouTube youtubeService;

    public YouTubeServiceManager(YouTube youtubeService) {
        this.youtubeService = youtubeService;
    }

    /**
     * Retrieves a list of playlists for the currently authenticated user.
     * @return A list of Playlist objects.
     * @throws IOException If an error occurs during the API call.
     */
    public List<Playlist> getMyPlaylists() throws IOException {
        List<Playlist> allPlaylists = new ArrayList<>();
        String nextPageToken = null;

        do {
            YouTube.Playlists.List request = youtubeService.playlists()
                    .list(List.of("snippet", "contentDetails"));
            request.setMine(true); // Get playlists of the current user
            request.setMaxResults(50L); // Maximum number per request
            request.setPageToken(nextPageToken);

            PlaylistListResponse response = request.execute();
            if (response.getItems() != null) {
                allPlaylists.addAll(response.getItems());
            }
            nextPageToken = response.getNextPageToken();
        } while (nextPageToken != null);

        return allPlaylists;
    }

    /**
     * Retrieves all items (videos) from the specified playlist.
     * Handles pagination to get all items.
     * @param playlistId The ID of the playlist.
     * @return A list of PlaylistItem objects.
     * @throws IOException If an error occurs during the API call.
     */
    public List<PlaylistItem> getPlaylistItems(String playlistId) throws IOException {
        List<PlaylistItem> allItems = new ArrayList<>();
        String nextPageToken = null;

        do {
            YouTube.PlaylistItems.List request = youtubeService.playlistItems()
                    .list(List.of("snippet", "contentDetails", "status"));
            request.setPlaylistId(playlistId);
            request.setMaxResults(50L); // Maximum number per request
            request.setPageToken(nextPageToken);

            PlaylistItemListResponse response = request.execute();
            if (response.getItems() != null) {
                allItems.addAll(response.getItems());
            }
            nextPageToken = response.getNextPageToken();
        } while (nextPageToken != null);

        return allItems;
    }

    /**
     * Creates a new playlist.
     * @param title The title of the new playlist.
     * @param description The description of the new playlist.
     * @param privacyStatus The privacy status ("public", "private", "unlisted").
     * @return The created Playlist object.
     * @throws IOException If an error occurs during the API call.
     */
    public Playlist createPlaylist(String title, String description, String privacyStatus) throws IOException {
        PlaylistSnippet playlistSnippet = new PlaylistSnippet();
        playlistSnippet.setTitle(title);
        playlistSnippet.setDescription(description);

        PlaylistStatus playlistStatus = new PlaylistStatus();
        playlistStatus.setPrivacyStatus(privacyStatus);

        Playlist playlist = new Playlist();
        playlist.setSnippet(playlistSnippet);
        playlist.setStatus(playlistStatus);

        YouTube.Playlists.Insert request = youtubeService.playlists()
                .insert(List.of("snippet", "status"), playlist);
        return request.execute();
    }

    /**
     * Adds a video to the specified playlist.
     * @param playlistId The ID of the playlist to which the video is added.
     * @param videoId The ID of the video being added.
     * @return The added PlaylistItem object if successful.
     * @throws IOException If a non-404 API error occurs or other I/O issue.
     * @throws VideoNotFoundException if the API returns a 404 error specifically indicating the video was not found.
     * @throws VideoPreconditionFailedException if the API returns a 400 error with "failedPrecondition" reason.
     */
    public PlaylistItem addVideoToPlaylist(String playlistId, String videoId) throws IOException, VideoNotFoundException, VideoPreconditionFailedException {
        ResourceId resourceId = new ResourceId();
        resourceId.setKind("youtube#video");
        resourceId.setVideoId(videoId);

        PlaylistItemSnippet playlistItemSnippet = new PlaylistItemSnippet();
        playlistItemSnippet.setPlaylistId(playlistId);
        playlistItemSnippet.setResourceId(resourceId);
        // You can also set the video's position in the playlist: playlistItemSnippet.setPosition(0L);

        PlaylistItem playlistItem = new PlaylistItem();
        playlistItem.setSnippet(playlistItemSnippet);

        YouTube.PlaylistItems.Insert request = youtubeService.playlistItems()
                .insert(List.of("snippet"), playlistItem);
        try {
            return request.execute();
        } catch (GoogleJsonResponseException e) {
            if (e.getDetails() != null) { // Ensure details exist
                int statusCode = e.getStatusCode();
                List<GoogleJsonError.ErrorInfo> errors = e.getDetails().getErrors();
                String detailMessage = e.getDetails().getMessage() != null ? e.getDetails().getMessage().toLowerCase() : "";

                if (statusCode == 404 && detailMessage.contains("video not found")) {
                    // MainApp will log a user-friendly message. This log is for deeper debugging if needed.
                    // LOGGER.debug("VideoNotFoundException details for videoId: {}: {}", videoId, e.getDetails().toPrettyString());
                    throw new VideoNotFoundException("Video with ID: " + videoId + " not found or access denied.", e);
                }

                if (statusCode == 400 && errors != null && !errors.isEmpty()) {
                    String reason = errors.get(0).getReason();
                    if ("failedPrecondition".equals(reason)) {
                        String apiMessage = errors.get(0).getMessage();
                        // MainApp will log a user-friendly message. This log is for deeper debugging if needed.
                        // LOGGER.debug("VideoPreconditionFailedException details for videoId: {}: Reason: {}, API Message: '{}', Details: {}", videoId, reason, apiMessage, e.getDetails().toPrettyString());
                        throw new VideoPreconditionFailedException("Precondition failed for video ID: " + videoId + ". API Message: " + apiMessage, e);
                    }
                }
            } else {
                // Log for cases where details might be null, though less common for structured errors
                LOGGER.error("GoogleJsonResponseException (details object is null) when adding videoId: {} to playlistId: {}. Status: {}, Message: {}", videoId, playlistId, e.getStatusCode(), e.getMessage(), e);
            }
            throw e; // Re-throw if not specifically handled above or if details were null
        }
    }

    /**
     * Deletes a video from the specified playlist.
     * @param playlistItemId The ID of the playlist item to delete.
     * @throws IOException If an error occurs during the API call.
     */
    public void deleteVideoFromPlaylist(String playlistItemId) throws IOException {
        YouTube.PlaylistItems.Delete request = youtubeService.playlistItems().delete(playlistItemId);
        request.execute();
    }

    /**
     * Moves a video within a playlist by updating its position.
     * @param playlistItemId The ID of the playlist item to move.
     * @param newPosition The new position for the video in the playlist (0-based index).
     * @throws IOException If an error occurs during the API call.
     */
    public void moveVideoInPlaylist(String playlistItemId, long newPosition) throws IOException {
        if (playlistItemId == null || playlistItemId.trim().isEmpty()) {
            throw new IllegalArgumentException("Playlist item ID cannot be null or empty.");
        }

        // Retrieve the existing playlist item to get its snippet
        YouTube.PlaylistItems.List request = youtubeService.playlistItems()
                .list(List.of("snippet"));
        request.setId(List.of(playlistItemId)); // Wrap the ID in a list
        PlaylistItemListResponse response = request.execute();

        if (response.getItems() == null || response.getItems().isEmpty()) {
            throw new IOException("Playlist item not found or an error occurred: " + playlistItemId);
        }
        PlaylistItem playlistItem = response.getItems().get(0);

        // Update the position in the snippet and send the update request
        playlistItem.getSnippet().setPosition(newPosition);
        youtubeService.playlistItems().update(List.of("snippet"), playlistItem).execute();
    }

    /**
     * Finds a playlist by its exact title for the currently authenticated user.
     * Note: This can be inefficient if the user has many playlists.
     * @param title The exact title of the playlist to find.
     * @return An Optional containing the Playlist if found, otherwise an empty Optional.
     * @throws IOException If an error occurs during the API call.
     */
    public Optional<Playlist> findPlaylistByTitle(String title) throws IOException {
        List<Playlist> myPlaylists = getMyPlaylists(); // Reuses the existing method to get all playlists
        for (Playlist playlist : myPlaylists) {
            if (playlist.getSnippet() != null && title.equals(playlist.getSnippet().getTitle())) {
                return Optional.of(playlist);
            }
        }
        return Optional.empty();
    }
}
