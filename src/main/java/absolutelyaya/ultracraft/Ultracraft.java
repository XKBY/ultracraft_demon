package absolutelyaya.ultracraft;

import absolutelyaya.ultracraft.accessor.WingedPlayerEntity;
import absolutelyaya.ultracraft.api.CybergrindInitializer;
import absolutelyaya.ultracraft.command.Commands;
import absolutelyaya.ultracraft.command.EditModeCommands;
import absolutelyaya.ultracraft.command.WhitelistCommand;
import absolutelyaya.ultracraft.components.UltraComponents;
import absolutelyaya.ultracraft.components.player.IEditorComponent;
import absolutelyaya.ultracraft.components.player.IWingDataComponent;
import absolutelyaya.ultracraft.config.CybergrindConfig;
import absolutelyaya.ultracraft.config.HivelConfig;
import absolutelyaya.ultracraft.config.ServerConfig;
import absolutelyaya.ultracraft.config.Setting;
import absolutelyaya.ultracraft.data.LevelDataManager;
import absolutelyaya.ultracraft.data.StyleBonusManager;
import absolutelyaya.ultracraft.data.TerminalScreensaverManager;
import absolutelyaya.ultracraft.data.UltraRecipeManager;
import absolutelyaya.ultracraft.dimension.LevelManager;
import absolutelyaya.ultracraft.dimension.UltraDimensions;
import absolutelyaya.ultracraft.cybergrind.CybergrindManager;
import absolutelyaya.ultracraft.item.weapons.AbstractNailgunItem;
import absolutelyaya.ultracraft.item.weapons.AbstractRevolverItem;
import absolutelyaya.ultracraft.recipe.RecipeSerializers;
import absolutelyaya.ultracraft.registry.*;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;

public class Ultracraft implements ModInitializer
{
    public static final String MOD_ID = "ultracraft";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final Event<TimeFreeze> TIME_FREEZE_EVENT = EventFactory.createArrayBacked(TimeFreeze.class, callbacks -> (state) -> {
        for (TimeFreeze callback : callbacks)
            callback.onSetTimeFrozen(state);
    });
    static final String SUPPORTER_LIST = "https://raw.githubusercontent.com/absolutelyaya/absolutelyaya/main/cool-people.json";
    public static String VERSION;
	public static boolean DYN_LIGHTS, SERVER_SIDE, VIVECRAFT, TRINKETS;
	static int freezeTicks;
    static Map<UUID, Integer> supporterCache = new HashMap<>(), supporterCacheAdditions = new HashMap<>();
    static ServerConfig config;
    static HivelConfig hivelConfig;
    static List<DamageType> likelyPerTickDamageTypes = new ArrayList<>();
    
    @Override
    public void onInitialize()
    {
        ParticleRegistry.init();
        EntityRegistry.register();
        BlockRegistry.registerBlocks();
        FluidRegistry.register();
        BlockEntityRegistry.register();
        ItemRegistry.register();
        PacketRegistry.registerC2S();
        TagRegistry.register();
        SoundRegistry.register();
        GameruleRegistry.register();
        RecipeSerializers.register();
        CriteriaRegistry.register();
        StatusEffectRegistry.register();
        ScreenHandlerRegistry.registerServer();
        StatisticRegistry.register();
        StructureRegistry.register();
        new UltraRecipeManager();
        new TerminalScreensaverManager();
        new StyleBonusManager();
        new LevelManager();
        
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            new UltraDimensions(server);
            UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
                IEditorComponent editor = UltraComponents.EDITOR.get(player);
                if(editor.isActive() && hand.equals(Hand.MAIN_HAND))
                    return editor.useBlock(player, hitResult.getBlockPos());
                return ActionResult.PASS;
            });
        });
        
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LevelManager.Instance.onServerStop();
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            Commands.register(dispatcher);
            WhitelistCommand.register(dispatcher);
            EditModeCommands.register(dispatcher);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickFreeze();
            ServerHitscanHandler.tickSchedule();
            supporterCache.putAll(supporterCacheAdditions);
            supporterCacheAdditions.clear();
            supporterCache.forEach((uuid, i) -> {
                if(i > 0)
                    supporterCache.put(uuid, i - 1);
            });
            UltraDimensions.Instance.tickManagers();
            CybergrindManager.Instance.tick();
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> rechargeWeapons(newPlayer));
        
        ServerPlayConnectionEvents.JOIN.register((networkHandler, sender, server) -> {
            ServerPlayerEntity player = networkHandler.player;
            config.syncAll(player);
            UltraRecipeManager.sync(player);
            LevelDataManager.sync(player);
            Setting hivel = config.hivel.getValue();
            if(!hivel.equals(Setting.FREE))
            {
                IWingDataComponent wings = UltraComponents.WING_DATA.get(player);
                wings.setActive(hivel.equals(Setting.FORCE_ON));
                wings.sync();
            }
        });
        ServerLifecycleEvents.SERVER_STARTING.register((server) -> {
            loadConfig(server);
            new CybergrindManager(server);
        });
        ServerLifecycleEvents.START_DATA_PACK_RELOAD.register((server, handler) -> {
            loadConfig(server);
            server.getPlayerManager().getPlayerList().forEach(player -> {
                UltraRecipeManager.sync(player);
                LevelDataManager.sync(player);
            });
        });
        
        SERVER_SIDE = FabricLoader.getInstance().getEnvironmentType().equals(EnvType.SERVER);
        FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(modContainer -> VERSION = modContainer.getMetadata().getVersion().getFriendlyString());
        FabricLoader.getInstance().getModContainer("lambdynlights").ifPresent(container -> DYN_LIGHTS = true);
        FabricLoader.getInstance().getModContainer("vivecraft").ifPresent(container -> VIVECRAFT = true);
        FabricLoader.getInstance().getModContainer("trinkets").ifPresent(container -> TRINKETS = true);
        LOGGER.info("Ultracraft initialized.");
    }
    
    public static Identifier identifier(String path)
    {
        return new Identifier(Ultracraft.MOD_ID, path);
    }
    
    public static Identifier texIdentifier(String path)
    {
        return identifier(path + ".png");
    }
    
    void loadConfig(MinecraftServer server)
    {
        config = new ServerConfig(server);
        config.syncAll(server);
        hivelConfig = new HivelConfig(server);
        hivelConfig.syncAll(server);
        new CybergrindConfig(server);
        CybergrindConfig.clearCosts();
        for (CybergrindInitializer initializer : FabricLoader.getInstance().getEntrypoints("cybergrind", CybergrindInitializer.class))
            initializer.registerEnemyCosts(CybergrindConfig.INSTANCE);
        CybergrindConfig.freeze();
    }
    
    public static boolean isTimeFrozen()
    {
        return freezeTicks > 0;
    }
    
    public static void freeze(ServerPlayerEntity player, int ticks)
    {
        if(player != null)
        {
            boolean freezeDisabled = player.getServer().isRemote() && ServerConfig.INSTANCE.timestop.getValue().equals(Setting.FORCE_OFF);
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeInt(ticks);
            buf.writeBoolean(freezeDisabled);
            if(freezeDisabled)
            {
                ServerPlayNetworking.send(player, PacketRegistry.FREEZE_PACKET_ID, buf);
                return;
            }
            for (ServerPlayerEntity p : ((ServerWorld)player.getWorld()).getPlayers())
                ServerPlayNetworking.send(p, PacketRegistry.FREEZE_PACKET_ID, buf);
        }
        freezeTicks += ticks;
        LOGGER.info("Stopping time for " + ticks + " ticks.");
        TIME_FREEZE_EVENT.invoker().onSetTimeFrozen(true);
    }
    
    public static void freeze(ServerWorld world, int ticks)
    {
        if(world != null)
        {
            boolean freezeDisabled = world.getServer().isRemote() && ServerConfig.INSTANCE.timestop.getValue().equals(Setting.FORCE_OFF);
            if(freezeDisabled)
                return;
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeInt(ticks);
            buf.writeBoolean(false);
            for (ServerPlayerEntity p : world.getPlayers())
                ServerPlayNetworking.send(p, PacketRegistry.FREEZE_PACKET_ID, buf);
        }
        freezeTicks += ticks;
        LOGGER.info("Stopping time for " + ticks + " ticks.");
        TIME_FREEZE_EVENT.invoker().onSetTimeFrozen(true);
    }
    
    public static void cancelFreeze(ServerWorld world)
    {
        if(world != null)
        {
            boolean freezeDisabled = world.getServer().isRemote() && ServerConfig.INSTANCE.timestop.getValue().equals(Setting.FORCE_OFF);
            if(freezeDisabled)
                return;
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeInt(-1);
            buf.writeBoolean(false);
            for (ServerPlayerEntity p : world.getPlayers())
                ServerPlayNetworking.send(p, PacketRegistry.FREEZE_PACKET_ID, buf);
        }
        freezeTicks = 0;
        LOGGER.info("Forcefully Unstopped time.");
        TIME_FREEZE_EVENT.invoker().onSetTimeFrozen(false);
    }
    
    public static void tickFreeze()
    {
        if(freezeTicks > 0)
        {
            freezeTicks--;
            if(freezeTicks == 0)
                TIME_FREEZE_EVENT.invoker().onSetTimeFrozen(false);
        }
    }
    
    public static boolean checkSupporter(UUID uuid, boolean client)
    {
        //Supporter checking is disabled: never contact GitHub (a failed/blocked request could freeze the game) and
        //always treat everyone as a non-supporter.
        return false;
    }

    public static JsonObject fetchSupporterList()
    {
        //Disabled: never fetch the supporter list from GitHub. Returning null is handled gracefully by all callers.
        return null;
    }
    
    public static void screenshake(PlayerEntity player, float strength)
    {
        if(player.getWorld().isClient && player instanceof WingedPlayerEntity winged)
            winged.addScreenshake(strength);
        else if(player instanceof ServerPlayerEntity serverPlayer)
        {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeFloat(strength);
            ServerPlayNetworking.send(serverPlayer, PacketRegistry.SCREENSHAKE_PACKET_ID, buf);
        }
    }
    
    public static void rechargeWeapons(PlayerEntity player)
    {
        player.getInventory().main.forEach(stack -> {
            Item item = stack.getItem();
            if(item instanceof AbstractRevolverItem revolver)
            {
                revolver.setNbt(stack, "coins", revolver.getNbtDefault("coins"));
                revolver.setNbt(stack, "charges", revolver.getNbtDefault("charges"));
                revolver.resetAllHammers(stack);
            }
            else if (item instanceof AbstractNailgunItem nailgun && nailgun.getNbt(stack, "nails") < 100)
                nailgun.setNbt(stack, "nails", 100);
        });
    }
    
    public static boolean isLikelyPerTickDamageType(DamageType type)
    {
        return likelyPerTickDamageTypes.contains(type);
    }
    
    public static void addLikelyPerTickDamageType(DamageType type)
    {
        likelyPerTickDamageTypes.add(type);
        LOGGER.info("Identified Damage Type '{}' as potential per-tick Damage Type", type.msgId());
    }
    
    public static void clearLikelyPerTickDamageTypes()
    {
        likelyPerTickDamageTypes.clear();
    }
    
    public interface TimeFreeze
    {
        void onSetTimeFrozen(boolean state);
    }
}
