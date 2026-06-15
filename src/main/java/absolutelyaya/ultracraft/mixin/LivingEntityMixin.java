package absolutelyaya.ultracraft.mixin;

import absolutelyaya.ultracraft.ExplosionHandler;
import absolutelyaya.ultracraft.compat.TrinketUtil;
import absolutelyaya.ultracraft.components.UltraComponents;
import absolutelyaya.ultracraft.Ultracraft;
import absolutelyaya.ultracraft.accessor.LivingEntityAccessor;
import absolutelyaya.ultracraft.accessor.WingedPlayerEntity;
import absolutelyaya.ultracraft.compat.PlayerAnimator;
import absolutelyaya.ultracraft.components.player.IArmComponent;
import absolutelyaya.ultracraft.components.player.IHivelComponent;
import absolutelyaya.ultracraft.components.player.ProgressionComponent;
import absolutelyaya.ultracraft.config.HivelConfig;
import absolutelyaya.ultracraft.config.RegenSetting;
import absolutelyaya.ultracraft.config.ServerConfig;
import absolutelyaya.ultracraft.damage.DamageSources;
import absolutelyaya.ultracraft.damage.DamageTypeTags;
import absolutelyaya.ultracraft.data.StyleBonusManager;
import absolutelyaya.ultracraft.entity.AbstractUltraHostileEntity;
import absolutelyaya.ultracraft.entity.IAntiCheeseBoss;
import absolutelyaya.ultracraft.entity.machine.V2Entity;
import absolutelyaya.ultracraft.entity.projectile.NailEntity;
import absolutelyaya.ultracraft.registry.ItemRegistry;
import absolutelyaya.ultracraft.registry.PacketRegistry;
import absolutelyaya.ultracraft.registry.SoundRegistry;
import absolutelyaya.ultracraft.registry.StatusEffectRegistry;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.List;
import java.util.function.Supplier;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements LivingEntityAccessor
{
	@Shadow public abstract boolean isPushable();
	
	@Shadow public abstract void swingHand(Hand hand);
	
	@Shadow protected boolean jumping;
	
	@Shadow protected abstract void jump();
	
	@Shadow private int jumpingCooldown;
	
	@Shadow protected abstract float getBaseMovementSpeedMultiplier();
	
	@Shadow public abstract float getMovementSpeed();
	
	@Shadow public abstract boolean hasStatusEffect(StatusEffect effect);
	
	@Shadow protected abstract boolean shouldSwimInFluids();
	
	@Shadow public abstract boolean isClimbing();
	
	@Shadow public abstract boolean canWalkOnFluid(FluidState state);
	
	@Shadow public abstract void updateLimbs(boolean flutter);
	
	@Shadow public abstract boolean isAlive();
	
	@Shadow protected float lastDamageTaken;
	
	@Shadow public abstract float getHealth();
	
	@Shadow public abstract float getMaxHealth();
	
	@Shadow public abstract @Nullable LivingEntity getAttacker();
	
	@Shadow public abstract boolean addStatusEffect(StatusEffectInstance effect);
	
	@Shadow public abstract LivingEntity getLastAttacker();
	
	@Shadow protected abstract void consumeItem();
	
	int punchDuration = 60;
	Supplier<Boolean> canBleedSupplier = () -> true, takePunchKnockpackSupplier = this::isPushable; //TODO: add Sandy Enemies (eventually)
	int punchTicks, knuckleTicks, ticksSincePunch = Integer.MAX_VALUE, ricochetCooldown, fatique, firecooldown;
	boolean punching, blasting, timeFrozen, punchCancelled, punchInterruptable;
	float punchProgress, prevPunchProgress, knuckleProgress, prevKnuckleProgress, recoil, lastHealth;
	
	public LivingEntityMixin(EntityType<?> type, World world)
	{
		super(type, world);
	}
	
	@Inject(method = "tick", at = @At("HEAD"))
	void onTick(CallbackInfo ci)
	{
		timeFrozen = Ultracraft.isTimeFrozen();
		if(!timeFrozen || punchTicks < 2)
			punchTick();
		recoil = MathHelper.lerp(0.3f, recoil, 0f);
		if(ricochetCooldown > 0)
			ricochetCooldown--;
		if(fatique > 0 && !punching)
			fatique--;
		if(firecooldown > 0)
			firecooldown--;
	}
	
	@ModifyArgs(method = "damage", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;applyDamage(Lnet/minecraft/entity/damage/DamageSource;F)V"))
	void modifyAppliedDamage(Args args)
	{
		if((Object)this instanceof PlayerEntity)
			return;
		DamageSource source = args.get(0);
		float amount = args.get(1);
		if(source.isIn(DamageTypeTags.ULTRACRAFT) && !source.isIn(DamageTypeTags.UNBOOSTED) && !((Object)this instanceof AbstractUltraHostileEntity))
			args.set(1, amount * 2.5f);
	}
	
	@Inject(method = "damage", at = @At("HEAD"))
	void beforeDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir)
	{
		lastHealth = getHealth();
		if(source.getSource() instanceof NailEntity || source.getAttacker() instanceof NailEntity)
			UltraComponents.LIVING.get(this).incrementNails();
	}
	
	@Inject(method = "damage", at = @At("RETURN"), cancellable = true)
	void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir)
	{
		if(source.isOf(DamageSources.NAIL) || (source.isOf(DamageSources.SHARPSHOOTER) && !((Object)this instanceof PlayerEntity)))
		{
			timeUntilRegen = 4;
			lastDamageTaken = 0f;
			if(!getWorld().isClient)
				bleed(getPos(), getHeight() / 2f, source, amount * 0.25f);
			cir.setReturnValue(true);
			return;
		}
		if(!cir.getReturnValue() || getWorld().isClient)
			return;
		if(source.isOf(DamageSources.RICOCHET))
			ricochetCooldown = 5; //after ricochet hit, cant ricochet to this enemy again for 5 ticks
		if(IsCanBleed() && !source.isIn(DamageTypeTags.NO_BLEEDING))
			bleed(getPos(), getHeight() / 2f, source, amount);
		if(source.isOf(DamageTypes.IN_FIRE) || source.isOf(DamageTypes.ON_FIRE))
		{
			if(firecooldown > 0)
			{
				cir.setReturnValue(false);
				return;
			}
			timeUntilRegen = 9;
			lastDamageTaken = 0f;
			firecooldown = 20;
		}
		if(source.isIn(DamageTypeTags.IS_PER_TICK))
			return;
		if(source.isOf(DamageSources.GUN) || source.isOf(DamageSources.SHOTGUN))
			timeUntilRegen = 9;
		if(source.isOf(DamageSources.SWORDSMACHINE))
			timeUntilRegen = 12;
		if(source.isOf(DamageSources.CANCER))
			addStatusEffect(new StatusEffectInstance(StatusEffectRegistry.CANCEROUS, (int)(60 * amount)));
	}
	
	@SuppressWarnings("EqualsBetweenInconvertibleTypes")
	@Override
	public void bleed(Vec3d pos, float halfheight, DamageSource source, float amount)
	{
		List<PlayerEntity> nearby = getWorld().getEntitiesByType(TypeFilter.instanceOf(PlayerEntity.class), getBoundingBox().expand(32), e -> !e.equals(this));
		List<PlayerEntity> heal = getWorld().getEntitiesByType(TypeFilter.instanceOf(PlayerEntity.class), getBoundingBox().expand(4), e -> !e.equals(this));
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		buf.writeFloat(amount * (source.isIn(DamageTypeTags.IS_PER_TICK) || (Ultracraft.isLikelyPerTickDamageType(source.getType())) ? 0.66f : 1f));
		buf.writeDouble(pos.x);
		buf.writeDouble(pos.y);
		buf.writeDouble(pos.z);
		buf.writeDouble(halfheight);
		buf.writeBoolean(source.isOf(DamageSources.SHOTGUN));
		for (PlayerEntity player : nearby)
			ServerPlayNetworking.send((ServerPlayerEntity)player, PacketRegistry.BLEED_PACKET_ID, buf);
		RegenSetting healRule = ServerConfig.INSTANCE.bloodHeal.getValue();
		if(!healRule.equals(RegenSetting.NEVER))
		{
			for (PlayerEntity player : heal)
			{
				if(!(player instanceof WingedPlayerEntity))
					continue;
				if(!(UltraComponents.PROGRESSION.get(player).isUnlocked(ProgressionComponent.BLOODHEAL) ||
							(Ultracraft.TRINKETS && TrinketUtil.isHasTrinketEquipped(player, ItemRegistry.ABSORBANT_PLATING))))
					continue;
				if((healRule.equals(RegenSetting.ONLY_HIVEL) && !UltraComponents.WING_DATA.get(player).isActive()))
					continue;
				float healing = amount * (source.isOf(DamageSources.SHOTGUN) ? 1f : 2.5f);
				healing = Math.min(healing, lastHealth + getMaxHealth() * 2f);
				if(source.isIn(DamageTypeTags.MELEE))
					healing /= 3.5f;
				UltraComponents.WINGED.get(player).bloodHeal(healing);
				if(ServerConfig.INSTANCE.bloodSaturation.getValue())
					player.getHungerManager().add((int)healing, 1f);
			}
		}
	}
	
	@ModifyReturnValue(method = "getJumpVelocity", at = @At("RETURN"))
	float onGetJumpVel(float original)
	{
		if(isAffectedByMovementConfig() && !getWorld().isClient)
			return original + 0.1f * Math.max(HivelConfig.INSTANCE.jumpBoost.getValue() + (isTouchingWater() ? 0.5f : 0f), 0);
		return original;
	}
	
	Vec3d fluidMovement(double gravity, boolean falling, Vec3d motion)
	{
		gravity /= falling ? 16f : 3f;
		double vel = motion.y;
		if (falling && Math.abs(vel - gravity) > 0.15)
			vel = -0.15;
		else
			vel -= gravity;
		return new Vec3d(motion.x, vel, motion.z);
	}
	
	@Inject(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;shouldSwimInFluids()Z", ordinal = 0), cancellable = true)
	void onTravel(Vec3d movementInput, CallbackInfo ci, @Local double d, @Local boolean bl, @Local FluidState fluidState)
	{
		//custom water physics
		if(!(this instanceof WingedPlayerEntity winged && UltraComponents.WING_DATA.get(winged).isActive()) || ((PlayerEntity)winged).getAbilities().flying)
			return;
		if(!isTouchingWater() || shouldSwimInFluids() || canWalkOnFluid(fluidState))
			return;
		IHivelComponent hivel = UltraComponents.HIVEL.get(winged);
		float f = hivel.isSliding() ? 0.9F : getBaseMovementSpeedMultiplier();
		float g = 0.03f;
		if(fluidState.isIn(FluidTags.WATER))
		{
			float h = (float)EnchantmentHelper.getDepthStrider((LivingEntity)(Object)this);
			if (h > 3.0f)
				h = 3.0f;
			if (!isOnGround())
				h *= 0.5f;
			if (h > 0.0f)
			{
				f += (0.54f - f) * h / 3.0f;
				g += (getMovementSpeed() - g) * h / 3.0f;
			}
			if (hasStatusEffect(StatusEffects.DOLPHINS_GRACE))
				f = 0.96f;
		}
		//Extra hi-vel swim speed: equivalent to Forge's swim_speed attribute at 1.7 (it multiplies the swim acceleration).
		float swimSpeed = fluidState.isIn(FluidTags.WATER) ? 1.7f : 1f;
		updateVelocity(g * swimSpeed, movementInput);
		move(MovementType.SELF, getVelocity());
		Vec3d vec3d = getVelocity();
		if (horizontalCollision && isClimbing())
			vec3d = new Vec3d(vec3d.x, 0.2, vec3d.z);
		if(hivel.shouldIgnoreSlowdown())
			f = 0.9f;
		setVelocity(vec3d.multiply(f, 1f, f));
		setVelocity(fluidMovement(d, getVelocity().y <= 0f, getVelocity()));
		
		updateLimbs(this instanceof Flutterer);
		ci.cancel();
	}
	
	@ModifyExpressionValue(method = "travel", at = @At(value = "CONSTANT", args = "floatValue=0.91f"))
	float modifySlowdown(float val)
	{
		if(!(this instanceof WingedPlayerEntity winged && UltraComponents.WING_DATA.get(winged).isActive()) ||
				   ((PlayerEntity)winged).getAbilities().flying || !UltraComponents.HIVEL.get(winged).shouldIgnoreSlowdown())
			return val;
		return HivelConfig.INSTANCE.drag.getValue();
	}
	
	@ModifyVariable(method = "travel", ordinal = 0, at = @At("STORE"))
	private double modifyGravity(double value)
	{
		if(!(isAffectedByMovementConfig()) || (((Object)this instanceof PlayerEntity player) && player.getAbilities().flying) || touchingWater)
			return value;
		float val = (getWorld().isClient ? getGravityModifier() : HivelConfig.INSTANCE.gravity.getValue());
		return Math.max(value - (value * (1f - val)), 0.01f);
	}
	
	@ModifyVariable(method = "computeFallDamage", ordinal = 2, at = @At("STORE"))
	private float modifyFalldamageReduction(float value)
	{
		if(!(this instanceof WingedPlayerEntity winged && UltraComponents.WING_DATA.get(winged).isActive()))
			return value;
		return value + ((HivelConfig.INSTANCE.jumpBoost.getValue() + 1) * HivelConfig.INSTANCE.gravity.getValue());
	}
	
	@ModifyVariable(method = "computeFallDamage", ordinal = 1, at = @At("LOAD"), argsOnly = true)
	private float modifyFalldamageMultiplier(float value)
	{
		if(!(this instanceof WingedPlayerEntity winged && UltraComponents.WING_DATA.get(winged).isActive()))
			return value;
		return 0.5f;
	}
	
	@ModifyReturnValue(method = "computeFallDamage", at = @At("RETURN"))
	private int onComputeFallDamage(int original)
	{
		if(isPlayer() && UltraComponents.HIVEL.get(this).isSliding())
			return 0;
		return original;
	}
	
	@Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;shouldSwimInFluids()Z"))
	void onTickMovement(CallbackInfo ci)
	{
		if (this instanceof WingedPlayerEntity winged && UltraComponents.WING_DATA.get(winged).isActive() && isOnGround() && jumping && jumpingCooldown == 0 && !isTouchingWater())
		{
			jump();
			jumpingCooldown = 10;
		}
	}
	
	@ModifyReturnValue(method = "shouldSwimInFluids", at = @At("RETURN"))
	boolean onShouldSwimInFluids(boolean original)
	{
		if(this instanceof WingedPlayerEntity winged && UltraComponents.WING_DATA.get(winged).isActive() &&
				   getWorld().getFluidState(((PlayerEntity)winged).getBlockPos()).isIn(FluidTags.WATER))
			return false;
		return original;
	}
	
	@ModifyReturnValue(method = "canWalkOnFluid", at = @At("RETURN"))
	boolean onCanWalkOnFluid(boolean original)
	{
		return original || (this instanceof WingedPlayerEntity && UltraComponents.HIVEL.get(this).isSliding());
	}
	
	@Inject(method = "onDamaged", at = @At("HEAD"))
	void onOnDamaged(DamageSource damageSource, CallbackInfo ci)
	{
		if(damageSource.getAttacker() instanceof IAntiCheeseBoss boss)
			boss.resetFrustration();
		if(damageSource.getSource() instanceof IAntiCheeseBoss boss)
			boss.resetFrustration();
	}
	
	@Inject(method = "onDeath", at = @At("HEAD"))
	void onDeath(DamageSource source, CallbackInfo ci)
	{
		LivingEntity attacker = getAttacker();
		if(attacker == null && getLastAttacker() != null)
			attacker = getLastAttacker();
		if(attacker == null && source.getAttacker() instanceof PlayerEntity player)
			attacker = player;
		if(attacker == null && source.getSource() instanceof PlayerEntity player)
			attacker = player;
		if(attacker == null && (Object)this instanceof MobEntity mob)
			attacker = mob.getTarget();
		if(!(attacker instanceof PlayerEntity playerAttacker))
			return;
		UltraComponents.STYLE.get(playerAttacker).onKill((LivingEntity)(Object)this, source);
		StyleBonusManager.getBonuses().forEach((id, bonus) -> {
			if(bonus.check(getWorld(), getType(), source.getType()))
				UltraComponents.STYLE.get(playerAttacker).styleBonusGet(bonus);
		});
	}
	
	@ModifyArgs(method = "damage", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;takeKnockback(DDD)V"))
	void onKnockback(Args args, DamageSource source, float damage)
	{
		if(source.isIn(DamageTypeTags.REDUCED_KNOCKBACK))
			args.set(0, (double)args.get(0) / 4f);
	}
	
	@Inject(method = "onStatusEffectApplied", at = @At("HEAD"))
	void onApplyEffect(StatusEffectInstance effect, Entity source, CallbackInfo ci)
	{
		if(effect.getEffectType().equals(StatusEffectRegistry.CANCEROUS))
			UltraComponents.LIVING.get(this).setCanerous(true);
		if(effect.getEffectType().equals(StatusEffectRegistry.ENRAGED))
			UltraComponents.LIVING.get(this).setEnraged(true);
	}
	
	@Inject(method = "onStatusEffectRemoved", at = @At("HEAD"))
	void onRemoveEffect(StatusEffectInstance effect, CallbackInfo ci)
	{
		if(effect.getEffectType().equals(StatusEffectRegistry.CANCEROUS))
			UltraComponents.LIVING.get(this).setCanerous(false);
		if(effect.getEffectType().equals(StatusEffectRegistry.ENRAGED))
			UltraComponents.LIVING.get(this).setEnraged(false);
	}
	
	void punchTick()
	{
		int i = Math.round(punchDuration * (fatique > 30 ? 1.5f : 1f));
		if (punching)
		{
			++punchTicks;
			if (punchTicks >= i)
			{
				punchTicks = 0;
				punching = false;
			}
			else if(timeFrozen)
				punchTicks = 1;
		}
		else
		{
			punchTicks = 0;
			punchCancelled = false;
		}
		if((Object)this instanceof PlayerEntity)
		{
			IArmComponent arm = UltraComponents.ARMS.get(this);
			if(ticksSincePunch < 8)
			{
				ticksSincePunch++;
				if(!punchCancelled && ticksSincePunch == 8 && arm.isKnuckleblaster() && arm.isPunchPressed())
				{
					knuckleBlast();
					playSound(SoundRegistry.KNUCKLEBLASTER_RELOAD, 1f, 1f);
				}
			}
		}
		
		prevPunchProgress = punchProgress;
		punchProgress = Math.min(punchTicks / 8f, 0.5f);
		punchProgress += MathHelper.clamp((4 - punchDuration + punchTicks) / 8f, 0f, 0.5f);
		if(timeFrozen)
			prevPunchProgress = punchProgress;
		
		if(blasting)
		{
			if(knuckleTicks < 8)
			{
				knuckleTicks++;
				if(knuckleTicks >= 8)
				{
					knuckleTicks = 0;
					blasting = false;
				}
			}
		}
		
		prevKnuckleProgress = knuckleProgress;
		knuckleProgress = Math.min(knuckleTicks / 8f, 1f);
		if(timeFrozen)
			prevKnuckleProgress = knuckleProgress;
	}
	
	@Override
	public boolean punch()
	{
		if(!punching || punchInterruptable)
		{
			IArmComponent arm = UltraComponents.ARMS.get(this);
			punchDuration = switch(arm.getActiveArm())
			{
				case 0 -> 8;
				case 1 -> 16;
				default -> 6;
			};
			punchTicks = 0;
			ticksSincePunch = 0;
			punching = true;
			fatique = Math.min(fatique + 10, 40);
			if(!getWorld().isClient)
				swingHand(Hand.OFF_HAND);
			if(arm.isFeedbacker())
				playSound(SoundRegistry.FEEDBACKER_PUNCH, 1f, 1f);
			else if(arm.isKnuckleblaster())
				playSound(SoundRegistry.KNUCKLEBLASTER_PUNCH, 1f, 1f);
			punchInterruptable = false;
			return true;
		}
		return false;
	}
	
	@Override
	public void fakePunch()
	{
		punch();
		punchInterruptable = true;
	}
	
	@Override
	public float getPunchProgress(float tickDelta)
	{
		float f = punchProgress - prevPunchProgress;
		if (f < 0f)
			++f;
		if (Ultracraft.isTimeFrozen())
			return punchProgress;
		else
			return prevPunchProgress + f * tickDelta;
	}
	
	@Override
	public boolean IsPunching()
	{
		return punching;
	}
	
	@Override
	public void knuckleBlast()
	{
		knuckleTicks = 0;
		blasting = true;
		if(!((Object)this instanceof PlayerEntity player))
			return;
		if(!getWorld().isClient)
			ExplosionHandler.explosion(player, player.getWorld(), player.getPos(), DamageSources.get(player.getWorld(),
					DamageSources.KNUCKLE_BLAST, player), 2f, 0.75f, 6, true);
		else
		{
			if(player instanceof WingedPlayerEntity && UltraComponents.HIVEL.get(this).isSliding())
				PlayerAnimator.playAnimation(player, player.getMainArm().equals(Arm.LEFT) ? PlayerAnimator.SLIDE_KNUCKLE_BLAST_FLIPPED : PlayerAnimator.SLIDE_KNUCKLE_BLAST,
						0, false, false);
			else
				PlayerAnimator.playAnimation(player, player.getMainArm().equals(Arm.LEFT) ? PlayerAnimator.KNUCKLE_BLAST_FLIPPED : PlayerAnimator.KNUCKLE_BLAST,
						0, false, false);
		}
	}
	
	@Override
	public float getKnuckleBlastProgress(float tickDelta)
	{
		float f = knuckleProgress - prevKnuckleProgress;
		if (f < 0f)
			++f;
		if (Ultracraft.isTimeFrozen())
			return knuckleProgress;
		else
			return prevKnuckleProgress + f * tickDelta;
	}
	
	@Override
	public void cancelPunch()
	{
		punchCancelled = true;
	}
	
	@Override
	public boolean IsCanBleed()
	{
		return canBleedSupplier.get();
	}
	
	@Override
	public void setCanBleedSupplier(Supplier<Boolean> supplier)
	{
		canBleedSupplier = supplier;
	}
	
	@Override
	public boolean takePunchKnockback()
	{
		return takePunchKnockpackSupplier.get();
	}
	
	@Override
	public void setTakePunchKnockbackSupplier(Supplier<Boolean> supplier)
	{
		takePunchKnockpackSupplier = supplier;
	}
	
	@Override
	public void addRecoil(float recoil)
	{
		this.recoil += recoil;
	}
	
	@Override
	public float getRecoil()
	{
		return recoil;
	}
	
	@Override
	public boolean isRicochetHittable()
	{
		return isAlive() && ricochetCooldown <= 0;
	}
	
	boolean isAffectedByMovementConfig()
	{
		return (this instanceof WingedPlayerEntity winged && UltraComponents.WING_DATA.get(winged).isActive()) || (Object)this instanceof V2Entity;
	}
}
