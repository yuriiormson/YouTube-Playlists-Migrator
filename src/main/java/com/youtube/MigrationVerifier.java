package com.youtube;

import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItem;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class MigrationVerifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationVerifier.class);
    private final YouTubeServiceManager sourceServiceManager;
    private final YouTubeServiceManager targetServiceManager;
    private final String migratedPlaylistPrefix;

    public MigrationVerifier(YouTubeServiceManager sourceServiceManager,
                             YouTubeServiceManager targetServiceManager,
                             String migratedPlaylistPrefix) {
        this.sourceServiceManager = sourceServiceManager;
        this.targetServiceManager = targetServiceManager;
        this.migratedPlaylistPrefix = migratedPlaylistPrefix;
    }

    private static class PlaylistVerificationResult {
        String sourcePlaylistName;
        String sourcePlaylistId;
        int sourceVideoCount = 0;
        String expectedTargetPlaylistName;
        String actualTargetPlaylistId = "Not Found";
        int targetVideoCount = 0;
        String migrationStatus = "Verification Pending";
        List<String> missingVideoIdsInTarget = new ArrayList<>();
        List<String> extraVideoIdsInTarget = new ArrayList<>(); // Videos in target but not in source
        String notes = "";

        String getYouTubeLink(String videoId) {
            return "https://www.youtube.com/watch?v=" + videoId;
        }
    }

    public void generateVerificationReport(List<Playlist> sourcePlaylistsToVerify, String outputDirectory) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String outputFilePath = outputDirectory + "/Migration_Verification_Report_" + timestamp + ".xlsx";
        LOGGER.info("Starting migration verification. Report will be saved to: {}", outputFilePath);

        List<PlaylistVerificationResult> results = new ArrayList<>();

        for (Playlist sourcePlaylist : sourcePlaylistsToVerify) {
            PlaylistVerificationResult pvr = new PlaylistVerificationResult();
            pvr.sourcePlaylistName = sourcePlaylist.getSnippet().getTitle();
            pvr.sourcePlaylistId = sourcePlaylist.getId();
            pvr.expectedTargetPlaylistName = migratedPlaylistPrefix + pvr.sourcePlaylistName;

            Set<String> sourceVideoIds = new HashSet<>();
            try {
                List<PlaylistItem> sourceItems = sourceServiceManager.getPlaylistItems(pvr.sourcePlaylistId);
                pvr.sourceVideoCount = sourceItems.size();
                sourceVideoIds = sourceItems.stream()
                        .map(item -> item.getSnippet().getResourceId().getVideoId())
                        .collect(Collectors.toSet());
            } catch (IOException e) {
                LOGGER.error("Error fetching items for source playlist '{}' ({}): {}", pvr.sourcePlaylistName, pvr.sourcePlaylistId, e.getMessage());
                pvr.migrationStatus = "Error fetching source items";
                pvr.notes = "Could not retrieve videos from source: " + e.getMessage();
                results.add(pvr);
                continue;
            }

            try {
                Optional<Playlist> targetPlaylistOpt = targetServiceManager.findPlaylistByTitle(pvr.expectedTargetPlaylistName);
                if (targetPlaylistOpt.isPresent()) {
                    Playlist targetPlaylist = targetPlaylistOpt.get();
                    pvr.actualTargetPlaylistId = targetPlaylist.getId();
                    Set<String> targetVideoIds = new HashSet<>();
                    try {
                        List<PlaylistItem> targetItems = targetServiceManager.getPlaylistItems(pvr.actualTargetPlaylistId);
                        pvr.targetVideoCount = targetItems.size();
                        targetVideoIds = targetItems.stream()
                                .map(item -> item.getSnippet().getResourceId().getVideoId())
                                .collect(Collectors.toSet());

                        pvr.missingVideoIdsInTarget = new ArrayList<>(sourceVideoIds);
                        pvr.missingVideoIdsInTarget.removeAll(targetVideoIds);

                        pvr.extraVideoIdsInTarget = new ArrayList<>(targetVideoIds);
                        pvr.extraVideoIdsInTarget.removeAll(sourceVideoIds);

                        if (pvr.missingVideoIdsInTarget.isEmpty() && pvr.extraVideoIdsInTarget.isEmpty() && pvr.sourceVideoCount == pvr.targetVideoCount) {
                            pvr.migrationStatus = "Complete";
                        } else {
                            pvr.migrationStatus = "Partial";
                            if (!pvr.missingVideoIdsInTarget.isEmpty()) pvr.notes += pvr.missingVideoIdsInTarget.size() + " video(s) missing from target. ";
                            if (!pvr.extraVideoIdsInTarget.isEmpty()) pvr.notes += pvr.extraVideoIdsInTarget.size() + " extra video(s) in target. ";
                        }

                    } catch (IOException e) {
                        LOGGER.error("Error fetching items for target playlist '{}' ({}): {}", pvr.expectedTargetPlaylistName, pvr.actualTargetPlaylistId, e.getMessage());
                        pvr.migrationStatus = "Error fetching target items";
                        pvr.notes = "Found target playlist, but could not retrieve its videos: " + e.getMessage();
                    }
                } else {
                    pvr.migrationStatus = "Target Playlist Not Found";
                    pvr.notes = "Expected target playlist was not found in the target account.";
                    pvr.missingVideoIdsInTarget.addAll(sourceVideoIds); // All source videos are considered missing
                }
            } catch (IOException e) {
                LOGGER.error("Error finding target playlist '{}': {}", pvr.expectedTargetPlaylistName, e.getMessage());
                pvr.migrationStatus = "Error finding target playlist";
                pvr.notes = "API error while searching for target playlist: " + e.getMessage();
                pvr.missingVideoIdsInTarget.addAll(sourceVideoIds);
            }
            results.add(pvr);
        }

        writeResultsToExcel(results, outputFilePath);
        LOGGER.info("Verification report generated successfully: {}", outputFilePath);
    }

    private void writeResultsToExcel(List<PlaylistVerificationResult> results, String filePath) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet summarySheet = workbook.createSheet("Summary");
            Sheet detailSheet = workbook.createSheet("Playlist Details");
            Sheet missingVideosSheet = workbook.createSheet("Missing Videos Details");

            // --- Create Headers ---
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerCellStyle = workbook.createCellStyle();
            headerCellStyle.setFont(headerFont);

            // Detail Sheet Headers
            String[] detailHeaders = {"Source Playlist Name", "Source Playlist ID", "Source Video Count",
                                      "Expected Target Name", "Target Playlist ID", "Target Video Count",
                                      "Migration Status", "Missing in Target Count", "Extra in Target Count", "Notes"};
            Row detailHeaderRow = detailSheet.createRow(0);
            for (int i = 0; i < detailHeaders.length; i++) {
                Cell cell = detailHeaderRow.createCell(i);
                cell.setCellValue(detailHeaders[i]);
                cell.setCellStyle(headerCellStyle);
            }

            // Missing Videos Sheet Headers
            String[] missingHeaders = {"Source Playlist Name", "Missing Video ID", "YouTube Link"};
            Row missingHeaderRow = missingVideosSheet.createRow(0);
            for (int i = 0; i < missingHeaders.length; i++) {
                Cell cell = missingHeaderRow.createCell(i);
                cell.setCellValue(missingHeaders[i]);
                cell.setCellStyle(headerCellStyle);
            }

            // --- Populate Detail Sheet & Missing Videos Sheet ---
            int detailRowNum = 1;
            int missingRowNum = 1;
            long totalSourceVideos = 0;
            long totalTargetVideosFound = 0;
            long playlistsComplete = 0;
            long playlistsPartial = 0;
            long playlistsTargetNotFound = 0;

            for (PlaylistVerificationResult res : results) {
                Row row = detailSheet.createRow(detailRowNum++);
                row.createCell(0).setCellValue(res.sourcePlaylistName);
                row.createCell(1).setCellValue(res.sourcePlaylistId);
                row.createCell(2).setCellValue(res.sourceVideoCount);
                row.createCell(3).setCellValue(res.expectedTargetPlaylistName);
                row.createCell(4).setCellValue(res.actualTargetPlaylistId);
                row.createCell(5).setCellValue(res.targetVideoCount);
                row.createCell(6).setCellValue(res.migrationStatus);
                row.createCell(7).setCellValue(res.missingVideoIdsInTarget.size());
                row.createCell(8).setCellValue(res.extraVideoIdsInTarget.size());
                row.createCell(9).setCellValue(res.notes);

                totalSourceVideos += res.sourceVideoCount;
                if (!"Not Found".equals(res.actualTargetPlaylistId) && !"Error fetching target items".equals(res.migrationStatus)) {
                     totalTargetVideosFound += res.targetVideoCount;
                }
                if ("Complete".equals(res.migrationStatus)) playlistsComplete++;
                else if ("Partial".equals(res.migrationStatus)) playlistsPartial++;
                else if ("Target Playlist Not Found".equals(res.migrationStatus)) playlistsTargetNotFound++;

                for (String missingVideoId : res.missingVideoIdsInTarget) {
                    Row missingRow = missingVideosSheet.createRow(missingRowNum++);
                    missingRow.createCell(0).setCellValue(res.sourcePlaylistName);
                    missingRow.createCell(1).setCellValue(missingVideoId);
                    missingRow.createCell(2).setCellValue(res.getYouTubeLink(missingVideoId));
                }
            }

            // --- Populate Summary Sheet ---
            int summaryRowNum = 0;
            summarySheet.createRow(summaryRowNum++).createCell(0).setCellValue("Migration Verification Summary");
            summarySheet.getRow(0).getCell(0).setCellStyle(headerCellStyle);
            summarySheet.createRow(summaryRowNum++).createCell(0).setCellValue("Report Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            summarySheet.createRow(summaryRowNum++); // Spacer
            summarySheet.createRow(summaryRowNum++).createCell(0).setCellValue("Total Source Playlists Analyzed: " + results.size());
            summarySheet.createRow(summaryRowNum++).createCell(0).setCellValue("Total Videos in Analyzed Source Playlists: " + totalSourceVideos);
            summarySheet.createRow(summaryRowNum++).createCell(0).setCellValue("Total Videos Found in Corresponding Target Playlists: " + totalTargetVideosFound);
            summarySheet.createRow(summaryRowNum++); // Spacer
            summarySheet.createRow(summaryRowNum++).createCell(0).setCellValue("Playlists Fully Migrated (Complete): " + playlistsComplete);
            summarySheet.createRow(summaryRowNum++).createCell(0).setCellValue("Playlists Partially Migrated: " + playlistsPartial);
            summarySheet.createRow(summaryRowNum++).createCell(0).setCellValue("Playlists Where Target Was Not Found: " + playlistsTargetNotFound);

            // Auto-size columns for readability
            for (int i = 0; i < detailHeaders.length; i++) detailSheet.autoSizeColumn(i);
            for (int i = 0; i < missingHeaders.length; i++) missingVideosSheet.autoSizeColumn(i);
            summarySheet.autoSizeColumn(0);

            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }

        } catch (IOException e) {
            LOGGER.error("Error writing verification report to Excel file: {}", e.getMessage(), e);
        }
    }
}