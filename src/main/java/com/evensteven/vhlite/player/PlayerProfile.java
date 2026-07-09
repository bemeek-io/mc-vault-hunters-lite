package com.evensteven.vhlite.player;

import com.evensteven.vhlite.knowledge.ResearchNode;
import com.evensteven.vhlite.skills.StatType;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the plugin remembers about one player. Mutable, cached by
 * ProfileStore while online, persisted to playerdata/&lt;uuid&gt;.yml.
 */
public final class PlayerProfile {

    public final UUID id;
    public String name = "";
    public int vaultLevel;
    public int vaultXp;
    public int skillPoints;
    public int knowledgePoints;
    public final Map<StatType, Integer> stats = new EnumMap<>(StatType.class);
    public final Set<ResearchNode> research = EnumSet.noneOf(ResearchNode.class);
    /** One-time hand-outs and quest bookkeeping. */
    public boolean guideGiven;
    public final Map<String, Integer> questProgress = new java.util.HashMap<>();
    public final Set<String> questsCompleted = new java.util.HashSet<>();

    public PlayerProfile(UUID id) {
        this.id = id;
        for (StatType stat : StatType.values()) {
            stats.put(stat, 0);
        }
    }

    public int stat(StatType type) {
        return stats.getOrDefault(type, 0);
    }

    public boolean has(ResearchNode node) {
        return research.contains(node);
    }
}
