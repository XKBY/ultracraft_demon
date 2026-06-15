package absolutelyaya.ultracraft.config;

import absolutelyaya.ultracraft.Ultracraft;
import net.minecraft.server.MinecraftServer;

public class HivelConfig extends Config
{
	public static HivelConfig INSTANCE;
	
	public final IntegerEntry jumpBoost = new IntegerEntry("JumpBoost", 2);
	public final BooleanEntry fallDamage = new BooleanEntry("FallDamage", false);
	public final BooleanEntry drowning = new BooleanEntry("Drowning", false);
	public final BooleanEntry storage = new BooleanEntry("SlamStorage", true);
	public final FloatEntry speed = new FloatEntry("Speed", 1.7f);
	public final FloatEntry gravity = (FloatEntry)new FloatEntry("Gravity", 1.0f).setRange(0f, 1f);
	public final IntegerEntry iFrames = new IntegerEntry("IFrames", 2);
	
	public final IntegerEntry slamCooldown = new IntegerEntry("slamCooldown", 5);
	public final IntegerEntry slamJumpWindow = new IntegerEntry("slamJumpWindow", 4);
	public final IntegerEntry coyoteThreshold = new IntegerEntry("coyoteThreshold", 4);
	public final IntegerEntry slidePreservationTicks = new IntegerEntry("slidePreservationTicks", 5);
	public final IntegerEntry slideSlowdownTicks = new IntegerEntry("slideSlowdownTicks", 20);
	public final IntegerEntry wallJumps = new IntegerEntry("wallJumps", 0);
	public final IntegerEntry slamDisableJumpTicks = new IntegerEntry("slamDisableJumpTicks", 8);
	public final FloatEntry drag = new FloatEntry("MoveTechSlowdown", 1f);
	public final FloatEntry dragVelocitySoftcap = new FloatEntry("MoveTechVelocitySoftcap", 0.4f);
	public final IntegerEntry dashTicks = new IntegerEntry("DashTicks", 4);
	public final FloatEntry staminaRegen = new FloatEntry("StaminaRegeneration", 1.5f);
	public final FloatEntry slamDamageMargin = new FloatEntry("SlamDamageMargin", 0.25f);
	public final FloatEntry strongSlamImpactMargin = new FloatEntry("StrongSlamImpactMargin", 3f);
	public final FloatEntry strongSlamImpactVelocity = new FloatEntry("StrongSlamImpactVelocity", 1f);
	public final FloatEntry slamVelocity = new FloatEntry("slamVelocity", 2f);
	public final FloatEntry baseSlideVelocity = new FloatEntry("baseSlideVelocity", 0.4f);
	public final FloatEntry baseJumpVelocity = new FloatEntry("baseJumpVelocity", 0.42f);
	public final FloatEntry dashVelocity = new FloatEntry("dashVelocity", 1f);
	public final FloatEntry skeweredDashVelocity = new FloatEntry("skeweredDashVelocity", 0.05f);
	public final FloatEntry dashSlipAndSlideThreshold = new FloatEntry("dashSlipAndSlideThreshold", 0.6f);
	public final FloatEntry dashSlipAndSlideReduction = new FloatEntry("dashSlipAndSlideReduction", 0.5f);
	public final FloatEntry dashAirStopVelocityMultiplier = new FloatEntry("dashAirStopVelocityMultiplier", 0.3f);
	public final FloatEntry skeweredDashAirStopVelocityMultiplier = new FloatEntry("skeweredDashAirStopVelocityMultiplier", 0.03f);
	public final FloatEntry slideJumpSpeedBonus = new FloatEntry("slideJumpSpeedBonus", 0.025f);
	public final FloatEntry slideSpeedSoftCap = new FloatEntry("slideSpeedSoftCap", 0.99f);
	public final FloatEntry slideSlowdownMultiplier = new FloatEntry("slideSlowdownMultiplier", 0.95f);
	public final FloatEntry slamJumpVelocityMultiplier = new FloatEntry("slamJumpVelocityMultiplier", 1.5f);
	public final FloatEntry slamDiveVelocity = new FloatEntry("slamDiveVelocity", 1.5f);
	public final FloatEntry slamStoreJumpVelocityMultiplier = new FloatEntry("slamStoreJumpVelocityMultiplier", 4.5f);
	public final FloatEntry slamStoreDiveVelocity = new FloatEntry("slamStoreDiveVelocity", 2.5f);
	public final FloatEntry slamTickVelocityBonus = new FloatEntry("slamTickVelocityBonus", 0.05f);
	public final FloatEntry slamSlideVelocity = new FloatEntry("slamSlideVelocity", 0.66f);
	public final FloatEntry slamStoreSlideVelocity = new FloatEntry("slamStoreSlideVelocity", 1f);
	public final FloatEntry skimUpwardsVelocityMultiplier = new FloatEntry("skimUpwardsVelocityMultiplier", 0.75f);
	public final FloatEntry wallSlideVelocity = new FloatEntry("wallSlideVelocity", 0.2f);
	public final FloatEntry wallJumpHorizontalVelocity = new FloatEntry("wallJumpHorizontalVelocity", 0.25f);
	public final FloatEntry wallJumpVerticalVelocityMultiplier = new FloatEntry("wallJumpVerticalVelocityMultiplier", 0.8f);
	public final FloatEntry groundCheckDistance = new FloatEntry("groundCheckDistance", 0.1f);
	public final FloatEntry dashGroundStopVelocityMultiplier = new FloatEntry("dashGroundStopVelocityMultiplier", 0.05f);
	public final FloatEntry slideStartGroundTolerance = new FloatEntry("slideStartGroundTolerance", 0.4f);
	public final FloatEntry slamDiveVerticalVelocityMultiplier = new FloatEntry("slamDiveVerticalVelocityMultiplier", 0.6f);
	public final FloatEntry dashJumpVerticalVelocityMultiplier = new FloatEntry("dashJumpVerticalVelocityMultiplier", 0.5f);
	public final FloatEntry slideJumpVerticalVelocityMultiplier = new FloatEntry("slideJumpVerticalVelocityMultiplier", 0.55f);
	public final FloatEntry offGroundSpeed = new FloatEntry("offGroundSpeed", 0.035f);
	
	public HivelConfig(MinecraftServer server)
	{
		super(server, "hivel");
		entries.add(new Comment(" ## ############################# ##  #"));
		entries.add(new Comment("         High Velocity Mode"));
		entries.add(new Comment(" ## ############################# ##  #"));
		entries.add(jumpBoost);
		entries.add(new Comment("1.2 == 120% speed in hivel"));
		entries.add(speed);
		entries.add(new Comment("0.8 == 80% gravity in hivel (range: 0.0-1.0)"));
		entries.add(gravity);
		entries.add(fallDamage);
		entries.add(drowning);
		entries.add(storage);
		entries.add(iFrames);
		entries.add(new Comment(" ## ############################# ##  #"));
		entries.add(new Comment("         Advanced Config"));
		entries.add(new Comment(""));
		entries.add(new Comment("If you mess these values up, that's your fault"));
		entries.add(new Comment("Delete the file and all default values will be restored"));
		entries.add(new Comment("Most Value Names are self explanatory;"));
		entries.add(new Comment("If you don't know what a value does, play around with it, but don't complain if you break something."));
		entries.add(new Comment(" ## ############################# ##  #"));
		entries.add(groundCheckDistance);
		entries.add(new Comment("Just look up \"coyote time\". Used for dash//slide jumping off of cliffs"));
		entries.add(coyoteThreshold);
		entries.add(baseSlideVelocity);
		entries.add(new Comment("How far the ground can be away to start sliding. Higher than the other groundchecks to make slide jumping easier//feel better"));
		entries.add(slideStartGroundTolerance);
		entries.add(new Comment("When on ground and not sliding, store slide Velocity for X ticks before resetting it to baseSlideVelocity"));
		entries.add(slidePreservationTicks);
		entries.add(new Comment("Ticks of uninterrupted sliding until a player slows down (to a minimum of baseSlideVelocity)"));
		entries.add(slideSlowdownTicks);
		entries.add(slideSlowdownMultiplier);
		entries.add(new Comment("Added to slide velocity when slide jumping and below slideVelocitySoftCap"));
		entries.add(slideJumpSpeedBonus);
		entries.add(slideSpeedSoftCap);
		entries.add(slideJumpVerticalVelocityMultiplier);
		entries.add(new Comment("Internally known as \"drag\". legacy value: 0.925"));
		entries.add(drag);
		entries.add(dragVelocitySoftcap);
		entries.add(dashTicks);
		entries.add(dashVelocity);
		entries.add(skeweredDashVelocity);
		entries.add(new Comment("minimum slipperiness of the ground to slide when a dash ends on it"));
		entries.add(dashSlipAndSlideThreshold);
		entries.add(new Comment("how much slipperines should be reduced for velocity calculations"));
		entries.add(dashSlipAndSlideReduction);
		entries.add(new Comment("how much velocity should be retained when a dash ends in the air"));
		entries.add(dashAirStopVelocityMultiplier);
		entries.add(skeweredDashAirStopVelocityMultiplier);
		entries.add(new Comment("how much velocity should be retained when a dash ends while grounded"));
		entries.add(dashGroundStopVelocityMultiplier);
		entries.add(dashJumpVerticalVelocityMultiplier);
		entries.add(staminaRegen);
		entries.add(slamVelocity);
		entries.add(slamCooldown);
		entries.add(slamJumpWindow);
		entries.add(new Comment("multiplies the jump velocity with jump boost and stuff taken into account"));
		entries.add(slamJumpVelocityMultiplier);
		entries.add(slamStoreJumpVelocityMultiplier);
		entries.add(slamDiveVelocity);
		entries.add(slamStoreDiveVelocity);
		entries.add(slamDiveVerticalVelocityMultiplier);
		entries.add(new Comment("added to the slam jump velocity for every tick a slam has lasted"));
		entries.add(slamTickVelocityBonus);
		entries.add(new Comment("when starting to slide immediately after slam impact, this is your velocity"));
		entries.add(slamSlideVelocity);
		entries.add(slamStoreSlideVelocity);
		entries.add(new Comment("Disable jumping for X ticks IF slam impacts while jump key is already held down"));
		entries.add(slamDisableJumpTicks);
		entries.add(skimUpwardsVelocityMultiplier);
		entries.add(wallSlideVelocity);
		entries.add(wallJumps);
		entries.add(wallJumpHorizontalVelocity);
		entries.add(wallJumpVerticalVelocityMultiplier);
		entries.add(slamDamageMargin);
		entries.add(strongSlamImpactMargin);
		entries.add(strongSlamImpactVelocity);
		entries.add(baseJumpVelocity);
		entries.add(new Comment("Hivel Default: 0.035, Vanilla Default 0.02"));
		entries.add(offGroundSpeed);
		
		load(server);
		INSTANCE = this;
	}
	
	@Override
	protected String getExportPath()
	{
		return "ultracraft/";
	}
	
	@Override
	protected String getFileName()
	{
		return "hivel.properties";
	}
	
	@Override
	public void load(MinecraftServer server)
	{
		super.load(server);
		Ultracraft.LOGGER.info("Ultracraft Hivel Config Loaded.");
	}
}
