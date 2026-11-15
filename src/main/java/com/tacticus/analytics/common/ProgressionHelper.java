package com.tacticus.analytics.common;

import com.tacticus.analytics.model.GameModels.HeroAbilitiesEntry;
import com.tacticus.analytics.model.HeroProfile;
import com.tacticus.analytics.model.PlayerModels.PlayerAbility;
import com.tacticus.analytics.model.PlayerModels.PlayerUnit;
import com.tacticus.analytics.model.PlayerModels.ProgressionTier;

import java.util.Collection;
import java.util.List;

public final class ProgressionHelper {

    private ProgressionHelper() {
    }

    private static final String[] RANK_NAMES = {
            "Stone I", "Stone II", "Stone III",
            "Iron I", "Iron II", "Iron III",
            "Bronze I", "Bronze II", "Bronze III",
            "Silver I", "Silver II", "Silver III",
            "Gold I", "Gold II", "Gold III",
            "Diamond I", "Diamond II", "Diamond III",
            "Adamantine I", "Adamantine II", "Adamantine III"
    };

    private static final List<ProgressionTier> PROGRESSION_TIERS = List.of(
            new ProgressionTier("Common (0 Stars)", 0, 1.0), // 0
            new ProgressionTier("Common (1 Star)", 1, 1.0), // 1
            new ProgressionTier("Common (2 Stars)", 2, 1.0), // 2
            new ProgressionTier("Uncommon (2 Stars)", 2, 1.2), // 3
            new ProgressionTier("Uncommon (3 Stars)", 3, 1.2), // 4
            new ProgressionTier("Uncommon (4 Stars)", 4, 1.2), // 5
            new ProgressionTier("Rare (4 Stars)", 4, 1.4), // 6
            new ProgressionTier("Rare (5 Stars)", 5, 1.4), // 7
            new ProgressionTier("Rare (1 Red Star)", 6, 1.4), // 8
            new ProgressionTier("Epic (1 Red Star)", 6, 1.6), // 9
            new ProgressionTier("Epic (2 Red Stars)", 7, 1.6), // 10
            new ProgressionTier("Epic (3 Red Stars)", 8, 1.6), // 11
            new ProgressionTier("Legendary (3 Red Stars)", 8, 1.8), // 12
            new ProgressionTier("Legendary (4 Red Stars)", 9, 1.8), // 13
            new ProgressionTier("Legendary (5 Red Stars)", 10, 1.8), // 14
            new ProgressionTier("Legendary (Blue Star)", 11, 1.8), // 15
            new ProgressionTier("Mythic (Blue Star)", 11, 2.0), // 16
            new ProgressionTier("Mythic (1 Mythic Star)", 12, 2.0), // 17
            new ProgressionTier("Mythic (2 Mythic Stars)", 13, 2.0), // 18
            new ProgressionTier("Mythic (3 Mythic Stars)", 14, 2.0) // 19
    );

    public static ProgressionTier getProgressionTier(int progressionIndex, String baseRarity) {
        if (progressionIndex >= 0 && progressionIndex < PROGRESSION_TIERS.size()) {
            return PROGRESSION_TIERS.get(progressionIndex);
        }
        return switch (baseRarity) {
            case "Uncommon" -> PROGRESSION_TIERS.get(3);
            case "Rare" -> PROGRESSION_TIERS.get(6);
            case "Epic" -> PROGRESSION_TIERS.get(9);
            case "Legendary" -> PROGRESSION_TIERS.get(12);
            default -> PROGRESSION_TIERS.get(0);
        };
    }

    public static String formatRank(int rank) {
        if (rank >= 0 && rank < RANK_NAMES.length) {
            return RANK_NAMES[rank];
        }
        return String.valueOf(rank);
    }

    public static HeroProfile findMatchingHeroProfile(PlayerUnit pu, Collection<HeroProfile> profiles) {
        if (pu.id() != null) {
            for (HeroProfile hp : profiles) {
                if (pu.id().equalsIgnoreCase(hp.id)
                        || (hp.details != null && pu.id().equalsIgnoreCase(hp.details.gameId()))) {
                    return hp;
                }
            }
        }
        if (pu.name() != null) {
            String normalizedTarget = normalizeName(pu.name());
            for (HeroProfile hp : profiles) {
                if (hp.name != null && normalizeName(hp.name).equals(normalizedTarget)) {
                    return hp;
                }
            }
        }
        if (pu.abilities() != null && !pu.abilities().isEmpty()) {
            for (PlayerAbility pa : pu.abilities()) {
                if (pa.id() == null)
                    continue;
                for (HeroProfile hp : profiles) {
                    if (matchesAnyAbilityId(pa.id(), hp.abilities)) {
                        return hp;
                    }
                }
            }
        }
        return null;
    }

    private static boolean matchesAnyAbilityId(String playerAbilityId, HeroAbilitiesEntry abilities) {
        if (abilities == null || playerAbilityId == null) {
            return false;
        }
        if (playerAbilityId.equalsIgnoreCase(abilities.activeAbility())
                || playerAbilityId.equalsIgnoreCase(abilities.secondaryAbility())
                || playerAbilityId.equalsIgnoreCase(abilities.passiveAbility())
                || playerAbilityId.equalsIgnoreCase(abilities.mythicAbility())) {
            return true;
        }
        if (abilities.mythicAbilities() != null) {
            for (String mythicId : abilities.mythicAbilities()) {
                if (playerAbilityId.equalsIgnoreCase(mythicId)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Integer findPlayerAbilityLevel(PlayerUnit pu, String abilityKey, String gameId) {
        if (pu == null || pu.abilities() == null)
            return null;
        for (PlayerAbility pa : pu.abilities()) {
            if (pa.id() == null)
                continue;
            if ((abilityKey != null && pa.id().equalsIgnoreCase(abilityKey))
                    || (gameId != null && pa.id().equalsIgnoreCase(gameId))) {
                return pa.level();
            }
        }
        return null;
    }

    public static Integer findPlayerAbilityLevel(
            PlayerUnit pu, String abilityKey, String gameId, int fallbackSlotIndex) {
        Integer byId = findPlayerAbilityLevel(pu, abilityKey, gameId);
        if (byId != null) {
            return byId;
        }
        if (abilityKey == null
                && gameId == null
                && pu != null
                && pu.abilities() != null
                && fallbackSlotIndex >= 0
                && fallbackSlotIndex < pu.abilities().size()) {
            return pu.abilities().get(fallbackSlotIndex).level();
        }
        return null;
    }

    private static String normalizeName(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}