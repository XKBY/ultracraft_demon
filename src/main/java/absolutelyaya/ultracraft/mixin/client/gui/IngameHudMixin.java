package absolutelyaya.ultracraft.mixin.client.gui;

import absolutelyaya.ultracraft.accessor.WingedPlayerEntity;
import absolutelyaya.ultracraft.client.UltracraftClient;
import absolutelyaya.ultracraft.client.gui.EditModeHUD;
import absolutelyaya.ultracraft.client.gui.TitleHUD;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class IngameHudMixin
{
	@Shadow @Final private MinecraftClient client;
	
	@Inject(method = "renderCrosshair", at = @At(value = "HEAD"), cancellable = true)
	void onRenderCrosshair(DrawContext context, CallbackInfo ci)
	{
		if(client.player instanceof WingedPlayerEntity winged && winged.getFocusedTerminal() != null)
			ci.cancel();
	}
	
	//Render the UltraHUD at the very start of the HUD pass. This runs after the first-person hand (so it covers the
	//offhand item) but before all HUD content incl. chat and info popups (so those stay on top). Using HEAD of render()
	//instead of an INVOKE point keeps it working under Sinytra Connector/Forge, where Forge moves chat rendering into a
	//separate GUI overlay and the "before ChatHud.render" injection point no longer exists.
	@Inject(method="render", at = @At("HEAD"))
	void onRenderHudHead(DrawContext context, float tickDelta, CallbackInfo ci)
	{
		UltracraftClient.renderUltraHud(tickDelta);
	}

	@Inject(method="render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;render(Lnet/minecraft/client/gui/DrawContext;III)V", shift = At.Shift.BEFORE))
	void beforeRenderChat(DrawContext context, float tickDelta, CallbackInfo ci)
	{
		TitleHUD.Instance.render(context, tickDelta);
		EditModeHUD.Instance.render(context, tickDelta);
	}
	
	@Inject(method="renderHotbar", at = @At("HEAD"), cancellable = true)
	void onRenderHotbar(float tickDelta, DrawContext context, CallbackInfo ci)
	{
		if(UltracraftClient.getConfig().hideVanillaHotbar)
			ci.cancel();
	}
}
