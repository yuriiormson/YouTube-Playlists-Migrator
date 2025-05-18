package com.youtube;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeScopes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

public class YouTubeAuth {

    private static final String APPLICATION_NAME = "YouTube Playlist Transfer";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String CLIENT_SECRETS_FILE = "/client_secret.json"; // Path to the file in resources
    private static final List<String> SCOPES = Arrays.asList(
            YouTubeScopes.YOUTUBE_READONLY, // To read playlists and their items
            YouTubeScopes.YOUTUBE_FORCE_SSL // To create/modify playlists and add items
    );

    // Directory to store user credentials
    // Each user (source, destination) will have a separate credentials file
    private static final String CREDENTIALS_DIRECTORY_PATH = ".oauth-credentials";

    /**
     * Creates an authorized Credential object.
     * @param userIdentifier A unique identifier for the user (e.g., "source_account", "destination_account")
     *                       to store credentials separately.
     * @return An authorized Credential object.
     * @throws IOException If an I/O error occurs.
     * @throws GeneralSecurityException If a security error occurs.
     */
    public static Credential authorize(String userIdentifier) throws IOException, GeneralSecurityException {
        GoogleClientSecrets clientSecrets;
        try (InputStream in = YouTubeAuth.class.getResourceAsStream(CLIENT_SECRETS_FILE)) {
            if (in == null) {
                throw new IOException("Resource not found: " + CLIENT_SECRETS_FILE +
                        ". Make sure client_secret.json is in src/main/resources");
            }
            clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        }

        // Create the credentials directory if it doesn't exist
        File credentialsDir = new File(CREDENTIALS_DIRECTORY_PATH);
        if (!credentialsDir.exists()) {
            if (!credentialsDir.mkdirs()) {
                throw new IOException("Could not create directory: " + credentialsDir.getAbsolutePath());
            }
        }

        // Use userIdentifier to create a unique path for the data store
        File dataStoreFile = new File(credentialsDir, "oauth2_" + userIdentifier);
        FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(dataStoreFile);

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                new NetHttpTransport(), JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(dataStoreFactory)
                .setAccessType("offline") // To get a refresh token
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        // Користувач буде перенаправлений на URL для авторизації,
        // потім код авторизації буде отримано локальним сервером.
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    /**
     * Creates a YouTube API service using the provided credentials.
     * @param credential OAuth 2.0 credentials.
     * @return An authorized YouTube service object.
     * @throws GeneralSecurityException If a security error occurs.
     * @throws IOException If an I/O error occurs.
     */
    public static YouTube getService(Credential credential) throws GeneralSecurityException, IOException {
        return new YouTube.Builder(new NetHttpTransport(), JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}
