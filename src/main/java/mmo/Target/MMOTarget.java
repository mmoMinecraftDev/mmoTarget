/*
 * This file is part of mmoMinecraft (https://github.com/mmoMinecraftDev).
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
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package mmo.Target;

import java.util.HashMap;

import mmo.Core.MMO;
import mmo.Core.MMOPlugin;
import mmo.Core.gui.GenericLivingEntity;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import org.getspout.spoutapi.SpoutManager;
import org.getspout.spoutapi.event.spout.SpoutCraftEnableEvent;
import org.getspout.spoutapi.gui.Container;
import org.getspout.spoutapi.gui.ContainerType;
import org.getspout.spoutapi.player.SpoutPlayer;

public class MMOTarget extends MMOPlugin {
	protected static final HashMap<Player, perPlayer> data = new HashMap<Player, perPlayer>();
	/**
	 * Config values
	 */
	String config_ui_align = "TOP_CENTER";
	int config_ui_left = 0;
	int config_ui_top = 3;
	static int config_ui_maxwidth = 160;
	static int config_max_range = 15;

	@Override
	public void onEnable() {
		super.onEnable();
		pm.registerEvents(new mmoTargetListener(), this);
	}

	@Override
	public void loadConfiguration(FileConfiguration cfg) {
		config_ui_align = cfg.getString("ui.default.align", config_ui_align);
		config_ui_left = cfg.getInt("ui.default.left", config_ui_left);
		config_ui_top = cfg.getInt("ui.default.top", config_ui_top);
		config_ui_maxwidth = cfg.getInt("ui.default.width", config_ui_maxwidth);
		config_max_range = cfg.getInt("max_range", config_max_range);
	}

	public final class perPlayer extends GenericLivingEntity {
		protected final SpoutPlayer player;
		protected LivingEntity target = null;
		protected LivingEntity target2 = null;

		public perPlayer(SpoutPlayer player) {
			this(player, 80);
		}
		
		public perPlayer(SpoutPlayer player, int width) {
			super(width);
			this.player = player;
			setEntity((LivingEntity) null);
			setVisible(false);
		}

		public void setTarget(LivingEntity target) {
			if (target != this.target) {
				this.target = target;
				setEntity(target);
				setVisible(target != null);
			}
		}

		public LivingEntity getTarget() {
			return target;
		}

		@Override
		public void onTick() {
			if (target != null) {
				int health = MMO.getHealth(target);
				if (!target.isDead()
						&& health > 0
						&& player.getWorld() == target.getWorld()
						&& player.getLocation().distance(target.getLocation()) <= config_max_range) {
					if (target instanceof Player && data.containsKey((Player) target)) {
						setTargets(data.get((Player) target).target);
					} else if (target instanceof Creature && ((Creature) target).getTarget() != null && !((Creature) target).getTarget().isDead()) {
						setTargets(((Creature) target).getTarget());
					} else {
						setTargets();
					}
				} else {
					setTargets();
					setTarget(null);
				}
			}
			super.onTick();
		}
	}

	private class mmoTargetListener implements Listener {
		@SuppressWarnings("unused")
		@EventHandler
		public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
			Entity target = event.getRightClicked();
			perPlayer per = data.get(event.getPlayer());
			if (target instanceof LivingEntity && per != null) {
				per.setTarget((LivingEntity) target);
			}
		}

		@SuppressWarnings("unused")
		@EventHandler
		public void onEntityDamage(EntityDamageEvent event) {
			if (event.isCancelled()) {
				return;
			}
			LivingEntity attacker = null;
			Entity defender = event.getEntity();
			if (event.getCause() == DamageCause.ENTITY_ATTACK) {
				EntityDamageByEntityEvent e = (EntityDamageByEntityEvent) event;
				if (e.getDamager() instanceof LivingEntity) {
					attacker = (LivingEntity) e.getDamager();
				}
			} else if (event.getCause() == DamageCause.PROJECTILE) {
				EntityDamageByEntityEvent e = (EntityDamageByEntityEvent) event;
				Projectile arrow = (Projectile) e.getDamager();
				if (arrow.getShooter() instanceof LivingEntity) {
					attacker = arrow.getShooter();
				}
			}
			if (attacker instanceof Player && defender instanceof LivingEntity && !attacker.equals(defender)) {
				perPlayer per = data.get(attacker);
				if (per != null) {
					per.setTarget((LivingEntity) defender);
				}
			}
		}

		@SuppressWarnings("unused")
		@EventHandler
		public void onSpoutcraftEnable(SpoutCraftEnableEvent event) {
			SpoutPlayer player = SpoutManager.getPlayer(event.getPlayer());
			Container container = getContainer(player, config_ui_align, config_ui_left, config_ui_top);
			perPlayer bar = new perPlayer(player,config_ui_maxwidth);
			container.addChild(bar).setLayout(ContainerType.VERTICAL);
			container.setVisible(false);
			data.put(player, bar);
		}

		@SuppressWarnings("unused")
		@EventHandler
		public void onPlayerQuit(PlayerQuitEvent event) {
			data.remove(event.getPlayer());
		}
	}
}
