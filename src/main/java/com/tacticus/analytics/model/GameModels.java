package com.tacticus.analytics.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public final class GameModels {

    private GameModels() {
    }

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
            String secondaryAbility,
            AbilityData secondaryAbilityData,
            String passiveAbility,
            AbilityData passiveAbilityData,
            String mythicAbility,
            AbilityData mythicAbilityData,
            List<String> mythicAbilities,
            Map<String, AbilityData> mythicAbilitiesData) {
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
            List<String> activeAbilities,
            List<String> passiveAbilities,
            List<String> mythicAbilities,
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
}