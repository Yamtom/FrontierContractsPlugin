package ua.grigo.frontiercontracts.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record ContractCatalog(
    Map<ContractRank, String> rankDescriptions,
    RoadSettings roadSettings,
    ConstructionRules constructionRules,
    ProgressionSettings progressionSettings,
    ProjectGenerationSettings projectGenerationSettings,
    List<ContractTemplate> rankedTemplates,
    List<ContractTemplate> projectTemplates
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
        progressionSettings = progressionSettings == null ? ProgressionSettings.defaults() : progressionSettings;
        projectGenerationSettings = projectGenerationSettings == null
            ? ProjectGenerationSettings.defaults()
            : projectGenerationSettings;
        rankedTemplates = rankedTemplates == null ? List.of() : List.copyOf(rankedTemplates);
        projectTemplates = projectTemplates == null ? List.of() : List.copyOf(projectTemplates);
    }

    public String rankDescription(ContractRank rank) {
        if (rank == null) {
            return "Project";
        }
        return rankDescriptions.getOrDefault(rank, rank.name());
    }

    public List<ContractTemplate> rankedTemplates(ContractScope scope) {
        return rankedTemplates.stream().filter(template -> template.scope() == scope).toList();
    }

    public List<ContractTemplate> projectTemplates(ContractScope scope) {
        return projectTemplates.stream().filter(template -> template.scope() == scope).toList();
    }
}
