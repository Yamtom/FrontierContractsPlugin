package ua.grigo.frontiercontracts.contract;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ua.grigo.frontiercontracts.model.BonusConfig;
import ua.grigo.frontiercontracts.model.ConstructionMetadata;
import ua.grigo.frontiercontracts.model.ContractCatalog;
import ua.grigo.frontiercontracts.model.ContractRank;
import ua.grigo.frontiercontracts.model.ContractRequirement;
import ua.grigo.frontiercontracts.model.ContractScope;
import ua.grigo.frontiercontracts.model.ContractTemplate;
import ua.grigo.frontiercontracts.model.ContractType;
import ua.grigo.frontiercontracts.model.ProjectTrigger;
import ua.grigo.frontiercontracts.model.RewardBundle;

/**
 * Registry of all loaded {@link ContractTemplate} instances, indexed by id and by pool name.
 *
 * <p>Supports two loading modes:
 * <ol>
 *   <li><b>Folder scan</b> — {@link #loadFromFolder(File)} reads every {@code *.yml} file inside
 *       a {@code contracts/} directory.  Each file must contain a flat template definition (see
 *       {@link #parseTemplate(String, YamlConfiguration)}).
 *   <li><b>Catalog import</b> — {@link #importFromCatalog(ContractCatalog)} registers all
 *       templates from an existing {@link ContractCatalog} produced by the archetype-expansion
 *       loader.
 * </ol>
 *
 * <p>Both modes can be combined; templates loaded later simply overwrite earlier ones with the
 * same id.
 */
public final class ContractTemplateRegistry {

    private static final Logger LOGGER = Logger.getLogger(ContractTemplateRegistry.class.getName());

    /** Templates keyed by their canonical id ({@link ContractTemplate#key()}). */
    private final Map<String, ContractTemplate> byId = new HashMap<>();

    /** Templates grouped by each pool tag they belong to. */
    private final Map<String, List<ContractTemplate>> byPool = new HashMap<>();

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    /**
     * Scans {@code contractsFolder} for {@code *.yml} files and loads each one as a single flat
     * {@link ContractTemplate}.
     *
     * <p>The folder is created automatically if it does not exist so that first-run behaviour is
     * seamless.  Files that fail to parse are skipped with a warning.
     *
     * @param contractsFolder directory to scan (typically {@code <dataFolder>/contracts/})
     * @return number of templates successfully registered
     */
    public int loadFromFolder(File contractsFolder) {
        if (!contractsFolder.exists()) {
            contractsFolder.mkdirs();
            LOGGER.info("Created contracts/ folder at " + contractsFolder.getPath());
            return 0;
        }

        File[] files = contractsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            LOGGER.info("No *.yml files found in contracts/ folder.");
            return 0;
        }

        int loaded = 0;
        for (File file : files) {
            String fileId = file.getName().replace(".yml", "");
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                ContractTemplate template = parseTemplate(fileId, yaml);
                if (template != null) {
                    register(template);
                    loaded++;
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to load contract template from " + file.getName() + ": " + e.getMessage());
            }
        }
        LOGGER.info("Loaded " + loaded + " contract template(s) from contracts/ folder.");
        return loaded;
    }

    /**
     * Imports all ranked and project templates from a pre-built {@link ContractCatalog}.
     *
     * @param catalog catalog produced by the archetype-expansion loader
     */
    public void importFromCatalog(ContractCatalog catalog) {
        if (catalog == null) return;
        for (ContractTemplate t : catalog.rankedTemplates()) register(t);
        for (ContractTemplate t : catalog.projectTemplates()) register(t);
    }

    /**
     * Registers a single template into both indices.  Overwrites any previous entry with the same
     * id.
     *
     * @param template the template to register
     */
    public void register(ContractTemplate template) {
        if (template == null) return;
        byId.put(template.key(), template);
        for (String pool : template.contractPools()) {
            byPool.computeIfAbsent(pool, k -> new ArrayList<>()).add(template);
        }
    }

    /** Clears all loaded templates. */
    public void clear() {
        byId.clear();
        byPool.clear();
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /**
     * Returns the template with the given id, or {@link Optional#empty()} if not found.
     *
     * @param id template id ({@link ContractTemplate#key()})
     */
    public Optional<ContractTemplate> getById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Returns all templates that belong to the given pool tag.
     *
     * @param pool pool name
     * @return unmodifiable list; empty if pool not registered
     */
    public List<ContractTemplate> getByPool(String pool) {
        List<ContractTemplate> list = byPool.get(pool);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    /**
     * Returns all registered templates as an unmodifiable collection.
     */
    public List<ContractTemplate> all() {
        return List.copyOf(byId.values());
    }

    /** Number of registered templates. */
    public int size() {
        return byId.size();
    }

    // -------------------------------------------------------------------------
    // Flat YAML parsing
    // -------------------------------------------------------------------------

    /**
     * Parses a single flat YAML file into a {@link ContractTemplate}.
     *
     * <p>Expected keys (all optional unless marked *required*):
     * <pre>
     *   id: &lt;string&gt;            # overrides file name if provided
     *   displayName: &lt;string&gt;   # *required*
     *   rank: C                  # ContractRank enum, default C
     *   scope: LOCAL             # ContractScope enum, default LOCAL
     *   type: DELIVERY           # ContractType enum, default DELIVERY
     *   weight: 10               # selection weight, default 10
     *   durationMinutes: 60      # default 60
     *   maxSimultaneousPlayers: 1
     *   contractPools:           # list of pool tags
     *     - default
     *   regionTags:              # list of region tags for REGIONAL filtering
     *     - my-region
     *   requirements:
     *     - material: OAK_LOG
     *       amount: 32
     *   reward:
     *     money: 500.0
     *     xp: 0                  # stored as reputation internally
     *     commands: []
     * </pre>
     *
     * @param fileId   stem of the file name, used as fallback id
     * @param yaml     loaded YAML configuration
     * @return parsed template, or {@code null} if the file is missing required fields
     */
    ContractTemplate parseTemplate(String fileId, YamlConfiguration yaml) {
        String id = yaml.getString("id", fileId);
        String displayName = yaml.getString("displayName", yaml.getString("display-name", id));
        if (displayName.isBlank()) {
            LOGGER.warning("Template '" + id + "' has no displayName — skipping.");
            return null;
        }

        ContractRank rank = ContractRank.fromString(yaml.getString("rank", "C"));
        ContractScope scope = parseScope(yaml.getString("scope", "LOCAL"));
        ContractType type = parseType(yaml.getString("type", "DELIVERY"));
        int weight = Math.max(1, yaml.getInt("weight", 10));
        int durationMinutes = Math.max(1, yaml.getInt("durationMinutes", yaml.getInt("duration-minutes", 60)));
        int maxPlayers = Math.max(1, yaml.getInt("maxSimultaneousPlayers", yaml.getInt("max-simultaneous-players", 1)));

        List<String> contractPools = yaml.getStringList("contractPools");
        if (contractPools.isEmpty()) contractPools = yaml.getStringList("contract-pools");

        List<String> regionTags = yaml.getStringList("regionTags");
        if (regionTags.isEmpty()) regionTags = yaml.getStringList("region-tags");

        List<ContractRequirement> requirements = parseRequirements(yaml.getConfigurationSection("requirements"));

        // Reward
        ConfigurationSection rewardSec = yaml.getConfigurationSection("reward");
        double money = rewardSec != null ? rewardSec.getDouble("money", 0.0) : 0.0;
        int xp = rewardSec != null ? rewardSec.getInt("xp", 0) : 0;
        List<String> commands = rewardSec != null ? rewardSec.getStringList("commands") : List.of();
        RewardBundle reward = new RewardBundle(money, xp, List.of(), commands);

        Material icon = requirements.isEmpty() ? Material.CHEST : requirements.getFirst().material();

        return new ContractTemplate(
            id, scope, type, rank,
            /* difficulty */ "NORMAL",
            weight,
            icon,
            displayName,
            /* description */ List.of(),
            requirements,
            ConstructionMetadata.empty(),
            durationMinutes,
            /* partialDelivery */ true,
            /* publicOffer */ true,
            reward,
            RewardBundle.empty(),
            new BonusConfig(1.0),
            contractPools,
            regionTags,
            ProjectTrigger.none(),
            maxPlayers
        );
    }

    private static List<ContractRequirement> parseRequirements(ConfigurationSection sec) {
        if (sec == null) return List.of();
        List<ContractRequirement> list = new ArrayList<>();
        for (String key : sec.getKeys(false)) {
            ConfigurationSection reqSec = sec.getConfigurationSection(key);
            if (reqSec == null) continue;
            String matName = reqSec.getString("material", reqSec.getString("item", ""));
            Material material = matName.isBlank() ? null : Material.matchMaterial(matName.toUpperCase());
            if (material == null || material == Material.AIR) {
                LOGGER.warning("Requirement '" + key + "' has unknown material '" + matName + "' — skipping.");
                continue;
            }
            int amount = Math.max(1, reqSec.getInt("amount", 1));
            list.add(new ContractRequirement(material, amount));
        }
        return Collections.unmodifiableList(list);
    }

    private static ContractScope parseScope(String raw) {
        if (raw == null) return ContractScope.LOCAL;
        try {
            return ContractScope.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ContractScope.LOCAL;
        }
    }

    private static ContractType parseType(String raw) {
        if (raw == null) return ContractType.DELIVERY;
        try {
            return ContractType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ContractType.DELIVERY;
        }
    }
}
