package net.saifs.neutron.item.gun;

import net.saifs.neutron.Neutron;
import net.saifs.neutron.helper.NMSHandler;
import net.saifs.neutron.item.Item;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GunItem extends Item implements Listener {
    private final Map<UUID, Long> lastFiredMap = new HashMap<>();

    protected Particle.DustOptions dustOptions;
    protected double speed;
    protected double damage;
    protected int ammo;
    protected double delay;
    protected boolean explosive = false;

    public GunItem(String id, ItemStack itemStack, Neutron plugin, Particle.DustOptions dustOptions, double speed, double damage, int ammo, double delay) {
        super(id, itemStack, plugin);
        this.dustOptions = dustOptions;
        this.speed = speed;
        this.damage = damage;
        this.ammo = ammo;
        this.delay = delay;
    }

    public GunItem(String id, ItemStack itemStack, Neutron plugin, Particle.DustOptions dustOptions, double speed, double damage, int ammo) {
        super(id, itemStack, plugin);
        this.dustOptions = dustOptions;
        this.speed = speed;
        this.damage = damage;
        this.ammo = ammo;
        this.delay = 2000;
    }

    @Override
    public ItemStack getItemStack() {
        return NMSHandler.writeInt(super.getItemStack(), "NEUTRON_GUN_AMMO", ammo);
    }

    private String getItemName(int ammo) {
        ItemMeta meta = this.itemStack.getItemMeta();
        return (meta != null ? meta.getDisplayName() : this.getID()) + (" <" + ammo + ">");
    }

    private ItemStack setAmmo(ItemStack itemStack, int ammo) {
        itemStack = NMSHandler.writeInt(itemStack, "NEUTRON_GUN_AMMO", ammo);
        String name = getItemName(ammo);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return itemStack;
        meta.setDisplayName(name);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getItem() == null || !isItem(e.getItem())) return;

        int ammo = NMSHandler.readInt(e.getItem(), "NEUTRON_GUN_AMMO");
        if (ammo <= 0) {
            e.getPlayer().sendMessage("§cYou do not have any ammo left!");
            return;
        }
        ammo--;

        UUID uuid = e.getPlayer().getUniqueId();
        long now = System.currentTimeMillis();
        if (lastFiredMap.containsKey(uuid)) {
            long lastFired = lastFiredMap.get(uuid);
            if (now < lastFired + delay) {
                int wait = (int) Math.round(((lastFired + delay) - now) / 1000);
                e.getPlayer().sendMessage("§cYou can't do that yet. Wait " + wait + " seconds.");
                return;
            }
        }
        lastFiredMap.put(uuid, now);

        ItemStack itemStack = setAmmo(e.getItem(), ammo);
        EquipmentSlot hand = e.getHand();
        if (hand == null) return;
        switch (hand) {
            case HAND:
                e.getPlayer().getInventory().setItemInMainHand(itemStack);
                break;
            case OFF_HAND:
                e.getPlayer().getInventory().setItemInOffHand(itemStack);
                break;
            default:
                return;
        }

        handlePlayerFire(e.getPlayer());
    }

    protected void handlePlayerFire(Player player) {
        Location bulletLocation = player.getLocation().add(new Vector(0, 1, 0));
        Vector vector = player.getLocation().getDirection().normalize().multiply(speed);

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                // move the bullet
                bulletLocation.add(vector);

                World world = bulletLocation.getWorld();
                if (world == null) {
                    cancel();
                    return;
                }

                renderBullet(bulletLocation);

                Collection<Entity> targets = bulletLocation.getWorld().getNearbyEntities(bulletLocation, 1, 1, 1);
                if (targets.size() > 0) {
                    for (Entity entity : targets) {
                        if (!(entity instanceof LivingEntity) || entity.equals(player)) continue;
                        LivingEntity target = (LivingEntity) entity;
                        target.damage(damage, player);
                        if (target instanceof Player) {
                            target.sendMessage("§7You've been hit by §a" + player.getName());
                        }
                        cancel();
                    }
                }

                if (bulletLocation.getBlock().getType() != Material.AIR) {
                    if (explosive)
                        world.createExplosion(bulletLocation, 10.0f);
                    cancel();
                }
            }
        };
        runnable.runTaskTimer(getPlugin(), 0L, 0L);
    }

    public void setExplosive(boolean explosive) {
        this.explosive = explosive;
    }

    protected void renderBullet(Location location) {
        World world = location.getWorld();
        if (world != null)
            world.spawnParticle(Particle.REDSTONE, location, 20, dustOptions);
    }
}