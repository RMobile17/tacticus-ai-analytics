package com.tacticus.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class TacticusKnowledgeGenerator {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\[([^\\]]+)\\]\\}");
    private static final Pattern ARRAY_INDEX_PATTERN = Pattern.compile("(\\w+)\\[(\\d+)\\]");

    private static final String[] RANK_NAMES = {
            "Stone I", "Stone II", "Stone III",
            "Iron I", "Iron II", "Iron III",
            "Bronze I", "Bronze II", "Bronze III",
            "Silver I", "Silver II", "Silver III",
            "Gold I", "Gold II", "Gold III",
            "Diamond I", "Diamond II", "Diamond III",
            "Mythic I", "Mythic II", "Mythic III"
    };

    // --- XREF Data Models (data/xref/) ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FactionHeroRef(String id, String name, String rarity) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TraitHeroRef(String id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TraitMetadata(String id, String label, String description, List<String> aliases) {
        public String displayName(String fallback) {
            if (label != null && !label.isBlank())
                return label;
            if (id != null && !id.isBlank())
                return id;
            return fallback;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AbilityDisplayMetadata(
            @JsonProperty("display_name") String displayName,
            @JsonProperty("web_icon_url") String webIconUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HeroAbilitiesEntry(
            String name,
            String activeAbility,
            AbilityData activeAbilityData,
            String passiveAbility,
            AbilityData passiveAbilityData) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AbilityData(
            String name,
            String gameId,
            String attackRangeType,
            String description,
            Map<String, String> constants,
            Map<String, List<Object>> variables,
            List<String> variablesAffectedByRarityBonus) {
    }

    // --- Detailed Hero Models (data/heroes/*.json) ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DetailedHeroFile(
            String id,
            String gameId,
            String name,
            String longName,
            String allianceId,
            String factionId,
            String baseRarity,
            Integer movement,
            WeaponData meleeWeapon,
            WeaponData rangeWeapon,
            List<String> itemSlots,
            List<HeroRankData> ranks,
            List<String> traits) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WeaponData(
            String damageProfile,
            int hits,
            String pierceDescription,
            Integer piercingRatio,
            Integer range,
            List<String> traits) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HeroRankData(
            String level,
            int health,
            int damage,
            int armor,
            List<RankUpgradeData> upgrades) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RankUpgradeData(
            String upgradeId,
            String statType,
            Integer statIncrease) {
    }

    // --- Items Models (data/items.json) ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemDefinition(
            String name,
            String itemType,
            String rarity,
            List<ItemLevelData> levels) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemLevelData(
            Map<String, Integer> stats) {
    }

    // --- Player API Data Models (data/my_player_data.json) ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlayerApiResponse(PlayerBlock player) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlayerBlock(PlayerDetails details, List<PlayerUnit> units) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlayerDetails(String name, int powerLevel) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlayerUnit(
            String id,
            String name,
            String faction,
            String grandAlliance,
            int progressionIndex,
            int xp,
            int xpLevel,
            int rank,
            List<PlayerAbility> abilities,
            List<Integer> upgrades,
            List<PlayerItem> items,
            int shards,
            int mythicShards) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlayerAbility(String id, int level) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlayerItem(String slotId, int level, String id, String name, String rarity) {
    }

    // --- Aggregated Hero Profile ---
    public static class HeroProfile {
        String id;
        String name;
        String faction = "Unknown";
        String rarity = "Unknown";
        List<TraitMetadata> traits = new ArrayList<>();
        HeroAbilitiesEntry abilities;
        DetailedHeroFile details;
        PlayerUnit playerUnit;
    }

    public static void main(String[] args) throws IOException {
        Path dataDir = Path.of("data");
        Path xrefDir = Files.exists(dataDir.resolve("xref")) ? dataDir.resolve("xref") : dataDir;
        Path heroesDir = dataDir.resolve("heroes");

        System.out.println("Starting Tacticus Knowledge Base generation...");
        System.out.println("Data directory: " + dataDir.toAbsolutePath());
        System.out.println("Xref directory: " + xrefDir.toAbsolutePath());

        ObjectMapper mapper = new ObjectMapper();

        // 1. Read XREF files
        Map<String, List<FactionHeroRef>> factionHeroes = mapper.readValue(
                xrefDir.resolve("faction_heroes.json").toFile(),
                new TypeReference<>() {
                });

        Map<String, HeroAbilitiesEntry> heroAbilities = mapper.readValue(
                xrefDir.resolve("hero_abilities.json").toFile(),
                new TypeReference<>() {
                });

        Map<String, TraitMetadata> rawTraits = mapper.readValue(
                xrefDir.resolve("trait_display_metadata.generated.json").toFile(),
                new TypeReference<>() {
                });

        Map<String, List<TraitHeroRef>> traitHeroes = mapper.readValue(
                xrefDir.resolve("trait_heroes.json").toFile(),
                new TypeReference<>() {
                });

        Map<String, AbilityDisplayMetadata> abilityDisplayLookup = new HashMap<>();
        File abilityMetaFile = xrefDir.resolve("ability_display_metadata.generated.json").toFile();
        if (abilityMetaFile.exists()) {
            Map<String, AbilityDisplayMetadata> rawAbilityMeta = mapper.readValue(
                    abilityMetaFile,
                    new TypeReference<>() {
                    });
            rawAbilityMeta.forEach((k, v) -> abilityDisplayLookup.put(k.toLowerCase(), v));
        }

        // Index trait metadata
        Map<String, TraitMetadata> traitLookup = new HashMap<>();
        rawTraits.forEach((k, v) -> {
            traitLookup.put(k.toLowerCase(), v);
            if (v.id() != null) {
                traitLookup.put(v.id().toLowerCase(), v);
            }
        });

        // 2. Read items.json if available
        Map<String, ItemDefinition> itemsCatalog = new HashMap<>();
        File itemsFile = dataDir.resolve("items.json").toFile();
        if (itemsFile.exists()) {
            System.out.println("Loading items catalog from items.json...");
            itemsCatalog = mapper.readValue(itemsFile, new TypeReference<>() {
            });
        }

        // 3. Build base Hero Profiles from XREF
        Map<String, HeroProfile> heroesById = new TreeMap<>();

        factionHeroes.forEach((factionKey, list) -> {
            String readableFaction = formatFactionName(factionKey);
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

        // 4. Load all detailed hero files from data/heroes/*.json
        if (Files.exists(heroesDir) && Files.isDirectory(heroesDir)) {
            System.out.println("Loading detailed hero files from: " + heroesDir.toAbsolutePath());
            try (Stream<Path> paths = Files.list(heroesDir)) {
                List<Path> jsonFiles = paths.filter(p -> p.toString().endsWith(".json")).toList();
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

        // 5. Load and map Player Data (data/my_player_data.json)
        PlayerDetails playerDetails = null;
        int matchedPlayerUnits = 0;
        File playerFile = dataDir.resolve("my_player_data.json").toFile();
        if (playerFile.exists()) {
            System.out.println("Loading personal player data from my_player_data.json...");
            PlayerApiResponse playerResponse = mapper.readValue(playerFile, PlayerApiResponse.class);
            if (playerResponse != null && playerResponse.player() != null) {
                playerDetails = playerResponse.player().details();
                List<PlayerUnit> pUnits = playerResponse.player().units();
                if (pUnits != null) {
                    for (PlayerUnit pu : pUnits) {
                        HeroProfile matched = findMatchingHeroProfile(pu, heroesById.values());
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

        // 6. Generate English Markdown for Google NotebookLM
        StringBuilder md = new StringBuilder();
        md.append("# Warhammer 40,000: Tacticus Knowledge Base\n\n");
        md.append(
                "This document contains structured information about characters, Machines of War, combat stats, weapons, abilities, traits, factions, and roster progression for building optimal team compositions.\n\n");

        if (playerDetails != null) {
            md.append("## Player Profile Summary\n");
            md.append("- **Player Name:** ").append(playerDetails.name()).append("\n");
            md.append("- **Power Level:** ").append(playerDetails.powerLevel()).append("\n");
            md.append("- **Unlocked Units:** ").append(matchedPlayerUnits).append(" / ").append(heroesById.size())
                    .append("\n\n");

            md.append("### Unlocked Roster Overview (Sorted by Rank & Level)\n");
            heroesById.values().stream()
                    .filter(h -> h.playerUnit != null)
                    .sorted(Comparator.<HeroProfile>comparingInt(h -> h.playerUnit.rank())
                            .thenComparingInt(h -> h.playerUnit.xpLevel()).reversed())
                    .forEach(h -> {
                        PlayerUnit pu = h.playerUnit;
                        md.append("- **").append(h.name).append("** (").append(h.faction).append("): ")
                                .append("Rank ").append(formatRank(pu.rank()))
                                .append(", XP Level ").append(pu.xpLevel())
                                .append(", Shards: ").append(pu.shards()).append("\n");
                    });
            md.append("\n");
        }

        // SECTION 1: Characters & Machines of War
        md.append("---\n## SECTION 1: Characters and Machines of War\n\n");
        for (HeroProfile hero : heroesById.values()) {
            boolean isMoW = hero.traits.stream().anyMatch(t -> "MachineOfWar".equalsIgnoreCase(t.id()));
            md.append("## ").append(isMoW ? "Machine of War: " : "Character: ")
                    .append(hero.name).append("\n");
            md.append("- **Faction:** ").append(hero.faction).append("\n");

            String alliance = resolveAlliance(hero);
            if (alliance != null) {
                md.append("- **Grand Alliance:** ").append(alliance).append("\n");
            }
            md.append("- **Base Rarity:** ").append(hero.rarity).append("\n");

            // Progression & Combat Stats Block
            if (hero.playerUnit != null) {
                PlayerUnit pu = hero.playerUnit;
                int appliedUpgrades = (pu.upgrades() != null) ? pu.upgrades().size() : 0;
                md.append("- **Status:** UNLOCKED\n");
                md.append("- **Progression:** Rank ").append(formatRank(pu.rank()));
                if (!isMoW) {
                    md.append(" (").append(appliedUpgrades).append("/6 rank upgrades applied)");
                }
                md.append(" | XP Level: ").append(pu.xpLevel())
                        .append(" | Shards: ").append(pu.shards()).append("\n");

                String currentStats = formatPlayerCombatStats(hero.details, pu);
                if (currentStats != null) {
                    md.append("- **Combat Stats:** ").append(currentStats).append("\n");
                }

                if (pu.items() != null && !pu.items().isEmpty()) {
                    List<String> itemStrings = new ArrayList<>();
                    for (PlayerItem item : pu.items()) {
                        itemStrings.add(formatEquippedItem(item, itemsCatalog));
                    }
                    md.append("- **Equipped Items:** ").append(String.join("; ", itemStrings)).append("\n");
                }
            } else {
                md.append("- **Status:** LOCKED (Not Unlocked)\n");
                String baseStats = formatLockedCombatStats(hero.details);
                if (baseStats != null) {
                    md.append("- **Combat Stats (Stone I -> Max Rank):** ").append(baseStats).append("\n");
                }
            }

            // Movement and Weapons from data/heroes/*.json
            if (hero.details != null && !isMoW) {
                if (hero.details.movement() != null && hero.details.movement() > 0) {
                    md.append("- **Movement:** ").append(hero.details.movement()).append("\n");
                }
                if (hero.details.meleeWeapon() != null) {
                    WeaponData mw = hero.details.meleeWeapon();
                    md.append("- **Normal Melee Attack:** ").append(mw.hits()).append("x ")
                            .append(mw.damageProfile()).append(" Damage (")
                            .append(mw.piercingRatio()).append("% pierce ratio)\n");
                }
                if (hero.details.rangeWeapon() != null) {
                    WeaponData rw = hero.details.rangeWeapon();
                    md.append("- **Normal Ranged Attack:** Range ").append(rw.range()).append(", ")
                            .append(rw.hits()).append("x ")
                            .append(rw.damageProfile()).append(" Damage (")
                            .append(rw.piercingRatio()).append("% pierce ratio)\n");
                } else {
                    md.append("- **Normal Ranged Attack:** None (Melee Only)\n");
                }
            }

            if (!hero.traits.isEmpty()) {
                md.append("- **Traits:**\n");
                hero.traits.stream()
                        .map(t -> t.displayName(t.id()))
                        .distinct()
                        .forEach(traitName -> md.append("  - ").append(traitName).append("\n"));
            } else {
                md.append("- **Traits:** None\n");
            }

            if (hero.abilities != null) {
                if (hero.abilities.activeAbilityData() != null) {
                    String activeId = hero.abilities.activeAbility();
                    String activeName = resolveAbilityDisplayName(
                            activeId,
                            hero.abilities.activeAbilityData(),
                            abilityDisplayLookup);
                    Integer playerActiveLvl = findPlayerAbilityLevel(hero.playerUnit, activeId,
                            hero.abilities.activeAbilityData().gameId());
                    md.append("\n### Active Ability: ").append(activeName);
                    if (playerActiveLvl != null) {
                        md.append(" (Level ").append(playerActiveLvl).append(")");
                    }
                    md.append("\n");
                    md.append(formatAbility(hero.abilities.activeAbilityData(), hero.name, playerActiveLvl));
                }
                if (hero.abilities.passiveAbilityData() != null) {
                    String passiveId = hero.abilities.passiveAbility();
                    String passiveName = resolveAbilityDisplayName(
                            passiveId,
                            hero.abilities.passiveAbilityData(),
                            abilityDisplayLookup);
                    Integer playerPassiveLvl = findPlayerAbilityLevel(hero.playerUnit, passiveId,
                            hero.abilities.passiveAbilityData().gameId());
                    md.append("\n### Passive Ability: ").append(passiveName);
                    if (playerPassiveLvl != null) {
                        md.append(" (Level ").append(playerPassiveLvl).append(")");
                    }
                    md.append("\n");
                    md.append(formatAbility(hero.abilities.passiveAbilityData(), hero.name, playerPassiveLvl));
                }
            }
            md.append("\n---\n\n");
        }

        // SECTION 2: Traits Reference
        md.append("## SECTION 2: Traits Reference and Descriptions\n\n");
        traitHeroes.forEach((traitKey, heroList) -> {
            TraitMetadata meta = traitLookup.get(traitKey.toLowerCase());
            String label = (meta != null) ? meta.displayName(traitKey) : traitKey;
            String desc = (meta != null && meta.description() != null && !meta.description().isBlank())
                    ? meta.description().replace("\n", " ")
                    : "No additional mechanical description.";

            md.append("### ").append(label).append("\n");
            md.append("- **Effect:** ").append(desc).append("\n");
            List<String> names = heroList.stream().map(TraitHeroRef::name).toList();
            md.append("- **Characters with this trait:** ").append(String.join(", ", names)).append("\n\n");
        });

        // SECTION 3: Factions Summary
        md.append("---\n## SECTION 3: Factions Roster\n\n");
        factionHeroes.forEach((factionKey, list) -> {
            String readableFaction = formatFactionName(factionKey);
            List<String> names = list.stream().map(h -> h.name() + " (" + h.rarity() + ")").toList();
            md.append("- **").append(readableFaction).append(":** ").append(String.join(", ", names)).append("\n");
        });
        md.append("\n");

        // Write output file
        Path outputPath = Path.of("tacticus_notebooklm_kb.md");
        Files.writeString(outputPath, md.toString());
        System.out.println("Success! Knowledge base generated at: " + outputPath.toAbsolutePath());
        System.out.println("Total characters processed: " + heroesById.size());
    }

    private static String resolveAlliance(HeroProfile hero) {
        if (hero.playerUnit != null && hero.playerUnit.grandAlliance() != null) {
            return hero.playerUnit.grandAlliance();
        }
        if (hero.details != null && hero.details.allianceId() != null) {
            return hero.details.allianceId();
        }
        return null;
    }

    private static String formatPlayerCombatStats(DetailedHeroFile details, PlayerUnit pu) {
        if (details == null || details.ranks() == null || details.ranks().isEmpty()) {
            return null;
        }
        int rankIdx = Math.min(Math.max(pu.rank(), 0), details.ranks().size() - 1);
        HeroRankData rankData = details.ranks().get(rankIdx);

        int hp = rankData.health();
        int dmg = rankData.damage();
        int armor = rankData.armor();

        if (pu.upgrades() != null && rankData.upgrades() != null) {
            for (int upgSlot : pu.upgrades()) {
                if (upgSlot >= 0 && upgSlot < rankData.upgrades().size()) {
                    RankUpgradeData upg = rankData.upgrades().get(upgSlot);
                    if (upg != null && upg.statIncrease() != null && upg.statType() != null) {
                        switch (upg.statType()) {
                            case "hp" -> hp += upg.statIncrease();
                            case "dmg" -> dmg += upg.statIncrease();
                            case "fixedArmor" -> armor += upg.statIncrease();
                        }
                    }
                }
            }
        }
        return "Health: " + hp + " | Damage: " + dmg + " | Armor: " + armor;
    }

    private static String formatLockedCombatStats(DetailedHeroFile details) {
        if (details == null || details.ranks() == null || details.ranks().isEmpty()) {
            return null;
        }
        HeroRankData first = details.ranks().get(0);
        HeroRankData last = details.ranks().get(details.ranks().size() - 1);
        return "Health: [" + first.health() + " .. " + last.health() + "] | "
                + "Damage: [" + first.damage() + " .. " + last.damage() + "] | "
                + "Armor: [" + first.armor() + " .. " + last.armor() + "]";
    }

    private static String formatEquippedItem(PlayerItem item, Map<String, ItemDefinition> itemsCatalog) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.name()).append(" (").append(item.rarity()).append(" Lvl ").append(item.level());

        ItemDefinition def = itemsCatalog.get(item.id());
        if (def != null && def.levels() != null && item.level() >= 1 && item.level() <= def.levels().size()) {
            Map<String, Integer> stats = def.levels().get(item.level() - 1).stats();
            if (stats != null && !stats.isEmpty()) {
                List<String> statStrings = new ArrayList<>();
                stats.forEach((k, v) -> statStrings.add(formatItemStat(k, v)));
                sb.append(": ").append(String.join(", ", statStrings));
            }
        }
        sb.append(")");
        return sb.toString();
    }

    private static String formatItemStat(String statKey, int value) {
        return switch (statKey) {
            case "critChance" -> "+" + value + "% Crit Chance";
            case "critDmg" -> "+" + value + " Crit Damage";
            case "blockChance" -> "+" + value + "% Block Chance";
            case "blockDmg" -> "+" + value + " Block Damage";
            case "fixedArmor", "armor" -> "+" + value + " Armor";
            case "hp", "health" -> "+" + value + " Health";
            default -> "+" + value + " " + statKey;
        };
    }

    private static HeroProfile findMatchingHeroProfile(PlayerUnit pu, Collection<HeroProfile> profiles) {
        // 1. Exact match by gameId from data/heroes/*.json
        if (pu.id() != null) {
            for (HeroProfile hp : profiles) {
                if (hp.details != null && pu.id().equalsIgnoreCase(hp.details.gameId())) {
                    return hp;
                }
            }
        }
        // 2. Match by normalized name
        if (pu.name() != null) {
            String normalizedTarget = normalizeName(pu.name());
            for (HeroProfile hp : profiles) {
                if (hp.name != null && normalizeName(hp.name).equals(normalizedTarget)) {
                    return hp;
                }
            }
        }
        // 3. Fallback: match by ability IDs
        if (pu.abilities() != null && !pu.abilities().isEmpty()) {
            for (PlayerAbility pa : pu.abilities()) {
                if (pa.id() == null)
                    continue;
                for (HeroProfile hp : profiles) {
                    if (hp.abilities == null)
                        continue;
                    if (pa.id().equalsIgnoreCase(hp.abilities.activeAbility())
                            || pa.id().equalsIgnoreCase(hp.abilities.passiveAbility())) {
                        return hp;
                    }
                }
            }
        }
        return null;
    }

    private static String normalizeName(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private static Integer findPlayerAbilityLevel(PlayerUnit pu, String abilityKey, String gameId) {
        if (pu == null || pu.abilities() == null)
            return null;
        for (PlayerAbility pa : pu.abilities()) {
            if (pa.id() == null)
                continue;
            if (pa.id().equalsIgnoreCase(abilityKey) || pa.id().equalsIgnoreCase(gameId)) {
                return pa.level();
            }
        }
        return null;
    }

    private static String formatRank(int rank) {
        if (rank >= 0 && rank < RANK_NAMES.length) {
            return RANK_NAMES[rank];
        }
        return String.valueOf(rank);
    }

    private static String resolveAbilityDisplayName(
            String abilityId,
            AbilityData data,
            Map<String, AbilityDisplayMetadata> abilityDisplayLookup) {
        if (abilityId != null) {
            AbilityDisplayMetadata meta = abilityDisplayLookup.get(abilityId.toLowerCase());
            if (meta != null && meta.displayName() != null && !meta.displayName().isBlank()) {
                return meta.displayName();
            }
        }
        if (data.gameId() != null) {
            AbilityDisplayMetadata meta = abilityDisplayLookup.get(data.gameId().toLowerCase());
            if (meta != null && meta.displayName() != null && !meta.displayName().isBlank()) {
                return meta.displayName();
            }
        }
        return data.name();
    }

    private static String formatFactionName(String rawFaction) {
        return switch (rawFaction) {
            case "Sisterhood" -> "Adepta Sororitas";
            case "Custodes" -> "Adeptus Custodes";
            case "AdeptusMechanicus" -> "Adeptus Mechanicus";
            case "AstraMilitarum" -> "Astra Militarum";
            case "BlackLegion" -> "Black Legion";
            case "BlackTemplars" -> "Black Templars";
            case "BloodAngels" -> "Blood Angels";
            case "DarkAngels" -> "Dark Angels";
            case "DeathGuard" -> "Death Guard";
            case "EmperorsChildren" -> "Emperor's Children";
            case "Genestealers" -> "Genestealer Cults";
            case "LeaguesOfVotann" -> "Leagues of Votann";
            case "SpaceWolves" -> "Space Wolves";
            case "Tau" -> "T'au Empire";
            case "ThousandSons" -> "Thousand Sons";
            case "WorldEaters" -> "World Eaters";
            case "AdeptusAstartes" -> "Adeptus Astartes";
            default -> rawFaction.replaceAll("([a-z])([A-Z])", "$1 $2");
        };
    }

    private static String formatAbility(AbilityData data, String unitName, Integer playerAbilityLevel) {
        StringBuilder sb = new StringBuilder();
        if (data.attackRangeType() != null) {
            sb.append("- **Attack Range Type:** ").append(data.attackRangeType()).append("\n");
        }

        String desc = data.description() != null ? data.description() : "";
        desc = HTML_TAG_PATTERN.matcher(desc).replaceAll("");
        desc = desc.replace("{[UnitName]}", unitName);

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(desc);
        StringBuilder resolvedDesc = new StringBuilder();

        while (matcher.find()) {
            String rawKey = matcher.group(1).trim();
            String replacement = resolvePlaceholder(rawKey, data, playerAbilityLevel);
            matcher.appendReplacement(resolvedDesc, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolvedDesc);

        sb.append("- **Description:**\n  ")
                .append(resolvedDesc.toString().trim().replace("\n", "\n  "))
                .append("\n");

        return sb.toString();
    }

    private static String resolvePlaceholder(String key, AbilityData data, Integer playerAbilityLevel) {
        if (data.constants() != null) {
            for (Map.Entry<String, String> entry : data.constants().entrySet()) {
                if (entry.getKey().trim().equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }
        }

        if (data.variables() != null) {
            Matcher idxMatcher = ARRAY_INDEX_PATTERN.matcher(key);
            if (idxMatcher.matches()) {
                String varName = idxMatcher.group(1);
                int idx = Integer.parseInt(idxMatcher.group(2));
                List<Object> list = findVariableList(data.variables(), varName);
                if (list != null && !list.isEmpty()) {
                    if (playerAbilityLevel != null && playerAbilityLevel >= 1 && playerAbilityLevel <= list.size()) {
                        return extractIndexedValue(list.get(playerAbilityLevel - 1).toString(), idx);
                    }
                    String first = extractIndexedValue(list.get(0).toString(), idx);
                    String last = extractIndexedValue(list.get(list.size() - 1).toString(), idx);
                    return first.equals(last) ? first : "[" + first + " .. " + last + "]";
                }
            }

            List<Object> list = findVariableList(data.variables(), key);
            if (list != null && !list.isEmpty()) {
                if (playerAbilityLevel != null && playerAbilityLevel >= 1 && playerAbilityLevel <= list.size()) {
                    return String.valueOf(list.get(playerAbilityLevel - 1));
                }
                String first = String.valueOf(list.get(0));
                String last = String.valueOf(list.get(list.size() - 1));
                return first.equals(last) ? first : "[" + first + " .. " + last + "]";
            }
        }
        return "{" + key + "}";
    }

    private static List<Object> findVariableList(Map<String, List<Object>> vars, String key) {
        for (Map.Entry<String, List<Object>> entry : vars.entrySet()) {
            if (entry.getKey().trim().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String extractIndexedValue(String csv, int idx) {
        String[] parts = csv.split(",");
        return idx < parts.length ? parts[idx].trim() : csv;
    }
}