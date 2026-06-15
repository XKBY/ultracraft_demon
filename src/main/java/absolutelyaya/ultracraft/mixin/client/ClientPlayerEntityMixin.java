package absolutelyaya.ultracraft.mixin.client;

import absolutelyaya.ultracraft.components.UltraComponents;
import absolutelyaya.ultracraft.Ultracraft;
import absolutelyaya.ultracraft.accessor.WingedPlayerEntity;
import absolutelyaya.ultracraft.block.TerminalBlockEntity;
import absolutelyaya.ultracraft.client.ClientConfig;
import absolutelyaya.ultracraft.client.UltracraftClient;
import absolutelyaya.ultracraft.client.gui.screen.TerminalScreen;
import absolutelyaya.ultracraft.client.gui.screen.WingCustomizationScreen;
import absolutelyaya.ultracraft.compat.PlayerAnimator;
import absolutelyaya.ultracraft.components.player.IHivelComponent;
import absolutelyaya.ultracraft.components.player.IWingDataComponent;
import absolutelyaya.ultracraft.config.HivelConfig;
import absolutelyaya.ultracraft.item.weapons.AbstractWeaponItem;
import absolutelyaya.ultracraft.registry.KeybindRegistry;
import absolutelyaya.ultracraft.registry.PacketRegistry;
import absolutelyaya.ultracraft.registry.StatusEffectRegistry;
import absolutelyaya.ultracraft.registry.TagRegistry;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Optional;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin extends AbstractClientPlayerEntity implements WingedPlayerEntity
{
	public ClientPlayerEntityMixin(ClientWorld world, GameProfile profile)
	{
		super(world, profile);
	}
	
	@Shadow private double lastX;
	
	@Shadow private double lastZ;
	
	@Shadow public abstract float getPitch(float tickDelta);
	
	@Shadow @Final public ClientPlayNetworkHandler networkHandler;
	@Shadow private double lastBaseY;
	@Shadow private int ticksSinceLastPositionPacketSent;
	@Shadow private float lastYaw;
	@Shadow private boolean lastOnGround;
	@Shadow private boolean autoJumpEnabled;
	@Shadow @Final protected MinecraftClient client;
	
	@Shadow public Input input;
	
	@Shadow public abstract boolean damage(DamageSource source, float amount);
	
	@Shadow public abstract boolean isUsingItem();
	
	@Shadow private @Nullable Hand activeHand;
	
	@Shadow public abstract boolean isMainPlayer();
	
	@Shadow public abstract boolean isSneaking();
	
	@Shadow public abstract void sendMessage(Text message, boolean overlay);
	
	@Shadow protected abstract boolean isCamera();
	
	@Shadow public abstract void dismountVehicle();
	
	Vec3d dashDir = Vec3d.ZERO;
	Vec3d slideDir = Vec3d.ZERO;
	boolean grounded, dashPressed, wasDashPressed, slidePressed, wasSlidePressed, slamming, wasSlamming, strongSlam, slamStored, wasJumping,
			wasHivel, slideStartedSideways;
	int slamTicks, curSlamCooldown, slamJumpTimer, coyote, curSlidePreservationTicks, slideTicks, curWallJumps, disableJumpTicks;
	int slamCooldown = 5, slamJumpWindow = 4, coyoteThreshold = 4, slidePreservationTicks = 5, slideSlowdownTicks = 20, wallJumps = 0,
			slamDisableJumpTicks = 8;
	float screenshake = 0f, slideVelocity;
	//Hi-vel underwater vertical movement: Space ascends fast, Ctrl(slide) descends a limited distance then stops.
	float waterAscendVelocity = 0.35f, waterDescendVelocity = 0.35f, waterDescendDistance = 50f, waterDescendRemaining = 50f, waterSinkVelocity = 0.15f;
	float hivelSpeed, slamVelocity = 2f, baseSlideVelocity = 0.35f, baseJumpVelocity = 0.42f, dashVelocity = 1f, skeweredDashVelocity = 0.05f,
			dashSlipAndSlideThreshold = 0.6f, dashSlipAndSlideReduction = 0.5f, dashAirStopVelocityMultiplier = 0.3f,
			skeweredDashAirStopVelocityMultiplier = 0.03f, slideJumpSpeedBonus = 0.025f, slideSpeedSoftCap = 0.99f, slideSlowdownMultiplier = 0.95f,
			slamJumpVelocityMultiplier = 1.5f, slamDiveVelocity = 1.5f, slamStoreJumpVelocityMultiplier = 4.5f, slamStoreDiveVelocity = 2.5f,
			slamTickVelocityBonus = 0.05f, slamSlideVelocity = 0.66f, slamStoreSlideVelocity = 1f, skimUpwardsVelocityMultiplier = 0.75f,
			wallSlideVelocity = 0.2f, wallJumpHorizontalVelocity = 0.33f, wallJumpVerticalVelocityMultiplier = 0.8f, groundCheckDistance = 0.1f,
			dashGroundStopVelocityMultiplier = 0.05f, slideStartGroundTolerance = 0.5f,
			slamDiveVerticalVelocityMultiplier = 0.6f, dashJumpVerticalVelocityMultiplier = 0.5f, slideJumpVerticalVelocityMultiplier = 0.55f;
	TerminalBlockEntity focusedTerminal;
	
	@Override
	public void initMovementConfig(HivelConfig config)
	{
		slamCooldown = config.slamCooldown.getValue();
		slamJumpWindow = config.slamJumpWindow.getValue();
		coyoteThreshold = config.coyoteThreshold.getValue();
		slidePreservationTicks = config.slidePreservationTicks.getValue();
		slideSlowdownTicks = config.slideSlowdownTicks.getValue();
		wallJumps = config.wallJumps.getValue();
		slamDisableJumpTicks = config.slamDisableJumpTicks.getValue();
		hivelSpeed = config.speed.getValue();
		slamVelocity = config.slamVelocity.getValue();
		baseSlideVelocity = config.baseSlideVelocity.getValue();
		baseJumpVelocity = config.baseJumpVelocity.getValue();
		dashVelocity = config.dashVelocity.getValue();
		skeweredDashVelocity = config.skeweredDashVelocity.getValue();
		dashSlipAndSlideThreshold = config.dashSlipAndSlideThreshold.getValue();
		dashSlipAndSlideReduction = config.dashSlipAndSlideReduction.getValue();
		dashAirStopVelocityMultiplier = config.dashAirStopVelocityMultiplier.getValue();
		skeweredDashAirStopVelocityMultiplier = config.skeweredDashAirStopVelocityMultiplier.getValue();
		slideJumpSpeedBonus = config.slideJumpSpeedBonus.getValue();
		slideSpeedSoftCap = config.slideSpeedSoftCap.getValue();
		slideSlowdownMultiplier = config.slideSlowdownMultiplier.getValue();
		slamJumpVelocityMultiplier = config.slamJumpVelocityMultiplier.getValue();
		slamDiveVelocity = config.slamDiveVelocity.getValue();
		slamStoreJumpVelocityMultiplier = config.slamStoreJumpVelocityMultiplier.getValue();
		slamStoreDiveVelocity = config.slamStoreDiveVelocity.getValue();
		slamTickVelocityBonus = config.slamTickVelocityBonus.getValue();
		slamSlideVelocity = config.slamSlideVelocity.getValue();
		slamStoreSlideVelocity = config.slamStoreSlideVelocity.getValue();
		skimUpwardsVelocityMultiplier = config.skimUpwardsVelocityMultiplier.getValue();
		wallSlideVelocity = config.wallSlideVelocity.getValue();
		wallJumpHorizontalVelocity = config.wallJumpHorizontalVelocity.getValue();
		wallJumpVerticalVelocityMultiplier = config.wallJumpVerticalVelocityMultiplier.getValue();
		groundCheckDistance = config.groundCheckDistance.getValue();
		dashGroundStopVelocityMultiplier = config.dashGroundStopVelocityMultiplier.getValue();
		slideStartGroundTolerance = config.slideStartGroundTolerance.getValue();
		slamDiveVerticalVelocityMultiplier = config.slamDiveVerticalVelocityMultiplier.getValue();
		dashJumpVerticalVelocityMultiplier = config.dashJumpVerticalVelocityMultiplier.getValue();
		slideJumpVerticalVelocityMultiplier = config.slideJumpVerticalVelocityMultiplier.getValue();
		Ultracraft.LOGGER.info("initialized Hivel Movement");
	}
	
	void tryDash()
	{
		IWingDataComponent wings = UltraComponents.WING_DATA.get(this);
		if(wings.isActive() && !getAbilities().flying && wouldPoseNotCollide(EntityPose.STANDING))
		{
			IHivelComponent hivel = UltraComponents.HIVEL.get(this);
			if(!hivel.consumeStamina())
				return;
			if(slamming)
				setSlammingClient(false);
			if(slamStored)
				slamStored = false;
			Vec3d dir = new Vec3d(input.movementSideways, 0f, input.movementForward).rotateY(-(float)Math.toRadians(getYaw())).normalize();
			if(dir.lengthSquared() < 0.9f)
				dir = Vec3d.fromPolar(0f, getYaw()).normalize();
			
			if(isMainPlayer())
			{
				PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
				buf.writeDouble(dir.x);
				buf.writeDouble(dir.y);
				buf.writeDouble(dir.z);
				ClientPlayNetworking.send(PacketRegistry.DASH_C2S_PACKET_ID, buf);
				PlayerAnimator.playAnimation(client.player, forwardSpeed >= 0 ? PlayerAnimator.DASH_FORWARD : PlayerAnimator.DASH_BACK, 5, false);
			}
			setVelocity(dir);
			dashDir = dir;
			hivel.onDash();
			if(hivel.isSliding())
				setSliding(false, true);
		}
	}
	
	@Inject(method = "sendMovementPackets", at = @At(value = "HEAD"), cancellable = true)
	public void onSendMovementPackets(CallbackInfo ci)
	{
		IHivelComponent hivel = UltraComponents.HIVEL.get(this);
		IWingDataComponent wings = UltraComponents.WING_DATA.get(this);
		
		if(wings.isActive() && !getAbilities().flying && !isSpectator())
		{
			dashPressed = KeybindRegistry.DASH.isPressed();
			slidePressed = KeybindRegistry.SLIDE.isPressed();
			
			//ground check
			boolean newGrounded = isGrounded(groundCheckDistance);
			if(!grounded && newGrounded) //landing
			{
				coyote = 0;
				curWallJumps = wallJumps;
				curSlidePreservationTicks = slidePreservationTicks;
			}
			//coyote ticker
			if(!grounded && coyote <= coyoteThreshold)
				coyote++;
			
			//dash
			if(!hivel.isDashing() && dashPressed && !wasDashPressed)
				tryDash();
			//dash velocity
			boolean isSkewered = hasStatusEffect(StatusEffectRegistry.IMPALED);
			if(hivel.isDashing())
				setVelocity(dashDir.multiply(isSkewered ? skeweredDashVelocity : dashVelocity));
			//dash jump
			if(hivel.wasDashing() && canJump() && jumping && !wasJumping)
			{
				hivel.onDashJump();
				if(!hivel.consumeStamina())
					setVelocity(dashDir.multiply(0.3));
				addVelocity(0f, getJumpVelocity() * dashJumpVerticalVelocityMultiplier, 0f);
				hivel.setIgnoreSlowdown(true);
				slideVelocity = dashVelocity;
				if(isMainPlayer())
					PlayerAnimator.playAnimation(client.player, forwardSpeed >= 0 ? PlayerAnimator.DASH_FORWARD : PlayerAnimator.DASH_BACK, 5, false);
			}
			//dash end velocity
			if(hivel.wasDashing() && !hivel.isDashing())
			{
				float slipAndSlide = getSteppingBlockState().getBlock().getSlipperiness();
				if(!grounded)
					setVelocity(dashDir.multiply(isSkewered ? skeweredDashAirStopVelocityMultiplier : dashAirStopVelocityMultiplier));
				else if(slipAndSlide > dashSlipAndSlideThreshold)
					setVelocity(dashDir.multiply(Math.min(slipAndSlide - dashSlipAndSlideReduction, dashSlipAndSlideThreshold)));
				else
					setVelocity(dashDir.multiply(dashGroundStopVelocityMultiplier));
			}
			
			//slide
			if(slidePressed && !wasSlidePressed && !slamming)
			{
				if(!isGrounded(slideStartGroundTolerance) && !verticalCollision && curSlamCooldown == 0 && !isTouchingWater()) //start slam
				{
					hivel.cancelDash();
					slamTicks = 0;
					setSlammingClient(true);
					strongSlam = true;
					setSlidingClient(false);
					hivel.setIgnoreSlowdown(false);
					hivel.setAirControlIncreased(false);
					if(isMainPlayer())
						PlayerAnimator.playAnimation(client.player, PlayerAnimator.SLAM_LOOP, 5, false);
				}
				else if(!jumping && !hivel.isDashing() && !hivel.isSliding()) //start slide if possible
				{
					BlockPos pos = posToBlock(getPos().add(Vec3d.fromPolar(0f, getYaw()).normalize()));
					setSliding((isGrounded(slideStartGroundTolerance) || verticalCollision) && isUnSolid(pos), hivel.isSliding());
				}
				else if(hivel.isSliding()) //cancel slide cause it's not even possible rn
					setSlidingClient(false);
			}
			//cancel strong slam
			if(strongSlam && !slidePressed)
				strongSlam = false;
			//slam jump
			if(slamJumpTimer > 0 && jumping && !wasJumping)
			{
				slamJumpTimer = -1;
				if(slidePressed && !strongSlam) //Dive // Ultradive
				{
					setVelocity(Vec3d.fromPolar(0, getYaw()).multiply(slamStored ? slamStoreDiveVelocity : slamDiveVelocity)
										.add(0, getJumpVelocity() * slamDiveVerticalVelocityMultiplier, 0));
					hivel.setIgnoreSlowdown(true);
					setSlidingClient(false);
					if(isMainPlayer())
						PlayerAnimator.playAnimation(client.player,
								slamStored ? PlayerAnimator.SLAMSTORE_DIVE : PlayerAnimator.SLAM_DIVE, 0, false);
				}
				else
				{
					setVelocity(0f, getJumpVelocity() * (slamStored ? slamStoreJumpVelocityMultiplier : slamJumpVelocityMultiplier) +
							slamTicks * slamTickVelocityBonus, 0f);
					if(isMainPlayer())
						PlayerAnimator.playAnimation(client.player, PlayerAnimator.SLAM_JUMP, 0, false);
				}
				slamStored = false;
				slamTicks = 0;
			}
			if(slamming && verticalCollision && getVelocity().y < 0f) //slam impact
			{
				setSlammingClient(false);
				slamJumpTimer = slamJumpWindow;
				curSlamCooldown = slamCooldown;
				slideVelocity = slamStored ? slamStoreSlideVelocity : slamSlideVelocity;
				if(isMainPlayer())
					PlayerAnimator.playAnimation(client.player, PlayerAnimator.SLAM_IMPACT, 0, false);
			}
			//slam velocity
			if(slamming)
			{
				slamTicks++;
				if(!slamStored)
					setVelocity(0f, -slamVelocity, 0f);
				else if(grounded)
					slamStored = false;
				if(jumping && wasJumping) //prevent slam spam by holding down space
					disableJumpTicks = slamDisableJumpTicks;
			}
			//slide tick
			if(hivel.isSliding())
			{
				if(jumping && !wasJumping && canJump()) //slide jump
				{
					setVelocity(slideDir.multiply(Math.min(slideVelocity + slideJumpSpeedBonus, slideSpeedSoftCap)));
					addVelocity(0, getJumpVelocity() * slideJumpVerticalVelocityMultiplier, 0);
					hivel.setIgnoreSlowdown(true);
					curSlidePreservationTicks = -1;
				}
				else //slide velocity
					setVelocity(slideDir.multiply(slideVelocity).add(0f, getVelocity().y, 0f));
				boolean moved = new Vec3d(lastX, lastBaseY, lastZ).distanceTo(getPos()) > slideVelocity / 2f || Ultracraft.isTimeFrozen() || slideTicks < 1;
				slideTicks++;
				if(!(slidePressed && !slamming && moved && !jumping))
				{
					setSliding(false, hivel.isSliding()); //stop slide
					if(!jumping)
						curSlidePreservationTicks = slidePreservationTicks;
				}
				if(!grounded)
					slideTicks = 0;
			}
			
			//skim on liquids
			BlockPos belowPos = posToBlock(getPos().subtract(0f, 0.1f, 0f));
			FluidState fluidBelow = getWorld().getBlockState(belowPos).getFluidState();
			if(hivel.isSliding() && !fluidBelow.getFluid().equals(Fluids.EMPTY) && !fluidBelow.isIn(TagRegistry.UNSKIMMABLE_FLUIDS) &&
					   getWorld().getFluidState(posToBlock(getPos().add(0f, 0.1f, 0f))).getFluid().equals(Fluids.EMPTY))
			{
				Vec3d vel = getVelocity();
				setVelocity(new Vec3d(vel.x, Math.max(baseJumpVelocity / 2f, vel.y * -skimUpwardsVelocityMultiplier), vel.z));
				PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
				buf.writeVector3f(getPos().toVector3f());
				ClientPlayNetworking.send(PacketRegistry.SKIM_C2S_PACKET_ID, buf);
			}
			
			//wall sliding
			ArrayList<VoxelShape> touchingWalls = new ArrayList<>();
			//x and z axis are checked seperately because we don't want diagonal false-positives
			getWorld().getBlockCollisions(this, getBoundingBox().expand(0.1f, 0, 0f)).forEach(touchingWalls::add); //x-axis wall check
			getWorld().getBlockCollisions(this, getBoundingBox().expand(0f, 0, 0.1f)).forEach(touchingWalls::add); //z-axis wall check
			boolean isTouchingWall = touchingWalls.size() > 0;
			//wall slide (also gated by the wallJumps config so that setting it to 0 fully disables the wall ability, not just the jump)
			if(wallJumps > 0 && !slamming && !hivel.isSliding() && isTouchingWall && !grounded)
			{
				Vec3d vel = getVelocity();
				setVelocity(new Vec3d(vel.x, Math.max(vel.y, -wallSlideVelocity), vel.z));
			}
			//wall jump (also gated directly by the wallJumps config so a value of 0 fully disables it, even before/without a config sync)
			if(wallJumps > 0 && curWallJumps > 0 && !isGrounded(0.5f) && jumping && !wasJumping && !lastOnGround && isTouchingWall &&
					   (UltracraftClient.isSlamStorageEnabled() || !slamming) && !hivel.isSliding())
				wallJump(touchingWalls, hivel);

			//Underwater vertical control (overrides the slam/jump vertical, only in water, dashing keeps its own velocity):
			//Space ascends fast, Ctrl(slide) descends a limited distance (~4-5 blocks) then stops, otherwise hover (no infinite sink).
			if(isTouchingWater() && !hivel.isDashing())
			{
				if(slamming)
					setSlammingClient(false);
				Vec3d v = getVelocity();
				if(jumping)
				{
					setVelocity(v.x, waterAscendVelocity, v.z);
					waterDescendRemaining = waterDescendDistance;
				}
				else if(slidePressed && waterDescendRemaining > 0f)
				{
					setVelocity(v.x, -waterDescendVelocity, v.z);
					waterDescendRemaining -= waterDescendVelocity;
				}
				else if(isSubmergedInWater())
				{
					//Submerged = swimming: hover at the current depth (stop sinking) so the player floats instead of dropping to the bottom.
					double ny = v.y * 0.5;
					if(ny < 0d && ny > -0.02d)
						ny = 0d;
					setVelocity(v.x, ny, v.z);
					if(!slidePressed)
						waterDescendRemaining = waterDescendDistance;
				}
				else
				{
					//Not submerged yet (just entered from the surface): actively sink in instead of skating on top, so even a
					//fast-moving player gets pulled under. Once the head goes under, the submerged branch above takes over and hovers.
					setVelocity(v.x, Math.min(v.y, -waterSinkVelocity), v.z);
					if(!slidePressed)
						waterDescendRemaining = waterDescendDistance;
				}
			}

			//stop ignoring slowdown and increasing slowdown
			if((verticalCollision && grounded) && !hivel.isDashing())
			{
				hivel.setAirControlIncreased(false);
				hivel.setIgnoreSlowdown(false);
			}
			
			//update movement data
			if(true)
			{
				networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(getX(), getY(), getZ(), getYaw(), getPitch(), isOnGround()));
				lastX = getX();
				lastBaseY = getY();
				lastZ = getZ();
				ticksSinceLastPositionPacketSent = 0;
				lastYaw = getYaw();
				autoJumpEnabled = client.options.getAutoJump().getValue();
				//slam impact
				if(wasSlamming != slamming)
				{
					boolean strong = strongSlam && !slamming && hivel.consumeStamina();
					PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
					buf.writeBoolean(slamming);
					buf.writeBoolean(strong);
					ClientPlayNetworking.send(PacketRegistry.SLAM_C2S_PACKET_ID, buf);
					if(slamming)
						startSlam();
					else
						endSlam(strong);
				}
				wasSlamming = slamming;
			}
			if(hivel.isSliding() && slideVelocity > baseSlideVelocity && slideTicks > slideSlowdownTicks)
				slideVelocity = Math.max(baseSlideVelocity, slideVelocity * slideSlowdownMultiplier);
			wasJumping = jumping;
			lastOnGround = isOnGround();
			wasDashPressed = dashPressed;
			wasSlidePressed = slidePressed;
			grounded = newGrounded;
			if(curSlidePreservationTicks > 0 && !hivel.isSliding())
			{
				curSlidePreservationTicks--;
				if(curSlidePreservationTicks == 0)
					slideVelocity = baseSlideVelocity;
			}
			setSprinting(false);
			ci.cancel();
		}
		else
		{
			if (hivel.shouldIgnoreSlowdown())
				hivel.setIgnoreSlowdown(false);
			if((!wasHivel || getAbilities().flying || isSpectator()) && hivel.isSliding())
				setSliding(false, true);
			if(slamming)
				cancelSlam();
		}
		wasHivel = wings.isActive();
	}
	
	boolean canJump()
	{
		return disableJumpTicks == 0 && (grounded || coyote < coyoteThreshold);
	}
	
	void wallJump(ArrayList<VoxelShape> touchingWalls, IHivelComponent hivel)
	{
		Vec3d vel = new Vec3d(0, 0, 0);
		Optional<Integer> X = Optional.empty();
		Optional<Integer> Z = Optional.empty();
		for (VoxelShape shape : touchingWalls)
		{
			Optional<Vec3d> opos = shape.getClosestPointTo(getPos());
			if(opos.isEmpty())
				continue;
			Vec3d v = getMainAxisDir(getPos().multiply(1, 0, 1).subtract(opos.get().multiply(1, 0, 1)).normalize());
			if(X.isPresent() && v.z != 0 && Math.abs(X.get() - opos.get().x) < 1f)
				continue;
			if(Z.isPresent() && v.x != 0 && Math.abs(Z.get() - opos.get().z) < 1f)
				continue;
			if(v.x != 0)
				Z = Optional.of((int)opos.get().z);
			else
				X = Optional.of((int)opos.get().x);
			vel = vel.add(v);
		}
		vel = new Vec3d(MathHelper.clamp(vel.x, -1f, 1f), 0, MathHelper.clamp(vel.z, -1f, 1f));
		setVelocity(vel.normalize().multiply(wallJumpHorizontalVelocity));
		addVelocity(0f, getJumpVelocity() * wallJumpVerticalVelocityMultiplier, 0f);
		if(slamming && UltracraftClient.isSlamStorageEnabled())
			slamStored = true;
		if(!isCreative())
			curWallJumps--;
		hivel.setIgnoreSlowdown(true);
		hivel.setAirControlIncreased(true);
	}
	
	@Inject(method = "tickNewAi", at = @At("RETURN"))
	void onTickAI(CallbackInfo ci)
	{
		if(isCamera())
		{
			jumping = jumping && disableJumpTicks == 0;
			if(disableJumpTicks > 0)
				disableJumpTicks--;
			if(curSlamCooldown > 0)
				curSlamCooldown--;
			if(slamJumpTimer > 0)
			{
				slamJumpTimer--;
				if(slamJumpTimer == 0 && slamStored)
					slamStored = false;
			}
		}
	}
	
	@Inject(method = "damage", at = @At("RETURN"))
	void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir)
	{
		if(WingCustomizationScreen.MenuOpen)
			WingCustomizationScreen.Instance.close();
	}
	
	Vec3d getMainAxisDir(Vec3d in)
	{
		//turns a relative position into a normalized direction on a single axis
		return Math.abs(in.x) > Math.abs(in.z) ? new Vec3d(in.x > 0f ? 1f : -1f, 0f, 0f) : new Vec3d(0f, 0f, in.z > 0f ? 1f : -1f);
	}
	
	void cancelSlam()
	{
		setSlammingClient(wasSlamming = false);
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		buf.writeBoolean(slamming);
		buf.writeBoolean(false);
		ClientPlayNetworking.send(PacketRegistry.SLAM_C2S_PACKET_ID, buf);
		endSlam(false);
	}
	
	void setSliding(boolean sliding, boolean last)
	{
		if(sliding == last)
			return;
		IHivelComponent hivel = UltraComponents.HIVEL.get(this);
		setSlidingClient(sliding);
		if(sliding && !last)
		{
			Vec2f movementDir = input.getMovementInput();
			slideStartedSideways = movementDir.x != 0f;
			if(movementDir.lengthSquared() == 0)
				slideDir = Vec3d.fromPolar(0f, getYaw()).normalize();
			else
				slideDir = new Vec3d(movementDir.x, 0, movementDir.y).rotateY((float)Math.toRadians(-getRotationClient().y)).normalize();
			if(isMainPlayer())
				PlayerAnimator.playAnimation(client.player, PlayerAnimator.START_SLIDE, 0, false);
		}
		else if(!sliding && last && isMainPlayer())
		{
			slideVelocity = Math.max(baseSlideVelocity, slideVelocity * 0.8f);
			PlayerAnimator.playAnimation(client.player, PlayerAnimator.STOP_SLIDE, 0, false);
		}
		if(!hivel.wasDashing())
			slideVelocity = Math.max(baseSlideVelocity, Math.max((float)getVelocity().multiply(1f, 0f, 1f).length(), last ? 0f : slideVelocity));
		else
			slideVelocity = baseSlideVelocity;
		slideTicks = 0;
		if(sliding)
			curSlidePreservationTicks = -1;
	}
	
	boolean isUnSolid(BlockPos pos)
	{
		BlockState state = getWorld().getBlockState(pos);
		return !state.hasSolidTopSurface(getWorld(), pos, this);
	}
	
	public boolean isGrounded(float distance)
	{
		if(isBlockHit(groundCheck(getPos(), distance)))
			return true;
		Box box = getBoundingBox();
		float y = (float)box.getMin(Direction.Axis.Y);
		if(isBlockHit(groundCheck(new Vec3d(box.getMin(Direction.Axis.X), y, box.getMin(Direction.Axis.Z)), distance)))
			return true;
		if(isBlockHit(groundCheck(new Vec3d(box.getMax(Direction.Axis.X) - 0.05, y, box.getMin(Direction.Axis.Z)), distance)))
			return true;
		if(isBlockHit(groundCheck(new Vec3d(box.getMin(Direction.Axis.X), y, box.getMax(Direction.Axis.Z) - 0.05), distance)))
			return true;
		return isBlockHit(groundCheck(new Vec3d(box.getMax(Direction.Axis.X) - 0.05, y, box.getMax(Direction.Axis.Z) - 0.05), distance));
	}
	
	BlockHitResult groundCheck(Vec3d start, float distance)
	{
		return getWorld().raycast(new RaycastContext(start, start.subtract(0, distance, 0),
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
	}
	
	boolean isBlockHit(BlockHitResult hit)
	{
		return hit != null && hit.getType().equals(HitResult.Type.BLOCK);
	}
	
	@WrapOperation(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;hasForwardMovement()Z"))
	boolean onTickMovement(Input instance, Operation<Boolean> original)
	{
		IWingDataComponent wings = UltraComponents.WING_DATA.get(this);
		if(wings.isActive())
			return true;
		return original.call(instance);
	}
	
	@WrapOperation(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;setSprinting(Z)V"))
	void onTickMovement(ClientPlayerEntity instance, boolean sprinting, Operation<Void> original)
	{
		IWingDataComponent wings = UltraComponents.WING_DATA.get(this);
		//cancel normal sprint triggers when in HiVelMode
		if(!wings.isActive() || isSpectator() || getAbilities().flying)
			original.call(instance, sprinting);
	}
	
	@WrapOperation(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
	boolean redirectIsUsingItem(ClientPlayerEntity instance, Operation<Boolean> original)
	{
		if(activeHand != null && getStackInHand(activeHand).getItem() instanceof AbstractWeaponItem)
			return false;
		return original.call(instance);
	}
	
	@ModifyReturnValue(method = "isSneaking", at = @At("RETURN"))
	boolean onIsSneaking(boolean original)
	{
		IWingDataComponent wings = UltraComponents.WING_DATA.get(this);
		if(wings.isActive() && !(isSpectator() || getAbilities().flying))
			return false;
		return original;
	}
	
	@Override
	public Vec3d getSlideDir()
	{
		return slideDir;
	}
	
	BlockPos posToBlock(Vec3d vec)
	{
		return new BlockPos(new Vec3i((int)Math.floor(vec.x), (int)Math.floor(vec.y), (int)Math.floor(vec.z)));
	}
	
	@Override
	public TerminalBlockEntity getFocusedTerminal()
	{
		return focusedTerminal;
	}
	
	@Override
	public void setFocusedTerminal(TerminalBlockEntity terminal)
	{
		MinecraftClient client = MinecraftClient.getInstance();
		if(terminal != null)
		{
			if(this.focusedTerminal != terminal)
				sendMessage(Text.translatable("screen.ultracraft.terminal.unfocus"), true);
			if(getWorld().isClient)
			{
				MinecraftClient.getInstance().gameRenderer.setRenderHand(false);
				client.setScreen(new TerminalScreen(terminal));
			}
		}
		else
		{
			this.focusedTerminal.unFocus(this);
			client.gameRenderer.setRenderHand(true);
			if(client.currentScreen instanceof TerminalScreen)
				client.setScreen(null);
		}
		this.focusedTerminal = terminal;
	}
	
	@Override
	public float getScreenShake()
	{
		return screenshake;
	}
	
	@Override
	public void addScreenshake(float val)
	{
		ClientConfig config = UltracraftClient.getConfig();
		if(config.screenshake)
			screenshake += config.safeVFX ? Math.min(val / 5f, 1f) : val;
	}
	
	public void setSlidingClient(boolean v)
	{
		UltraComponents.HIVEL.get(this).setSliding(v);
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		buf.writeBoolean(v);
		ClientPlayNetworking.send(PacketRegistry.SLIDE_STATE_PACKET_ID, buf);
	}
	
	public void setSlammingClient(boolean v)
	{
		slamming = v;
		if(!v && slamStored)
			slamStored = false;
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		buf.writeBoolean(v);
		ClientPlayNetworking.send(PacketRegistry.SLAM_STATE_PACKET_ID, buf);
	}
}
