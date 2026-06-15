package absolutelyaya.ultracraft.client.rendering;

import absolutelyaya.ultracraft.compat.TrinketUtil;
import absolutelyaya.ultracraft.components.UltraComponents;
import absolutelyaya.ultracraft.Ultracraft;
import absolutelyaya.ultracraft.accessor.WingedPlayerEntity;
import absolutelyaya.ultracraft.client.ClientConfig;
import absolutelyaya.ultracraft.client.UltracraftClient;
import absolutelyaya.ultracraft.components.player.IArmComponent;
import absolutelyaya.ultracraft.components.player.IStyleComponent;
import absolutelyaya.ultracraft.components.player.IWingDataComponent;
import absolutelyaya.ultracraft.components.player.ProgressionComponent;
import absolutelyaya.ultracraft.item.*;
import absolutelyaya.ultracraft.item.weapons.AbstractWeaponItem;
import absolutelyaya.ultracraft.registry.ItemRegistry;
import absolutelyaya.ultracraft.util.RenderingUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import me.shedaniel.math.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BannerItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec2f;
import org.joml.*;

import java.lang.Math;
import java.util.*;
import java.util.Random;

@SuppressWarnings("SameParameterValue")
public class UltraHudRenderer
{
	private static final Vector3f[] STYLE_OFFSETS = new Vector3f[] { new Vector3f(-43, -5, 40), new Vector3f(-2, -5, 20), new Vector3f(2, 0, 0) };
	private static final ClientConfig config = UltracraftClient.getConfig();
	final Identifier GUI_TEXTURE = Ultracraft.texIdentifier("textures/gui/ultrahud");
	final Identifier GUI_TEXTURE_HIVEL = Ultracraft.texIdentifier("textures/gui/ultrahud_2");
	final Identifier STYLE_TEXTURE = Ultracraft.texIdentifier("textures/gui/style");
	final Identifier WEAPONS_TEXTURE = Ultracraft.texIdentifier("textures/gui/weapon_icons");
	final Identifier CROSSHAIR_TEXTURE = Ultracraft.texIdentifier("textures/gui/crosshair_stats");
	float healthPercent, staminaPercent, absorptionPercent, hungerPercent, hiVelTexTransition;
	static float fishTimer, coinTimer, coinRot = 0, coinRotDest = 0, wingHintDisplayTimer, whitelistHintDisplayTimer, styleTimer;
	static ItemStack lastCatch;
	static int coinCombo;
	final String[] fishMania = new String[] {"message.ultracraft.fish.mania1", "message.ultracraft.fish.mania2", "message.ultracraft.fish.mania3", "message.ultracraft.fish.mania4"};
	final Random rand = new Random();
	
	public void render(float delta, Camera cam)
	{
		if(!MinecraftClient.isHudEnabled() || config.ultraHudVisibility.equals(UltraHudVisibility.NEVER))
			return;
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if(player == null || player.isSpectator())
			return;
		if(!(UltraComponents.PROGRESSION.get(player).isUnlocked(ProgressionComponent.ULTRAHUD) ||
				   (Ultracraft.TRINKETS && TrinketUtil.isHasTrinketEquipped(player, ItemRegistry.VIEW_AUGMENT))))
			return;
		if(player instanceof WingedPlayerEntity winged && winged.getFocusedTerminal() != null)
			return;
		renderExtras(delta);
		IWingDataComponent wings = UltraComponents.WING_DATA.get(player);
		boolean wingsActive = wings.isActive();
		if(config.ultraHudVisibility.equals(UltraHudVisibility.LIMITED) && !wingsActive)
			return;

		//We now render in the 2D HUD pass (so the HUD draws on top of the first-person hand). That pass leaves a GUI
		//z-offset on the model-view stack, which would push our perspective content out of view, so reset it to identity.
		RenderSystem.getModelViewStack().push();
		RenderSystem.getModelViewStack().loadIdentity();
		RenderSystem.applyModelViewMatrix();
		RenderSystem.disableDepthTest();
		RenderSystem.disableCull();
		MatrixStack matrices = new MatrixStack();
		int width = client.getWindow().getFramebufferWidth();
		int height = client.getWindow().getFramebufferHeight();
		float aspect = (float)height / (float)width;
		RenderSystem.backupProjectionMatrix();
		RenderSystem.setProjectionMatrix(client.gameRenderer.getBasicProjectionMatrix(90), VertexSorter.BY_DISTANCE);
		RenderSystem.enableBlend();
		
		healthPercent = MathHelper.lerp(delta, healthPercent, player.getHealth() / player.getMaxHealth());
		hungerPercent = MathHelper.lerp(delta, hungerPercent, player.getHungerManager().getFoodLevel() / 20f);
		staminaPercent = MathHelper.lerp(delta, staminaPercent, UltraComponents.HIVEL.get(player).getStamina() / 90f);
		absorptionPercent = MathHelper.lerp(delta, absorptionPercent, Math.min(player.getAbsorptionAmount() / 20f, 1f));
		//Fade the HUD texture toward the hi-vel variant while wings are active, and back when they're not.
		hiVelTexTransition = MathHelper.lerp(MathHelper.clamp(delta * 0.25f, 0f, 1f), hiVelTexTransition, wingsActive ? 1f : 0f);
		//Crosshair
		if(config.ultraHudCrosshair)
		{
			matrices.push();
			matrices.scale(aspect, 1f, 1f);
			matrices.translate(-0.75, -0.5, 0);
			RenderSystem.setShaderTexture(0, CROSSHAIR_TEXTURE);
			RenderingUtil.drawTexture(matrices.peek().getPositionMatrix(), new Vector4f(-6f, -5f, 5f, 11f * healthPercent), 100f,
					new Vec2f(32f, 32f), new Vector4f(0f, 11 - 11f * healthPercent, 5f, 11f * healthPercent), 0.75f);
			RenderingUtil.drawTexture(matrices.peek().getPositionMatrix(), new Vector4f(2f, -5f, 5f, 11f * staminaPercent), 100f,
					new Vec2f(32f, 32f), new Vector4f(8f, 11 - 11f * staminaPercent, 5f, 11f * staminaPercent), 0.75f);
			matrices.pop();
		}
		
		if(wings.isActive() && config.showStylePanel)
			renderStyle(matrices, client, player, Math.max(delta, 0f), MathHelper.clamp(styleTimer, 0f, 1f));
		renderHotbar(matrices, client, cam, player, wingsActive, delta);
		
		if(whitelistHintDisplayTimer > 0.001f)
			whitelistHintDisplayTimer -= delta / 20f;

		RenderSystem.getModelViewStack().pop();
		RenderSystem.applyModelViewMatrix();
	}
	
	public void renderHotbar(MatrixStack matrices, MinecraftClient client, Camera cam, ClientPlayerEntity player, boolean wingsActive, float delta)
	{
		matrices.push();
		matrices.push();
		boolean flip = player.getMainArm().equals(Arm.LEFT) ^ config.switchSides;
		if(!UltracraftClient.getConfig().ultraHudFixed)
		{
			float h = MathHelper.lerp(delta, player.lastRenderPitch, player.renderPitch);
			float i = MathHelper.lerp(delta, player.lastRenderYaw, player.renderYaw);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((player.getPitch(delta) - h) * 0.15f));
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((player.getYaw(delta) - i) * 0.05f));
		}
		matrices.translate(flip ? 60 : -48, 0, -50);
		if(config.switchSides)
			matrices.translate(0, -30, 0);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(flip ? -10 : 10));

		matrices.push();
		matrices.translate(-28, -36, 0f);
		int guiScale = Math.min(client.options.getGuiScale().getValue(), 3);
		if(guiScale == 0)
			guiScale = 3;
		float scale = guiScale * 0.15f;
		matrices.scale(scale, scale, scale);
		Matrix4f textureMatrix = new Matrix4f(matrices.peek().getPositionMatrix());
		IArmComponent arms = UltraComponents.ARMS.get(player);
		//Crossfade the panel between the default texture and the hi-vel texture based on the transition progress.
		if(hiVelTexTransition < 0.999f)
		{
			RenderSystem.setShaderTexture(0, GUI_TEXTURE);
			drawHudPanel(textureMatrix, arms, 1f - hiVelTexTransition);
		}
		if(hiVelTexTransition > 0.001f)
		{
			RenderSystem.setShaderTexture(0, GUI_TEXTURE_HIVEL);
			drawHudPanel(textureMatrix, arms, hiVelTexTransition);
		}

		//Hi-vel toggle hint text. Rendered inside the panel's own coordinate space (still in the HUD's perspective
		//projection) so it stays anchored just above the panel and scales together with it at every GUI scale, instead of
		//scaling around an off-screen pivot. Panel space spans x 0..96, y 0..60 with +y up, so "above the HUD" is y > 60.
		if(wingHintDisplayTimer > 0.001f)
		{
			Text t = Text.translatable(wingsActive ? "message.ultracraft.hi-vel.enable" : "message.ultracraft.hi-vel.disable");
			if(whitelistHintDisplayTimer > 0.001f)
				t = Text.translatable("message.ultracraft.hi-vel.whitelist");
			matrices.push();
			matrices.translate(48f, 68f, 1f);   //centered over the 96-wide panel, just above its 60-tall top edge
			matrices.scale(0.5f, -0.5f, 0.5f);  //flip Y for upright text and scale it relative to the panel
			drawText(matrices, t, 0f, 0f, MathHelper.clamp(wingHintDisplayTimer, 0.05f, 1f), true);
			client.getBufferBuilders().getEntityVertexConsumers().draw(); //flush while the perspective projection is bound
			matrices.pop();
			wingHintDisplayTimer -= delta / 20f;
		}

		matrices.pop();
		RenderSystem.restoreProjectionMatrix();
		matrices.pop();
	}

	//Draws the HUD box, bars and arm indicator from the currently-bound texture, with every layer's alpha scaled by `am`
	//so the two textures can be crossfaded during the hi-vel transition.
	void drawHudPanel(Matrix4f textureMatrix, IArmComponent arms, float am)
	{
		//main box
		RenderingUtil.drawTexture(textureMatrix, new Vector4f(0, 0, 96f, 60f), 0f,
				new Vec2f(160f, 128f), new Vector4f(0f, 0f, 96f, 60f), 0.75f * am);
		//health
		RenderingUtil.drawTexture(textureMatrix, new Vector4f(8, 22, 70 * healthPercent, 14), 0f,
				new Vec2f(160f, 128f), new Vector4f(8f, 64f, 70f * healthPercent, 14f), am);
		//hunger
		RenderingUtil.drawTexture(textureMatrix, new Vector4f(5, 2, 74 * hungerPercent, 16), 0f,
				new Vec2f(160f, 128f), new Vector4f(5f, 108f, 74f * hungerPercent, 16f), am);
		if(absorptionPercent > 0f)
			RenderingUtil.drawTexture(textureMatrix, new Vector4f(8, 22, 70 * absorptionPercent, 14), 0f,
					new Vec2f(160f, 128f), new Vector4f(8f, 95f, 70f * absorptionPercent, 14f), am);
		//stamina
		RenderingUtil.drawTexture(textureMatrix, new Vector4f(22, 9, 69 * staminaPercent, 17), 0f,
				new Vec2f(160f, 128f), new Vector4f(22f, 78f, 69f * staminaPercent, 17f), am);
		//arm
		if(arms.getUnlockedArmCount() > 1)
		{
			RenderingUtil.drawTexture(textureMatrix, new Vector4f(49, 0, 15, 14), 0f,
					new Vec2f(80f, 64f), new Vector4f(49f, 34f, 15f, 14f), 0.75f * am);
			RenderingUtil.drawTexture(textureMatrix, new Vector4f(51, 2f, 11, 10), 0f,
					new Vec2f(80f, 64f), new Vector4f(47f + 11 * arms.getActiveArm(), 48f, 11f, 10f), am);
		}
	}

	public void renderStyle(MatrixStack matrices, MinecraftClient client, ClientPlayerEntity player, float delta, float alpha)
	{
		RenderSystem.disableDepthTest();
		matrices.push();
		boolean flip = player.getMainArm().equals(Arm.LEFT) == config.switchSides;
		if(!UltracraftClient.getConfig().ultraHudFixed)
		{
			float h = MathHelper.lerp(delta, player.lastRenderPitch, player.renderPitch);
			float i = MathHelper.lerp(delta, player.lastRenderYaw, player.renderYaw);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((player.getPitch(delta) - h) * 0.15f));
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((player.getYaw(delta) - i) * 0.05f));
		}
		matrices.translate(flip ? 60 : -48, 32, -50);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(flip ? -10 : 10));
		
		matrices.push();
		RenderSystem.setShaderTexture(0, STYLE_TEXTURE);
		matrices.translate(-24, -36, 0f);
		int guiScale = client.options.getGuiScale().getValue();
		if(guiScale == 0)
			guiScale = 3;
		float scale = Math.min(guiScale, 3) * 0.2f;
		matrices.scale(scale, scale, scale);
		//main box
		Matrix4f textureMatrix = new Matrix4f(matrices.peek().getPositionMatrix());
		IStyleComponent style = UltraComponents.STYLE.get(player);
		if(alpha > 0f)
		{
			RenderingUtil.drawTexture(textureMatrix, new Vector4f(0f, 0f, 64f, 76f), 0f,
					new Vec2f(128f, 128f), new Vector4f(0f, 0f, 64f, 76f), alpha * 0.75f);
			RenderingUtil.drawTexture(textureMatrix, new Vector4f(2f, 76f - 10f, 60f, 8f), 0f,
					new Vec2f(128f, 128f), new Vector4f(65f, 21f + style.getRank() * 9f, 60f, 8f), alpha);
			//progressBar
			RenderingUtil.drawTexture(textureMatrix, new Vector4f(1f, 76f - 16f, 62f, 4f), 0f,
					new Vec2f(128f, 128f), new Vector4f(1f, 112f, 62f, 4f), alpha);
			float progress = style.getRankProgress();
			RenderingUtil.drawTexture(textureMatrix, new Vector4f(1f, 76f - 16f, 62f * progress, 4f), 0f,
					new Vec2f(128f, 128f), new Vector4f(1f, 108f, 62f * progress, 4f), alpha);
			if(player.getMainHandStack().getItem() instanceof AbstractWeaponItem)
			{
				//stalenessBar
				RenderingUtil.drawTexture(textureMatrix, new Vector4f(3f, 2f, 58f, 8f), 0f,
						new Vec2f(128f, 128f), new Vector4f(3f, 100f, 58f, 8f), alpha);
				int staleness = 150 - style.getStaleness(Registries.ITEM.getId(player.getMainHandStack().getItem()));
				float stalePercent = MathHelper.clamp(staleness / 50f, 0f, 1f);
				RenderingUtil.drawTexture(textureMatrix, new Vector4f(3f, 2f, 58f * stalePercent, 8f), 0f,
						new Vec2f(128f, 128f), new Vector4f(3f, 92f, 58f * stalePercent, 8f), alpha);
				float usedPercent = MathHelper.clamp(staleness / 50f - 1f, 0f, 1f);
				RenderingUtil.drawTexture(textureMatrix, new Vector4f(3f, 2f, 58f * usedPercent, 8f), 0f,
						new Vec2f(128f, 128f), new Vector4f(3f, 84f, 58f * usedPercent, 8f), alpha);
				float freshPercent = MathHelper.clamp(staleness / 50f - 2f, 0f, 1f);
				RenderingUtil.drawTexture(textureMatrix, new Vector4f(3f, 2f, 58f * freshPercent, 8f), 0f,
						new Vec2f(128f, 128f), new Vector4f(3f, 76f, 58f * freshPercent, 8f), alpha);
				
				if(staleness > 100)
					RenderingUtil.drawTexture(textureMatrix, new Vector4f(6f, 4f, 34f, 4f), 0f,
							new Vec2f(128f, 128f), new Vector4f(65f, 1f, 34f, 4f), alpha);
				else if(staleness > 50)
					RenderingUtil.drawTexture(textureMatrix, new Vector4f(6f, 4f, 34f, 4f), 0f,
							new Vec2f(128f, 128f), new Vector4f(65f, 6f, 34f, 4f), alpha);
				else if(staleness > 0)
					RenderingUtil.drawTexture(textureMatrix, new Vector4f(6f, 4f, 34f, 4f), 0f,
							new Vec2f(128f, 128f), new Vector4f(65f, 11f, 34f, 4f), alpha);
				else
					RenderingUtil.drawTexture(textureMatrix, new Vector4f(6f, 4f, 34f, 4f), 0f,
							new Vec2f(128f, 128f), new Vector4f(65f, 16f, 34f, 4f), alpha);
			}
		}
		
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		int count = Math.min(style.getBonusQueue().size(), 6);
		matrices.push();
		matrices.scale(0.5f, -0.5f, -1f);
		Vector3f offset = STYLE_OFFSETS[MathHelper.clamp(guiScale - 1, 0, STYLE_OFFSETS.length - 1)];
		matrices.translate((flip ? 124 : 144) + offset.x, -100 + offset.y, 10 + offset.z);
		if(alpha > 0f)
		{
			matrices.push();
			float f = style.getMovementMultiplier() / 3f;
			float shake = Math.max(f - 0.5f, 0f) * 3;
			matrices.translate((rand.nextFloat() - 0.5f) * shake, (rand.nextFloat() - 0.5f) * shake, 0f);
			drawTextNoBG(matrices, Text.translatable("stylebonus.ultracraft.movement-multiplier",
					String.format(java.util.Locale.US,"%.2f", style.getMovementMultiplier())), flip ? -150 : -50, 70, alpha, false);
			matrices.pop();
		}
		if(style.getBonusQueue() != null && count > 0)
		{
			if(alpha > 0f)
			{
				int i = count;
				List<Pair<String, Long>> bonusList = new ArrayList<>(style.getBonusQueue());
				if(!bonusList.isEmpty())
					Collections.reverse(bonusList);
				for (Pair<String, Long> p : bonusList)
				{
					if(p == null || i <= 0)
						break;
					Text t = Text.translatable(p.getLeft());
					drawTextNoBG(matrices, t, flip ? -150 : -50, 5 + (i - 1) * 10, alpha, false);
					i--;
				}
			}
		}
		matrices.pop();
		if(style.getChain() > 0 || !style.getBonusQueue().isEmpty())
			styleTimer = 1f;
		else if(styleTimer > 0f)
			styleTimer -= delta / 2f;
		matrices.pop();
		matrices.pop();
	}
	
	public void renderExtras(float tickDelta)
	{
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if(player == null)
			return;
		//Reset the GUI z-offset left on the model-view stack by the 2D HUD pass, otherwise this perspective content is pushed out of view.
		RenderSystem.getModelViewStack().push();
		RenderSystem.getModelViewStack().loadIdentity();
		RenderSystem.applyModelViewMatrix();
		RenderSystem.disableDepthTest();
		RenderSystem.disableCull();

		MatrixStack matrices = new MatrixStack();
		int width = client.getWindow().getFramebufferWidth();
		int height = client.getWindow().getFramebufferHeight();
		float aspect = (float)width / (float)height;
		matrices.peek().getPositionMatrix().perspective(90 * 0.0174f,
				aspect, 0.05F, client.gameRenderer.getViewDistance() * 4.0F);
		RenderSystem.backupProjectionMatrix();
		RenderSystem.setProjectionMatrix(matrices.peek().getPositionMatrix(), VertexSorter.BY_DISTANCE);
		RenderSystem.enableBlend();
		
		//Fishing Joke
		if(fishTimer > 0f && lastCatch != null && config.fishingJoke)
		{
			int fishCaught = UltraComponents.EASTER.get(player).getFishes();
			matrices.push();
			matrices.scale(aspect, 1f, 1f);
			matrices.push();
			matrices.scale(0.001f, -0.001f, 0.001f);
			drawText(matrices, Text.translatable("message.ultracraft.fish.caught", lastCatch.getName().getString()),
					0, -32f, 1f, true);
			drawText(matrices, Text.translatable("message.ultracraft.fish.size", fishCaught == 69 ? "1.5" : "1"),
					0, 16f, 1f, true);
			float fishManiaLevel = Math.max(0, fishCaught - 10) / 32f;
			float shake = config.safeVFX ? 0f : 1f;
			drawText(matrices, fishCaught == 69 ? Text.translatable("message.ultracraft.fish.mania5") :
										   Text.translatable(fishMania[fishCaught % fishMania.length]),
					(rand.nextFloat() - 0.5f) * fishManiaLevel / 2f * shake,
					32f + (rand.nextFloat() - 0.5f) * fishManiaLevel / 2f * shake,
					MathHelper.clamp(0.5f * fishManiaLevel, 0.05f, 1f), true);
			matrices.scale(0.5f, 0.5f, 0.5f);
			if(fishCaught < 5)
				drawText(matrices, Text.translatable("message.ultracraft.fish.disable"),
						0, 128f, 0.5f, true);
			matrices.pop();
			matrices.push();
			matrices.translate(0f, 0f, 0.1f);
			matrices.scale(0.1f, 0.1f, 0.1f);
			matrices.multiply(new Quaternionf(new AxisAngle4f(-fishTimer / 0.75f, 0f, 1f, 0f)));
			matrices.multiply(new Quaternionf(new AxisAngle4f((float)Math.toRadians(-45.0), 0f, 0f, 1f)));
			VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
			drawItem(matrices, new Matrix4f(matrices.peek().getPositionMatrix()), client, immediate, lastCatch, false);
			immediate.draw();
			matrices.pop();
			fishTimer -= MinecraftClient.getInstance().getLastFrameDuration() / 15f;
			matrices.pop();
		}
		
		//Coin Combo
		if(coinTimer > 0f)
		{
			matrices.push();
			matrices.scale(aspect, 1f, 1f);
			matrices.push();
			matrices.scale(0.001f, -0.001f, 0.001f);
			TextRenderer render = client.textRenderer;
			Text t;
			drawOutlinedText(matrices, t = Text.of(String.valueOf(coinCombo)), -render.getWidth(t) / 2f, 50f, 1f);
			matrices.pop();
			matrices.push();
			matrices.translate(0f, -0.725f, 1f);
			RenderSystem.setShaderColor(1f, 1f, 1f, Math.min(coinTimer, 1f));
			coinRot = MathHelper.lerp(tickDelta / 10, coinRot, coinRotDest);
			matrices.translate(0f, 0.14f, 0f);
			matrices.scale(0.25f, 0.25f, 0.25f);
			matrices.multiply(new Quaternionf(new AxisAngle4f(
					(float)(Math.toRadians(15)), 1f, 0f, 0f)));
			matrices.multiply(new Quaternionf(new AxisAngle4f(
					(float)(Math.toRadians(coinRot * 180)), 0f, 1f, 0f)));
			RenderSystem.setShaderLights(new Vector3f(0, 0, -1), new Vector3f(0, 0, -1));
			VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
			drawItem(matrices, new Matrix4f(matrices.peek().getPositionMatrix()), client, immediate, new ItemStack(ItemRegistry.COIN), false);
			immediate.draw();
			RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
			coinTimer -= tickDelta / 20;
			matrices.pop();
			matrices.pop();
		}
		else if(coinCombo > 0)
		{
			coinCombo = 0;
			coinRot = 0;
			coinRotDest = 0;
		}
		matrices.pop();
		RenderSystem.restoreProjectionMatrix();
		RenderSystem.getModelViewStack().pop();
		RenderSystem.applyModelViewMatrix();
	}

	boolean shouldRenderSpriteInstead(Item item)
	{
		return (item instanceof AbstractWeaponItem weapon && weapon.getHUDTexture() != null) || item instanceof MachineSwordItem || item instanceof PlushieItem || item instanceof BlahajItem || item instanceof FlorpItem;
	}
	
	void drawItem(MatrixStack matrices, Matrix4f textureMatrix, MinecraftClient client, VertexConsumerProvider immediate, ItemStack stack, boolean hand)
	{
		Item item = stack.getItem();
		if(shouldRenderSpriteInstead(item))
		{
			if(!hand)
				return;
			Vector2i uv = new Vector2i(3, 5);
			if(item instanceof AbstractWeaponItem weapon)
				uv = weapon.getHUDTexture();
			else if (item.equals(ItemRegistry.MACHINE_SWORD))
				uv = new Vector2i(MachineSwordItem.getType(stack).ordinal(), 5);
			else if (item instanceof PlushieItem || item instanceof BlahajItem)
				uv = new Vector2i(3, 0);
			else if (item instanceof FlorpItem)
				uv = new Vector2i(3, 3);
			RenderSystem.setShaderTexture(0, WEAPONS_TEXTURE);
			RenderingUtil.drawTexture(textureMatrix, new Vector4f(0, 16, 48, 32f), 0f,
					new Vec2f(384, 384), new Vector4f(uv.x * 48f, uv.y * 32f, 48f, 32f), 0.75f);
			RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		}
		else
		{
			matrices.push();
			RenderSystem.disableDepthTest();
			//This HUD is *terribly* made, and entity item models don't render correctly. To hide this, I did the following :D
			if(item.equals(Items.SHIELD))
				stack = ItemRegistry.FAKE_SHIELD.getDefaultStack();
			else if(item instanceof BannerItem)
				stack = ItemRegistry.FAKE_BANNER.getDefaultStack();
			else if(item instanceof TerminalItem)
				stack = ItemRegistry.FAKE_TERMINAL.getDefaultStack();
			else if(stack.isOf(Items.CHEST) || stack.isOf(Items.TRAPPED_CHEST))
				stack = ItemRegistry.FAKE_CHEST.getDefaultStack();
			else if(stack.isOf(Items.ENDER_CHEST))
				stack = ItemRegistry.FAKE_ENDER_CHEST.getDefaultStack();
			else if(stack.isOf(ItemRegistry.HELL_SPAWNER))
				stack = ItemRegistry.FAKE_HELL_SPAWNER.getDefaultStack();
			else if(client.getItemRenderer().getModel(stack, client.world, client.player, 0).isBuiltin())
				stack = ItemRegistry.PLACEHOLDER.getDefaultStack();
			RenderSystem.disableDepthTest();
			client.getItemRenderer().renderItem(stack, ModelTransformationMode.GUI,
					15728880, OverlayTexture.DEFAULT_UV, matrices, immediate, client.world, 1);
			RenderSystem.enableDepthTest();
			
			matrices.pop();
		}
	}
	
	void drawText(MatrixStack matrices, Text text, float x, float y, float alpha, boolean centered)
	{
		MinecraftClient client = MinecraftClient.getInstance();
		VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
		String[] lines = text.getString().split("\n");
		for (int i = 0; i < lines.length; i++)
		{
			Matrix4f matrix = matrices.peek().getPositionMatrix();
			float x1 = x - (centered ? client.textRenderer.getWidth(lines[i]) / 2f : 0);
			float y1 = y + (client.textRenderer.fontHeight + 2) * i;
			client.textRenderer.draw(Text.of(lines[i]), x1, y1, Color.ofRGBA(1f, 1f, 1f, alpha).getColor(), false,
					matrix, immediate, TextRenderer.TextLayerType.SEE_THROUGH, Color.ofRGBA(0f, 0f, 0f, 0.5f * alpha).getColor(), 15728880);
			matrix.translate(0f, 0f, -0.1f);
			client.textRenderer.draw(Text.of(lines[i]), x1, y1, Color.ofRGBA(1f, 1f, 1f, alpha).getColor(), false,
					matrix, immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
		}
	}
	
	void drawTextNoBG(MatrixStack matrices, Text text, float x, float y, float alpha, boolean centered)
	{
		if(text == null)
			return;
		MinecraftClient client = MinecraftClient.getInstance();
		VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
		String[] lines = text.getString().split("\n");
		for (int i = 0; i < lines.length; i++)
		{
			Matrix4f matrix = matrices.peek().getPositionMatrix();
			float x1 = x - (centered ? client.textRenderer.getWidth(lines[i]) / 2f : 0);
			float y1 = y + (client.textRenderer.fontHeight + 2) * i;
			client.textRenderer.draw(Text.of(lines[i]), x1, y1, Color.ofRGBA(1f, 1f, 1f, alpha).getColor(), false,
					matrix, immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
		}
	}
	
	void drawOutlinedText(MatrixStack matrices, Text text, float x, float y, float alpha)
	{
		MinecraftClient client = MinecraftClient.getInstance();
		VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		client.textRenderer.draw(text, x, y, Color.ofRGBA(1f, 1f, 1f, alpha).getColor(), false,
				matrix, immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
		matrix.translate(0f, 0f, 0.5f);
		client.textRenderer.draw(text, x, y - 1, Color.ofRGBA(0f, 0f, 0f, alpha).getColor(), false,
				matrix, immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
		client.textRenderer.draw(text, x, y + 1, Color.ofRGBA(0f, 0f, 0f, alpha).getColor(), false,
				matrix, immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
		client.textRenderer.draw(text, x - 1, y, Color.ofRGBA(0f, 0f, 0f, alpha).getColor(), false,
				matrix, immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
		client.textRenderer.draw(text, x + 1, y, Color.ofRGBA(0f, 0f, 0f, alpha).getColor(), false,
				matrix, immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
	}
 
	public static void onUpdateWingsActive()
	{
		wingHintDisplayTimer = 2.5f;
	}
	
	public static void onWhitelistHint()
	{
		whitelistHintDisplayTimer = 3f;
	}
	
	public static void onCatchFish(ItemStack stack)
	{
		if(UltracraftClient.getConfig().fishingJoke)
		{
			lastCatch = stack;
			fishTimer = 10f;
			UltraComponents.EASTER.get(MinecraftClient.getInstance().player).addFish();
		}
	}
	
	public static void onPunchCoin(int score)
	{
		if(UltracraftClient.getConfig().coinPunchCounter)
		{
			coinTimer = 10f;
			coinCombo = score + 1;
			coinRotDest += 1f + 0.05f * score;
		}
	}
	
	@SuppressWarnings("unused")
	public enum UltraHudVisibility
	{
		ALWAYS,
		LIMITED,
		NEVER
	}
}
