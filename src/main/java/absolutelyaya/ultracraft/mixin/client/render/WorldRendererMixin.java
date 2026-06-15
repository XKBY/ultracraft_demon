package absolutelyaya.ultracraft.mixin.client.render;

import absolutelyaya.ultracraft.Ultracraft;
import absolutelyaya.ultracraft.block.SkyBlockEntity;
import absolutelyaya.ultracraft.client.RenderLayers;
import absolutelyaya.ultracraft.client.UltracraftClient;
import absolutelyaya.ultracraft.registry.StatusEffectRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin
{
	@Shadow protected abstract void renderLayer(RenderLayer renderLayer, MatrixStack matrices, double cameraX, double cameraY, double cameraZ, Matrix4f positionMatrix);
	
	@Shadow @Final private BufferBuilderStorage bufferBuilders;
	
//	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;renderLayer(Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/util/math/MatrixStack;DDDLorg/joml/Matrix4f;)V", ordinal = 1))
//	void onRenderLayers(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix, CallbackInfo ci)
//	{
//		if(UltracraftClient.SODIUM)
//			return;
//		Vec3d pos = camera.getPos();
//		renderLayer(RenderLayers.getFlesh(), matrices, pos.x, pos.y, pos.z, projectionMatrix);
//	}
	
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;draw(Lnet/minecraft/client/render/RenderLayer;)V", ordinal = 4, shift = At.Shift.BEFORE))
	void onRenderTileEntities(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix, CallbackInfo ci)
	{
		for (SkyBlockEntity.SkyType type : SkyBlockEntity.SkyType.values())
			bufferBuilders.getEntityVertexConsumers().draw(RenderLayers.getSky(type));
	}
	
	@ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;renderEntity(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V"), index = 4)
	float adjustTickDelta(float tickDelta)
	{
		return Ultracraft.isTimeFrozen() ? 0f : tickDelta;
	}
	
	//@Inject(method = "renderEntity", at = @At("HEAD"))
	//void beforeRender(Entity entity, double cameraX, double cameraY, double cameraZ, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci)
	//{
	//	if(entity instanceof LivingEntity living && living.hasStatusEffect(StatusEffectRegistry.CANCEROUS))
	//		RenderSystem.setShaderColor(0.2f, 1f, 0.3f, 1f);
	//}
}
