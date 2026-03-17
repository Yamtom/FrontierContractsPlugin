package ua.grigo.frontiercontracts;

import java.io.File;
import java.sql.SQLException;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ua.grigo.frontiercontracts.board.BoardService;
import ua.grigo.frontiercontracts.board.BoardSignService;
import ua.grigo.frontiercontracts.command.ContractsCommand;
import ua.grigo.frontiercontracts.config.PluginSettings;
import ua.grigo.frontiercontracts.contract.ContractService;
import ua.grigo.frontiercontracts.listener.BoardProtectionListener;
import ua.grigo.frontiercontracts.listener.ConstructionWorldListener;
import ua.grigo.frontiercontracts.gui.MenuService;
import ua.grigo.frontiercontracts.hook.FrontierPlaceholderExpansion;
import ua.grigo.frontiercontracts.hook.VaultHook;
import ua.grigo.frontiercontracts.listener.BlockInteractListener;
import ua.grigo.frontiercontracts.listener.ContractProgressListener;
import ua.grigo.frontiercontracts.listener.MenuListener;
import ua.grigo.frontiercontracts.storage.StorageService;
import ua.grigo.frontiercontracts.util.MessageService;

public final class FrontierContractsPlugin extends JavaPlugin {
    private int cleanupTaskId = -1;
    private PluginSettings settings;
    private MessageService messageService;
    private VaultHook vaultHook;
    private StorageService storageService;
    private BoardService boardService;
    private BoardSignService boardSignService;
    private ContractService contractService;
    private MenuService menuService;
    private FileConfiguration contractsConfiguration;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("contracts.yml");
        saveResourceIfMissing("messages.yml");
        saveResourceIfMissing("boards.yml");

        settings = PluginSettings.from(getConfig());
        messageService = new MessageService(this);
        vaultHook = new VaultHook(this);
        vaultHook.setup();

        storageService = new StorageService(getDataFolder(), settings.databaseFile());
        try {
            storageService.initialize();
        } catch (SQLException exception) {
            getLogger().severe("Could not initialize SQLite storage: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        boardService = new BoardService(getDataFolder(), getServer(), getLogger());
        boardService.load(settings);
        boardSignService = new BoardSignService(getServer(), messageService);

        contractService = new ContractService(this, storageService, vaultHook, messageService, boardService, boardSignService, settings);
        menuService = new MenuService(this, contractService, messageService);

        if (!reloadPluginState()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        registerCommands();
        registerListeners();
        registerHooks();
    }

    @Override
    public void onDisable() {
        if (storageService != null) {
            storageService.close();
        }
    }

    public boolean reloadPluginState() {
        reloadConfig();
        settings = PluginSettings.from(getConfig());
        messageService.reload();
        vaultHook.setup();
        boardService.load(settings);
        contractsConfiguration = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "contracts.yml"));
        try {
            contractService.reload(contractsConfiguration, settings);
            restartCleanupTask();
            return true;
        } catch (SQLException exception) {
            getLogger().severe("Could not reload Frontier Contracts data: " + exception.getMessage());
            return false;
        }
    }

    public PluginSettings getSettings() {
        return settings;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public ContractService getContractService() {
        return contractService;
    }

    public MenuService getMenuService() {
        return menuService;
    }

    public BoardService getBoardService() {
        return boardService;
    }

    private void registerCommands() {
        PluginCommand command = getCommand("contracts");
        if (command == null) {
            return;
        }

        ContractsCommand contractsCommand = new ContractsCommand(this, messageService);
        command.setExecutor(contractsCommand);
        command.setTabCompleter(contractsCommand);
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new MenuListener(menuService), this);
        Bukkit.getPluginManager().registerEvents(new BlockInteractListener(boardService, contractService, menuService, messageService), this);
        Bukkit.getPluginManager().registerEvents(new BoardProtectionListener(boardService, messageService), this);
        Bukkit.getPluginManager().registerEvents(new ContractProgressListener(contractService), this);
        Bukkit.getPluginManager().registerEvents(new ConstructionWorldListener(this, contractService), this);
    }

    private void registerHooks() {
        if (vaultHook.hasEconomy()) {
            messageService.send(Bukkit.getConsoleSender(), "system.economy-hooked");
        } else {
            messageService.send(Bukkit.getConsoleSender(), "system.economy-provider-missing");
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new FrontierPlaceholderExpansion(this).register();
            messageService.send(Bukkit.getConsoleSender(), "system.placeholder-hooked");
        } else {
            messageService.send(Bukkit.getConsoleSender(), "system.placeholder-missing");
        }
    }

    private void restartCleanupTask() {
        if (cleanupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cleanupTaskId);
        }
        long period = settings.cleanupIntervalSeconds() * 20L;
        cleanupTaskId = Bukkit.getScheduler().runTaskTimer(this, contractService::cleanupExpiredContent, period, period).getTaskId();
    }

    private void saveResourceIfMissing(String resourcePath) {
        File file = new File(getDataFolder(), resourcePath);
        if (!file.exists()) {
            saveResource(resourcePath, false);
        }
    }
}
