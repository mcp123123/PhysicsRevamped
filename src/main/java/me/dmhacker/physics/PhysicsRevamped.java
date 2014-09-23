package me.dmhacker.physics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class PhysicsRevamped extends JavaPlugin {
	private static PhysicsRevamped instance;
	private static int spreadPercent;
	
	@Override
	public void onEnable() {
		instance = this;
		
		getConfig().options().copyDefaults(true);
		saveConfig();
		spreadPercent = getConfig().getInt("spread_chance");
		this.getLogger().info("Chance trickling will spread to adjacent blocks: "+spreadPercent+"%");
		
		this.getServer().getPluginManager().registerEvents(new PhysicsListener(), this);
	}
	
	public void onDisable() {
		instance = null;
		// Avoid memory leaks by nullifying static instance
	}
	
	public class PhysicsListener implements Listener {
		
		public PhysicsListener() {
			new BukkitRunnable() {

				@SuppressWarnings("deprecation")
				@Override
				public void run() {
					for (World w : instance.getServer().getWorlds()) {
						for (Entity entity : w.getEntities()) {
							if (entity instanceof Damageable) {
								Damageable d = (Damageable) entity;
								List<FallingBlock> fbs = new ArrayList<FallingBlock>();
								List<Entity> entites = d.getNearbyEntities(1.0, 1.0, 1.0);
								for (Entity e : entites) {
									if (e.getType() == EntityType.FALLING_BLOCK) {
										fbs.add((FallingBlock) e);
									}
								}
								if (fbs.isEmpty() == false) {
									EntityDamageEvent event = new EntityDamageEvent(d, DamageCause.FALLING_BLOCK, 5.0);
									Bukkit.getServer().getPluginManager().callEvent(event);
									d.setLastDamageCause(event);
									if (event.isCancelled()) {
										return;
									}
									d.damage(5.0);
								}
							}
						}
					}
				}
				
			}.runTaskTimer(instance, 0, 5);
		}
		
		@SuppressWarnings("deprecation")
		@EventHandler
		public void onEntityExplode(EntityExplodeEvent e) {
			
			final List<BlockState> states = new ArrayList<BlockState>();
			List<Block> queue = new ArrayList<Block>();
			
			for (int i = 0; i < e.blockList().size() / 2; i++){
				Block temp = e.blockList().get(i);

				/*
				// Fake effects using ProtocolLib
				PacketContainer packet = new PacketContainer(PacketType.Play.Server.WORLD_PARTICLES);
				packet.getStrings().write(0, "lava");
				packet.getFloat().write(0, (float) temp.getLocation().getX()).write(1, (float) temp.getLocation().getY()).write(2, (float) temp.getLocation().getZ());
				packet.getFloat().write(3, (float) (random.nextInt(3) - 1)).write(4,  (float) 1).write(5, (float) (random.nextInt(3) - 1));
				packet.getIntegers().write(0, (int) (Math.random() * 10));
				Halft.sendPacket(temp.getLocation(), packet);
				
				PacketContainer packet2 = new PacketContainer(PacketType.Play.Server.WORLD_PARTICLES);
				packet2.getStrings().write(0, "flame");
				packet2.getFloat().write(0, (float) temp.getLocation().getX()).write(1, (float) temp.getLocation().getY()).write(2, (float) temp.getLocation().getZ());
				packet2.getFloat().write(3, (float) (random.nextInt(3) - 1)).write(4,  (float) 1).write(5, (float) (random.nextInt(3) - 1));
				packet2.getIntegers().write(0, (int) (Math.random() * 50));
				Halft.sendPacket(temp.getLocation(), packet2);
				 */
				
				if (temp.getType() != Material.TNT && temp.getType() != Material.AIR){
					queue.add(temp);
				}
			}
			for (Block b : queue) {
				FallingBlock fb = b.getWorld().spawnFallingBlock(b.getLocation(), b.getType(), b.getData());
				fb.setVelocity(new Vector(Math.random() - Math.random(), 0.4, Math.random() - Math.random()));
				trickleBlock(b);
			}
		
			for (Block b : e.blockList()) {
				if (b.getType() != Material.AIR) {
					if (!states.contains(b.getState())) 
						states.add(b.getState());
				}
				b.setType(Material.AIR);
			}
			
			e.setYield(0f);
			
			queue.clear();
			e.blockList().clear();
		}
		
		public void trickleBlock(final Block block) {
			final Random random = new Random();
			final Block above = block.getRelative(BlockFace.UP);
			if (above.getType() == Material.AIR || above == null || above.isEmpty()){
				return;
			}
			instance.getServer().getScheduler().runTaskLater(instance, new Runnable(){

				@SuppressWarnings("deprecation")
				@Override
				public void run() {
					above.getWorld().playEffect(above.getLocation(), Effect.STEP_SOUND, above.getTypeId());
					above.getWorld().playEffect(above.getLocation(), Effect.SMOKE, 4);
					FallingBlock fb = above.getWorld().spawnFallingBlock(above.getLocation().add(random.nextInt(3) - 1, 0, random.nextInt(3) - 1), above.getType(), above.getData());
					fb.setVelocity(new Vector(0, 0.1, 0));
					above.setType(Material.AIR);
					addMultiplierEffect(block);
					trickleBlock(above);
				}
					
			}, 2);
		}
		
		public void trickleBlocks(List<Block> blockList) {
			for (Block b : blockList){
				trickleBlock(b);
			}
		}
		
		private void addMultiplierEffect(Block block){
			List<Integer> numbers = new ArrayList<Integer>();
			for (int chance = spreadPercent; chance > 0; chance -= 1){
				numbers.add(chance);
			}
			Random random = new Random();
			int willDo = random.nextInt(100);
			for (int number: numbers){
				if (willDo == number){
					List<Block> blocks = new ArrayList<Block>();
					Block adjOne = block.getRelative(BlockFace.NORTH);
					Block adjTwo = block.getRelative(BlockFace.WEST);
					Block adjThree = block.getRelative(BlockFace.EAST);
					Block adjFour = block.getRelative(BlockFace.SOUTH);
					blocks.add(adjOne);
					blocks.add(adjTwo);
					blocks.add(adjThree);
					blocks.add(adjFour);
					trickleBlocks(blocks);
				}
			}
		}
		
		/*
		@EventHandler(priority = EventPriority.HIGH)
		public void onEntityChangeEvent(EntityChangeBlockEvent event) {
			Entity fell = event.getEntity();
			Location l = fell.getLocation();
			if (fell.getType() == EntityType.FALLING_BLOCK) {
				l.getWorld().playEffect(event.getBlock().getLocation(), Effect.STEP_SOUND, event.getTo().getId());
				event.setCancelled(true);
				fell.remove();
			}
		}
		*/
	}
}
