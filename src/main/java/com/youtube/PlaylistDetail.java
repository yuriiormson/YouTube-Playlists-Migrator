package com.youtube;

/**
 * Holds migration details for a specific playlist.
 *
 * @param playlistName The name of the playlist.
 * @param totalVideos The total number of videos in this playlist in the source account.
 * @param importedVideos The number of videos imported from this playlist so far.
 */
public record PlaylistDetail(
    String playlistName,
    int totalVideos,
    int importedVideos
) {
    public PlaylistDetail withVideosImported(int newlyImported) {
        return new PlaylistDetail(this.playlistName, this.totalVideos, this.importedVideos + newlyImported);
    }
}