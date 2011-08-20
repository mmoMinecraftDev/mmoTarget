/*
 * This file is part of mmoMinecraft (http://code.google.com/p/mmo-minecraft/).
 * 
 * mmoMinecraft is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package mmo.Target;

import java.util.HashMap;
import mmo.Core.GenericLivingEntity;
import mmo.Core.mmo;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.entity.*;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event.Type;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.getspout.spoutapi.SpoutManager;
import org.getspout.spoutapi.gui.WidgetAnchor;

public class mmoTarget extends JavaPlugin {

	protected static Server server;
	protected static PluginManager pm;
	protected static PluginDescriptionFile description;
	protected static mmo mmo;
	private int updateTask;
	protected static final HashMap<Player, LivingEntity> targets = new HashMap<Player, LivingEntity>();
	protected static final HashMap<Player, GenericLivingEntity> bars = new HashMap<Player, GenericLivingEntity>();

	@Override
	public void onEnable() {
		server = getServer();
		pm = server.getPluginManager();
		description = getDescription();

		mmo = mmo.create(this);
		mmo.mmoTarget = true;
		mmo.setPluginName("Target");
		mmo.setX(mmo.cfg.getInt("ui.default.left", 100));
		mmo.setY(mmo.cfg.getInt("ui.default.top", 3));

		mmo.log("loading " + description.getFullName());

		mmo.cfg.getBoolean("auto_update", true);
		mmo.cfg.save();

		mmoTargetPlayerListener tpl = new mmoTargetPlayerListener();
		pm.registerEvent(Type.PLAYER_INTERACT_ENTITY, tpl, Priority.Monitor, this);
		pm.registerEvent(Type.PLAYER_MOVE, tpl, Priority.Monitor, this);

		mmoTargetEntityListener tel = new mmoTargetEntityListener();
		pm.registerEvent(Type.ENTITY_DEATH, tel, Priority.Monitor, this);
		pm.registerEvent(Type.ENTITY_DAMAGE, tel, Priority.Monitor, this);
		pm.registerEvent(Type.PROJECTILE_HIT, tel, Priority.Monitor, this); // craftbukkit 1000

		updateTask = server.getScheduler().scheduleSyncRepeatingTask(this,
			new Runnable() {

				@Override
				public void run() {
					mmoTarget.updateAll();
				}
			}, 20, 20);
	}

	@Override
	public void onDisable() {
		server.getScheduler().cancelTask(updateTask);
		targets.clear();
		mmo.log("Disabled " + description.getFullName());
		mmo.autoUpdate();
		mmo.mmoTarget = false;
	}

	public static void updateAll() {
		for (Player player : ((HashMap<Player, LivingEntity>) targets.clone()).keySet()) {
			update(player);
		}
	}

	public static void update(Player player) {
		int health;

		LivingEntity target = targets.get(player);
		health = mmo.getHealth(target);
		if (health > 0) {
			GenericLivingEntity bar = bars.get(player);
			if (bar == null) {
				bars.put(player, bar = new GenericLivingEntity());
				bar.setAnchor(WidgetAnchor.TOP_CENTER).setX(-bar.getWidth() / 2);
				SpoutManager.getPlayer(player).getMainScreen().attachWidget(mmoTarget.mmo.plugin, bar);
			}
			bar.setHealth(health);
			bar.setArmor(Math.max(0, mmo.getArmor(target)));
			String name = mmo.getName(player, target);
			if (target instanceof Player) {
				if (targets.containsKey((Player) target)) {
					name += "\n" + ChatColor.GRAY + "(" + mmo.getName(player, targets.get((Player) target)) + ChatColor.GRAY + ")";
				}
			} else if (target instanceof Creature) {
				if (((Creature) target).getTarget() != null) {
					name += "\n" + ChatColor.GRAY + "(" + mmo.getName(player, ((Creature) target).getTarget()) + ChatColor.GRAY + ")";
				}
			}
			bar.setLabel(name, "");
		} else {
			targets.remove(player);
			GenericLivingEntity bar = bars.remove(player);
			if (bar != null && bar.getScreen() != null) {
				bar.getScreen().removeWidget(bar);
			}
		}
	}

	/**
	 * Player listener
	 */
	private class mmoTargetPlayerListener extends PlayerListener {

		@Override
		public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
			Player player = event.getPlayer();
			Entity target = event.getRightClicked();
			if (target instanceof LivingEntity) {
				mmoTarget.targets.put(player, (LivingEntity) target);
				mmoTarget.update(player);
			}
		}

		@Override
		public void onPlayerMove(PlayerMoveEvent event) {
			Player player = event.getPlayer();
			Entity target = mmoTarget.targets.get(player);
			if (target != null && player.getLocation().distance(target.getLocation()) > 15) {
				mmoTarget.targets.remove(player);
				GenericLivingEntity bar = mmoTarget.bars.remove(player);
				bar.getScreen().removeWidget(bar);
			}
		}
	}

	/**
	 * Entity Listener
	 */
	private class mmoTargetEntityListener extends EntityListener {

		@Override
		public void onEntityDeath(EntityDeathEvent event) {
			Entity target = event.getEntity();
			if (target instanceof LivingEntity && mmoTarget.targets.containsValue((LivingEntity) target)) {
				for (Player player : ((HashMap<Player, LivingEntity>) mmoTarget.targets.clone()).keySet()) {
					if (target.equals(mmoTarget.targets.get(player))) {
						mmoTarget.update(player);
					}
				}
			}
		}

		@Override
		public void onEntityDamage(EntityDamageEvent event) {
			if (event.isCancelled()) {
				return;
			}
			LivingEntity attacker = null, defender = null;
			if (event.getEntity() instanceof LivingEntity) {
				defender = (LivingEntity) event.getEntity();
			} else if (event.getEntity() instanceof Tameable) {
				Tameable pet = (Tameable) event.getEntity();
				if (pet.isTamed() && pet.getOwner() instanceof Player) {
					defender = (Player) pet.getOwner();
				}
			}
			if (event.getCause() == DamageCause.ENTITY_ATTACK) {
				EntityDamageByEntityEvent e = (EntityDamageByEntityEvent) event;
				if (e.getDamager() instanceof LivingEntity) {
					attacker = (LivingEntity) e.getDamager();
				} else if (e.getDamager() instanceof Tameable) {
					Tameable pet = (Tameable) e.getDamager();
					if (pet.isTamed() && pet.getOwner() instanceof Player) {
						defender = (Player) pet.getOwner();
					}
				} else if (event.getCause() == DamageCause.PROJECTILE) {
					Projectile arrow = (Projectile) e.getDamager();
					if (arrow.getShooter() instanceof LivingEntity) {
						attacker = arrow.getShooter();
					}
				}
				if (defender instanceof Player && attacker != null && !mmoTarget.targets.containsKey((Player) defender)) {
					mmoTarget.targets.put((Player) defender, attacker);
					mmoTarget.update((Player) defender);
				}
				if (attacker instanceof Player && defender != null) {
					mmoTarget.targets.put((Player) attacker, defender);
					mmoTarget.update((Player) attacker);
				}
			}
		}
	}
}
