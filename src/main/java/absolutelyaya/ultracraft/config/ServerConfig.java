package absolutelyaya.ultracraft.config;

import absolutelyaya.ultracraft.Ultracraft;
import net.minecraft.server.MinecraftServer;

public class ServerConfig extends Config
{
	public static ServerConfig INSTANCE;
	
	public final EnumEntry<ProjectileBoostSetting> projboost = new EnumEntry<>("ProjectileBoost", ProjectileBoostSetting.LIMITED);
	public final EnumEntry<Setting> hivel = new EnumEntry<>("HiVelMode", Setting.FREE);
	public final EnumEntry<Setting> timestop = new EnumEntry<>("TimeStop", Setting.FORCE_OFF).setValidOptions(new Setting[] {Setting.FORCE_ON, Setting.FORCE_OFF});
	public final EnumEntry<RegenSetting> bloodHeal = new EnumEntry<>("BloodHeal", RegenSetting.NEVER);
	public final EnumEntry<GraffitiSetting> graffiti = new EnumEntry<>("Graffiti", GraffitiSetting.ALLOW_ALL);
	public final BooleanEntry disableHandswap = new BooleanEntry("DisableHandswap", false);
	public final BooleanEntry effectivelyViolent = new BooleanEntry("EffectivelyViolent", false);
	public final BooleanEntry explosionBlockBreaking = new BooleanEntry("Explosion-BlockBreaking", true);
	public final BooleanEntry tntPriming = new BooleanEntry("Explosion-TntPriming", true);
	public final BooleanEntry smSafeLedges = new BooleanEntry("Swordsmachine-SafeLedges", false);
	public final BooleanEntry parryChaining = new BooleanEntry("ParryChaining", false);
	public final BooleanEntry terminalProtection = new BooleanEntry("TerminalProtection", true);
	public final BooleanEntry flamethrowerGrief = new BooleanEntry("FlamethrowerGrief", false);
	public final IntegerEntry hellObserverInterval = new IntegerEntry("HellObserverInterval", 5);
	public final BooleanEntry bloodSaturation = new BooleanEntry("BloodSaturation", false);
	public final BooleanEntry dodgeableOverpump = new BooleanEntry("DodgeableOverpump", false);
	public final BooleanEntry customLevelsUnlocked = new BooleanEntry("UnlockAllCustomLevels", false);
	public final FloatEntry parryRange = (FloatEntry)new FloatEntry("ParryRange", 3f).setRange(0f, Float.MAX_VALUE);
	public final FloatEntry coinPunchRange = (FloatEntry)new FloatEntry("CoinPunchRange", 4f).setRange(0f, Float.MAX_VALUE);
	public final BooleanEntry disableModificationSuppression = new BooleanEntry("DisableModificationSuppression", false);
	public final BooleanEntry protectNature = new BooleanEntry("ProtectNature", false);
	//Weapon Damage
	public final FloatEntry feedbackerDamage = (FloatEntry)new FloatEntry("FeedbackerDamage", 1f).setRange(0f, Float.MAX_VALUE);
	public final FloatEntry knuckleblasterDamage = (FloatEntry)new FloatEntry("KnuckleblasterDamage", 2.5f).setRange(0f, Float.MAX_VALUE);
	public final FloatEntry revolverDamage = (FloatEntry)new FloatEntry("RevolverDamage", 1f).setRange(0f, Float.MAX_VALUE);
	public final FloatEntry shotgunDamage = (FloatEntry)new FloatEntry("ShotgunDamage", 1f).setRange(0f, Float.MAX_VALUE);
	public final FloatEntry nailgunDamage = (FloatEntry)new FloatEntry("NailgunDamage", 1f).setRange(0f, Float.MAX_VALUE);
	//Debug
	public final BooleanEntry disableFixedStructures = new BooleanEntry("DisableFixedStructures", false);
	public final IntegerEntry version = new IntegerEntry("ConfigVersion", 0);
	
	public ServerConfig(MinecraftServer server)
	{
		super(server, "server");
		entries.add(new Comment(" ## ############################# ##  #"));
		entries.add(new Comment("     Welcome to Config Zone"));
		entries.add(new Comment(" ## ############################# ##  #"));
		entries.add(projboost);
		entries.add(hivel);
		entries.add(timestop);
		entries.add(bloodHeal);
		entries.add(graffiti);
		entries.add(disableHandswap);
		entries.add(effectivelyViolent);
		entries.add(explosionBlockBreaking);
		entries.add(tntPriming);
		entries.add(smSafeLedges);
		entries.add(parryChaining);
		entries.add(terminalProtection);
		entries.add(flamethrowerGrief);
		entries.add(hellObserverInterval);
		entries.add(bloodSaturation);
		entries.add(dodgeableOverpump);
		entries.add(customLevelsUnlocked);
		entries.add(parryRange);
		entries.add(coinPunchRange);
		entries.add(disableModificationSuppression);
		entries.add(protectNature);
		entries.add(new Comment(" ## ############################# ##  #"));
		entries.add(new Comment("      Weapon Damage Multipliers"));
		entries.add(new Comment(" ## ############################# ##  #"));
		entries.add(feedbackerDamage);
		entries.add(knuckleblasterDamage);
		entries.add(revolverDamage);
		entries.add(shotgunDamage);
		entries.add(nailgunDamage);
		entries.add(new Comment(" ## ############################# ##  #"));
		entries.add(new Comment("           Debug stuff"));
		entries.add(new Comment(" ## ############################# ##  #"));
		entries.add(disableFixedStructures);
		entries.add(version);
		
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
		return "server.properties";
	}
	
	@Override
	public void load(MinecraftServer server)
	{
		super.load(server);
		boolean updated = false;
		int newest = 1;
		Object version = this.version.value;
		if(version == null)
			version = 0;
		for (int i = (int)version; i < newest; i++) //update settings based on changes over versions
		{
			switch(i)
			{
				case 0 -> {
					if(customLevelsUnlocked.getValue()) customLevelsUnlocked.setValue(false);
				}
			}
			updated = true;
		}
		this.version.setValue(newest);
		if(updated)
			save(server);
		Ultracraft.LOGGER.info("Ultracraft Server Config has been changed based on Version default value Changes.");
		Ultracraft.LOGGER.info("Ultracraft Server Config Loaded.");
	}
}
