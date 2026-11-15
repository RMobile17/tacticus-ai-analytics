package com.tacticus.analytics.common;

import com.tacticus.analytics.model.GameModels.AbilityData;
import com.tacticus.analytics.model.GameModels.AbilityDisplayMetadata;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GameTextFormatter {

    private GameTextFormatter() {
    }

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\[(.+?)\\]\\}");
    private static final Pattern ARRAY_INDEX_PATTERN = Pattern.compile("(\\w+)\\[(\\d+)\\]");

    public static String formatFactionName(String rawFaction) {
        if (rawFaction == null)
            return "Unknown";
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

    public static String resolveAbilityDisplayName(
            String abilityId,
            AbilityData data,
            Map<String, AbilityDisplayMetadata> abilityDisplayLookup) {
        if (abilityId != null && abilityDisplayLookup != null) {
            AbilityDisplayMetadata meta = abilityDisplayLookup.get(abilityId.toLowerCase());
            if (meta != null && meta.displayName() != null && !meta.displayName().isBlank()) {
                return meta.displayName();
            }
        }
        if (data != null && data.gameId() != null && abilityDisplayLookup != null) {
            AbilityDisplayMetadata meta = abilityDisplayLookup.get(data.gameId().toLowerCase());
            if (meta != null && meta.displayName() != null && !meta.displayName().isBlank()) {
                return meta.displayName();
            }
        }
        return data != null ? data.name() : "Unknown Ability";
    }

    public static String formatAbility(
            AbilityData data,
            String unitName,
            Integer abilityLevel,
            double abilityRarityMultiplier) {
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
            String replacement = resolvePlaceholder(rawKey, data, abilityLevel, abilityRarityMultiplier);
            matcher.appendReplacement(resolvedDesc, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolvedDesc);

        sb.append("- **Description:**\n  ")
                .append(resolvedDesc.toString().trim().replace("\n", "\n  "))
                .append("\n");

        return sb.toString();
    }

    private static String resolvePlaceholder(
            String key,
            AbilityData data,
            Integer abilityLevel,
            double abilityRarityMultiplier) {
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
                    boolean affected = isVariableAffectedByRarity(varName, data);
                    if (abilityLevel != null && abilityLevel >= 1 && abilityLevel <= list.size()) {
                        String val = extractIndexedValue(list.get(abilityLevel - 1).toString(), idx);
                        return applyRarityMultiplier(val, affected, abilityRarityMultiplier);
                    }
                    String first = extractIndexedValue(list.get(0).toString(), idx);
                    String last = extractIndexedValue(list.get(list.size() - 1).toString(), idx);
                    return first.equals(last) ? first : "[" + first + " .. " + last + "]";
                }
            }

            List<Object> list = findVariableList(data.variables(), key);
            if (list != null && !list.isEmpty()) {
                boolean affected = isVariableAffectedByRarity(key, data);
                if (abilityLevel != null && abilityLevel >= 1 && abilityLevel <= list.size()) {
                    String val = String.valueOf(list.get(abilityLevel - 1));
                    return applyRarityMultiplier(val, affected, abilityRarityMultiplier);
                }
                String first = String.valueOf(list.get(0));
                String last = String.valueOf(list.get(list.size() - 1));
                return first.equals(last) ? first : "[" + first + " .. " + last + "]";
            }
        }
        return "{" + key + "}";
    }

    private static boolean isVariableAffectedByRarity(String varKey, AbilityData data) {
        if (data.variablesAffectedByRarityBonus() == null)
            return false;
        for (String v : data.variablesAffectedByRarityBonus()) {
            if (v != null && v.trim().equalsIgnoreCase(varKey.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String applyRarityMultiplier(String rawValue, boolean affected, double multiplier) {
        if (!affected || multiplier == 1.0) {
            return rawValue;
        }
        try {
            double num = Double.parseDouble(rawValue.trim());
            return String.valueOf(Math.round(num * multiplier));
        } catch (NumberFormatException e) {
            return rawValue;
        }
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
