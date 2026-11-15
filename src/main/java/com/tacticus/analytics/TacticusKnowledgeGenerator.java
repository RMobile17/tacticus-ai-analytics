package com.tacticus.analytics;

import com.tacticus.analytics.common.DataPaths;
import com.tacticus.analytics.common.GameDataLoader;
import com.tacticus.analytics.common.GameDataLoader.LoadedGameData;
import com.tacticus.analytics.common.GameTextFormatter;
import com.tacticus.analytics.common.HeroStatsCalculator;
import com.tacticus.analytics.common.ProgressionHelper;
import com.tacticus.analytics.model.GameModels.AbilityData;
import com.tacticus.analytics.model.GameModels.AbilityDisplayMetadata;
import com.tacticus.analytics.model.GameModels.FactionHeroRef;
import com.tacticus.analytics.model.GameModels.HeroAbilitiesEntry;
import com.tacticus.analytics.model.GameModels.TraitHeroRef;
import com.tacticus.analytics.model.GameModels.TraitMetadata;
import com.tacticus.analytics.model.GameModels.WeaponData;
import com.tacticus.analytics.model.HeroProfile;
import com.tacticus.analytics.model.PlayerModels.PlayerAbility;
import com.tacticus.analytics.model.PlayerModels.PlayerItem;
import com.tacticus.analytics.model.PlayerModels.PlayerUnit;
import com.tacticus.analytics.model.PlayerModels.ProgressionTier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TacticusKnowledgeGenerator {

    public static void main(String[] args) throws IOException {
        boolean parsedUnlockedOnly = false;
        String outputFileName = "tacticus_notebooklm_kb.md";

        for (String arg : args) {
            if ("--unlocked-only".equalsIgnoreCase(arg)) {
                parsedUnlockedOnly = true;
            } else if (arg.startsWith("--output=") && arg.length() > "--output=".length()) {
                outputFileName = arg.substring("--output=".length());
            }
        }

        final boolean unlockedOnly = parsedUnlockedOnly;

        DataPaths paths = DataPaths.resolve();
        System.out.println("Starting Tacticus Knowledge Base generation...");
        System.out.println(
                "Filter mode: " + (unlockedOnly ? "UNLOCKED characters only (--unlocked-only)" : "ALL characters"));
        System.out.println("Data directory: " + paths.dataDir().toAbsolutePath());

        LoadedGameData data = GameDataLoader.loadAll(paths);

        List<HeroProfile> heroesToExport = data.heroesById().values().stream()
                .filter(h -> !unlockedOnly || h.playerUnit != null)
                .toList();

        List<HeroProfile> charactersToExport = heroesToExport.stream()
                .filter(h -> !isMachineOfWar(h))
                .toList();

        List<HeroProfile> machinesOfWarToExport = heroesToExport.stream()
                .filter(TacticusKnowledgeGenerator::isMachineOfWar)
                .toList();

        Set<String> exportedHeroIds = new HashSet<>();
        for (HeroProfile h : heroesToExport) {
            exportedHeroIds.add(h.id);
        }

        final StringBuilder md = new StringBuilder();
        md.append("# Warhammer 40,000: Tacticus Knowledge Base\n\n");
        if (unlockedOnly) {
            md.append(
                    "This document contains structured information strictly about the player's **UNLOCKED** characters and Machines of War, combat stats, weapons, abilities, traits, and factions.\n\n");
        } else {
            md.append(
                    "This document contains structured information about all characters, Machines of War, combat stats, weapons, abilities, traits, factions, and roster progression.\n\n");
        }

        if (data.playerDetails() != null) {
            md.append("## Player Profile Summary\n");
            md.append("- **Player Name:** ").append(data.playerDetails().name()).append("\n");
            md.append("- **Power Level:** ").append(data.playerDetails().powerLevel()).append("\n");
            md.append("- **Unlocked Units:** ").append(data.matchedPlayerUnits()).append(" / ")
                    .append(data.heroesById().size()).append("\n\n");

            md.append("### Unlocked Roster Overview (Sorted by Rank & Level)\n");
            List<HeroProfile> unlockedSorted = data.heroesById().values().stream()
                    .filter(h -> h.playerUnit != null)
                    .sorted(Comparator.<HeroProfile>comparingInt(h -> h.playerUnit.rank())
                            .thenComparingInt(h -> h.playerUnit.xpLevel()).reversed())
                    .toList();

            for (HeroProfile h : unlockedSorted) {
                PlayerUnit pu = h.playerUnit;
                boolean isMoW = isMachineOfWar(h);
                ProgressionTier tier = ProgressionHelper.getProgressionTier(pu.progressionIndex(), h.rarity);
                md.append("- **").append(h.name).append("** (").append(h.faction).append("): ")
                        .append(tier.rarityLabel());
                if (isMoW) {
                    md.append(" [Machine of War]");
                } else {
                    md.append(", Rank ").append(ProgressionHelper.formatRank(pu.rank()))
                            .append(", XP Level ").append(pu.xpLevel());
                }
                md.append(", Shards: ").append(pu.shards())
                        .append(", Mythic Shards: ").append(pu.mythicShards())
                        .append("\n");
            }
            md.append("\n");
        }

        // SECTION 1: Characters
        md.append("---\n## SECTION 1: Characters\n\n");
        if (charactersToExport.isEmpty()) {
            md.append("_None._\n\n---\n\n");
        } else {
            for (HeroProfile hero : charactersToExport) {
                appendUnitEntry(md, hero, false, data);
            }
        }

        // SECTION 2: Machines of War
        md.append("## SECTION 2: Machines of War\n\n");
        if (machinesOfWarToExport.isEmpty()) {
            md.append("_None._\n\n---\n\n");
        } else {
            for (HeroProfile mow : machinesOfWarToExport) {
                appendUnitEntry(md, mow, true, data);
            }
        }

        // SECTION 3: Traits Reference
        md.append("## SECTION 3: Traits Reference and Descriptions\n\n");
        for (Map.Entry<String, List<TraitHeroRef>> entry : data.traitHeroes().entrySet()) {
            String traitKey = entry.getKey();
            List<String> names = entry.getValue().stream()
                    .filter(ref -> exportedHeroIds.contains(ref.id()))
                    .map(TraitHeroRef::name)
                    .toList();
            if (unlockedOnly && names.isEmpty()) {
                continue;
            }
            TraitMetadata meta = data.traitLookup().get(traitKey.toLowerCase());
            String label = (meta != null) ? meta.displayName(traitKey) : traitKey;
            String desc = (meta != null && meta.description() != null && !meta.description().isBlank())
                    ? meta.description().replace("\n", " ")
                    : "No additional mechanical description.";

            md.append("### ").append(label).append("\n");
            md.append("- **Effect:** ").append(desc).append("\n");
            md.append("- **Characters with this trait:** ").append(String.join(", ", names)).append("\n\n");
        }

        // SECTION 4: Factions Summary
        md.append("---\n## SECTION 4: Factions Roster\n\n");
        for (Map.Entry<String, List<FactionHeroRef>> entry : data.factionHeroes().entrySet()) {
            String factionKey = entry.getKey();
            List<String> names = entry.getValue().stream()
                    .filter(ref -> exportedHeroIds.contains(ref.id()))
                    .map(h -> h.name() + " (" + h.rarity() + ")")
                    .toList();
            if (unlockedOnly && names.isEmpty()) {
                continue;
            }
            String readableFaction = GameTextFormatter.formatFactionName(factionKey);
            md.append("- **").append(readableFaction).append(":** ").append(String.join(", ", names)).append("\n");
        }
        md.append("\n");

        Path outputPath = Path.of(outputFileName);
        Files.writeString(outputPath, md.toString());
        System.out.println("Success! Knowledge base generated at: " + outputPath.toAbsolutePath());
        System.out.println("Total characters exported: " + charactersToExport.size());
        System.out.println("Total Machines of War exported: " + machinesOfWarToExport.size());
    }

    private static boolean isMachineOfWar(HeroProfile hero) {
        return hero != null
                && hero.traits != null
                && hero.traits.stream().anyMatch(t -> "MachineOfWar".equalsIgnoreCase(t.id()));
    }

    private static void appendUnitEntry(
            StringBuilder md,
            HeroProfile hero,
            boolean isMoW,
            LoadedGameData data) {
        md.append("### ").append(isMoW ? "Machine of War: " : "Character: ")
                .append(hero.name).append("\n");
        md.append("- **Faction:** ").append(hero.faction).append("\n");

        String alliance = resolveAlliance(hero);
        if (alliance != null) {
            md.append("- **Grand Alliance:** ").append(alliance).append("\n");
        }
        md.append("- **Base Rarity:** ").append(hero.rarity).append("\n");

        double abilityRarityMultiplier = 1.0;

        if (hero.playerUnit != null) {
            PlayerUnit pu = hero.playerUnit;
            ProgressionTier tier = ProgressionHelper.getProgressionTier(pu.progressionIndex(), hero.rarity);
            abilityRarityMultiplier = tier.abilityMultiplier();

            int appliedUpgrades = (pu.upgrades() != null) ? pu.upgrades().size() : 0;
            md.append("- **Status:** UNLOCKED\n");
            md.append("- **Current Rarity:** ").append(tier.rarityLabel()).append("\n");
            if (isMoW) {
                md.append("- **Shards:** ").append(pu.shards())
                        .append(" | **Mythic Shards:** ").append(pu.mythicShards())
                        .append("\n");
            } else {
                md.append("- **Progression:** Rank ").append(ProgressionHelper.formatRank(pu.rank()))
                        .append(" (").append(appliedUpgrades).append("/6 rank upgrades applied)")
                        .append(" | XP Level: ").append(pu.xpLevel())
                        .append(" | Shards: ").append(pu.shards())
                        .append(" | Mythic Shards: ").append(pu.mythicShards())
                        .append("\n");

                String currentStats = HeroStatsCalculator.formatPlayerCombatStats(
                        hero.details, pu, tier.stars(), data.itemsCatalog());
                if (currentStats != null) {
                    md.append("- **Combat Stats:** ").append(currentStats).append("\n");
                }
            }

            if (pu.items() != null && !pu.items().isEmpty()) {
                md.append("- **Equipped Items:**\n");
                List<PlayerItem> sortedItems = pu.items().stream()
                        .sorted(Comparator.comparing(item -> item.slotId() != null ? item.slotId() : ""))
                        .toList();
                for (PlayerItem item : sortedItems) {
                    md.append("  - ").append(HeroStatsCalculator.formatEquippedItem(item, data.itemsCatalog()))
                            .append("\n");
                }

                String itemTotals = HeroStatsCalculator.formatItemTotalsSummary(pu.items(), data.itemsCatalog());
                if (itemTotals != null) {
                    md.append("- **Equipment Totals:** ").append(itemTotals).append("\n");
                }
            }
        } else {
            md.append("- **Status:** LOCKED (Not Unlocked)\n");
            if (!isMoW) {
                String baseStats = HeroStatsCalculator.formatLockedCombatStats(hero.details);
                if (baseStats != null) {
                    md.append("- **Combat Stats (Stone I -> Max Rank):** ").append(baseStats).append("\n");
                }
            }
        }

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
            List<String> traitNames = hero.traits.stream()
                    .map(t -> t.displayName(t.id()))
                    .distinct()
                    .toList();
            for (String traitName : traitNames) {
                md.append("  - ").append(traitName).append("\n");
            }
        } else {
            md.append("- **Traits:** None\n");
        }

        appendAbilitiesSection(md, hero, isMoW, abilityRarityMultiplier, data.abilityDisplayLookup());
        md.append("\n---\n\n");
    }

    private static void appendAbilitiesSection(
            StringBuilder md,
            HeroProfile hero,
            boolean isMoW,
            double abilityRarityMultiplier,
            Map<String, AbilityDisplayMetadata> abilityDisplayLookup) {
        HeroAbilitiesEntry ab = hero.abilities;
        Set<String> renderedIds = new LinkedHashSet<>();

        String primaryId = ab != null ? ab.activeAbility() : null;
        AbilityData primaryData = ab != null ? ab.activeAbilityData() : null;
        if (primaryId == null && isMoW && hero.playerUnit != null
                && hero.playerUnit.abilities() != null && !hero.playerUnit.abilities().isEmpty()) {
            primaryId = hero.playerUnit.abilities().get(0).id();
        }
        if (primaryId != null || primaryData != null) {
            String label = isMoW ? "Primary Ability" : "Active Ability";
            Integer lvl = ProgressionHelper.findPlayerAbilityLevel(
                    hero.playerUnit, primaryId, primaryData != null ? primaryData.gameId() : null, 0);
            renderSingleAbility(md, label, primaryId, primaryData, hero.name, lvl, abilityRarityMultiplier,
                    abilityDisplayLookup);
            trackRenderedId(renderedIds, primaryId, primaryData);
        }

        String secondaryId = ab != null ? ab.secondaryAbility() : null;
        AbilityData secondaryData = ab != null ? ab.secondaryAbilityData() : null;
        if (secondaryId == null && isMoW && hero.playerUnit != null
                && hero.playerUnit.abilities() != null && hero.playerUnit.abilities().size() > 1) {
            secondaryId = hero.playerUnit.abilities().get(1).id();
        }
        if (secondaryId != null || secondaryData != null) {
            Integer lvl = ProgressionHelper.findPlayerAbilityLevel(
                    hero.playerUnit, secondaryId, secondaryData != null ? secondaryData.gameId() : null, 1);
            renderSingleAbility(md, "Secondary Ability", secondaryId, secondaryData, hero.name, lvl,
                    abilityRarityMultiplier, abilityDisplayLookup);
            trackRenderedId(renderedIds, secondaryId, secondaryData);
        }

        String passiveId = ab != null ? ab.passiveAbility() : null;
        AbilityData passiveData = ab != null ? ab.passiveAbilityData() : null;
        if (passiveId != null || passiveData != null) {
            Integer lvl = ProgressionHelper.findPlayerAbilityLevel(
                    hero.playerUnit, passiveId, passiveData != null ? passiveData.gameId() : null);
            renderSingleAbility(md, "Passive Ability", passiveId, passiveData, hero.name, lvl, abilityRarityMultiplier,
                    abilityDisplayLookup);
            trackRenderedId(renderedIds, passiveId, passiveData);
        }

        Set<String> mythicIds = new LinkedHashSet<>();
        if (ab != null) {
            if (ab.mythicAbility() != null && !ab.mythicAbility().isBlank()) {
                mythicIds.add(ab.mythicAbility());
            }
            if (ab.mythicAbilities() != null) {
                mythicIds.addAll(ab.mythicAbilities());
            }
        }
        if (isMoW && hero.playerUnit != null
                && hero.playerUnit.abilities() != null && hero.playerUnit.abilities().size() > 2) {
            for (int i = 2; i < hero.playerUnit.abilities().size(); i++) {
                PlayerAbility pa = hero.playerUnit.abilities().get(i);
                if (pa.id() != null && !renderedIds.contains(pa.id().toLowerCase())) {
                    mythicIds.add(pa.id());
                }
            }
        }

        Map<String, AbilityData> mythicDataMap = (ab != null && ab.mythicAbilitiesData() != null)
                ? ab.mythicAbilitiesData()
                : Map.of();
        for (String mythicId : mythicIds) {
            if (renderedIds.contains(mythicId.toLowerCase())) {
                continue;
            }
            AbilityData mData = mythicDataMap.get(mythicId);
            if (mData == null && ab != null && mythicId.equalsIgnoreCase(ab.mythicAbility())) {
                mData = ab.mythicAbilityData();
            }
            Integer lvl = ProgressionHelper.findPlayerAbilityLevel(
                    hero.playerUnit, mythicId, mData != null ? mData.gameId() : null);
            renderSingleAbility(md, "Mythic Ability", mythicId, mData, hero.name, lvl, abilityRarityMultiplier,
                    abilityDisplayLookup);
            trackRenderedId(renderedIds, mythicId, mData);
        }
    }

    private static void renderSingleAbility(
            StringBuilder md,
            String roleLabel,
            String abilityId,
            AbilityData abilityData,
            String unitName,
            Integer playerLevel,
            double abilityRarityMultiplier,
            Map<String, AbilityDisplayMetadata> abilityDisplayLookup) {
        String displayName = GameTextFormatter.resolveAbilityDisplayName(
                abilityId, abilityData, abilityDisplayLookup);
        if ("Unknown Ability".equals(displayName) && abilityId != null && !abilityId.isBlank()) {
            displayName = abilityId.replaceAll("([a-z])([A-Z])", "$1 $2");
        }

        md.append("\n#### ").append(roleLabel).append(": ").append(displayName);
        if (playerLevel != null) {
            if (playerLevel == 0) {
                md.append(" (Locked / Level 0)");
            } else {
                md.append(" (Level ").append(playerLevel).append(")");
            }
        }
        md.append("\n");

        if (abilityData != null) {
            md.append(GameTextFormatter.formatAbility(
                    abilityData, unitName, playerLevel, abilityRarityMultiplier));
        } else if (abilityId != null) {
            md.append("- **Ability ID:** `").append(abilityId).append("` (Static description not found)\n");
        }
    }

    private static void trackRenderedId(Set<String> renderedIds, String abilityId, AbilityData data) {
        if (abilityId != null && !abilityId.isBlank()) {
            renderedIds.add(abilityId.toLowerCase());
        }
        if (data != null && data.gameId() != null && !data.gameId().isBlank()) {
            renderedIds.add(data.gameId().toLowerCase());
        }
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
}