//package absolutelyaya.ultracraft.mixin.client.gui;
//
//import absolutelyaya.ultracraft.client.gui.screen.TravelScreen;
//import absolutelyaya.ultracraft.components.UltraComponents;
//import absolutelyaya.ultracraft.components.player.ILevelStatsComponent;
//import com.llamalad7.mixinextras.sugar.Local;
//import net.minecraft.client.gui.screen.GameMenuScreen;
//import net.minecraft.client.gui.screen.Screen;
//import net.minecraft.client.gui.widget.ButtonWidget;
//import net.minecraft.client.gui.widget.GridWidget;
//import net.minecraft.text.Text;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
//
//@Mixin(GameMenuScreen.class)
//public abstract class GameMenuScreenMixin extends Screen
//{
//	protected GameMenuScreenMixin(Text title)
//	{
//		super(title);
//	}
//
//	@Inject(method = "initWidgets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/widget/GridWidget;refreshPositions()V"), locals = LocalCapture.CAPTURE_FAILHARD)
//	void onInit(CallbackInfo ci, @Local GridWidget.Adder adder)
//	{
//		ILevelStatsComponent levelStats = UltraComponents.LEVEL_STATS.get(client.player);
//		if(levelStats.getCurrentLevelInstance() != null)
//			adder.add(new ButtonWidget.Builder(Text.translatable("screen.ultracraft.pause.exitLevel"),
//					b -> client.setScreen(new TravelScreen(false, false, null))).width(204).build(), 2);
//	}
//}
