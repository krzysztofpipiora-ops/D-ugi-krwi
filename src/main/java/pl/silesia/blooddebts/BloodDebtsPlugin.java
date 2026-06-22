package pl.silesia.blooddebts;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class BloodDebtsPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private NamespacedKey debtKey;
    private NamespacedKey killerKey;
    private final String PREFIX = ChatColor.DARK_RED + "☠ " + ChatColor.RED + "[BloodDebts] " + ChatColor.RESET;

    @Override
    public void onEnable() {
        this.debtKey = new NamespacedKey(this, "blood_debt_count");
        this.killerKey = new NamespacedKey(this, "last_killer_uuid");
        
        saveDefaultConfig();
        
        getServer().getPluginManager().registerEvents(this, this);
        
        // POPRAWKA: Pewna rejestracja komend dla tego egzekutora
        if (getCommand("handlarz") != null) {
            getCommand("handlarz").setExecutor(this);
        }
        if (getCommand("bd") != null) {
            getCommand("bd").setExecutor(this);
            getCommand("bd").setTabCompleter(this);
        }
        
        getLogger().info("Plugin BloodDebts v3.1 (Fix komendy /handlarz) wlaczony!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // POPRAWKA: Naprawiona logika dostępu do sklepu
        if (command.getName().equalsIgnoreCase("handlarz")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Ta komenda jest tylko dla graczy!");
                return true;
            }
            Player player = (Player) sender;
            // Usunięto restrykcję blooddebts.admin - teraz każdy gracz może otworzyć menu
            openDeathMerchantGui(player);
            return true;
        }

        if (command.getName().equalsIgnoreCase("bd")) {
            if (!sender.hasPermission("blooddebts.admin")) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Brak uprawnien (blooddebts.admin)!");
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage(ChatColor.GOLD + "=== System BloodDebts ===");
                sender.sendMessage(ChatColor.YELLOW + "/bd givekey [gracz] [ilosc]" + ChatColor.GRAY + " - Daje Skazony Klucz");
                sender.sendMessage(ChatColor.YELLOW + "/bd givetoken [gracz] [ilosc]" + ChatColor.GRAY + " - Daje Token Dominacji");
                sender.sendMessage(ChatColor.YELLOW + "/bd getsb" + ChatColor.GRAY + " - Daje przedmio SKAZONEGO SKARBCA do EQ");
                sender.sendMessage(ChatColor.YELLOW + "/bd clear [gracz]" + ChatColor.GRAY + " - Czysci dlug gracza");
                return true;
            }

            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("getsb")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Tylko gracz moze odebrac skarbiec do ekwipunku!");
                    return true;
                }
                Player player = (Player) sender;
                player.getInventory().addItem(createCorruptedVaultItem());
                player.sendMessage(PREFIX + ChatColor.GREEN + "Otrzymales Skazony Skarbiec! Postaw go, aby go aktywowac.");
                return true;
            }

            if (subCommand.equals("givekey") || subCommand.equals("givetoken")) {
                Player target = (sender instanceof Player) ? (Player) sender : null;
                int amount = 1;

                if (args.length > 1) {
                    target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        sender.sendMessage(PREFIX + ChatColor.RED + "Nie znaleziono gracza " + args[1]);
                        return true;
                    }
                }
                if (args.length > 2) {
                    try {
                        amount = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(PREFIX + ChatColor.RED + "Ilosc musi byc liczba!");
                        return true;
                    }
                }

                if (target == null) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Musisz podac nick gracza, jesli uzywasz komendy z konsoli.");
                    return true;
                }

                ItemStack item = subCommand.equals("givekey") ? createOminousKey() : createDominanceToken();
                item.setAmount(amount);
                target.getInventory().addItem(item);
                
                String itemName = subCommand.equals("givekey") ? "Skazony Klucz" : "Token Dominacji";
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Dano " + amount + "x " + itemName + " dla " + target.getName());
                target.sendMessage(PREFIX + ChatColor.GOLD + "Otrzymales " + amount + "x " + itemName + " od administratora.");
                return true;
            }

            if (subCommand.equals("clear")) {
                if (args.length < 2) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Poprawne uzycie: /bd clear [gracz]");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Nie znaleziono takiego gracza.");
                    return true;
                }
                removeDebt(target);
                target.getPersistentDataContainer().remove(killerKey);
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Pomyslnie wyczyszczono dlug krwi dla " + target.getName());
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("bd") && sender.hasPermission("blooddebts.admin")) {
            if (args.length == 1) {
                return Arrays.asList("givekey", "givetoken", "clear", "getsb").stream()
                        .filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
            }
            if (args.length == 2 && !args[0].equalsIgnoreCase("getsb")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() == Material.VAULT && item.hasItemMeta() && item.getItemMeta().getDisplayName().contains("Skazony Skarbiec")) {
            Location loc = event.getBlockPlaced().getLocation();
            
            getConfig().set("corrupted_vault.world", loc.getWorld().getName());
            getConfig().set("corrupted_vault.x", loc.getBlockX());
            getConfig().set("corrupted_vault.y", loc.getBlockY());
            getConfig().set("corrupted_vault.z", loc.getBlockZ());
            saveConfig();
            
            event.getPlayer().sendMessage(PREFIX + ChatColor.DARK_RED + "Skazony Skarbiec zostal pomyslnie postawiony i aktywowany w tym miejscu!");
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null || killer.equals(victim)) return;

        String lastKillerUUIDStr = victim.getPersistentDataContainer().get(killerKey, PersistentDataType.STRING);
        if (lastKillerUUIDStr != null && lastKillerUUIDStr.equals(killer.getUniqueId().toString())) {
            removeDebt(victim);
            victim.sendMessage(PREFIX + ChatColor.GREEN + "Zemsciles sie! Twor Dlug Krwi zostal wymazany.");
            killer.sendMessage(PREFIX + ChatColor.GOLD + "Twoja ofiara dokonala zemsty. Straciles dominacje.");
            victim.getPersistentDataContainer().remove(killerKey);
            return;
        }

        int currentDebt = victim.getPersistentDataContainer().getOrDefault(debtKey, PersistentDataType.INTEGER, 0);
        if (currentDebt < 3) { 
            victim.getPersistentDataContainer().set(debtKey, PersistentDataType.INTEGER, currentDebt + 1);
            victim.getPersistentDataContainer().set(killerKey, PersistentDataType.STRING, killer.getUniqueId().toString());
            applyDebtDebuffs(victim);
            victim.sendMessage(PREFIX + ChatColor.DARK_RED + "Zostales naznaczony Krwawym Przekleństwem przez " + killer.getName() + "!");
        }

        killer.getInventory().addItem(createDominanceToken());
        killer.getInventory().addItem(createOminousKey());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        applyDebtDebuffs(event.getPlayer());
    }

    private void applyDebtDebuffs(Player player) {
        int debtLevel = player.getPersistentDataContainer().getOrDefault(debtKey, PersistentDataType.INTEGER, 0);
        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        
        if (maxHealth != null) {
            maxHealth.getModifiers().forEach(mod -> {
                if (mod.getKey().getKey().equals("blood_debt_hp")) maxHealth.removeModifier(mod);
            });
            if (debtLevel > 0) {
                AttributeModifier modifier = new AttributeModifier(
                        new NamespacedKey(this, "blood_debt_hp"),
                        -2.0 * debtLevel,
                        AttributeModifier.Operation.ADD_NUMBER
                );
                maxHealth.addModifier(modifier);
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            int debtLevel = attacker.getPersistentDataContainer().getOrDefault(debtKey, PersistentDataType.INTEGER, 0);
            if (debtLevel > 0) {
                event.setDamage(event.getDamage() * (1.0 - (0.05 * debtLevel)));
            }
        }
    }

    private void removeDebt(Player player) {
        player.getPersistentDataContainer().set(debtKey, PersistentDataType.INTEGER, 0);
        applyDebtDebuffs(player);
    }

    private ItemStack createCorruptedVaultItem() {
        ItemStack vault = new ItemStack(Material.VAULT, 1);
        ItemMeta meta = vault.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Skazony Skarbiec");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Postaw go w centralnym miejscu spawnu.", ChatColor.RED + "Bedzie otwierany Skazonymi Kluczami."));
            vault.setItemMeta(meta);
        }
        return vault;
    }

    public ItemStack createDominanceToken() {
        ItemStack token = new ItemStack(Material.GHAST_TEAR, 1);
        ItemMeta meta = token.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Token Dominacji");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Dowod triumfu nad innym graczem.", ChatColor.GOLD + "Wymien go u Wedrownego Handlarza Smiercia."));
            token.setItemMeta(meta);
        }
        return token;
    }

    public ItemStack createOminousKey() {
        ItemStack key = new ItemStack(Material.OMINOUS_TRIAL_KEY, 1);
        ItemMeta meta = key.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Skazony Klucz (Ominous Key)");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Mroczna energia poleglego.", ChatColor.DARK_RED + "Otwiera SKAZONY SKARBIEC na spawnie."));
            key.setItemMeta(meta);
        }
        return key;
    }

    @EventHandler
    public void onVaultInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.VAULT) return;
        
        Block block = event.getClickedBlock();
        Location loc = block.getLocation();
        
        if (!getConfig().contains("corrupted_vault.world")) return;
        
        String world = getConfig().getString("corrupted_vault.world");
        int x = getConfig().getInt("corrupted_vault.x");
        int y = getConfig().getInt("corrupted_vault.y");
        int z = getConfig().getInt("corrupted_vault.z");

        if (!loc.getWorld().getName().equals(world) || loc.getBlockX() != x || loc.getBlockY() != y || loc.getBlockZ() != z) {
            return; 
        }

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand.getType() == Material.OMINOUS_TRIAL_KEY && itemInHand.hasItemMeta() 
                && itemInHand.getItemMeta().getDisplayName().contains("Skazony Klucz")) {
            
            event.setCancelled(true);
            itemInHand.setAmount(itemInHand.getAmount() - 1);
            
            player.sendMessage(PREFIX + ChatColor.GREEN + "Otwierasz Skazony Skarbiec...");
            dropNexusLoot(loc.add(0, 1, 0));
        } else {
            event.setCancelled(true);
            player.sendMessage(PREFIX + ChatColor.RED + "Ten skarbiec jest skazony. Wymaga Skazonego Klucza!");
        }
    }

    private void dropNexusLoot(Location loc) {
        double chance = Math.random();
        ItemStack reward;

        if (chance < 0.10) reward = new ItemStack(Material.HEAVY_CORE);
        else if (chance < 0.35) reward = new ItemStack(Material.TOTEM_OF_UNDYING);
        else if (chance < 0.65) reward = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE);
        else reward = new ItemStack(Material.BREEZE_ROD, 4);

        loc.getWorld().dropItemNaturally(loc, reward);
    }
    
    public void openDeathMerchantGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Wedrowny Handlarz Smiercia");

        gui.setItem(0, createShopItem(Material.NETHERITE_INGOT, 2, "Sztabka Netheritu")); 
        gui.setItem(1, createShopItem(Material.DIAMOND, 1, "Diamenty x4", 4));
        gui.setItem(2, createShopItem(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 3, "Szablon Ulepszenia Netheritu"));
        gui.setItem(3, createShopItem(Material.ANCIENT_DEBRIS, 1, "Starozytne Odlamki x2", 2));
        gui.setItem(4, createShopItem(Material.EXPERIENCE_BOTTLE, 1, "Butelki z PD x32", 32));
        gui.setItem(5, createShopItem(Material.OBSIDIAN, 1, "Obsydian x32", 32));
        gui.setItem(6, createShopItem(Material.CRYING_OBSIDIAN, 1, "Placzacy Obsydian x8", 8));

        gui.setItem(9, createShopItem(Material.WIND_CHARGE, 1, "Wind Charges x16", 16));
        gui.setItem(10, createShopItem(Material.ENDER_PEARL, 1, "Perly Endu x8", 8));
        gui.setItem(11, createShopItem(Material.GOLDEN_APPLE, 1, "Zlote Jablko x4", 4));
        gui.setItem(12, createShopItem(Material.POTION, 2, "Mikstura Sily II"));
        gui.setItem(13, createShopItem(Material.ARROW, 1, "Strzaly x64", 64));
        gui.setItem(14, createShopItem(Material.BREEZE_ROD, 2, "Paleczki Breeze x3", 3));

        gui.setItem(18, createShopItem(Material.CROSSBOW, 2, "Zabojcza Kusza"));
        gui.setItem(19, createShopItem(Material.TNT, 1, "TNT x8", 8));
        gui.setItem(20, createShopItem(Material.ELYTRA, 8, "Skrzydla Elytra"));
        gui.setItem(21, createShopItem(Material.FIREWORK_ROCKET, 1, "Fajerwerki x16", 16));
        gui.setItem(22, createShopItem(Material.SHULKER_SHELL, 2, "Skorupa Shulkera x2", 2));

        player.openInventory(gui);
    }

    private ItemStack createShopItem(Material mat, int cost, String name, int amount) {
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            meta.setLore(Arrays.asList(ChatColor.RED + "Koszt: " + cost + " Token(ow) Dominacji"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createShopItem(Material mat, int cost, String name) {
        return createShopItem(mat, cost, name, 1);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(ChatColor.DARK_RED + "Wedrowny Handlarz Smiercia")) return;
        event.setCancelled(true);

        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        
        int cost = 0;
        if (clicked.getItemMeta().hasLore()) {
            for (String line : clicked.getItemMeta().getLore()) {
                if (line.contains("Koszt:")) {
                    cost = Integer.parseInt(line.replaceAll("[^0-9]", ""));
                    break;
                }
            }
        }

        if (cost > 0 && hasEnoughTokens(player, cost)) {
            removeTokens(player, cost);
            ItemStack reward = new ItemStack(clicked.getType(), clicked.getAmount());
            player.getInventory().addItem(reward);
            player.sendMessage(PREFIX + ChatColor.GREEN + "Zakupiono przedmiot!");
        } else if (cost > 0) {
            player.sendMessage(PREFIX + ChatColor.RED + "Nie masz wystarczajacej liczby Tokenow Dominacji!");
        }
    }

    private boolean hasEnoughTokens(Player player, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.GHAST_TEAR && item.hasItemMeta() 
                    && item.getItemMeta().getDisplayName().contains("Token Dominacji")) {
                count += item.getAmount();
            }
        }
        return count >= amount;
    }

    private void removeTokens(Player player, int amount) {
        int leftToRemove = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.GHAST_TEAR && item.hasItemMeta() 
                    && item.getItemMeta().getDisplayName().contains("Token Dominacji")) {
                if (item.getAmount() > leftToRemove) {
                    item.setAmount(item.getAmount() - leftToRemove);
                    break;
                } else {
                    leftToRemove -= item.getAmount();
                    player.getInventory().setItem(i, null);
                }
            }
            if (leftToRemove <= 0) break;
        }
    }
}
