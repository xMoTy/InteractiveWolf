package me.moty.iw;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Scanner;
import java.util.UUID;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Consumer;
import org.bukkit.util.Vector;

import com.jeff_media.morepersistentdatatypes.DataType;

public class InteractiveWolf extends JavaPlugin implements Listener {

	private AttributeModifier speed = new AttributeModifier(UUID.randomUUID(), "movementSpeed", 0.1,
			Operation.ADD_NUMBER);
	private NamespacedKey throwedItem = new NamespacedKey(this, "throwedItem");
	private HashMap<Wolf, ArmorStand> activedWolves = new HashMap<>();

	@Override
	public void onEnable() {
		getLogger().info(ChatColor.DARK_GRAY + "");
		getLogger().info(ChatColor.GRAY + "InteractiveWolf" + ChatColor.WHITE + " Enabled");
		getLogger().info(ChatColor.WHITE + "Powered by xMoTy#3812 | Version. " + getDescription().getVersion());
		getLogger().info(ChatColor.DARK_GRAY + "");
		getVersion(version -> {
			if (!this.getDescription().getVersion().equalsIgnoreCase(version))
				Bukkit.getConsoleSender().sendMessage(ChatColor.GRAY + "There is a new update available: " + version);
		});
		new Metrics(this, 17688);

		getServer().getPluginManager().registerEvents(this, this);
	}

	@Override
	public void onDisable() {
		activedWolves.keySet().stream().forEach(wolf -> resetWolf(wolf));
	}

	public void getVersion(final Consumer<String> consumer) {
		Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
			try (InputStream inputStream = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + "107924")
					.openStream(); Scanner scanner = new Scanner(inputStream)) {
				if (scanner.hasNext())
					consumer.accept(scanner.next());
			} catch (IOException exception) {
				this.getLogger().info("Cannot look for updates: " + exception.getMessage());
			}
		});
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent e) {
		if (e.getAction() != Action.LEFT_CLICK_AIR)
			return;
		if (!e.hasItem())
			return;
		if (!e.getMaterial().equals(Material.BONE))
			return;
		if (!e.getPlayer().getItemOnCursor().getType().isAir())
			return;
		if (e.getPlayer().getNearbyEntities(5, 3, 5).stream()
				.noneMatch(en -> en instanceof Wolf && ((Wolf) en).getOwner() != null
						&& ((Wolf) en).getOwner().getUniqueId().equals(e.getPlayer().getUniqueId())
						&& !((Wolf) en).isSitting() && !activedWolves.containsKey(((Wolf) en))))
			return;
		e.setCancelled(true);
		ItemStack item = e.getItem().clone();
		item.setAmount(1);
		if (e.getPlayer().getGameMode() != GameMode.CREATIVE)
			e.getItem().setAmount(e.getItem().getAmount() - 1);
		throwTask(e.getPlayer(), item);
	}

	@EventHandler
	public void onEntityTeleport(EntityTeleportEvent e) {
		if (e.getEntityType() != EntityType.WOLF)
			return;
		if (!activedWolves.containsKey(e.getEntity()))
			return;
		e.setCancelled(true);
	}

	public void throwTask(Player p, ItemStack item) {
		p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1F, .5F);
		p.getWorld().spawn(p.getEyeLocation().add(p.getLocation().getDirection()), ArmorStand.class, (i) -> {
			i.setInvisible(true);
			i.setInvulnerable(true);
			i.setSmall(true);
			i.setBasePlate(false);
			Item itemEntity = p.getWorld().spawn(p.getEyeLocation().add(p.getLocation().getDirection()), Item.class,
					(a) -> {
						a.setItemStack(item);
						a.setVelocity(p.getLocation().getDirection().add(new Vector(0, .5, 0)));
					});
			itemEntity.addPassenger(i);
			p.getNearbyEntities(5, 3, 5).stream()
					.filter(en -> en instanceof Wolf && ((Wolf) en).getOwner().getUniqueId().equals(p.getUniqueId()))
					.map(en -> (Wolf) en).filter(w -> !w.isSitting() && !activedWolves.containsKey(w))
					.forEach(w -> retrieveTask(i, itemEntity, w));
		});
	}

	public void retrieveTask(ArmorStand i, Item item, Wolf wolf) {
		activedWolves.put(wolf, i);
		wolf.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).addModifier(speed);
		wolf.setTarget(i);
		this.getServer().getScheduler().runTaskTimer(this, (task) -> {
			if (item.isDead()) {
				resetWolf(wolf);
				task.cancel();
				return;
			}
			if (wolf.getLocation().distance(i.getLocation()) > 1.5)
				return;
			wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_ITEM_PICKUP, .5F, 1F);
			item.remove();
			task.cancel();
			wolf.getPersistentDataContainer().set(throwedItem, DataType.ITEM_STACK, item.getItemStack());
			returnTask(i, wolf);
		}, 20, 1);
		this.getServer().getScheduler().runTaskLater(this, () -> {
			if (activedWolves.containsKey(wolf))
				resetWolf(wolf);
		}, 20 * 60);
	}

	public void returnTask(ArmorStand i, Wolf wolf) {
		Player owner = (Player) wolf.getOwner();
		owner.addPassenger(i);
		this.getServer().getScheduler().runTaskTimer(this, (t) -> {
			if (wolf.getLocation().distance(owner.getLocation()) > 2.4)
				return;
			t.cancel();
			i.remove();
			wolf.setTarget(null);
			activedWolves.remove(wolf);
			wolf.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).removeModifier(speed);
			wolf.getWorld().spawn(wolf.getLocation(), Item.class, (it) -> {
				it.setItemStack(wolf.getPersistentDataContainer().get(throwedItem, DataType.ITEM_STACK));
				wolf.getPersistentDataContainer().remove(throwedItem);
				it.setVelocity(owner.getLocation().toVector().subtract(wolf.getLocation().toVector()).normalize()
						.multiply(.5).add(new Vector(0, .5, 0)));
			});
		}, 5, 3);
	}

	public void resetWolf(Wolf wolf) {
		if (wolf.getPersistentDataContainer().has(throwedItem, DataType.ITEM_STACK))
			wolf.getPersistentDataContainer().remove(throwedItem);
		wolf.setTarget(null);
		wolf.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).removeModifier(speed);
		ArmorStand ar = activedWolves.get(wolf);
		activedWolves.remove(wolf);
		if (ar != null && ar.isValid() && !activedWolves.containsValue(ar))
			ar.remove();
	}
}
