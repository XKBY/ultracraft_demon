package absolutelyaya.ultracraft.mixin;

import absolutelyaya.ultracraft.Ultracraft;
import absolutelyaya.ultracraft.block.mapping.CheckpointBlockEntity;
import absolutelyaya.ultracraft.components.UltraComponents;
import absolutelyaya.ultracraft.accessor.EntityAccessor;
import absolutelyaya.ultracraft.accessor.LivingEntityAccessor;
import absolutelyaya.ultracraft.accessor.WingedPlayerEntity;
import absolutelyaya.ultracraft.block.TerminalBlockEntity;
import absolutelyaya.ultracraft.components.player.*;
import absolutelyaya.ultracraft.config.HivelConfig;
import absolutelyaya.ultracraft.config.ServerConfig;
import absolutelyaya.ultracraft.damage.DamageSources;
import absolutelyaya.ultracraft.damage.DamageTypeTags;
import absolutelyaya.ultracraft.dimension.LevelManager;
import absolutelyaya.ultracraft.entity.other.BackTank;
import absolutelyaya.ultracraft.item.IOverrideMeleeDamageType;
import absolutelyaya.ultracraft.item.ISelectionAwareItem;
import absolutelyaya.ultracraft.registry.*;
import com.chocohead.mm.api.ClassTinkerers;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity implements WingedPlayerEntity
{
	@Shadow public abstract boolean isCreative();
	
	@Shadow @Final @Mutable private static Map<EntityPose, EntityDimensions> POSE_DIMENSIONS;
	
	@Shadow @Final private PlayerAbilities abilities;
	
	@Shadow public abstract void playSound(SoundEvent sound, float volume, float pitch);
	
	@Shadow public abstract void playSound(SoundEvent event, SoundCategory category, float volume, float pitch);
	
	@Shadow public abstract boolean isSpectator();
	
	@Shadow public abstract void incrementStat(Identifier stat);
	
	@Shadow protected abstract Vec3d adjustMovementForSneaking(Vec3d movement, MovementType type);
	
	@Shadow public abstract void disableShield(boolean sprinting);
	
	@Shadow public abstract void sendMessage(Text message, boolean overlay);
	
	@Shadow @Final private PlayerInventory inventory;
	Multimap<EntityAttribute, EntityAttributeModifier> curSpeedMod;
	BackTank backtank;
	int parryIFrames, damageTypeChain;
	long lastDamageAge = 0;
	DamageType lastDamageType;
	Item lastHeldItem;
	
	private final Vec3d[] curWingPose = new Vec3d[] {new Vec3d(0.0f, 0.0f, 0.0f), new Vec3d(0.0f, 0.0f, 0.0f), new Vec3d(0.0f, 0.0f, 0.0f), new Vec3d(0.0f, 0.0f, 0.0f), new Vec3d(0.0f, 0.0f, 0.0f), new Vec3d(0.0f, 0.0f, 0.0f), new Vec3d(0.0f, 0.0f, 0.0f), new Vec3d(0.0f, 0.0f, 0.0f)};
	
	
	protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world)
	{
		super(entityType, world);
	}
	
	@Inject(method = "<init>", at = @At("TAIL"))
	private void onInit(CallbackInfo ci)
	{
		POSE_DIMENSIONS = new HashMap<>(POSE_DIMENSIONS);
		POSE_DIMENSIONS.put(ClassTinkerers.getEnum(EntityPose.class, "SLIDE"), EntityDimensions.changing(0.6f, 1f));
		((EntityAccessor)this).setTargettableSupplier(() -> !isCreative() && !isSpectator());
	}
	
	@WrapOperation(method = "updatePose", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;setPose(Lnet/minecraft/entity/EntityPose;)V"))
	void onUpdatePose(PlayerEntity instance, EntityPose entityPose, Operation<Void> original)
	{
		WingedPlayerEntity winged = ((WingedPlayerEntity)instance);
		boolean hiVelMode = UltraComponents.WING_DATA.get(winged).isActive();
		if(hiVelMode)
		{
			IHivelComponent hivel = UltraComponents.HIVEL.get(winged);
			if(hivel.isDashing())
				setPose(ClassTinkerers.getEnum(EntityPose.class, "DASH"));
			else if(hivel.isSliding())
				setPose(ClassTinkerers.getEnum(EntityPose.class, "SLIDE"));
			else
				original.call(instance, entityPose);
		}
		else
			original.call(instance, entityPose);
	}
	
	@ModifyReturnValue(method = "getActiveEyeHeight", at = @At("RETURN"))
	float onGetActiveEyeHeight(float original, @Local EntityPose pose)
	{
		if(pose.equals(ClassTinkerers.getEnum(EntityPose.class, "SLIDE")))
			return 0.4f;
		else if(pose.equals(ClassTinkerers.getEnum(EntityPose.class, "DASH")))
			return 1.27f;
		return original;
	}
	
	@ModifyReturnValue(method = "isInvulnerableTo", at = @At("RETURN"))
	boolean onIsInvulnerableTo(boolean original, @Local DamageSource source)
	{
		if(UltraComponents.HIVEL.get(this).isDashing() && !source.isIn(DamageTypeTags.UNDODGEABLE))
			return true;
		if(isWingsActive() && source.isOf(DamageTypes.FALL) &&
				   (!HivelConfig.INSTANCE.fallDamage.getValue() || getSteppingBlockState().getBlock() instanceof FluidBlock))
			return true;
		return original;
	}
	
	@Inject(method = "damage", at = @At("RETURN"))
	void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir)
	{
		if(cir.getReturnValue() && amount > 0)
		{
			if(getHealth() <= 0)
				UltraComponents.STYLE.get(this).resetScore();
			else
				UltraComponents.STYLE.get(this).takeDamage(amount);
			UltraComponents.LEVEL_STATS.get(this).onDamage();
		}
		if(source.isOf(DamageSources.KNUCKLE_BLAST))
			disableShield(true);
	}
	
	@Inject(method = "damage", at = @At("TAIL"))
	void afterDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir)
	{
		if(!isWingsActive() || source.isIn(DamageTypeTags.IS_PER_TICK) || source.isOf(DamageTypes.OUT_OF_WORLD) || Ultracraft.isLikelyPerTickDamageType(source.getType()))
		{
			UltraComponents.WINGED.get(this).setBloodHealCooldown(4);
			return;
		}
		if(source.isOf(DamageSources.GUN) || source.isOf(DamageSources.SHOTGUN))
			timeUntilRegen = 9;
		else
			timeUntilRegen = 11 + HivelConfig.INSTANCE.iFrames.getValue();
		
		if(!source.isOf(DamageSources.NAIL) && lastDamageType != null && lastDamageType.equals(source.getType()))
		{
			if(age - lastDamageAge < 2)
				damageTypeChain++;
			else if(damageTypeChain > 0)
				damageTypeChain = 0;
			if(damageTypeChain > 5)
				Ultracraft.addLikelyPerTickDamageType(source.getType());
		}
		else if(damageTypeChain > 0)
			damageTypeChain = 0;
		lastDamageAge = age;
		lastDamageType = source.getType();
	}
	
	@ModifyReturnValue(method="findRespawnPosition", at = @At("RETURN"))
	private static Optional<Vec3d> onFindRespawnPosition(Optional<Vec3d> original, @Local ServerWorld world, @Local BlockPos pos)
	{
		if(world.getBlockEntity(pos) instanceof CheckpointBlockEntity)
		{
			SnowballEntity entity = new SnowballEntity(EntityType.SNOWBALL, world);
			entity.setPosition(pos.toCenterPos());
			world.spawnEntity(entity);
			BlockHitResult hit = world.raycast(new RaycastContext(pos.toCenterPos(), pos.add(0, -32, 0).toCenterPos(),
					RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity));
			entity.remove(RemovalReason.DISCARDED);
			return(Optional.of(hit.getPos().add(0f, 0.1f, 0f)));
		}
		return original;
	}
	
	@Inject(method="dropInventory", at = @At("HEAD"), cancellable = true)
	void onDropInventory(CallbackInfo ci)
	{
		if(getWorld().getRegistryKey().equals(LevelManager.WORLD_KEY) || UltraComponents.WINGED.get(this).getLastCheckpoint() != null)
			ci.cancel();
	}
	
	@Inject(method="getXpToDrop", at = @At("HEAD"), cancellable = true)
	void onGetXpToDrop(CallbackInfoReturnable<Integer> cir)
	{
		if(getWorld().getRegistryKey().equals(LevelManager.WORLD_KEY) || UltraComponents.WINGED.get(this).getLastCheckpoint() != null)
			cir.setReturnValue(0);
	}
	
	@ModifyReturnValue(method = "isSwimming", at = @At("RETURN"))
	boolean onIsSwimming(boolean original)
	{
		//Hi-vel mode normally suppresses swimming, but allow it while in water so the player uses the vanilla swim pose.
		return original && (!isWingsActive() || isTouchingWater());
	}
	
	@ModifyReturnValue(method = "shouldSwimInFluids", at = @At("RETURN"))
	boolean onShouldSwimInFluids(boolean original)
	{
		return original && !(isWingsActive() || abilities.flying);
	}
	
	@Override
	public Vec3d[] getWingPose()
	{
		Vec3d[] pose = new Vec3d[8];
		System.arraycopy(curWingPose, 0, pose, 0, 8);
		return pose;
	}
	
	@Override
	public void setWingPose(Vec3d[] pose)
	{
		System.arraycopy(pose, 0, curWingPose, 0, 8);
	}
	
	Multimap<EntityAttribute, EntityAttributeModifier> getSpeedMod()
	{
		Multimap<EntityAttribute, EntityAttributeModifier> speedMod = HashMultimap.create();
		speedMod.put(EntityAttributes.GENERIC_MOVEMENT_SPEED, new EntityAttributeModifier(UUID.fromString("9c92fac8-0018-11ee-be56-0242ac120002"), "spd_up",
				HivelConfig.INSTANCE.speed.getValue() - 1f, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
		return speedMod;
	}
	
	@Override
	public void updateSpeedConfig()
	{
		updateSpeedConfig(isWingsActive());
	}
	
	@Override
	public void updateSpeedConfig(boolean wingsActive)
	{
		if(curSpeedMod != null)
			getAttributes().removeModifiers(curSpeedMod);
		if(wingsActive)
		{
			curSpeedMod = getSpeedMod();
			getAttributes().addTemporaryModifiers(curSpeedMod);
		}
	}
	
	public boolean isWingsActive()
	{
		return UltraComponents.WING_DATA.get(this).isActive();
	}
	
	@Override
	public void startSlam()
	{
		UltraComponents.HIVEL.get(this).setSlamming(true);
	}
	
	@Override
	public void endSlam(boolean strong)
	{
		IHivelComponent hivel = UltraComponents.HIVEL.get(this);
		hivel.setSlamming(false);
		if(!isOnGround())
			return;
		getWorld().playSound(null, getBlockPos(), SoundRegistry.SLAM, SoundCategory.PLAYERS,
				strong ? 1f : 0.75f, strong ? 0.75f : 1.25f);
		HivelConfig config = HivelConfig.INSTANCE;
		float f = config.slamDamageMargin.getValue();
		getWorld().getOtherEntities(this, getBoundingBox().expand(f, 1f, f).offset(0f, -0.5f, 0f)).forEach(e ->
				{
					if(!(e instanceof ItemEntity))
						e.damage(DamageSources.get(getWorld(), DamageSources.SLAM, this), hivel.getSlamDamageCooldown() > 0 ? 1 : 6);
				});
		hivel.setSlamDamageCooldown(30);
		if(!strong)
			return;
		f = config.strongSlamImpactMargin.getValue();
		getWorld().getOtherEntities(this, getBoundingBox().expand(f, 0.5f, f)).forEach(e -> {
			if((e instanceof LivingEntityAccessor l) && l.takePunchKnockback())
				e.addVelocity(0f, config.strongSlamImpactVelocity.getValue(), 0f);
		});
		World world = getWorld();
		if(!world.isClient)
		{
			for (int y = 0; y <= 1; y++)
			{
				for (int x = -1; x <= 1; x++)
				{
					for (int z = -1; z <= 1; z++)
					{
						BlockPos pos = getSteppingPos().add(new Vec3i(x, y, z));
						if(!(ServerConfig.INSTANCE.protectNature.getValue() && world.getBlockState(pos).isIn(TagRegistry.FRAGILE_NATURE)) &&
								   world.getBlockState(pos).isIn(TagRegistry.SLAM_BREAKABLE))
							world.breakBlock(pos, true, this);
					}
				}
			}
		}
	}
	
	@Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;isSpectator()Z", shift = At.Shift.AFTER))
	void preTick(CallbackInfo ci)
	{
		IEditorComponent edit = UltraComponents.EDITOR.get(this);
		if(edit.isNoClip())
			noClip = true;
		if(edit.isActive())
		{
			abilities.flying = abilities.allowFlying = true;
			setOnGround(false);
		}
		if(parryIFrames > 0)
			parryIFrames--;
		
		ItemStack stack = inventory.getMainHandStack();
		if(!stack.getItem().equals(lastHeldItem))
		{
			if(lastHeldItem instanceof ISelectionAwareItem aware)
				aware.onUnselect((PlayerEntity)((Object)this));
			lastHeldItem = stack.isEmpty() ? null : stack.getItem();
			if(stack.getItem() instanceof ISelectionAwareItem aware)
				aware.onSelect((PlayerEntity)((Object)this));
		}
	}
	
	@Inject(method = "tick", at = @At("TAIL"))
	void onTick(CallbackInfo ci)
	{
		if(!getWorld().isClient() && getMainHandStack().isOf(ItemRegistry.FLAMETHROWER) && (backtank == null || backtank.isRemoved()) && isAlive())
			backtank = BackTank.spawn(getWorld(), this);
	}
	
	@Inject(method = "tickMovement", at = @At("TAIL"))
	void onTickMovement(CallbackInfo ci)
	{
		IHivelComponent hivel = UltraComponents.HIVEL.get(this);
		//In hi-vel mode, automatically use the vanilla swimming pose in water: start when the head submerges and keep
		//swimming while touching water (like vanilla). Skipped while dashing/sliding, which have their own poses. The
		//fast hi-vel water physics is left untouched.
		if(isWingsActive() && !abilities.flying && !hivel.isDashing() && !hivel.isSliding())
			setSwimming(isSwimming() ? isTouchingWater() : isSubmergedInWater());
		if(hivel.getDashingTicks() >= -1)
		{
			Vec3d dir = getVelocity();
			Vec3d particleVel = new Vec3d(-dir.x, 0, -dir.z).multiply(random.nextDouble() * 0.33 + 0.1);
			Vec3d pos = getPos().add((random.nextDouble() - 0.5) * getWidth(),
					random.nextDouble() * getHeight(), (random.nextDouble() - 0.5) * getWidth()).add(dir.multiply(0.25));
			getWorld().addParticle(ParticleRegistry.DASH, true, pos.x, pos.y, pos.z, particleVel.x, particleVel.y, particleVel.z);
		}
		if(hivel.isSliding())
		{
			Vec3d dir = getVelocity().multiply(1.0, 0.0, 1.0).normalize();
			Vec3d particleVel = new Vec3d(-dir.x, -dir.y, -dir.z).multiply(random.nextDouble() * 0.1 + 0.025);
			Vec3d pos = getPos().add(dir.multiply(1.5));
			getWorld().addParticle(ParticleRegistry.SLIDE, true, pos.x, pos.y + 0.1, pos.z, particleVel.x, particleVel.y, particleVel.z);
			incrementStat(StatisticRegistry.SLIDE);
		}
		if(hivel.isSlamming())
		{
			Vec3d particleVel = new Vec3d(0, 1, 0);
			for (int i = 0; i < random.nextInt(4) + 8; i++)
			{
				Vec3d pos = getPos().add((random.nextDouble() - 0.5) * 10.0, 5.0 * ((random.nextDouble() - 0.9) * 2), (random.nextDouble() - 0.5) * 10.0);
				getWorld().addParticle(ParticleRegistry.GROUND_POUND, true, pos.x, pos.y, pos.z, particleVel.x, particleVel.y, particleVel.z);
			}
			fallDistance = 0f;
		}
		if(backtank != null)
		{
			if(backtank.isRemoved())
				backtank = null;
			else
				backtank.positionSelf(this);
		}
	}
	
	@ModifyReturnValue(method = "adjustMovementForSneaking", at = @At("RETURN"))
	Vec3d onAdjustMovementForSneaking(Vec3d original, @Local Vec3d movement)
	{
		if(isWingsActive())
			return movement;
		return original;
	}
	
	@ModifyArg(method = "increaseTravelMotionStats", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;addExhaustion(F)V", ordinal = 3))
	float addExhaustion(float exhaustion)
	{
		if(!UltraComponents.WING_DATA.get(this).isActive())
			return exhaustion;
		return 0;
	}
	
	@ModifyExpressionValue(method = "getOffGroundSpeed", at = @At(value = "CONSTANT", args = "floatValue=0.02f"))
	float modifyAirControl(float val)
	{
		if(isWingsActive() && UltraComponents.HIVEL.get(this).isAirControlIncreased())
			return HivelConfig.INSTANCE.offGroundSpeed.getValue();
		else
			return val;
	}
	
	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	void onWriteCustomData(NbtCompound nbt, CallbackInfo ci)
	{
		NbtCompound ultra = new NbtCompound();
		
		NbtCompound progression = new NbtCompound();
		UltraComponents.PROGRESSION.get(this).writeToNbt(progression);
		ultra.put("progression", progression);
		NbtCompound arms = new NbtCompound();
		UltraComponents.ARMS.get(this).writeToNbt(arms);
		ultra.put("arms", arms);
		NbtCompound loadouts = new NbtCompound();
		UltraComponents.LOADOUT.get(this).writeToNbt(loadouts);
		ultra.put("loadouts", loadouts);
		
		nbt.put("ultracraft", ultra);
	}
	
	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	void onReadCustomData(NbtCompound nbt, CallbackInfo ci)
	{
		if(!nbt.contains("ultracraft", NbtElement.COMPOUND_TYPE))
			return;
		NbtCompound ultra = nbt.getCompound("ultracraft");
		if(ultra.contains("progression", NbtElement.COMPOUND_TYPE))
		{
			NbtCompound progression = ultra.getCompound("progression");
			IProgressionComponent progressionComponent = UltraComponents.PROGRESSION.get(this);
			progressionComponent.readFromNbt(progression);
			progressionComponent.sync();
		}
		if(ultra.contains("arms", NbtElement.COMPOUND_TYPE))
		{
			NbtCompound arms = ultra.getCompound("arms");
			IArmComponent armComponent = UltraComponents.ARMS.get(this);
			armComponent.readFromNbt(arms);
			armComponent.sync();
		}
		if(ultra.contains("loadout", NbtElement.COMPOUND_TYPE))
		{
			NbtCompound arms = ultra.getCompound("loadout");
			ILoadoutComponent loadoutComponent = UltraComponents.LOADOUT.get(this);
			loadoutComponent.readFromNbt(arms);
			UltraComponents.LOADOUT.sync(this);
		}
	}
	
	@WrapOperation(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/damage/DamageSources;playerAttack(Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/entity/damage/DamageSource;"))
	DamageSource onGetDamageSource(net.minecraft.entity.damage.DamageSources instance, PlayerEntity attacker, Operation<DamageSource> original)
	{
		if(attacker.getMainHandStack().getItem() instanceof IOverrideMeleeDamageType weapon)
			return weapon.getDamageSource(attacker.getWorld(), attacker);
		return original.call(instance, attacker);
	}
	
	@ModifyReturnValue(method = "canBeHitByProjectile", at = @At("RETURN"))
	boolean canBeHitByProjectiles(boolean original)
	{
		return original && parryIFrames <= 0;
	}
	
	@Override
	public boolean canBreatheInWater()
	{
		//In hi-vel mode never consume oxygen / drown, regardless of the drowning config.
		return isWingsActive();
	}
	
	@Override
	public void setFocusedTerminal(TerminalBlockEntity terminal)
	{
	
	}
	
	@Override
	public TerminalBlockEntity getFocusedTerminal()
	{
		return null;
	}
	
	@Override
	public boolean isOpped()
	{
		return getPermissionLevel() >= 2;
	}
	
	@Override
	public void setBackTank(BackTank backtank)
	{
		this.backtank = backtank;
	}
	
	@Override
	public BackTank getBacktank()
	{
		return backtank;
	}
	
	@Override
	public void onParry()
	{
		timeUntilRegen = 11 + HivelConfig.INSTANCE.iFrames.getValue();
	}
}
