package pl.silesia.blooddebts;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
        
        getServer().getPluginManager().registerEvents(this, this);
        
        if (getCommand("handlarz") != null) getCommand("handlarz").setExecutor(this);
        if (getCommand("bd") != null) {
            getCommand("bd").setExecutor(this);
            getCommand("bd").setTabCompleter(this);
        }
        
        getLogger().info("Plugin BloodDebts zostal pomyslnie wlaczony z komendami admina!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Obsługa komendy /handlarz
        if (command.getName().equalsIgnoreCase("handlarz")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Ta komenda jest tylko dla graczy!");
                return true;
            }
            Player player = (Player) sender;
            if (player.hasPermission("blooddebts.admin")) {
                openDeathMerchantGui(player);
            } else {
                player.sendMessage(PREFIX + ChatColor.RED + "Nie masz uprawnien do otwarcia tego sklepu komenda.");
            }
            return true;
        }

        // Obsługa głównej komendy /bd (BloodDebts Admin)
        if (command.getName().equalsIgnoreCase("bd")) {
            if (!sender.hasPermission("blooddebts.admin")) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Brak uprawnien (blooddebts.admin)!");
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage(ChatColor.GOLD + "=== System BloodDebts ===");
                sender.sendMessage(ChatColor.YELLOW + "/bd givekey [gracz] [ilosc]" + ChatColor.GRAY + " - Daje Skazony Klucz");
                sender.sendMessage(ChatColor.YELLOW + "/bd givetoken [gracz] [ilosc]" + ChatColor.GRAY + " - Daje Token Dominacji");
                sender.sendMessage(ChatColor.YELLOW + "/bd clear [gracz]" + ChatColor.GRAY + " - Czysci dlug gracza");
                return true;
            }

            String subCommand = args[0].toLowerCase();

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
                target.sendMessage(PREFIX + ChatColor.GREEN + "Twoj dlug zostal odgornie usuniety przez admina.");
                return true;
            }
        }
        return false;
    }

    // TabCompleter - Podpowiedzi komend w grze
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("bd") && sender.hasPermission("blooddebts.admin")) {
            if (args.length == 1) {
                return Arrays.asList("givekey", "givetoken", "clear").stream()
                        .filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
            }
            if (args.length == 2) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            if (args.length == 3 && (args[0].equalsIgnoreCase("givekey") || args[0].equalsIgnoreCase("givetoken"))) {
                return Arrays.asList("1", "5", "10", "64");
            }
        }
        return new ArrayList<>();
    }

    // --- DOPASOWANA DO 1.21 MECHANIKA PRZEKLEŃSTW ---

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null || killer.equals(victim)) return;

        String lastKillerUUIDStr = victim.getPersistentDataContainer().get(killerKey, PersistentDataType.STRING);
        if (lastKillerUUIDStr != null && lastKillerUUIDStr.equals(killer.getUniqueId().toString())) {
            removeDebt(victim);
            victim.sendMessage(PREFIX + ChatColor.GREEN + "Zemsciles sie! Twoj Dlug Krwi zostal wymazany, a sily powrocily.");
            killer.sendMessage(PREFIX + ChatColor.GOLD + "Twoja ofiara dokonala zemsty. Straciles dominacje nad nia.");
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
        killer.sendMessage(PREFIX + ChatColor.GOLD + "Pokonales gracza " + victim.getName() + ". Otrzymujesz Token Dominacji oraz Skazony Klucz!");
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
                if (mod.getKey().getKey().equals("blood_debt_hp")) {
                    maxHealth.removeModifier(mod);
                }
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
                double reduction = 1.0 - (0.05 * debtLevel);
                event.setDamage(event.getDamage() * reduction);
            }
        }
    }

    private void removeDebt(Player player) {
        player.getPersistentDataContainer().set(debtKey, PersistentDataType.INTEGER, 0);
        applyDebtDebuffs(player);
    }

    public ItemStack createDominanceToken() {
        ItemStack token = new ItemStack(Material.GHAST_TEAR, 1);
        ItemMeta meta = token.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Token Dominacji");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Dowod triumfu nad innym graczem.");
            lore.add(ChatColor.GOLD + "Wymien go u Wedrownego Handlarza Smiercia.");
            meta.setLore(lore);
            token.setItemMeta(meta);
        }
        return token;
    }

    public ItemStack createOminousKey() {
        ItemStack key = new ItemStack(Material.OMINOUS_TRIAL_KEY, 1);
        ItemMeta meta = key.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Skazony Klucz (Ominous Key)");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Przepełniony mroczna energia poleglego.");
            lore.add(ChatColor.DARK_RED + "Otwiera Nexus Vault na spawnie.");
            meta.setLore(lore);
            key.setItemMeta(meta);
        }
        return key;
    }

    // --- NEXUS VAULT & HANDLARZ INTERAKCJE ---

    @EventHandler
    public void onVaultInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        
        if (event.getClickedBlock().getType() == Material.VAULT) {
            Player player = event.getPlayer();
            ItemStack itemInHand = player.getInventory().getItemInMainHand();

            if (itemInHand.getType() == Material.OMINOUS_TRIAL_KEY && itemInHand.hasItemMeta() 
                    && itemInHand.getItemMeta().getDisplayName().contains("Skazony Klucz")) {
                
                event.setCancelled(true);
                itemInHand.setAmount(itemInHand.getAmount() - 1);
                
                player.sendMessage(PREFIX + ChatColor.GREEN + "Otwierasz Nexus Vault...");
                dropNexusLoot(event.getClickedBlock().getLocation().add(0, 1, 0));
            }
        }
    }

    private void dropNexusLoot(org.bukkit.Location loc) {
        double chance = Math.random();
        ItemStack reward;

        if (chance < 0.10) {
            reward = new ItemStack(Material.HEAVY_CORE);
        } else if (chance < 0.35) {
            reward = new ItemStack(Material.TOTEM_OF_UNDYING);
        } else if (chance < 0.65) {
            reward = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE);
        } else {
            reward = new ItemStack(Material.BREEZE_ROD, 4);
        }

        loc.getWorld().dropItemNaturally(loc, reward);
    }
    
    public void openDeathMerchantGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9, ChatColor.DARK_RED + "Wedrowny Handlarz Smiercia");

        gui.setItem(1, createShopItem(Material.NETHERITE_INGOT, 3, "Sztabka Netheritu"));
        gui.setItem(3, createShopItem(Material.WIND_CHARGE, 1, "Wind Charges x16", 16));
        gui.setItem(5, createShopItem(Material.GOLDEN_APPLE, 2, "Zlotowe Jablko x4", 4));
        gui.setItem(7, createShopItem(Material.OBSIDIAN, 1, "Obsydian x32", 32));

        player.openInventory(gui);
    }

    private ItemStack createShopItem(Material mat, int cost, String name, int amount) {
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.RED + "Koszt: " + cost + " Token(ow) Dominacji");
            meta.setLore(lore);
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
