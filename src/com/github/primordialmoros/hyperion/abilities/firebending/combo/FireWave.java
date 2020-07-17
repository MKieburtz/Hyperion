/*
 *   Copyright 2016, 2017, 2020 Moros <https://github.com/PrimordialMoros>
 *
 * 	  This file is part of Hyperion.
 *
 *    Hyperion is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    Hyperion is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with Hyperion.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.primordialmoros.hyperion.abilities.firebending.combo;

import com.github.primordialmoros.hyperion.Hyperion;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.AirAbility;
import com.projectkorra.projectkorra.ability.ComboAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.ability.util.ComboManager.AbilityInformation;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.firebending.WallOfFire;
import com.projectkorra.projectkorra.firebending.util.FireDamageTimer;
import com.projectkorra.projectkorra.util.ClickType;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.ParticleEffect;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class FireWave extends FireAbility implements AddonAbility, ComboAbility {
	private final Set<Block> blocks = new HashSet<>();
	private ListIterator<Block> waveIterator;
	private Location origin;
	private Vector direction;

	private double damage;
	private long cooldown;
	private long duration;
	private long moveRate;
	private int maxHeight;
	private int width;
	private int height;

	private int damageTick;
	private int moveTick;

	public FireWave(Player player) {
		super(player);

		if (!bPlayer.canBendIgnoreBinds(this)) {
			return;
		}

		damage = Hyperion.getPlugin().getConfig().getDouble("Abilities.Fire.FireCombo.FireWave.Damage");
		cooldown = Hyperion.getPlugin().getConfig().getLong("Abilities.Fire.FireCombo.FireWave.Cooldown");
		int range = Hyperion.getPlugin().getConfig().getInt("Abilities.Fire.FireCombo.FireWave.Range");
		duration = Hyperion.getPlugin().getConfig().getLong("Abilities.Fire.FireCombo.FireWave.Duration");
		moveRate = Hyperion.getPlugin().getConfig().getLong("Abilities.Fire.FireCombo.FireWave.MoveRate");
		maxHeight = Hyperion.getPlugin().getConfig().getInt("Abilities.Fire.FireCombo.FireWave.MaxHeight");
		width = Hyperion.getPlugin().getConfig().getInt("Abilities.Fire.FireCombo.FireWave.Width");

		damage = getDayFactor(damage, player.getWorld());
		height = (int) getDayFactor(2, player.getWorld());
		maxHeight = (int) getDayFactor(maxHeight, player.getWorld());
		width = (int) getDayFactor(width, player.getWorld());
		duration = (long) getDayFactor(duration, player.getWorld());

		origin = GeneralMethods.getTargetedLocation(player, 3);
		direction = player.getEyeLocation().getDirection().clone().setY(0).normalize();
		final BlockIterator tempBlockIterator = new BlockIterator(origin.getWorld(), origin.toVector(), direction, 0, range);
		final List<Block> blockList = new LinkedList<>();
		tempBlockIterator.forEachRemaining(blockList::add);
		waveIterator = blockList.listIterator();
		if (prepare(origin.getBlock())) {
			if (hasAbility(player, WallOfFire.class)) {
				getAbility(player, WallOfFire.class).remove();
			}
			bPlayer.addCooldown(this);
			start();
		}
	}

	@Override
	public void progress() {
		if (!bPlayer.canBendIgnoreBindsCooldowns(this) || bPlayer.getBoundAbilityName() == null || !bPlayer.getBoundAbilityName().equalsIgnoreCase("WallOfFire") || blocks.isEmpty() || !player.isSneaking()) {
			remove();
			return;
		}

		if (hasAbility(player, WallOfFire.class)) {
			getAbility(player, WallOfFire.class).remove();
		}

		final long time = System.currentTimeMillis();

		if (time > getStartTime() + duration) {
			remove();
			return;
		}

		if (time - getStartTime() > damageTick * 500) {
			damageTick++;
			checkDamage();
		}

		if (time - getStartTime() > moveTick * moveRate) {
			moveTick++;
			if (waveIterator.hasNext()) {
				Location tempLoc = waveIterator.next().getLocation();
				if (!prepare(tempLoc.getBlock())) {
					waveIterator.previous();
				}
			}
			visualiseWall();
		}
	}

	private void checkDamage() {
		double radius = Math.max(width, height);
		for (Entity entity : GeneralMethods.getEntitiesAroundPoint(origin, radius)) {
			if (entity instanceof LivingEntity && entity.getEntityId() != player.getEntityId() && !(entity instanceof ArmorStand)) {
				if (entity instanceof Player && Commands.invincible.contains(entity.getName())) {
					continue;
				}
				for (Block block : blocks) {
					if (entity.getLocation().distanceSquared(block.getLocation()) <= 1.5 * 1.5) {
						entity.setVelocity(new Vector(0, 0, 0));
						DamageHandler.damageEntity(entity, damage, this);
						entity.setFireTicks(30);
						new FireDamageTimer(entity, player);
						AirAbility.breakBreathbendingHold(entity);
						break;
					}
				}
			}
		}
	}

	private void initializeBlocks() {
		blocks.clear();
		final Vector orthoLR = GeneralMethods.getOrthogonalVector(direction, 0, 1).normalize();
		final Vector orthoUD = GeneralMethods.getOrthogonalVector(direction, 90, 1).normalize();

		if (height < maxHeight) height++;

		for (double i = -height; i <= height; i++) {
			for (double j = -width; j <= width; j++) {
				final Location loc = origin.clone().add(orthoUD.clone().multiply(j));
				loc.add(orthoLR.clone().multiply(i));
				if (GeneralMethods.isRegionProtectedFromBuild(this, loc)) {
					continue;
				}
				blocks.add(loc.getBlock());
			}
		}
	}

	private boolean prepare(Block block) {
		if (block.isLiquid() || GeneralMethods.isSolid(block)) {
			return false;
		}
		origin = block.getLocation();
		initializeBlocks();
		return !blocks.isEmpty();
	}

	private void visualiseWall() {
		for (Block block : blocks) {
			if (!isTransparent(block)) {
				continue;
			}
			playFirebendingParticles(block.getLocation(), 3, 0.3, 0.3, 0.3);
			ParticleEffect.SMOKE_NORMAL.display(block.getLocation(), 1, 0.3, 0.3, 0.3);
			if (ThreadLocalRandom.current().nextInt(10) == 0) {
				playFirebendingSound(block.getLocation());
			}
		}
	}

	@Override
	public boolean isEnabled() {
		return Hyperion.getPlugin().getConfig().getBoolean("Abilities.Fire.FireCombo.FireWave.Enabled");
	}

	@Override
	public String getName() {
		return "FireWave";
	}

	@Override
	public String getDescription() {
		return Hyperion.getPlugin().getConfig().getString("Abilities.Fire.FireCombo.FireWave.Description");
	}

	@Override
	public String getAuthor() {
		return Hyperion.getAuthor();
	}

	@Override
	public String getVersion() {
		return Hyperion.getVersion();
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
	}

	@Override
	public boolean isSneakAbility() {
		return true;
	}

	@Override
	public long getCooldown() {
		return cooldown;
	}

	@Override
	public Location getLocation() {
		return origin;
	}

	@Override
	public List<Location> getLocations() {
		return blocks.stream().map(Block::getLocation).collect(Collectors.toList());
	}

	@Override
	public Object createNewComboInstance(Player player) {
		return new FireWave(player);
	}

	@Override
	public ArrayList<AbilityInformation> getCombination() {
		ArrayList<AbilityInformation> combination = new ArrayList<>();
		combination.add(new AbilityInformation("HeatControl", ClickType.SHIFT_DOWN));
		combination.add(new AbilityInformation("HeatControl", ClickType.SHIFT_UP));
		combination.add(new AbilityInformation("HeatControl", ClickType.SHIFT_DOWN));
		combination.add(new AbilityInformation("HeatControl", ClickType.SHIFT_UP));
		combination.add(new AbilityInformation("WallOfFire", ClickType.SHIFT_DOWN));
		return combination;
	}

	@Override
	public String getInstructions() {
		return "HeatControl (Tap Sneak) > HeatControl (Tap Sneak) > WallOfFire (Hold Sneak)";
	}

	@Override
	public void load() {
	}

	@Override
	public void stop() {
	}
}
