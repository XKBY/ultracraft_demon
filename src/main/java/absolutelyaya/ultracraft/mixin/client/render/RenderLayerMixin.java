package absolutelyaya.ultracraft.mixin.client.render;

import absolutelyaya.ultracraft.client.RenderLayers;
import absolutelyaya.ultracraft.client.UltracraftClient;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

@Mixin(RenderLayer.class)
public abstract class RenderLayerMixin
{
	@ModifyReturnValue(method = "getBlockLayers", at = @At("RETURN"))
	private static List<RenderLayer> onGetBlockLayers(List<RenderLayer> original)
	{
        return original;
//		if(UltracraftClient.SODIUM)
//			return original;
//		List<RenderLayer> layers = new ArrayList<>(original);
//		layers.add(RenderLayers.getFlesh());
//		return layers;
	}
}
