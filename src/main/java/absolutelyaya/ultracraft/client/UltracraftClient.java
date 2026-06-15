package absolutelyaya.ultracraft.client;

import absolutelyaya.goop.client.GoopClient;
import absolutelyaya.ultracraft.client.gui.*;
import absolutelyaya.ultracraft.client.rendering.CybergrindArenaRenderer;
import absolutelyaya.ultracraft.compat.TrinketUtil;
import absolutelyaya.ultracraft.components.UltraComponents;
import absolutelyaya.ultracraft.Ultracraft;
import absolutelyaya.ultracraft.accessor.WingedPlayerEntity;
import absolutelyaya.ultracraft.api.terminal.TerminalCodeRegistry;
import absolutelyaya.ultracraft.client.gui.screen.ServerConfigScreen;
import absolutelyaya.ultracraft.client.rendering.EditModeRenderer;
import absolutelyaya.ultracraft.client.rendering.TrailRenderer;
import absolutelyaya.ultracraft.client.rendering.UltraHudRenderer;
import absolutelyaya.ultracraft.client.rendering.block.entity.*;
import absolutelyaya.ultracraft.client.rendering.entity.demon.*;
import absolutelyaya.ultracraft.client.rendering.entity.feature.*;
import absolutelyaya.ultracraft.client.rendering.entity.husk.FilthRenderer;
import absolutelyaya.ultracraft.client.rendering.entity.husk.GreaterFilthRenderer;
import absolutelyaya.ultracraft.client.rendering.entity.husk.SchismRenderer;
import absolutelyaya.ultracraft.client.rendering.entity.husk.StrayRenderer;
import absolutelyaya.ultracraft.client.rendering.entity.machine.DroneEntityRenderer;
import absolutelyaya.ultracraft.client.rendering.entity.machine.StreetCleanerEntityRenderer;
import absolutelyaya.ultracraft.client.rendering.entity.machine.SwordsmachineRenderer;
import absolutelyaya.ultracraft.client.rendering.entity.machine.V2Renderer;
import absolutelyaya.ultracraft.client.rendering.entity.other.*;
import absolutelyaya.ultracraft.client.rendering.entity.projectile.*;
import absolutelyaya.ultracraft.client.sound.*;
import absolutelyaya.ultracraft.compat.PlayerAnimator;
import absolutelyaya.ultracraft.components.player.IWingDataComponent;
import absolutelyaya.ultracraft.components.player.ProgressionComponent;
import absolutelyaya.ultracraft.config.*;
import absolutelyaya.ultracraft.entity.husk.AbstractHuskEntity;
import absolutelyaya.ultracraft.entity.machine.SwordsmachineEntity;
import absolutelyaya.ultracraft.entity.projectile.ChainsawEntity;
import absolutelyaya.ultracraft.entity.projectile.IHomingProjectile;
import absolutelyaya.ultracraft.entity.projectile.ThrownMachineSwordEntity;
import absolutelyaya.ultracraft.particle.*;
import absolutelyaya.ultracraft.registry.*;
import com.mojang.blaze3d.systems.RenderSystem;
import io.netty.buffer.Unpooled;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.particle.WaterBubbleParticle;
import net.minecraft.client.particle.WaterSplashParticle;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.resource.ResourceType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class UltracraftClient implements ClientModInitializer
{
	public static final EntityModelLayer WINGS_LAYER = new EntityModelLayer(Ultracraft.identifier("wings"), "main");
	public static final EntityModelLayer MALICIOUS_LAYER = new EntityModelLayer(Ultracraft.identifier("malicious"), "main");
	public static final EntityModelLayer ENRAGE_LAYER = new EntityModelLayer(Ultracraft.identifier("enraged"), "main");
	public static String wingPreset = "", wingPattern = "", wingOverlay = "";
	private static ShaderProgram wingsColoredProgram, wingsColoredUIProgram, texPosFade, flesh, sky;
	public static ClientHitscanHandler HITSCAN_HANDLER;
	public static TrailRenderer TRAIL_RENDERER;
	private static EditModeRenderer EDITMODE_RENDERER;
	public static boolean REPLACE_MENU_MUSIC = true, APPLY_ENTITY_POSES, GRAFFITI_WHITELISTED = true, SODIUM = true, IRIS = false;
	static boolean wasMovementSoundsEnabled, supporter = false, joinInfoPending, travelling;
	static float screenblood;
	static Vector3f[] wingColors = new Vector3f[] { new Vector3f(247f, 255f, 154f), new Vector3f(117f, 154f, 255f) };
	static final Vector3f[] defaultWingColors = new Vector3f[] { new Vector3f(247f, 255f, 154f), new Vector3f(117f, 154f, 255f) };
	static int visualFreezeTicks;
	static Optional<Boolean> forcedHivel = Optional.empty();
	
	static UltraHudRenderer hudRenderer;
	static WeaponInfoHUD weaponInfoHUD;
	static EditModeHUD editModeHUD;
	static TitleHUD titleHUD;
	static LevelHUD levelHUD;
	static CybergrindHUD cybergrindHUD;
	static ConfigHolder<ClientConfig> config;

	//Rendered from IngameHudMixin right before the chat, so the UltraHUD draws over the first-person hand (offhand item)
	//but still stays below chat, info popups and toasts.
	public static void renderUltraHud(float tickDelta)
	{
		if(hudRenderer != null)
			hudRenderer.render(tickDelta, MinecraftClient.getInstance().gameRenderer.getCamera());
	}

	//True when this Fabric mod is actually running on Forge (e.g. via Sinytra Connector), detected by the presence of a
	//Forge-only class. Used to avoid registering custom non-chunk block render layers that Forge hard-rejects.
	public static boolean isForgeLikePlatform()
	{
		try
		{
			Class.forName("net.minecraftforge.fml.loading.FMLLoader");
			return true;
		}
		catch(Throwable e)
		{
			return false;
		}
	}

	@Override
	public void onInitializeClient()
	{
		SODIUM = FabricLoader.getInstance().getModContainer("sodium").isPresent();
		IRIS = FabricLoader.getInstance().getModContainer("iris").isPresent();
		
		config = AutoConfig.register(ClientConfig.class, GsonConfigSerializer::new);
		wasMovementSoundsEnabled = config.get().movementSounds;
		KeybindRegistry.register();
		
		//EntityRenderers
		EntityRendererRegistry.register(EntityRegistry.FILTH, FilthRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.STRAY, StrayRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.SCHISM, SchismRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.MALICIOUS_FACE, MaliciousFaceRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.CERBERUS, CerberusRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.HIDEOUS_MASS, HideousMassRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.RETALIATION, RetaliationRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.SWORDSMACHINE, SwordsmachineRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.DESTINY_SWORDSMACHINE, SwordsmachineRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.HELL_BULLET, HellBulletRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.CERBERUS_BALL, CerberusBallRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.SHOTGUN_PELLET, ShotgunPelletRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.CANCER_BULLET, CancerBulletRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.EJECTED_CORE, EjectedCoreRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.THROWN_MACHINE_SWORD, ThrownMachineSwordRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.THROWN_COIN, ThrownCoinRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.FLAME, FlameRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.MORTAR, HideousMortarRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.HARPOON, HarpoonEntityRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.SOAP, ThrownSoapRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.MAGNET, MagnetEntityRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.NAIL, NailEntityRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.SHOCKWAVE, ShockwaveRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.VERICAL_SHOCKWAVE, VerticalShockwaveRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.INTERRUPTABLE_CHARGE, InterruptableChargeRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.SOUL_ORB, OrbRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.BLOOD_ORB, OrbRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.DRONE, DroneEntityRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.STREET_CLEANER, StreetCleanerEntityRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.BACK_TANK, BackTankRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.STAINED_GLASS_WINDOW, StainedGlassWindowRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.PROGRESSION_ITEM, ItemEntityRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.V2, V2Renderer::new);
		EntityRendererRegistry.register(EntityRegistry.BEAM, BeamProjectileRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.RODENT, RodentRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.GREATER_FILTH, GreaterFilthRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.CHAINSAW, ChainsawEntityRenderer::new);
		EntityRendererRegistry.register(EntityRegistry.JUMPSTART_HOOK, JumpstartHookRenderer::new);
		//Particles
		ParticleFactoryRegistry particleRegistry = ParticleFactoryRegistry.getInstance();
		particleRegistry.register(ParticleRegistry.MALICIOUS_CHARGE, MaliciousChargeParticle.Factory::new);
		particleRegistry.register(ParticleRegistry.DASH, DashParticle.Factory::new);
		particleRegistry.register(ParticleRegistry.SLIDE, SlideParticle.Factory::new);
		particleRegistry.register(ParticleRegistry.GROUND_POUND, GroundPoundParticle.Factory::new);
		particleRegistry.register(ParticleRegistry.EJECTED_CORE_FLASH, EjectedCoreFlashParticle.Factory::new);
		particleRegistry.register(ParticleRegistry.BLOOD_SPLASH, WaterSplashParticle.Factory::new);
		particleRegistry.register(ParticleRegistry.BLOOD_BUBBLE, WaterBubbleParticle.Factory::new);
		particleRegistry.register(ParticleRegistry.SOAP_BUBBLE, SoapBubbleParticle.Factory::new);
		particleRegistry.register(ParticleRegistry.RIPPLE, RippleParticle.Factory::new);
		particleRegistry.register(ParticleRegistry.PARRY_INDICATOR, ParryIndicatorParticle.Factory::new);
		particleRegistry.register(ParticleRegistry.TELEPORT, TeleportParticle.Factory::new);
		particleRegistry.register(ParticleRegistry.EXPLOSION, ExplosionParticle.Factory::new);
		particleRegistry.register(ParticleRegistry.RICOCHET_WARNING, RicochetWarningParticle.Factory::new);
		particleRegistry.register(ParticleRegistry.BIG_CIRCLE, BigCircleParticle.Factory::new);
		particleRegistry.register(ParticleRegistry.DRONE_CHARGE, DroneChargeParticle.Factory::new);
		particleRegistry.register(ParticleRegistry.SHOCK, ShockParticle.Factory::new);
		particleRegistry.register(ParticleRegistry.BUTTERFLY, ButterflyParticle.Factory::new);
		//Entity model layers
		EntityModelLayerRegistry.registerModelLayer(WINGS_LAYER, WingsModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(MALICIOUS_LAYER, MaliciousFaceModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(ENRAGE_LAYER, EnragedModel::getTexturedModelData);
		//BlockEntityRenderers
		BlockEntityRendererFactories.register(BlockEntityRegistry.PEDESTAL, PedestalBlockEntityRenderer::new);
		BlockEntityRendererFactories.register(BlockEntityRegistry.CERBERUS, context -> new CerberusBlockRenderer());
		BlockEntityRendererFactories.register(BlockEntityRegistry.TERMINAL, context -> new TerminalBlockEntityRenderer());
		BlockEntityRendererFactories.register(BlockEntityRegistry.HELL_OBSERVER, context -> new HellObserverRenderer());
		BlockEntityRendererFactories.register(BlockEntityRegistry.HELL_SPAWNER, context -> new HellSpawnerBlockRenderer());
		BlockEntityRendererFactories.register(BlockEntityRegistry.SKY, context -> new SkyBlockRenderer());
		BlockEntityRendererFactories.register(BlockEntityRegistry.MAP_CHECKPOINT, context -> new CheckpointRenderer());
		BlockEntityRendererFactories.register(BlockEntityRegistry.MAP_DOOR, context -> new DoorListenerRenderer());
		BlockEntityRendererFactories.register(BlockEntityRegistry.HANK, context -> new HankBlockEntityRenderer());
		//Player Animations
		PlayerAnimator.init();
		
		ModelPredicateRegistry.registerModels();
		ScreenHandlerRegistry.registerClient();
		
		WingColorPresetManager.restoreDefaults();
		
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new MusicMetadataManager());
		
		HITSCAN_HANDLER = new ClientHitscanHandler();
		TRAIL_RENDERER = new TrailRenderer();
		EDITMODE_RENDERER = new EditModeRenderer();
		
		ResourceManagerHelper.registerBuiltinResourcePack(new Identifier("ultracraft_non_essential"),
				FabricLoader.getInstance().getModContainer(Ultracraft.MOD_ID).orElseThrow(), Text.literal("ULTRACRAFT Non-Essential"),
				ResourcePackActivationType.DEFAULT_ENABLED);
		
		ClientTickEvents.END_WORLD_TICK.register((client) -> {
			HITSCAN_HANDLER.tick();
			if(visualFreezeTicks > 0)
				visualFreezeTicks--;
		});
		
		hudRenderer = new UltraHudRenderer();
		weaponInfoHUD = new WeaponInfoHUD();
		editModeHUD = new EditModeHUD();
		titleHUD = new TitleHUD();
		levelHUD = new LevelHUD();
		cybergrindHUD = new CybergrindHUD();
		HudRenderCallback.EVENT.register((context, delta) -> {
			weaponInfoHUD.render(context, delta);
			levelHUD.render(context, delta);
			cybergrindHUD.render(context);
		});
		
		ClientPlayConnectionEvents.INIT.register((handler, client) -> {
			new ServerConfig(client.getServer());
			new HivelConfig(client.getServer());
			if(config.get().serverJoinInfo)
				joinInfoPending = true;
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			refreshSupporter();
			IWingDataComponent wings = UltraComponents.WING_DATA.get(client.player);
			wings.setColor(wingColors[0], 0);
			wings.setColor(wingColors[1], 1);
			wings.setPattern(wingPattern);
			wings.setOverlay(wingOverlay);
			if(forcedHivel.isEmpty())
				wings.setActive(config.get().hivel);
			PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
			buf.writeVector3f(wings.getColors()[0]);
			buf.writeVector3f(wings.getColors()[1]);
			buf.writeString(wings.getPattern());
			buf.writeString(wings.getOverlay());
			ClientPlayNetworking.send(PacketRegistry.SEND_WING_DATA_C2S_PACKET_ID, buf);
			buf = new PacketByteBuf(Unpooled.buffer());
			buf.writeBoolean(config.get().armSkinThirdPerson);
			ClientPlayNetworking.send(PacketRegistry.ARM_VISIBLE_PACKET_ID, buf);
		});
		
		ClientEntityEvents.ENTITY_LOAD.register((entity, clientWorld) -> {
			if (entity instanceof PlayerEntity player)
			{
				if(config.get().movementSounds && player.getUuid().equals(MinecraftClient.getInstance().player.getUuid()))
				{
					SoundManager sound = MinecraftClient.getInstance().getSoundManager();
					sound.play(new MovingSlideSoundInstance(player));
					sound.play(new MovingWindSoundInstance(player));
				}
				UltraComponents.WING_DATA.get(player).sync();
				if(player.getUuid().equals(MinecraftClient.getInstance().player.getUuid()))
					return;
				PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
				buf.writeUuid(player.getUuid());
				ClientPlayNetworking.send(PacketRegistry.REQUEST_WINGED_DATA_PACKET_ID, buf);
			}
			else if (entity instanceof ThrownMachineSwordEntity sword)
				MinecraftClient.getInstance().getSoundManager().play(new MovingMachineSwordSoundInstance(sword));
			else if (entity instanceof SwordsmachineEntity sm)
				MinecraftClient.getInstance().getSoundManager().play(new MovingSwordsmachineSoundInstance(sm));
			else if (entity instanceof AbstractHuskEntity husk)
				MinecraftClient.getInstance().getSoundManager().play(new MovingHuskScreamSoundInstance(husk));
			else if (entity instanceof IHomingProjectile projectile)
				MinecraftClient.getInstance().getSoundManager().play(new MovingHomingProjectileSoundInstance(projectile));
			else if (entity instanceof ChainsawEntity projectile)
				MinecraftClient.getInstance().getSoundManager().play(new MovingChainsawSoundInstance(projectile));
		});
		
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			helper.register(new EnragedFeature<>(context.getModelLoader()));
			if(type.equals(EntityType.PLAYER))
			{
				helper.register(new WingsFeature<>((PlayerEntityRenderer)renderer, context.getModelLoader()));
				helper.register(new PlayerBackTankFeature<>((PlayerEntityRenderer)renderer));
				helper.register(new ArmFeature<>((PlayerEntityRenderer)renderer));
			}
		});
		
		WorldRenderEvents.BEFORE_ENTITIES.register((ctx) -> APPLY_ENTITY_POSES = true);
		
		WorldRenderEvents.AFTER_ENTITIES.register((ctx) -> {
			float delta = MinecraftClient.getInstance().getLastFrameDuration();
			UltracraftClient.HITSCAN_HANDLER.render(ctx.matrixStack(), ctx.camera(), ctx.tickDelta());
			UltracraftClient.TRAIL_RENDERER.render(ctx.matrixStack(), ctx.camera());
			UltracraftClient.EDITMODE_RENDERER.render(ctx.matrixStack(), ctx.camera(), delta);
			CybergrindArenaRenderer.render(ctx.matrixStack(), ctx.camera(), delta);
			APPLY_ENTITY_POSES = false;
		});
		
		HudRenderCallback.EVENT.register((matrices, delta) -> {
			if(config.get().safeVFX)
				return;
			if(config.get().bloodOverlay)
			{
				RenderSystem.enableBlend();
				String bloodName = GoopClient.getConfig().censorMature ? "textures/misc/blood_overlay_c" : "textures/misc/blood_overlay";
				MinecraftClient.getInstance().inGameHud.renderOverlay(matrices, Ultracraft.texIdentifier(bloodName + "3"),
						Math.min(screenblood - 1.25f, 0.75f));
				MinecraftClient.getInstance().inGameHud.renderOverlay(matrices, Ultracraft.texIdentifier(bloodName + "2"),
						Math.min(screenblood - 0.25f, Math.max(0.6f - Math.min(screenblood - 0.75f, 0.6f), 0f)));
				MinecraftClient.getInstance().inGameHud.renderOverlay(matrices, Ultracraft.texIdentifier(bloodName + "1"),
						Math.min(screenblood - 0.75f, 0.6f));
				screenblood = Math.max(0f, screenblood - delta / 120);
			}
			if(visualFreezeTicks > 0)
				MinecraftClient.getInstance().inGameHud.renderOverlay(matrices, Ultracraft.identifier("textures/misc/time_freeze_overlay.png"),
						0.25f);
		});
		
		WingPatterns.init();
		CoreShaderRegistrationCallback.EVENT.register((callback) -> {
			callback.register(Ultracraft.identifier("rendertype_wings_colored"), VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, (program) -> {
				program.getUniform("MetalColor");
				program.getUniform("WingColor");
				program.getUniform("Pattern");
				program.markUniformsDirty();
				wingsColoredProgram = program;
			});
			callback.register(Ultracraft.identifier("wings_colored_ui"), VertexFormats.POSITION_TEXTURE_COLOR, (program) -> {
				program.getUniform("MetalColor");
				program.getUniform("WingColor");
				program.getUniform("Pattern");
				program.markUniformsDirty();
				wingsColoredUIProgram = program;
			});
			callback.register(Ultracraft.identifier("position_tex_fade"), VertexFormats.POSITION_TEXTURE_COLOR, (program) -> {
				program.getUniform("TextureSize");
				program.markUniformsDirty();
				texPosFade = program;
			});
			callback.register(Ultracraft.identifier("flesh"), VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, (program) -> flesh = program);
			callback.register(Ultracraft.identifier("sky"), VertexFormats.POSITION_TEXTURE, (program) -> {
				program.getUniform("RotMat");
				program.markUniformsDirty();
				sky = program;
			});
		});
		
		ClientPacketRegistry.registerS2C();
		
		ClientTickEvents.END_WORLD_TICK.register(minecraft -> {
			Ultracraft.tickFreeze();
			UltracraftClient.TRAIL_RENDERER.tick();
			if(joinInfoPending)
				serverSyncFinished();
			PlayerEntity player = MinecraftClient.getInstance().player;
			if(player == null)
				return;
			if(!wasMovementSoundsEnabled && config.get().movementSounds)
			{
				SoundManager sound = MinecraftClient.getInstance().getSoundManager();
				sound.play(new MovingSlideSoundInstance(player));
				sound.play(new MovingWindSoundInstance(player));
			}
			wasMovementSoundsEnabled = config.get().movementSounds;
			if(UltraComponents.WING_DATA.get(player).isActive() && !(UltraComponents.PROGRESSION.get(player).isUnlocked(ProgressionComponent.HIVEL) ||
						 (Ultracraft.TRINKETS && TrinketUtil.isHasTrinketEquipped(player, ItemRegistry.HIVEL_WINGS))))
				setHiVel(false, false);
		});
		//Block Layers
		FluidRenderHandlerRegistry.INSTANCE.register(FluidRegistry.STILL_BLOOD, FluidRegistry.Flowing_BLOOD,
				new SimpleFluidRenderHandler(Ultracraft.identifier("block/blood_still"), Ultracraft.identifier("block/blood_flow")));
		BlockRenderLayerMap.INSTANCE.putFluids(RenderLayer.getTranslucent(), FluidRegistry.STILL_BLOOD, FluidRegistry.Flowing_BLOOD);
		//The custom Flesh render layer is a non-chunk layer that only works with vanilla Fabric's lenient chunk rendering.
		//Sodium rejects it, and Forge (e.g. under Sinytra Connector) hard-validates block render layers against the vanilla
		//chunk layers and crashes on it, so fall back to a plain solid chunk layer on those platforms.
		BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.FLESH,
				(SODIUM || isForgeLikePlatform()) ? RenderLayer.getSolid() : RenderLayers.getFlesh());
		BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.ADORNED_RAILING, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.VENT_COVER, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.SLAB_BLOCK, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.SKY_BLOCK, RenderLayer.getTranslucent());
		BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.FLOWERBED, RenderLayer.getTranslucent());
		BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.ZOOTYCOONCHAINLINKFENCE, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.FAKE_LEAVES, RenderLayer.getTranslucent());
		BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.HYACINTH, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.POTTED_HYACINTH, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.DARKNESS, RenderLayer.getTranslucent());
		
		//TerminalCodeRegistry.registerCode("florp", t -> t.setTab(new PetTab()));
		TerminalCodeRegistry.registerCode("somethingwicked", new TerminalCodeRegistry.Result(t -> {
			t.setColorOverride(0x460006);
			MinecraftClient.getInstance().player.sendMessage(Text.of("Something Wicked this way comes"), true);
		}, SoundEvents.ENTITY_PLAYER_BREATH, 1.15f));
		
		setWingColor(config.get().wingColors[0].toVector3f(), 0);
		setWingColor(config.get().wingColors[1].toVector3f(), 1);
		wingPreset = config.get().wingPreset;
		setWingPattern(config.get().wingPattern);
		setWingOverlay(config.get().wingOverlay);
		
		refreshSupporter();
	}
	
	public static boolean sendJoinInfo(MinecraftClient client, boolean manual)
	{
		ServerConfig sConfig = ServerConfig.INSTANCE;
		HivelConfig hivelConfig = HivelConfig.INSTANCE;
		if(client.player == null || sConfig == null)
			return false;
		client.player.sendMessage(Text.translatable("message.ultracraft.join-info-header"));
		if(!sConfig.hivel.getValue().equals(Setting.FREE))
			client.player.sendMessage(Text.translatable("message.ultracraft.hi-vel-forced",
					sConfig.hivel.getValue().equals(Setting.FORCE_ON) ? Text.translatable("options.on") : Text.translatable("options.off")));
		else
			client.player.sendMessage(Text.translatable("message.ultracraft.hi-vel-free"));
		if(client.getServer() != null && client.getServer().isRemote())
			client.player.sendMessage(Text.translatable("message.ultracraft.freeze-forced",
					sConfig.timestop.getValue().equals(Setting.FORCE_ON) ? Text.translatable("options.on") : Text.translatable("options.off")));
		client.player.sendMessage(Text.translatable("message.ultracraft.attributes", hivelConfig.speed.getValue(), hivelConfig.jumpBoost.getValue(),
				(hivelConfig.gravity.getValue() * 100f)).append("%"));
		client.player.sendMessage(Text.translatable("message.ultracraft.blood-heal." + sConfig.bloodHeal.getValue().name()));
		if(hivelConfig.fallDamage.getValue())
			client.player.sendMessage(Text.translatable("message.ultracraft.fall-damage"));
		if(hivelConfig.drowning.getValue())
			client.player.sendMessage(Text.translatable("message.ultracraft.drowning"));
		if(config.get().detailedJoinInfo || manual)
		{
			client.player.sendMessage(Text.translatable("message.ultracraft.projectile-boost." + ServerConfig.INSTANCE.projboost.getValue().name()));
			if(sConfig.disableHandswap.getValue())
				client.player.sendMessage(Text.translatable("message.ultracraft.disabled-handswap"));
			if(!hivelConfig.storage.getValue())
				client.player.sendMessage(Text.translatable("message.ultracraft.disabled-slamstorage"));
			if(sConfig.effectivelyViolent.getValue())
				client.player.sendMessage(Text.translatable("message.ultracraft.effectively-violent"));
			if(sConfig.parryChaining.getValue())
				client.player.sendMessage(Text.translatable("message.ultracraft.parry-chaining"));
		}
		if(!manual)
			client.player.sendMessage(Text.translatable("message.ultracraft.join-info"));
		client.player.sendMessage(Text.of("========================================="));
		return true;
	}
	
	public static void addBlood(float f)
	{
		screenblood = Math.min(3.5f, screenblood + f);
	}
	
	public static void clearBlood()
	{
		screenblood = 0f;
	}
	
	public static void toggleHiVelEnabled()
	{
		PlayerEntity player = MinecraftClient.getInstance().player;
		if(player == null)
			return;
		IWingDataComponent wings = UltraComponents.WING_DATA.get(player);
		Setting option = ServerConfig.INSTANCE.hivel.getValue();
		if(option.equals(Setting.FREE))
		{
			if(!(UltraComponents.PROGRESSION.get(player).isUnlocked(ProgressionComponent.HIVEL) ||
						 (Ultracraft.TRINKETS && TrinketUtil.isHasTrinketEquipped(player, ItemRegistry.HIVEL_WINGS))))
			{
				UltraComponents.WINGED.get(player).sendBoxTitle(Text.translatable("message.ultracraft.hivel-not-unlocked"), 2.5f);
				if(wings.isActive())
					setHiVel(!wings.isActive(), false);
				return;
			}
			setHiVel(!wings.isActive(), false);
			PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
			buf.writeBoolean(wings.isActive());
			ClientPlayNetworking.send(PacketRegistry.SEND_WING_STATE_C2S_PACKET_ID, buf);
			config.get().hivel = wings.isActive();
			config.save();
		}
		else
			player.sendMessage(Text.translatable("message.ultracraft.hi-vel-forced",
									Text.translatable(option.equals(Setting.FORCE_ON) ? "options.on" : "options.off")), true);
	}
	
	public static boolean isSlamStorageEnabled()
	{
		return HivelConfig.INSTANCE.storage.getValue();
	}
	
	public static boolean isViolentFeaturesEnabled(World world)
	{
		return world.getDifficulty() == Difficulty.HARD || ServerConfig.INSTANCE.effectivelyViolent.getValue();
	}
	
	public static void setHiVel(boolean b, boolean fromServer)
	{
		if(forcedHivel.isPresent())
			b = forcedHivel.get();
		PlayerEntity player = MinecraftClient.getInstance().player;
		if(player == null)
			return;
		IWingDataComponent wings = UltraComponents.WING_DATA.get(player);
		wings.setActive(b);
		SoundInstanceManager.attachMovementSounds(player);
		if(!fromServer)
		{
			PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
			buf.writeBoolean(b);
			ClientPlayNetworking.send(PacketRegistry.SEND_WING_STATE_C2S_PACKET_ID, buf);
		}
	}
	
	public static ClientConfig getConfig()
	{
		return config.get();
	}
	
	public static void saveConfig()
	{
		config.save();
	}
	
	public static void syncConfigEntry(String configID, String rule, int value)
	{
		Config config = Config.getFromID(configID);
		if(config == null)
		{
			Ultracraft.LOGGER.error(String.format("no config with id '%s' found", configID));
			return;
		}
		ConfigEntry<?> entry = config.getEntry(rule);
		if(entry instanceof EnumEntry<?> enumEntry)
			onExternalRuleUpdate(enumEntry.setValue(value), value);
		else
			onExternalRuleUpdate(config.set(rule, value), value);
		if(config instanceof ServerConfig serverConfig && rule.equals(serverConfig.hivel.getId()))
		{
			Setting hivel = Setting.values()[value];
			if(hivel != Setting.FREE)
				forcedHivel = Optional.of(hivel == Setting.FORCE_ON);
			else
				forcedHivel = Optional.empty();
		}
		if(config instanceof HivelConfig hivelConfig && rule.equals(hivelConfig.speed.getId()))
			((WingedPlayerEntity)MinecraftClient.getInstance().player).updateSpeedConfig();
	}
	
	public static void syncConfigEntry(String configID, String id, float value)
	{
		Config config = Config.getFromID(configID);
		if(config == null)
		{
			Ultracraft.LOGGER.error(String.format("no config with id '%s' found", configID));
			return;
		}
		config.set(id, value);
	}
	
	public static void syncConfigEntry(String configID, String id, boolean value)
	{
		Config config = Config.getFromID(configID);
		if(config == null)
		{
			Ultracraft.LOGGER.error(String.format("no config with id '%s' found", configID));
			return;
		}
		config.set(id, value);
	}
	
	public static void finishSyncingConfig(String configID)
	{
		Config config = Config.getFromID(configID);
		if(config == null)
		{
			Ultracraft.LOGGER.error(String.format("no config with id '%s' found", configID));
			return;
		}
		if(config instanceof ServerConfig)
			serverSyncFinished();
		if(config instanceof HivelConfig hivel && MinecraftClient.getInstance().player instanceof WingedPlayerEntity winged)
			winged.initMovementConfig(hivel);
	}
	
	public static void serverSyncFinished()
	{
		if(joinInfoPending && sendJoinInfo(MinecraftClient.getInstance(), false))
			joinInfoPending = false;
	}
	
	static <V, T extends ConfigEntry<V>> void onExternalRuleUpdate(T rule, V value)
	{
		ServerConfig.INSTANCE.set(rule, value);
		if(ServerConfigScreen.INSTANCE != null)
			ServerConfigScreen.INSTANCE.onExternalRuleUpdate(rule, value.toString());
	}
	
	static void onExternalRuleUpdate(EnumEntry<?> rule, int value)
	{
		ServerConfig.INSTANCE.set(rule, value);
		if(ServerConfigScreen.INSTANCE != null)
			ServerConfigScreen.INSTANCE.onExternalRuleUpdate(rule, String.valueOf(value));
	}
	
	public static ShaderProgram getWingsColoredShaderProgram()
	{
		return wingsColoredProgram;
	}
	
	public static ShaderProgram getWingsColoredUIShaderProgram()
	{
		return wingsColoredUIProgram;
	}
	
	public static ShaderProgram getTexPosFadeProgram()
	{
		return texPosFade;
	}
	
	public static ShaderProgram getFleshProgram()
	{
		return flesh;
	}
	
	public static ShaderProgram getSkyProgram()
	{
		return sky;
	}
	
	public static void setWingColor(Vector3f val, int idx)
	{
		wingColors[idx] = val;
		if(MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player instanceof WingedPlayerEntity winged)
			UltraComponents.WING_DATA.get(winged).setColor(val, idx);
	}
	
	public static Vector3f[] getWingColors()
	{
		return wingColors;
	}
	
	public static Vector3f[] getDefaultWingColors()
	{
		return defaultWingColors;
	}
	
	public static void setWingPattern(String id)
	{
		wingPattern = id;
		if(MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player instanceof WingedPlayerEntity winged)
			UltraComponents.WING_DATA.get(winged).setPattern(id);
	}
	
	public static void setWingOverlay(String id)
	{
		wingOverlay = id;
		if(MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player instanceof WingedPlayerEntity winged)
			UltraComponents.WING_DATA.get(winged).setOverlay(id);
	}
	
	public static void refreshSupporter()
	{
		MinecraftClient client = MinecraftClient.getInstance();
		UUID uuid = client.player != null ? client.player.getUuid() : Uuids.getUuidFromProfile(client.getSession().getProfile());
		supporter = Ultracraft.checkSupporter(uuid, true);
	}
	
	public static boolean isSupporter()
	{
		return supporter;
	}
	
	public static void freezeVFX(int ticks)
	{
		if(ticks == -1)
			visualFreezeTicks = 0;
		else
			visualFreezeTicks += ticks;
	}
	
	public static boolean isParryVisualsActive()
	{
		return visualFreezeTicks > 0;
	}
	
	public static boolean isBlocked(UUID uuid)
	{
		return config.get().blockedPlayers.contains(uuid);
	}
	
	public static boolean isCanGraffiti()
	{
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		ClientPlayNetworking.send(PacketRegistry.REQUEST_GRAFFITI_WHITELIST_PACKET_ID, buf);
		return switch(ServerConfig.INSTANCE.graffiti.getValue())
		{
			case ALLOW_ALL -> GRAFFITI_WHITELISTED;
			case ONLY_ADMINS -> ((WingedPlayerEntity)MinecraftClient.getInstance().player).isOpped() && GRAFFITI_WHITELISTED;
			case DISALLOW -> false;
		};
	}
	
	public static boolean isTerminalProtEnabled()
	{
		return ServerConfig.INSTANCE.terminalProtection.getValue();
	}
	
	public static boolean isTravelling()
	{
		return travelling;
	}
	
	public static void setTravelling(boolean travelling)
	{
		UltracraftClient.travelling = travelling;
	}
	
	/**
	 * This should be used for rendering effects that need deltaTime instead of tick delta
	 */
	public static float getDeltaTime()
	{
		if(MinecraftClient.getInstance() == null)
			return 0f;
		return MinecraftClient.getInstance().renderTickCounter.lastFrameDuration;
	}
}
