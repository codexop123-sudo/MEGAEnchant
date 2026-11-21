package me.megaenchant;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class MegaEnchant extends JavaPlugin implements TabCompleter {

    private static final int MAX_LEVEL = 255;

    @Override
    public void onEnable() {
        if (getCommand("enchant") != null) {
            getCommand("enchant").setTabCompleter(this);
        }
        getLogger().info("MegaEnchant enabled. /enchant now allows levels up to " + MAX_LEVEL + ".");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /enchant <target|@s|@p|@a|@r> <enchantment> [level 1-" + MAX_LEVEL + "]",
                    NamedTextColor.YELLOW));
            return true;
        }

        List<Player> targets = resolveTargets(sender, args[0]);
        if (targets.isEmpty()) {
            sender.sendMessage(Component.text("No matching players found.", NamedTextColor.RED));
            return true;
        }

        Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(args[1].toLowerCase()));
        if (enchantment == null) {
            sender.sendMessage(Component.text("Unknown enchantment: " + args[1], NamedTextColor.RED));
            return true;
        }

        int requestedLevel = 1;
        if (args.length >= 3) {
            try {
                requestedLevel = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Level must be a number.", NamedTextColor.RED));
                return true;
            }
        }
        if (requestedLevel < 1) {
            sender.sendMessage(Component.text("Level must be 1 or higher.", NamedTextColor.RED));
            return true;
        }

        // Vanilla's own max level tells us whether this enchant actually scales with level
        // (Sharpness, Protection, Efficiency, ...) or is a binary "has it or doesn't" enchant
        // (Mending, Silk Touch, Infinity, Flame, Multishot, Channeling, Aqua Affinity, the curses).
        boolean binary = enchantment.getMaxLevel() <= 1;
        int effectiveLevel;
        boolean levelWasCapped = false;

        if (binary) {
            effectiveLevel = 1;
            levelWasCapped = requestedLevel != 1;
        } else {
            if (requestedLevel > MAX_LEVEL) {
                sender.sendMessage(Component.text(
                        "Level must be between 1 and " + MAX_LEVEL + ".", NamedTextColor.RED));
                return true;
            }
            effectiveLevel = requestedLevel;
        }

        int applied = 0;
        for (Player target : targets) {
            if (applyEnchant(target, enchantment, effectiveLevel)) {
                applied++;
            }
        }

        if (applied == 0) {
            sender.sendMessage(Component.text(
                    "No items were enchanted (target(s) had nothing in hand).", NamedTextColor.RED));
        } else {
            sender.sendMessage(Component.text(
                    "Applied " + enchantment.getKey().getKey() + " " + effectiveLevel + " to " + applied
                            + " item(s).", NamedTextColor.GREEN));
            if (levelWasCapped) {
                sender.sendMessage(Component.text(
                        enchantment.getKey().getKey() + " has no levels in vanilla Minecraft, so it was "
                                + "applied at level 1 instead of " + requestedLevel + ".",
                        NamedTextColor.YELLOW));
            }
        }
        return true;
    }

    private boolean applyEnchant(Player target, Enchantment enchantment, int level) {
        ItemStack item = target.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            target.sendMessage(Component.text("You have nothing in your hand to enchant.", NamedTextColor.RED));
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta bookMeta) {
            bookMeta.addStoredEnchant(enchantment, level, true);
        } else {
            meta.addEnchant(enchantment, level, true);
        }
        item.setItemMeta(meta);

        target.sendMessage(Component.text(
                "Your item was enchanted with " + enchantment.getKey().getKey() + " " + level + ".",
                NamedTextColor.AQUA));
        return true;
    }

    private List<Player> resolveTargets(CommandSender sender, String selector) {
        List<Player> result = new ArrayList<>();

        switch (selector) {
            case "@a" -> result.addAll(Bukkit.getOnlinePlayers());
            case "@r" -> {
                List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
                if (!online.isEmpty()) {
                    result.add(online.get(ThreadLocalRandom.current().nextInt(online.size())));
                }
            }
            case "@s" -> {
                if (sender instanceof Player p) {
                    result.add(p);
                } else {
                    sender.sendMessage(Component.text(
                            "Console can't use @s, specify a player name instead.", NamedTextColor.RED));
                }
            }
            case "@p" -> {
                if (sender instanceof Player p) {
                    Player nearest = null;
                    double best = Double.MAX_VALUE;
                    for (Player other : p.getWorld().getPlayers()) {
                        if (other.equals(p)) {
                            continue;
                        }
                        double d = other.getLocation().distanceSquared(p.getLocation());
                        if (d < best) {
                            best = d;
                            nearest = other;
                        }
                    }
                    if (nearest != null) {
                        result.add(nearest);
                    }
                } else {
                    sender.sendMessage(Component.text(
                            "Console can't use @p, specify a player name instead.", NamedTextColor.RED));
                }
            }
            default -> {
                Player named = Bukkit.getPlayerExact(selector);
                if (named != null) {
                    result.add(named);
                }
            }
        }
        return result;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();

        if (args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                options.add(p.getName());
            }
            options.add("@s");
            options.add("@p");
            options.add("@a");
            options.add("@r");
        } else if (args.length == 2) {
            for (Enchantment ench : Registry.ENCHANTMENT) {
                options.add(ench.getKey().getKey());
            }
        } else if (args.length == 3) {
            options.add("1");
            options.add("5");
            options.add("10");
            options.add("50");
            options.add("100");
            options.add("255");
        }

        String current = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
        List<String> filtered = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(current)) {
                filtered.add(option);
            }
        }
        return filtered;
    }
}
