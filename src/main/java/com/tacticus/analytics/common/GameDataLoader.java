package com.tacticus.analytics.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tacticus.analytics.model.GameModels.AbilityData;
import com.tacticus.analytics.model.GameModels.AbilityDisplayMetadata;
import com.tacticus.analytics.model.GameModels.DetailedHeroFile;
import com.tacticus.analytics.model.GameModels.FactionHeroRef;
import com.tacticus.analytics.model.GameModels.HeroAbilitiesEntry;
import com.tacticus.analytics.model.GameModels.ItemDefinition;
import com.tacticus.analytics.model.GameModels.TraitHeroRef;
import com.tacticus.analytics.model.GameModels.TraitMetadata;
import com.tacticus.analytics.model.HeroProfile;
import com.tacticus.analytics.model.PlayerModels.PlayerAbility;
import com.tacticus.analytics.model.PlayerModels.PlayerApiResponse;
import com.tacticus.analytics.model.PlayerModels.PlayerDetails;
import com.tacticus.analytics.model.PlayerModels.PlayerUnit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class GameDataLoader {

    private GameDataLoader() {
    }

    public record LoadedGameData(
            Map<String, HeroProfile> heroesById,
            Map<String, List<FactionHeroRef>> factionHeroes,
            Map<String, List<TraitHeroRef>> traitHeroes,
            Map<String, TraitMetadata> traitLookup,
            Map<String, AbilityDisplayMetadata> abilityDisplayLookup,
            Map<String, ItemDefinition> itemsCatalog,
            PlayerDetails playerDetails,
            int matchedPlayerUnits) {
    }

    public static LoadedGameData loadAll(DataPaths paths) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        Map<String, List<FactionHeroRef>> factionHeroes = mapper.readValue(
                paths.xrefDir().resolve("faction_heroes.json").toFile(),
                new TypeReference<>() {
                });

        Map<String, HeroAbilitiesEntry> heroAbilities = mapper.readValue(
                paths.xrefDir().resolve("hero_abilities.json").toFile(),
                new TypeReference<>() {
                });

        Map<String, TraitMetadata> rawTraits = mapper.readValue(
                paths.xrefDir().resolve("trait_display_metadata.generated.json").toFile(),
                new TypeReference<>() {
                });

        Map<String, List<TraitHeroRef>> traitHeroes = mapper.readValue(
                paths.xrefDir().resolve("trait_heroes.json").toFile(),
                new TypeReference<>() {
                });

        Map<String, AbilityDisplayMetadata> abilityDisplayLookup = new HashMap<>();
        File abilityMetaFile = paths.xrefDir().resolve("ability_display_metadata.generated.json").toFile();
        if (abilityMetaFile.exists()) {
            Map<String, AbilityDisplayMetadata> rawAbilityMeta = mapper.readValue(
                    abilityMetaFile,
                    new TypeReference<>() {
                    });
            rawAbilityMeta.forEach((k, v) -> abilityDisplayLookup.put(k.toLowerCase(), v));
        }

        Map<String, TraitMetadata> traitLookup = new HashMap<>();
        rawTraits.forEach((k, v) -> {
            traitLookup.put(k.toLowerCase(), v);
            if (v.id() != null) {
                traitLookup.put(v.id().toLowerCase(), v);
            }
        });

        File itemsFile = paths.dataDir().resolve("items.json").toFile();
        final Map<String, ItemDefinition> itemsCatalog;
        if (itemsFile.exists()) {
            System.out.println("Loading items catalog from items.json...");
            itemsCatalog = mapper.readValue(itemsFile, new TypeReference<>() {
            });
        } else {
            itemsCatalog = Map.of();
        }

        Map<String, HeroProfile> heroesById = new TreeMap<>();

        factionHeroes.forEach((factionKey, list) -> {
            String readableFaction = GameTextFormatter.formatFactionName(factionKey);
            for (FactionHeroRef ref : list) {
                HeroProfile profile = heroesById.computeIfAbsent(ref.id(), k -> new HeroProfile());
                profile.id = ref.id();
                profile.name = ref.name();
                profile.faction = readableFaction;
                profile.rarity = ref.rarity();
            }
        });

        heroAbilities.forEach((heroId, abEntry) -> {
            HeroProfile profile = heroesById.computeIfAbsent(heroId, k -> new HeroProfile());
            profile.id = heroId;
            if (profile.name == null) {
                profile.name = abEntry.name();
            }
            profile.abilities = abEntry;
        });

        traitHeroes.forEach((traitKey, list) -> {
            TraitMetadata meta = traitLookup.getOrDefault(
                    traitKey.toLowerCase(),
                    new TraitMetadata(traitKey, traitKey, "", List.of()));
            for (TraitHeroRef ref : list) {
                HeroProfile profile = heroesById.get(ref.id());
                if (profile != null) {
                    profile.traits.add(meta);
                }
            }
        });

        if (Files.exists(paths.heroesDir()) && Files.isDirectory(paths.heroesDir())) {
            System.out.println("Loading detailed hero files from: " + paths.heroesDir().toAbsolutePath());
            try (Stream<Path> fileStream = Files.list(paths.heroesDir())) {
                List<Path> jsonFiles = fileStream.filter(p -> p.toString().endsWith(".json")).toList();
                for (Path heroFile : jsonFiles) {
                    DetailedHeroFile dHero = mapper.readValue(heroFile.toFile(), DetailedHeroFile.class);
                    if (dHero != null && dHero.id() != null) {
                        HeroProfile profile = heroesById.get(dHero.id());
                        if (profile != null) {
                            profile.details = dHero;
                        }
                    }
                }
            }
        }

        PlayerDetails playerDetails = null;
        int matchedPlayerUnits = 0;
        File playerFile = paths.dataDir().resolve("my_player_data.json").toFile();
        if (playerFile.exists()) {
            System.out.println("Loading personal player data from my_player_data.json...");
            PlayerApiResponse playerResponse = mapper.readValue(playerFile, PlayerApiResponse.class);
            if (playerResponse != null && playerResponse.player() != null) {
                playerDetails = playerResponse.player().details();
                List<PlayerUnit> pUnits = playerResponse.player().units();
                if (pUnits != null) {
                    for (PlayerUnit pu : pUnits) {
                        HeroProfile matched = ProgressionHelper.findMatchingHeroProfile(pu, heroesById.values());
                        if (matched != null) {
                            matched.playerUnit = pu;
                            matchedPlayerUnits++;
                        } else {
                            System.out.println(
                                    "Warning: Could not match player unit '" + pu.name() + "' (ID: " + pu.id() + ")");
                        }
                    }
                }
            }
            System.out.println("Matched " + matchedPlayerUnits + " unlocked player units!");
        }

        Path abilitiesDir = resolveAbilitiesDir(paths);
        Map<String, AbilityData> abilitiesCatalog = loadAbilitiesCatalog(abilitiesDir, mapper);
        Map<String, AbilityData> abilityFileCache = new HashMap<>();
        for (HeroProfile profile : heroesById.values()) {
            profile.abilities = enrichAbilities(profile, abilitiesCatalog, abilitiesDir, mapper, abilityFileCache);
        }

        return new LoadedGameData(
                heroesById,
                factionHeroes,
                traitHeroes,
                traitLookup,
                abilityDisplayLookup,
                itemsCatalog,
                playerDetails,
                matchedPlayerUnits);
    }

    private static Path resolveAbilitiesDir(DataPaths paths) {
        Path gameDataAbilities = paths.dataDir().resolve("game-data").resolve("abilities");
        if (Files.isDirectory(gameDataAbilities)) {
            return gameDataAbilities;
        }
        return paths.dataDir().resolve("abilities");
    }

    private static Map<String, AbilityData> loadAbilitiesCatalog(Path abilitiesDir, ObjectMapper mapper) {
        Map<String, AbilityData> catalog = new HashMap<>();
        if (!Files.isDirectory(abilitiesDir)) {
            return catalog;
        }

        List<String> bucketFiles = List.of("active.json", "passive.json", "other.json");
        for (String fileName : bucketFiles) {
            Path file = abilitiesDir.resolve(fileName);
            if (!Files.exists(file)) {
                continue;
            }
            try {
                Map<String, AbilityData> rawMap = mapper.readValue(
                        file.toFile(),
                        new TypeReference<>() {
                        });
                rawMap.forEach((key, data) -> {
                    if (key != null && data != null) {
                        catalog.put(key.toLowerCase(), data);
                        if (data.gameId() != null && !data.gameId().isBlank()) {
                            catalog.putIfAbsent(data.gameId().toLowerCase(), data);
                        }
                    }
                });
            } catch (IOException e) {
                System.out.println("Warning: Failed to parse abilities file " + file + ": " + e.getMessage());
            }
        }

        if (!catalog.isEmpty()) {
            System.out.println(
                    "Loaded " + catalog.size() + " ability definitions from: " + abilitiesDir.toAbsolutePath());
        }
        return catalog;
    }

    private static HeroAbilitiesEntry enrichAbilities(
            HeroProfile profile,
            Map<String, AbilityData> abilitiesCatalog,
            Path abilitiesDir,
            ObjectMapper mapper,
            Map<String, AbilityData> abilityFileCache) {
        HeroAbilitiesEntry raw = profile.abilities;
        DetailedHeroFile details = profile.details;
        PlayerUnit pu = profile.playerUnit;
        boolean isMoW = profile.traits.stream().anyMatch(t -> "MachineOfWar".equalsIgnoreCase(t.id()));

        String activeId = raw != null ? raw.activeAbility() : null;
        if ((activeId == null || activeId.isBlank())
                && details != null
                && details.activeAbilities() != null
                && !details.activeAbilities().isEmpty()) {
            activeId = details.activeAbilities().get(0);
        }
        if ((activeId == null || activeId.isBlank())
                && isMoW
                && pu != null
                && pu.abilities() != null
                && !pu.abilities().isEmpty()) {
            activeId = pu.abilities().get(0).id();
        }
        AbilityData activeData = resolveAbilityData(
                activeId,
                raw != null ? raw.activeAbilityData() : null,
                abilitiesCatalog,
                abilitiesDir,
                mapper,
                abilityFileCache);

        String secondaryId = raw != null ? raw.secondaryAbility() : null;
        if ((secondaryId == null || secondaryId.isBlank())
                && details != null
                && details.activeAbilities() != null
                && details.activeAbilities().size() > 1) {
            secondaryId = details.activeAbilities().get(1);
        }
        if ((secondaryId == null || secondaryId.isBlank())
                && isMoW
                && pu != null
                && pu.abilities() != null
                && pu.abilities().size() > 1) {
            secondaryId = pu.abilities().get(1).id();
        }
        AbilityData secondaryData = resolveAbilityData(
                secondaryId,
                raw != null ? raw.secondaryAbilityData() : null,
                abilitiesCatalog,
                abilitiesDir,
                mapper,
                abilityFileCache);

        String passiveId = raw != null ? raw.passiveAbility() : null;
        if ((passiveId == null || passiveId.isBlank())
                && details != null
                && details.passiveAbilities() != null
                && !details.passiveAbilities().isEmpty()) {
            passiveId = details.passiveAbilities().get(0);
        }
        AbilityData passiveData = resolveAbilityData(
                passiveId,
                raw != null ? raw.passiveAbilityData() : null,
                abilitiesCatalog,
                abilitiesDir,
                mapper,
                abilityFileCache);

        Set<String> nonMythicLower = new LinkedHashSet<>();
        addLowerIfPresent(nonMythicLower, activeId, activeData);
        addLowerIfPresent(nonMythicLower, secondaryId, secondaryData);
        addLowerIfPresent(nonMythicLower, passiveId, passiveData);

        Set<String> mythicIds = new LinkedHashSet<>();
        if (raw != null) {
            if (raw.mythicAbility() != null && !raw.mythicAbility().isBlank()) {
                mythicIds.add(raw.mythicAbility());
            }
            if (raw.mythicAbilities() != null) {
                for (String id : raw.mythicAbilities()) {
                    if (id != null && !id.isBlank()) {
                        mythicIds.add(id);
                    }
                }
            }
        }
        if (details != null && details.mythicAbilities() != null) {
            for (String id : details.mythicAbilities()) {
                if (id != null && !id.isBlank()) {
                    mythicIds.add(id);
                }
            }
        }
        if (pu != null && pu.abilities() != null) {
            for (PlayerAbility pa : pu.abilities()) {
                if (pa.id() != null && !pa.id().isBlank() && !nonMythicLower.contains(pa.id().toLowerCase())) {
                    mythicIds.add(pa.id());
                }
            }
        }

        Map<String, AbilityData> mythicDataMap = new LinkedHashMap<>();
        if (raw != null && raw.mythicAbilitiesData() != null) {
            mythicDataMap.putAll(raw.mythicAbilitiesData());
        }
        if (raw != null && raw.mythicAbility() != null && raw.mythicAbilityData() != null) {
            mythicDataMap.putIfAbsent(raw.mythicAbility(), raw.mythicAbilityData());
        }
        for (String mythicId : mythicIds) {
            if (!mythicDataMap.containsKey(mythicId)) {
                AbilityData loaded = resolveAbilityData(
                        mythicId, null, abilitiesCatalog, abilitiesDir, mapper, abilityFileCache);
                if (loaded != null) {
                    mythicDataMap.put(mythicId, loaded);
                }
            }
        }

        if (raw == null
                && activeId == null
                && secondaryId == null
                && passiveId == null
                && mythicIds.isEmpty()) {
            return null;
        }

        String entryName = raw != null && raw.name() != null ? raw.name() : profile.name;
        String primaryMythicId = mythicIds.isEmpty() ? null : mythicIds.iterator().next();
        AbilityData primaryMythicData = primaryMythicId != null ? mythicDataMap.get(primaryMythicId) : null;

        return new HeroAbilitiesEntry(
                entryName,
                activeId,
                activeData,
                secondaryId,
                secondaryData,
                passiveId,
                passiveData,
                primaryMythicId,
                primaryMythicData,
                new ArrayList<>(mythicIds),
                mythicDataMap);
    }

    private static void addLowerIfPresent(Set<String> target, String abilityId, AbilityData data) {
        if (abilityId != null && !abilityId.isBlank()) {
            target.add(abilityId.toLowerCase());
        }
        if (data != null && data.gameId() != null && !data.gameId().isBlank()) {
            target.add(data.gameId().toLowerCase());
        }
    }

    private static AbilityData resolveAbilityData(
            String abilityId,
            AbilityData existing,
            Map<String, AbilityData> abilitiesCatalog,
            Path abilitiesDir,
            ObjectMapper mapper,
            Map<String, AbilityData> abilityFileCache) {
        if (existing != null) {
            return existing;
        }
        if (abilityId == null || abilityId.isBlank()) {
            return null;
        }
        AbilityData fromCatalog = abilitiesCatalog.get(abilityId.toLowerCase());
        if (fromCatalog != null) {
            return fromCatalog;
        }
        return loadAbilityFromDisk(abilityId, abilitiesDir, mapper, abilityFileCache);
    }

    private static AbilityData loadAbilityFromDisk(
            String abilityId,
            Path abilitiesDir,
            ObjectMapper mapper,
            Map<String, AbilityData> cache) {
        if (abilityId == null || abilityId.isBlank() || !Files.isDirectory(abilitiesDir)) {
            return null;
        }
        if (cache.containsKey(abilityId)) {
            return cache.get(abilityId);
        }
        Path candidate = abilitiesDir.resolve(abilityId + ".json");
        AbilityData parsed = null;
        if (Files.exists(candidate)) {
            try {
                parsed = mapper.readValue(candidate.toFile(), AbilityData.class);
            } catch (IOException e) {
                System.out.println("Warning: Failed to parse ability file " + candidate + ": " + e.getMessage());
            }
        }
        cache.put(abilityId, parsed);
        return parsed;
    }
}