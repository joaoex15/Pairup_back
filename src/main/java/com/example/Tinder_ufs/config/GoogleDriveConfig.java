package com.example.Tinder_ufs.config;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

@Configuration
public class GoogleDriveConfig {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_FILE);
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    @Value("${google.drive.application.name:TinderUFS}")
    private String applicationName;

    @Value("${google.drive.folder-id:}")
    private String rootFolderId;

    // Roda ao subir o Spring — confirma se o @Value leu o folder-id corretamente
    @PostConstruct
    public void init() {
        System.out.println("==============================================");
        System.out.println("[Drive] application-name : " + applicationName);
        System.out.println("[Drive] root folder-id   : '" + rootFolderId + "'");
        System.out.println("[Drive] folder-id vazio? : " + (rootFolderId == null || rootFolderId.isBlank()));
        System.out.println("==============================================");
    }

    @Bean
    public Drive driveService() throws Exception {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                JSON_FACTORY,
                new InputStreamReader(
                        new ClassPathResource("google-drive-credentials.json").getInputStream()
                )
        );

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(8888)
                .build();

        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        return new Drive.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(applicationName)
                .build();
    }

    public String getRootFolderId() {
        return rootFolderId;
    }

    /**
     * Busca a subpasta com nome = pessoaId dentro da rootFolder.
     * Se não existir, cria automaticamente com o nome correto.
     */
    public String getOrCreateUserFolder(Drive drive, String pessoaId) throws IOException {

        System.out.println("[Drive] getOrCreateUserFolder → pessoaId='" + pessoaId
                + "' | rootFolderId='" + rootFolderId + "'");

        if (pessoaId == null || pessoaId.isBlank()) {
            throw new IllegalArgumentException("pessoaId não pode ser nulo ou vazio");
        }

        // Query: busca pasta com nome exato = pessoaId dentro da rootFolder
        String query = "mimeType = 'application/vnd.google-apps.folder'"
                + " and trashed = false"
                + " and name = '" + pessoaId + "'";

        if (rootFolderId != null && !rootFolderId.isBlank()) {
            query += " and '" + rootFolderId + "' in parents";
        }

        System.out.println("[Drive] Query: " + query);

        List<File> folders = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()
                .getFiles();

        // Pasta já existe → retorna o ID
        if (folders != null && !folders.isEmpty()) {
            String existingId = folders.get(0).getId();
            System.out.println("[Drive] Pasta já existe → ID: " + existingId);
            return existingId;
        }

        // Cria a pasta com nome = pessoaId
        File fileMetadata = new File();
        fileMetadata.setName(pessoaId);                                      // <-- nome correto
        fileMetadata.setMimeType("application/vnd.google-apps.folder");

        if (rootFolderId != null && !rootFolderId.isBlank()) {
            fileMetadata.setParents(Collections.singletonList(rootFolderId)); // <-- dentro da pasta raiz
            System.out.println("[Drive] Criando pasta dentro de: " + rootFolderId);
        } else {
            System.out.println("[Drive] AVISO: rootFolderId não configurado — pasta criada na raiz do Drive!");
        }

        File pastaCriada = drive.files().create(fileMetadata)
                .setFields("id, name, parents")
                .execute();

        System.out.println("[Drive] Pasta criada!"
                + " | name: '"  + pastaCriada.getName() + "'"
                + " | id: "     + pastaCriada.getId()
                + " | parents: " + pastaCriada.getParents());

        // Torna a pasta pública (leitura)
        try {
            Permission permission = new Permission()
                    .setType("anyone")
                    .setRole("reader");
            drive.permissions().create(pastaCriada.getId(), permission).execute();
            System.out.println("[Drive] Pasta tornada pública ✓");
        } catch (Exception e) {
            System.out.println("[Drive] Aviso: não foi possível tornar pasta pública: " + e.getMessage());
        }

        return pastaCriada.getId();
    }
}