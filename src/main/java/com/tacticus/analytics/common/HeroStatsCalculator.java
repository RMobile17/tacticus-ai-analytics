package com.tacticus.analytics.common;

import com.tacticus.analytics.model.GameModels.DetailedHeroFile;
import com.tacticus.analytics.model.GameModels.HeroRankData;
import com.tacticus.analytics.model.GameModels.ItemDefinition;
import com.tacticus.analytics.model.GameModels.RankUpgradeData;
import com.tacticus.analytics.model.PlayerModels.PlayerItem;
import com.tacticus.analytics.model.PlayerModels.PlayerUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class HeroStatsCalculator {

    private HeroStatsCalculator() {
    }

    public static String formatPlayerCombatStats(
            DetailedHeroFile details,
            PlayerUnit pu,
            int stars,
            Map<String, ItemDefinition> itemsCatalog) {
        if (details == null || details.ranks() == null || details.ranks().isEmpty()) {
            return null;
        }
        int rankIdx = Math.min(Math.max(pu.rank(), 0), details.ranks().size() - 1);
        HeroRankData rankData = details.ranks().get(rankIdx);

        double starMultiplier = 1.0 + (0.1 * stars);
        int hp = (int) Math.floor(rankData.health() * starMultiplier);
        int dmg = (int) Math.floor(rankData.damage() * starMultiplier);
        int armor = (int) Math.floor(rankData.armor() * starMultiplier);

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

        if (pu.items() != null) {
            for (PlayerItem item : pu.items()) {
                Map<String, Integer> stats = getEquippedItemStats(item, itemsCatalog);
                if (stats != null) {
                    hp += stats.getOrDefault("hp", 0) + stats.getOrDefault("health", 0);
                    armor += stats.getOrDefault("fixedArmor", 0) + stats.getOrDefault("armor", 0);
                }
            }
        }

        return "Health: " + hp + " | Damage: " + dmg + " | Armor: " + armor;
    }

    public static String formatLockedCombatStats(DetailedHeroFile details) {
        if (details == null || details.ranks() == null || details.ranks().isEmpty()) {
            return null;
        }
        HeroRankData first = details.ranks().get(0);
        HeroRankData last = details.ranks().get(details.ranks().size() - 1);
        return "Health: [" + first.health() + " .. " + last.health() + "] | "
                + "Damage: [" + first.damage() + " .. " + last.damage() + "] | "
                + "Armor: [" + first.armor() + " .. " + last.armor() + "]";
    }

    public static String formatEquippedItem(PlayerItem item, Map<String, ItemDefinition> itemsCatalog) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.name()).append(" (").append(item.rarity()).append(" Lvl ").append(item.level());

        Map<String, Integer> stats = getEquippedItemStats(item, itemsCatalog);
        if (stats != null && !stats.isEmpty()) {
            List<String> statStrings = new ArrayList<>();
            stats.forEach((k, v) -> statStrings.add(formatItemStat(k, v)));
            sb.append(": ").append(String.join(", ", statStrings));
        }
        sb.append(")");
        return sb.toString();
    }

    public static String formatItemTotalsSummary(List<PlayerItem> items, Map<String, ItemDefinition> itemsCatalog) {
        int critChance = 0;
        int critDmg = 0;
        int blockChance = 0;
        int blockDmg = 0;

        for (PlayerItem item : items) {
            Map<String, Integer> stats = getEquippedItemStats(item, itemsCatalog);
            if (stats != null) {
                critChance += stats.getOrDefault("critChance", 0) + stats.getOrDefault("critChanceBonus", 0);
                critDmg += stats.getOrDefault("critDmg", 0) + stats.getOrDefault("critDmgBonus", 0);
                blockChance += stats.getOrDefault("blockChance", 0) + stats.getOrDefault("blockChanceBonus", 0);
                blockDmg += stats.getOrDefault("blockDmg", 0) + stats.getOrDefault("blockDmgBonus", 0);
            }
        }

        List<String> parts = new ArrayList<>();
        if (critChance > 0 || critDmg > 0) {
            parts.add("Crit: +" + critDmg + " Damage (" + critChance + "% Chance)");
        }
        if (blockChance > 0 || blockDmg > 0) {
            parts.add("Block: +" + blockDmg + " Damage (" + blockChance + "% Chance)");
        }
        return parts.isEmpty() ? null : String.join(" | ", parts);
    }

    private static Map<String, Integer> getEquippedItemStats(PlayerItem item,
            Map<String, ItemDefinition> itemsCatalog) {
        if (itemsCatalog == null)
            return null;
        ItemDefinition def = itemsCatalog.get(item.id());
        if (def != null && def.levels() != null && item.level() >= 1 && item.level() <= def.levels().size()) {
            return def.levels().get(item.level() - 1).stats();
        }
        return null;
    }

    private static String formatItemStat(String statKey, int value) {
        return switch (statKey) {
            case "critChance" -> "+" + value + "% Crit Chance";
            case "critChanceBonus" -> "+" + value + "% Crit Chance Bonus";
            case "critDmg" -> "+" + value + " Crit Damage";
            case "critDmgBonus" -> "+" + value + " Crit Damage Bonus";
            case "blockChance" -> "+" + value + "% Block Chance";
            case "blockChanceBonus" -> "+" + value + "% Block Chance Bonus";
            case "blockDmg" -> "+" + value + " Block Damage";
            case "blockDmgBonus" -> "+" + value + " Block Damage Bonus";
            case "fixedArmor", "armor" -> "+" + value + " Armor";
            case "hp", "health" -> "+" + value + " Health";
            default -> "+" + value + " " + statKey;
        };
    }
}
