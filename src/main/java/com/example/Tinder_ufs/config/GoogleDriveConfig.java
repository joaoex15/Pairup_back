package com.example.Tinder_ufs.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Configuration
public class GoogleDriveConfig {

    @Value("${google.drive.application.name}")
    private String applicationName;

    @Value("${google.drive.folder.id}")
    private String folderId;

    @Bean
    public Drive driveService() throws IOException, GeneralSecurityException {
        // Caminho para o arquivo de credenciais da conta de serviço
        // Baixe este arquivo do Google Cloud Console
        InputStream credentialsStream = new ClassPathResource("google-drive-credentials.json").getInputStream();

        GoogleCredential credential = GoogleCredential.fromStream(credentialsStream)
                .createScoped(Collections.singletonList("https://www.googleapis.com/auth/drive.file"));

        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        return new Drive.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName(applicationName)
                .build();
    }

    public String getFolderId() {
        return folderId;
    }
}