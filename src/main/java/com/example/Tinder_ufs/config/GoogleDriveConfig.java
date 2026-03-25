package com.example.Tinder_ufs.config;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
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
import java.io.StringReader;
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

    // Variável de ambiente com o JSON das credenciais (usado no Railway/produção)
    // Ex: GOOGLE_DRIVE_CREDENTIALS_JSON={"web":{"client_id":"...","client_secret":"..."}}
    @Value("${GOOGLE_DRIVE_CREDENTIALS_JSON:}")
    private String credentialsJson;

    // Refresh token gerado localmente e salvo como variável de ambiente no Railway
    // Ex: GOOGLE_DRIVE_REFRESH_TOKEN=1//0g...
    @Value("${GOOGLE_DRIVE_REFRESH_TOKEN:}")
    private String refreshToken;

    @PostConstruct
    public void init() {
        System.out.println("==============================================");
        System.out.println("[Drive] application-name      : " + applicationName);
        System.out.println("[Drive] root folder-id        : '" + rootFolderId + "'");
        System.out.println("[Drive] credentials via env?  : " + !credentialsJson.isBlank());
        System.out.println("[Drive] refresh token via env?: " + !refreshToken.isBlank());
        System.out.println("==============================================");
    }

    @Bean
    public Drive driveService() throws Exception {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        // ── Modo Produção (Railway): usa variáveis de ambiente ──────────────
        if (!credentialsJson.isBlank() && !refreshToken.isBlank()) {
            System.out.println("[Drive] Usando credenciais via variável de ambiente (produção)");

            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                    JSON_FACTORY,
                    new StringReader(credentialsJson)
            );

            String clientId     = clientSecrets.getDetails().getClientId();
            String clientSecret = clientSecrets.getDetails().getClientSecret();

            // Cria credencial com refresh token salvo
            GoogleCredential credential = new GoogleCredential.Builder()
                    .setTransport(httpTransport)
                    .setJsonFactory(JSON_FACTORY)
                    .setClientSecrets(clientId, clientSecret)
                    .build();

            credential.setRefreshToken(refreshToken);
            // Força renovação do access token
            credential.refreshToken();

            return new Drive.Builder(httpTransport, JSON_FACTORY, credential)
                    .setApplicationName(applicationName)
                    .build();
        }

        // ── Modo Local (desenvolvimento): usa arquivo JSON + OAuth interativo ──
        System.out.println("[Drive] Usando credenciais via arquivo JSON (desenvolvimento)");

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

        // Verifica se já tem token salvo localmente
        Credential credential = flow.loadCredential("user");
        if (credential == null) {
            // Primeira vez: abre browser para autorizar
            com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver receiver =
                    new com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
                            .Builder().setPort(8888).build();
            credential = new com.google.api.client.extensions.java6.auth.oauth2
                    .AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

            // Imprime o refresh token para você salvar no Railway
            System.out.println("==============================================");
            System.out.println("[Drive] REFRESH TOKEN (salve no Railway!):");
            System.out.println(credential.getRefreshToken());
            System.out.println("==============================================");
        }

        return new Drive.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(applicationName)
                .build();
    }

    public String getRootFolderId() {
        return rootFolderId;
    }

    public String getOrCreateUserFolder(Drive drive, String pessoaId) throws IOException {

        System.out.println("[Drive] getOrCreateUserFolder → pessoaId='" + pessoaId
                + "' | rootFolderId='" + rootFolderId + "'");

        if (pessoaId == null || pessoaId.isBlank()) {
            throw new IllegalArgumentException("pessoaId não pode ser nulo ou vazio");
        }

        String query = "mimeType = 'application/vnd.google-apps.folder'"
                + " and trashed = false"
                + " and name = '" + pessoaId + "'";

        if (rootFolderId != null && !rootFolderId.isBlank()) {
            query += " and '" + rootFolderId + "' in parents";
        }

        List<File> folders = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()
                .getFiles();

        if (folders != null && !folders.isEmpty()) {
            String existingId = folders.get(0).getId();
            System.out.println("[Drive] Pasta já existe → ID: " + existingId);
            return existingId;
        }

        File fileMetadata = new File();
        fileMetadata.setName(pessoaId);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");

        if (rootFolderId != null && !rootFolderId.isBlank()) {
            fileMetadata.setParents(Collections.singletonList(rootFolderId));
        }

        File pastaCriada = drive.files().create(fileMetadata)
                .setFields("id, name, parents")
                .execute();

        System.out.println("[Drive] Pasta criada! | name: '" + pastaCriada.getName()
                + "' | id: " + pastaCriada.getId());

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