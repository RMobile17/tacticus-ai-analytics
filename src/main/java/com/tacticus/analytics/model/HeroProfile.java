package com.tacticus.analytics.model;

import com.tacticus.analytics.model.GameModels.DetailedHeroFile;
import com.tacticus.analytics.model.GameModels.HeroAbilitiesEntry;
import com.tacticus.analytics.model.GameModels.TraitMetadata;
import com.tacticus.analytics.model.PlayerModels.PlayerUnit;

import java.util.ArrayList;
import java.util.List;

public class HeroProfile {
    public String id;
    public String name;
    public String faction = "Unknown";
    public String rarity = "Unknown";
    public List<TraitMetadata> traits = new ArrayList<>();
    public HeroAbilitiesEntry abilities;
    public DetailedHeroFile details;
    public PlayerUnit playerUnit;
}
