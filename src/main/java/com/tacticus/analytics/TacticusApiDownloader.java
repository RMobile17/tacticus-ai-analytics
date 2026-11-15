package com.tacticus.analytics;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class TacticusApiDownloader {

    private static final String BASE_URL = "https://api.tacticusgame.com/api/v1";

    public static void main(String[] args) throws IOException, InterruptedException {
        Path dataDir = Files.exists(Path.of("faction_heroes.json")) ? Path.of(".") : Path.of("data");
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir);
        }

        String apiKey = loadApiKey(dataDir);
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("ERROR: API key not found!");
            System.out.println(
                    "Please create 'api_key.txt' in the project root or inside 'data' folder and paste your key into it.");
            return;
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        System.out.println("Starting data download from Tacticus API...");

        // 1. Download Player Data
        fetchAndSave(client, apiKey, "/player", dataDir.resolve("my_player_data.json"));

        // 2. Download Guild Data
        fetchAndSave(client, apiKey, "/guild", dataDir.resolve("my_guild_data.json"));

        // 3. Download Guild Raid Data
        fetchAndSave(client, apiKey, "/guildRaid", dataDir.resolve("my_guild_raid_data.json"));

        System.out.println("Finished! Check the '" + dataDir.toAbsolutePath() + "' folder.");
    }

    private static void fetchAndSave(
            HttpClient client,
            String apiKey,
            String endpoint,
            Path outputFile) throws IOException, InterruptedException {
        String url = BASE_URL + endpoint;
        System.out.println("Requesting: " + url + " ...");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("X-API-KEY", apiKey.trim())
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();

        if (status == 200) {
            Files.writeString(outputFile, response.body());
            System.out.println("SUCCESS [" + status + "]: Saved to " + outputFile.toAbsolutePath());
        } else if (status == 403 || status == 401) {
            System.out.println("SKIPPED [" + status + "] for " + endpoint
                    + ": Key does not have permission/scope for this endpoint.");
        } else {
            System.out.println("FAILED [" + status + "] for " + endpoint + ": " + response.body());
        }
    }

    private static String loadApiKey(Path dataDir) throws IOException {
        Path rootKeyFile = Path.of("api_key.txt");
        if (Files.exists(rootKeyFile)) {
            return Files.readString(rootKeyFile).trim();
        }
        Path dataKeyFile = dataDir.resolve("api_key.txt");
        if (Files.exists(dataKeyFile)) {
            return Files.readString(dataKeyFile).trim();
        }
        return System.getenv("TACTICUS_API_KEY");
    }
}