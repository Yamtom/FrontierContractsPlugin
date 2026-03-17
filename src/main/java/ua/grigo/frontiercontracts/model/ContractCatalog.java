package ua.grigo.frontiercontracts.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record ContractCatalog(
    Map<ContractRank, String> rankDescriptions,
    RoadSettings roadSettings,
    ConstructionRules constructionRules,
    List<ContractTemplate> templates
) {
    public ContractCatalog {
        EnumMap<ContractRank, String> defaults = new EnumMap<>(ContractRank.class);
        for (ContractRank rank : ContractRank.values()) {
            defaults.put(rank, rank.name());
        }
        if (rankDescriptions != null) {
            defaults.putAll(rankDescriptions);
        }
        rankDescriptions = Map.copyOf(defaults);
        roadSettings = roadSettings == null ? RoadSettings.defaults() : roadSettings;
        constructionRules = constructionRules == null ? ConstructionRules.defaults() : constructionRules;
        templates = templates == null ? List.of() : List.copyOf(templates);
    }

    public String rankDescription(ContractRank rank) {
        return rankDescriptions.getOrDefault(rank, rank.name());
    }
}
