package com.tacticus.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tacticus.analytics.common.DataPaths;
import com.tacticus.analytics.common.GameTextFormatter;
import com.tacticus.analytics.model.GameModels.AbilityData;
import com.tacticus.analytics.model.GameModels.AbilityDisplayMetadata;
import com.tacticus.analytics.model.GameModels.TraitMetadata;
import com.tacticus.analytics.model.GameModels.WeaponData;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class TacticusBossKnowledgeGenerator {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BossUnitFile(
            String id,
            String gameId,
            String unitId,
            String name,
            String longName,
            String factionId,
            Boolean isBoss,
            Boolean isPrime,
            Integer movement,
            WeaponData meleeWeapon,
            WeaponData rangeWeapon,
            String prime1,
            String prime1Name,
            String prime2,
            String prime2Name,
            List<String> activeAbilities,
            List<String> passiveAbilities,
            List<String> traits,
            List<BossStatEntry> stats) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BossStatEntry(
            int abilityLevel,
            String baseRarity,
            Integer blockChance,
            Integer blockDamage,
            Integer critChance,
            Integer critDamage,
            int damage,
            int fixedArmor,
            long health,
            String progressionIndex,
            int rank,
            int starLevel) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BossAbilityMeta(
            String name,
            String description,
            String kind,
            @JsonProperty("source_bosses") List<String> sourceBosses) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BossDebuffEntry(
            Integer amount,
            String description,
            String shortDescription,
            String subtarget,
            String target,
            String type) {
    }

    public static void main(String[] args) throws IOException {
        String outputFileName = "tacticus_bosses_kb.md";
        for (String arg : args) {
            if (arg.startsWith("--output=") && arg.length() > "--output=".length()) {
                outputFileName = arg.substring("--output=".length());
            }
        }

        DataPaths paths = DataPaths.resolve();
        Path bossesDir = paths.guildRaidDir().resolve("bosses");
        System.out.println("Starting Tacticus Guild Raid Bosses Knowledge Base generation...");
        System.out.println("Reading boss files from: " + bossesDir.toAbsolutePath());

        ObjectMapper mapper = new ObjectMapper();

        // 1. Load Trait metadata
        Map<String, TraitMetadata> rawTraits = mapper.readValue(
                paths.xrefDir().resolve("trait_display_metadata.generated.json").toFile(),
                new TypeReference<>() {
                });
        Map<String, TraitMetadata> traitLookup = new HashMap<>();
        for (Map.Entry<String, TraitMetadata> entry : rawTraits.entrySet()) {
            traitLookup.put(entry.getKey().toLowerCase(), entry.getValue());
            if (entry.getValue().id() != null) {
                traitLookup.put(entry.getValue().id().toLowerCase(), entry.getValue());
            }
        }

        // 2. Load Ability Display metadata
        Map<String, AbilityDisplayMetadata> abilityDisplayLookup = new HashMap<>();
        File abilityMetaFile = paths.xrefDir().resolve("ability_display_metadata.generated.json").toFile();
        if (abilityMetaFile.exists()) {
            Map<String, AbilityDisplayMetadata> rawAbilityMeta = mapper.readValue(
                    abilityMetaFile, new TypeReference<>() {
                    });
            for (Map.Entry<String, AbilityDisplayMetadata> entry : rawAbilityMeta.entrySet()) {
                abilityDisplayLookup.put(entry.getKey().toLowerCase(), entry.getValue());
            }
        }

        // 3. Load Boss Abilities from xref/boss_abilities.generated.json &
        // data/abilities/*.json
        Map<String, AbilityData> abilitiesLookup = new HashMap<>();
        File bossAbilitiesFile = paths.xrefDir().resolve("boss_abilities.generated.json").toFile();
        if (bossAbilitiesFile.exists()) {
            Map<String, BossAbilityMeta> rawBossAbilities = mapper.readValue(
                    bossAbilitiesFile, new TypeReference<>() {
                    });
            for (Map.Entry<String, BossAbilityMeta> entry : rawBossAbilities.entrySet()) {
                BossAbilityMeta bam = entry.getValue();
                AbilityData ad = new AbilityData(
                        bam.name(),
                        entry.getKey(),
                        null,
                        bam.description(),
                        Map.of(),
                        Map.of(),
                        List.of());
                abilitiesLookup.put(entry.getKey().toLowerCase(), ad);
            }
        }

        Path abilitiesDir = paths.dataDir().resolve("abilities");
        loadDetailedAbilitiesIfPresent(abilitiesDir.resolve("active.json").toFile(), mapper, abilitiesLookup);
        loadDetailedAbilitiesIfPresent(abilitiesDir.resolve("passive.json").toFile(), mapper, abilitiesLookup);
        loadDetailedAbilitiesIfPresent(abilitiesDir.resolve("other.json").toFile(), mapper, abilitiesLookup);

        // 4. Load Boss Debuffs
        Map<String, BossDebuffEntry> bossDebuffs = new LinkedHashMap<>();
        File debuffsFile = paths.dataDir().resolve("boss_debuffs.json").toFile();
        if (!debuffsFile.exists()) {
            debuffsFile = paths.guildRaidDir().resolve("boss_debuffs.json").toFile();
        }
        if (debuffsFile.exists()) {
            JsonNode debuffsRoot = mapper.readTree(debuffsFile);
            if (debuffsRoot.isObject()) {
                var fields = debuffsRoot.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    if (entry.getValue().isObject() && entry.getValue().has("target")) {
                        bossDebuffs.put(entry.getKey(), mapper.treeToValue(entry.getValue(), BossDebuffEntry.class));
                    }
                }
            }
        }

        // 5. Load all Boss/MiniBoss/NPC files from data/guild-raid/bosses/*.json
        List<BossUnitFile> allUnits = new ArrayList<>();
        Map<String, BossUnitFile> unitsById = new HashMap<>();

        if (Files.exists(bossesDir) && Files.isDirectory(bossesDir)) {
            try (Stream<Path> fileStream = Files.list(bossesDir)) {
                List<Path> jsonFiles = fileStream
                        .filter(p -> p.toString().endsWith(".json"))
                        .sorted()
                        .toList();
                for (Path file : jsonFiles) {
                    BossUnitFile unit = mapper.readValue(file.toFile(), BossUnitFile.class);
                    if (unit != null && unit.id() != null) {
                        allUnits.add(unit);
                        unitsById.put(unit.id(), unit);
                    }
                }
            }
        }

        List<BossUnitFile> mainBosses = new ArrayList<>();
        List<BossUnitFile> miniBosses = new ArrayList<>();
        List<BossUnitFile> minionsAndObjects = new ArrayList<>();

        for (BossUnitFile unit : allUnits) {
            if (Boolean.TRUE.equals(unit.isBoss())) {
                mainBosses.add(unit);
            } else if (Boolean.TRUE.equals(unit.isPrime()) || unit.id().contains("MiniBoss")) {
                miniBosses.add(unit);
            } else {
                minionsAndObjects.add(unit);
            }
        }

        Comparator<BossUnitFile> bossComparator = Comparator
                .comparing((BossUnitFile u) -> u.longName() != null ? u.longName() : u.name())
                .thenComparing(BossUnitFile::id);
        mainBosses.sort(bossComparator);
        miniBosses.sort(bossComparator);
        minionsAndObjects.sort(bossComparator);

        // 6. Generate Markdown
        StringBuilder md = new StringBuilder();
        md.append("# Warhammer 40,000: Tacticus — Guild Raid Bosses Knowledge Base\n\n");
        md.append(
                "This document contains structured information about all Guild Raid Main Bosses, Side Bosses (Primes), Minions, stats scaling by rarity, weapons, traits, abilities, and Prime debuffs for building optimal Guild Raid team compositions.\n\n");

        // SECTION 1: Main Guild Raid Bosses
        md.append("---\n## SECTION 1: Main Guild Raid Bosses\n\n");
        for (BossUnitFile boss : mainBosses) {
            appendBossProfile(md, boss, "Main Boss", unitsById, traitLookup, abilitiesLookup, abilityDisplayLookup,
                    bossDebuffs);
        }

        // SECTION 2: Side Bosses (Primes / Mini-Bosses)
        md.append("---\n## SECTION 2: Side Bosses (Primes / Mini-Bosses)\n\n");
        for (BossUnitFile prime : miniBosses) {
            appendBossProfile(md, prime, "Side Boss (Prime)", unitsById, traitLookup, abilitiesLookup,
                    abilityDisplayLookup, Map.of());
        }

        // SECTION 3: Minions, Summons, and Battlefield Objects
        md.append("---\n## SECTION 3: Boss Minions, Summons, and Battlefield Objects\n\n");
        for (BossUnitFile minion : minionsAndObjects) {
            appendBossProfile(md, minion, "Raid Minion / Object", unitsById, traitLookup, abilitiesLookup,
                    abilityDisplayLookup, Map.of());
        }

        Path outputPath = Path.of(outputFileName);
        Files.writeString(outputPath, md.toString());
        System.out.println("Success! Boss Knowledge Base generated at: " + outputPath.toAbsolutePath());
        System.out.println("Exported: " + mainBosses.size() + " Main Bosses, "
                + miniBosses.size() + " Side Bosses, "
                + minionsAndObjects.size() + " Minions/Objects.");
    }

    private static void appendBossProfile(
            StringBuilder md,
            BossUnitFile unit,
            String roleLabel,
            Map<String, BossUnitFile> unitsById,
            Map<String, TraitMetadata> traitLookup,
            Map<String, AbilityData> abilitiesLookup,
            Map<String, AbilityDisplayMetadata> abilityDisplayLookup,
            Map<String, BossDebuffEntry> bossDebuffs) {
        String displayName = (unit.longName() != null && !unit.longName().isBlank()) ? unit.longName() : unit.name();
        md.append("## ").append(roleLabel).append(": ").append(displayName).append("\n");
        md.append("- **Faction:** ").append(GameTextFormatter.formatFactionName(unit.factionId())).append("\n");

        // Linked Primes (for Main Bosses)
        List<String> primes = new ArrayList<>();
        if (unit.prime1Name() != null && !unit.prime1Name().isBlank()) {
            BossUnitFile p1 = unitsById.get(unit.prime1Name());
            primes.add(p1 != null && p1.longName() != null ? p1.longName() : unit.prime1Name());
        }
        if (unit.prime2Name() != null && !unit.prime2Name().isBlank()) {
            BossUnitFile p2 = unitsById.get(unit.prime2Name());
            primes.add(p2 != null && p2.longName() != null ? p2.longName() : unit.prime2Name());
        }
        if (!primes.isEmpty()) {
            md.append("- **Associated Side Bosses (Primes):** ").append(String.join(", ", primes)).append("\n");
        }

        if (unit.movement() != null && unit.movement() > 0) {
            md.append("- **Movement:** ").append(unit.movement()).append("\n");
        }

        if (unit.meleeWeapon() != null) {
            WeaponData mw = unit.meleeWeapon();
            md.append("- **Normal Melee Attack:** ").append(mw.hits()).append("x ")
                    .append(mw.damageProfile()).append(" Damage (")
                    .append(mw.piercingRatio()).append("% pierce ratio)\n");
        }
        if (unit.rangeWeapon() != null) {
            WeaponData rw = unit.rangeWeapon();
            md.append("- **Normal Ranged Attack:** Range ").append(rw.range()).append(", ")
                    .append(rw.hits()).append("x ")
                    .append(rw.damageProfile()).append(" Damage (")
                    .append(rw.piercingRatio()).append("% pierce ratio)\n");
        } else {
            md.append("- **Normal Ranged Attack:** None (Melee Only)\n");
        }

        if (unit.traits() != null && !unit.traits().isEmpty()) {
            md.append("- **Traits:**\n");
            for (String traitKey : unit.traits()) {
                TraitMetadata meta = traitLookup.get(traitKey.toLowerCase());
                String label = (meta != null) ? meta.displayName(traitKey) : traitKey;
                md.append("  - **").append(label).append("**");
                if (meta != null && meta.description() != null && !meta.description().isBlank()) {
                    md.append(": ").append(meta.description().replace("\n", " "));
                }
                md.append("\n");
            }
        }

        if (unit.stats() != null && !unit.stats().isEmpty()) {
            md.append("- **Stats Scaling by Rarity Tier:**\n");
            Map<String, List<BossStatEntry>> statsByRarity = new LinkedHashMap<>();
            for (BossStatEntry s : unit.stats()) {
                String r = s.baseRarity() != null ? s.baseRarity() : "Unknown";
                statsByRarity.computeIfAbsent(r, k -> new ArrayList<>()).add(s);
            }
            for (Map.Entry<String, List<BossStatEntry>> entry : statsByRarity.entrySet()) {
                List<BossStatEntry> tierList = entry.getValue();
                BossStatEntry first = tierList.get(0);
                BossStatEntry last = tierList.get(tierList.size() - 1);
                md.append("  - **").append(entry.getKey()).append("** (Ability Lvl ")
                        .append(formatRange(first.abilityLevel(), last.abilityLevel())).append("): ")
                        .append("Health: ").append(formatRange(first.health(), last.health()))
                        .append(" | Damage: ").append(formatRange(first.damage(), last.damage()))
                        .append(" | Armor: ").append(formatRange(first.fixedArmor(), last.fixedArmor()))
                        .append("\n");
            }
        }

        appendBossAbilitiesSection(md, "Active Abilities", unit.activeAbilities(), unit.name(), abilitiesLookup,
                abilityDisplayLookup);
        appendBossAbilitiesSection(md, "Passive Abilities", unit.passiveAbilities(), unit.name(), abilitiesLookup,
                abilityDisplayLookup);

        if (!bossDebuffs.isEmpty()) {
            List<String> matchingDebuffs = findMatchingDebuffs(unit, bossDebuffs);
            if (!matchingDebuffs.isEmpty()) {
                md.append("\n### Prime Debuffs (Applied to Boss when Side Bosses are defeated)\n");
                for (String d : matchingDebuffs) {
                    md.append("- ").append(d).append("\n");
                }
            }
        }

        md.append("\n---\n\n");
    }

    private static void appendBossAbilitiesSection(
            StringBuilder md,
            String sectionTitle,
            List<String> abilityIds,
            String unitName,
            Map<String, AbilityData> abilitiesLookup,
            Map<String, AbilityDisplayMetadata> abilityDisplayLookup) {
        if (abilityIds == null || abilityIds.isEmpty()) {
            return;
        }

        List<String> renderedItems = new ArrayList<>();
        for (String abilityId : abilityIds) {
            if ("GuildBossRunAway".equalsIgnoreCase(abilityId)) {
                continue;
            }
            AbilityData data = abilitiesLookup.get(abilityId.toLowerCase());
            if (data == null || data.description() == null || data.description().isBlank()) {
                continue;
            }

            String displayName = GameTextFormatter.resolveAbilityDisplayName(abilityId, data, abilityDisplayLookup);
            String formatted = GameTextFormatter.formatAbility(data, unitName, null, 1.0);

            String descMarker = "- **Description:**\n";
            int descIdx = formatted.indexOf(descMarker);
            String body = (descIdx >= 0) ? formatted.substring(descIdx + descMarker.length()) : formatted;

            StringBuilder item = new StringBuilder();
            item.append("- **").append(displayName).append("**");
            if (data.attackRangeType() != null && !data.attackRangeType().isBlank()) {
                item.append(" *(").append(data.attackRangeType()).append(")*");
            }
            item.append(":\n").append(body);
            renderedItems.add(item.toString());
        }

        if (!renderedItems.isEmpty()) {
            md.append("\n### ").append(sectionTitle).append("\n");
            for (String item : renderedItems) {
                md.append(item);
            }
        }
    }

    private static List<String> findMatchingDebuffs(BossUnitFile unit, Map<String, BossDebuffEntry> bossDebuffs) {
        List<String> allUnitAbilities = new ArrayList<>();
        if (unit.activeAbilities() != null)
            allUnitAbilities.addAll(unit.activeAbilities());
        if (unit.passiveAbilities() != null)
            allUnitAbilities.addAll(unit.passiveAbilities());

        List<String> result = new ArrayList<>();
        for (BossDebuffEntry debuff : bossDebuffs.values()) {
            if (debuff.target() == null)
                continue;
            boolean matchesAbility = false;
            for (String abId : allUnitAbilities) {
                if (abId.equalsIgnoreCase(debuff.target())) {
                    matchesAbility = true;
                    break;
                }
            }
            if (matchesAbility && debuff.description() != null) {
                String cleanDesc = HTML_TAG_PATTERN.matcher(debuff.description()).replaceAll("")
                        .replace("{[BOSS_NAME]}", unit.name())
                        .replace("{[UNIT_NAME]}", unit.name())
                        .trim();
                if (!result.contains(cleanDesc)) {
                    result.add(cleanDesc);
                }
            }
        }
        return result;
    }

    private static String formatRange(long min, long max) {
        return min == max ? String.valueOf(min) : "[" + min + " .. " + max + "]";
    }

    private static void loadDetailedAbilitiesIfPresent(
            File file,
            ObjectMapper mapper,
            Map<String, AbilityData> abilitiesLookup) {
        if (!file.exists())
            return;
        try {
            JsonNode root = mapper.readTree(file);
            if (root.isObject()) {
                var fields = root.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    mergeAbilityNode(entry.getKey(), entry.getValue(), mapper, abilitiesLookup);
                }
            } else if (root.isArray()) {
                for (JsonNode elem : root) {
                    if (elem.isObject()) {
                        String id = elem.hasNonNull("id") ? elem.get("id").asText()
                                : elem.hasNonNull("gameId") ? elem.get("gameId").asText() : null;
                        if (id != null) {
                            mergeAbilityNode(id, elem, mapper, abilitiesLookup);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Note: Skipped enriching from " + file.getName() + " (" + e.getMessage() + ")");
        }
    }

    private static void mergeAbilityNode(
            String key,
            JsonNode node,
            ObjectMapper mapper,
            Map<String, AbilityData> abilitiesLookup) {
        if (!node.isObject())
            return;
        if (!node.has("description") && !node.has("variables") && !node.has("constants"))
            return;
        try {
            AbilityData parsed = mapper.treeToValue(node, AbilityData.class);
            String lowerKey = key.toLowerCase();
            AbilityData existing = abilitiesLookup.get(lowerKey);
            if (existing == null) {
                abilitiesLookup.put(lowerKey, parsed);
            } else {
                AbilityData merged = new AbilityData(
                        existing.name() != null ? existing.name() : parsed.name(),
                        existing.gameId() != null ? existing.gameId() : parsed.gameId(),
                        parsed.attackRangeType() != null ? parsed.attackRangeType() : existing.attackRangeType(),
                        existing.description() != null ? existing.description() : parsed.description(),
                        parsed.constants() != null && !parsed.constants().isEmpty() ? parsed.constants()
                                : existing.constants(),
                        parsed.variables() != null && !parsed.variables().isEmpty() ? parsed.variables()
                                : existing.variables(),
                        parsed.variablesAffectedByRarityBonus() != null ? parsed.variablesAffectedByRarityBonus()
                                : existing.variablesAffectedByRarityBonus());
                abilitiesLookup.put(lowerKey, merged);
            }
        } catch (Exception ignored) {
        }
    }
}