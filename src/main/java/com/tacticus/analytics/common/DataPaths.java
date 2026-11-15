package com.tacticus.analytics.common;

import java.nio.file.Files;
import java.nio.file.Path;

public record DataPaths(
        Path dataDir,
        Path xrefDir,
        Path heroesDir,
        Path guildRaidDir) {
    public static DataPaths resolve() {
        Path dataDir = Files.exists(Path.of("faction_heroes.json")) ? Path.of(".") : Path.of("data");
        Path xrefDir = Files.exists(dataDir.resolve("xref")) ? dataDir.resolve("xref") : dataDir;
        Path heroesDir = dataDir.resolve("heroes");
        Path guildRaidDir = dataDir.resolve("guild-raid");
        return new DataPaths(dataDir, xrefDir, heroesDir, guildRaidDir);
    }
}
