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
import org.bukkit.Server;
import org.bukkit.entity.*;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event.Type;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.getspout.spoutapi.SpoutManager;
import org.getspout.spoutapi.event.spout.SpoutCraftEnableEvent;
import org.getspout.spoutapi.event.spout.SpoutListener;
import org.getspout.spoutapi.gui.GenericContainer;
import org.getspout.spoutapi.player.SpoutPlayer;

public class mmoTarget extends JavaPlugin {

	protected static Server server;
	protected static PluginManager pm;
	protected static PluginDescriptionFile description;
	protected static mmo mmo;
	private int updateTask;
	protected static final HashMap<Player, LivingEntity> targets = new HashMap<Player, LivingEntity>();
	protected static final HashMap<Player, GenericLivingEntity> bars = new HashMap<Player, GenericLivingEntity>();
	protected static final HashMap<Player, GenericContainer> containers = new HashMap<Player, GenericContainer>();

	@Override
	public void onEnable() {
		server = getServer();
		pm = server.getPluginManager();
		description = getDescription();

		mmo = mmo.create(this);
		mmo.mmoTarget = true;
		mmo.setPluginName("Target");
		mmo.cfg.getInt("max_range", 15);
		mmo.cfg.getString("ui.default.align", "TOP_CENTER");
		mmo.cfg.getInt("ui.default.left", 0);
		mmo.cfg.getInt("ui.default.top", 3);

		mmo.log("loading " + description.getFullName());

		mmo.cfg.getBoolean("auto_update", true);
		mmo.cfg.save();

		mmoTargetPlayerListener tpl = new mmoTargetPlayerListener();
		pm.registerEvent(Type.PLAYER_INTERACT_ENTITY, tpl, Priority.Monitor, this);
		pm.registerEvent(Type.PLAYER_MOVE, tpl, Priority.Monitor, this);

		mmoTargetEntityListener tel = new mmoTargetEntityListener();
		pm.registerEvent(Type.ENTITY_DEATH, tel, Priority.Monitor, this);
		pm.registerEvent(Type.ENTITY_DAMAGE, tel, Priority.Monitor, this);
		pm.registerEvent(Type.ENTITY_EXPLODE, tel, Priority.Monitor, this);
		pm.registerEvent(Type.PROJECTILE_HIT, tel, Priority.Monitor, this); // craftbukkit 1000

		mmoSpoutListener sl = new mmoSpoutListener();
		pm.registerEvent(Type.CUSTOM_EVENT, sl, Priority.Normal, this);

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
		LivingEntity target = targets.get(player);
		if (target != null) {
			int health = mmo.getHealth(target);
			if (!target.isDead()
					  && health > 0
					  && player.getWorld() == target.getWorld()
					  && player.getLocation().distance(target.getLocation()) <= mmo.cfg.getInt("max_range", 15)) {
				if (mmo.hasSpout && SpoutManager.getPlayer(player).isSpoutCraftEnabled()) {
					GenericLivingEntity bar = bars.get(player);
					if (bar == null) {
						bars.put(player, bar = new GenericLivingEntity());
						GenericContainer container = containers.get(player);
						container.addChild(bar);
					}
					bar.setEntity(target);
					if (target instanceof Player && targets.containsKey((Player) target)) {
						bar.setTargets(targets.get((Player) target));
					} else if (target instanceof Creature && ((Creature) target).getTarget() != null && !((Creature) target).getTarget().isDead()) {
						bar.setTargets(((Creature) target).getTarget());
					} else {
						bar.setTargets();
					}
				}
			} else {
				targets.remove(player);
				if (mmo.hasSpout && SpoutManager.getPlayer(player).isSpoutCraftEnabled()) {
					GenericLivingEntity bar = bars.remove(player);
					if (bar != null) {
						bar.getContainer().removeChild(bar);
					}
				}
			}
		}
	}

	private class mmoTargetPlayerListener extends PlayerListener {

		@Override
		public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
			Player player = event.getPlayer();
			Entity target = event.getRightClicked();
			if (target instanceof LivingEntity) {
				targets.put(player, (LivingEntity) target);
				update(player);
			}
		}

		@Override
		public void onPlayerMove(PlayerMoveEvent event) {
			Player player = event.getPlayer();
			update(player);
		}
	}

	public class mmoSpoutListener extends SpoutListener {

		@Override
		public void onSpoutCraftEnable(SpoutCraftEnableEvent event) {
			SpoutPlayer player = SpoutManager.getPlayer(event.getPlayer());
			GenericContainer container = mmo.getContainer();
			containers.put(player, container);
			player.getMainScreen().attachWidget(mmo.plugin, container);
		}
	}

	private class mmoTargetEntityListener extends EntityListener {

		@Override
		public void onEntityDeath(EntityDeathEvent event) {
			Entity target = event.getEntity();
			if (target instanceof LivingEntity && targets.containsValue((LivingEntity) target)) {
				for (Player player : ((HashMap<Player, LivingEntity>) targets.clone()).keySet()) {
					if (target.equals(targets.get(player))) {
						update(player);
					}
				}
			}
		}

		@Override
		public void onEntityExplode(EntityExplodeEvent event) {
			if (event.isCancelled()) {
				return;
			}
			Entity target = event.getEntity();
			if (target instanceof LivingEntity && targets.containsValue((LivingEntity) target)) {
				for (Player player : ((HashMap<Player, LivingEntity>) targets.clone()).keySet()) {
					if (target.equals(targets.get(player))) {
						update(player);
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
				}
			} else if (event.getCause() == DamageCause.PROJECTILE) {
				EntityDamageByEntityEvent e = (EntityDamageByEntityEvent) event;
				Projectile arrow = (Projectile) e.getDamager();
				if (arrow.getShooter() instanceof LivingEntity) {
					attacker = arrow.getShooter();
				}
			}
			if (defender instanceof Player && attacker != null && !targets.containsKey((Player) defender)) {
				targets.put((Player) defender, attacker);
				update((Player) defender);
			}
			if (attacker instanceof Player && defender != null) {
				targets.put((Player) attacker, defender);
				update((Player) attacker);
			}
		}
	}
}
