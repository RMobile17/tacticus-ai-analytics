package com.tacticus.analytics.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

public final class PlayerModels {

    private PlayerModels() {
    }

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

    public record ProgressionTier(String rarityLabel, int stars, double abilityMultiplier) {
    }
}
