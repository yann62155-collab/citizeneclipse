package fr.citizen.eclipse.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import fr.citizen.eclipse.CitizenEclipse;
import fr.citizen.eclipse.network.ClientEclipseState;
import fr.citizen.eclipse.client.render.DarkMonkModel;
import fr.citizen.eclipse.client.render.DarkMonkRenderer;
import fr.citizen.eclipse.client.render.EclipseRenderers;
import fr.citizen.eclipse.entity.ModEntities;
import fr.citizen.eclipse.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Matrix4f;

/**
 * Rendu côté joueur : la lune (disque sombre) glisse vers le soleil pendant l'approche,
 * le couvre pendant la totale (couronne lumineuse + ciel assombri), puis repart.
 */
public final class EclipseClient {
    private EclipseClient() {}

    private static final ResourceLocation MOON = CitizenEclipse.id("textures/environment/eclipse_moon.png");
    private static final ResourceLocation CORONA = CitizenEclipse.id("textures/environment/eclipse_corona.png");

    /** Distance (en unités du ciel) entre la lune et le soleil au début de l'approche. */
    private static final float MAX_SEPARATION = 85f;
    private static final float MOON_SIZE = 22f;
    private static final float CORONA_SIZE = 60f;

    @EventBusSubscriber(modid = CitizenEclipse.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
            if (!ClientEclipseState.active()) return;
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || level.dimension() != Level.OVERWORLD) return;
            float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            renderEclipse(event.getModelViewMatrix(), level, partial);
        }

        @SubscribeEvent
        public static void onFogColor(ViewportEvent.ComputeFogColor event) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || level.dimension() != Level.OVERWORLD) return;
            float dark = ClientEclipseState.darkness();
            if (dark <= 0f) return;
            float k = 1f - 0.82f * dark;
            event.setRed(event.getRed() * k + 0.05f * dark);
            event.setGreen(event.getGreen() * k + 0.01f * dark);
            event.setBlue(event.getBlue() * k + 0.04f * dark);
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientEclipseState.clear();
        }
    }

    @EventBusSubscriber(modid = CitizenEclipse.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(DarkMonkModel.LAYER, DarkMonkModel::createBodyLayer);
        }

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.<net.minecraft.world.entity.monster.Zombie>registerEntityRenderer(ModEntities.SHADOW_ZOMBIE.get(),
                    ctx -> new EclipseRenderers.Zombie(ctx, EclipseRenderers.SHADOW_ZOMBIE));
            event.<net.minecraft.world.entity.monster.Zombie>registerEntityRenderer(ModEntities.ECLIPSE_COLOSSUS.get(),
                    ctx -> new EclipseRenderers.Zombie(ctx, EclipseRenderers.COLOSSUS));
            event.registerEntityRenderer(ModEntities.VOID_SKELETON.get(), EclipseRenderers.Skeleton::new);
            event.registerEntityRenderer(ModEntities.DUSK_SPIDER.get(), EclipseRenderers.Spider::new);
            event.registerEntityRenderer(ModEntities.ECLIPSE_WRAITH.get(), EclipseRenderers.Wraith::new);
            event.registerEntityRenderer(ModEntities.DARK_MONK.get(), DarkMonkRenderer::new);
            event.<fr.citizen.eclipse.entity.DarkMonk>registerEntityRenderer(ModEntities.DISCIPLE_MONK.get(),
                    ctx -> new DarkMonkRenderer(ctx, DarkMonkRenderer.DISCIPLE_TEXTURE, DarkMonkRenderer.DISCIPLE_EYES, 0.5F));
        }

        @SubscribeEvent
        public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
            // Teinte sombre quand on regarde à travers le casque d'éclipse (comme la citrouille).
            event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, CitizenEclipse.id("eclipse_helmet_tint"), (graphics, delta) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;
                if (!mc.player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.ECLIPSE_HELMET.get())) return;
                int w = graphics.guiWidth();
                int h = graphics.guiHeight();
                graphics.fill(0, 0, w, h, 0x55140A00);
                int border = Math.max(8, h / 10);
                graphics.fill(0, 0, w, border, 0x99000000);
                graphics.fill(0, h - border, w, h, 0x99000000);
            });
        }
    }

    // ------------------------------------------------------------------ rendu du ciel

    private static void renderEclipse(Matrix4f modelView, ClientLevel level, float partial) {
        float sep = ClientEclipseState.separation();   // 1 → 0 → -1
        float dark = ClientEclipseState.darkness();     // 0 → 1
        float rain = 1f - level.getRainLevel(partial);

        PoseStack ps = new PoseStack();
        ps.mulPose(modelView);

        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        // 1) Ciel assombri : une boîte noire translucide autour de la caméra.
        if (dark > 0f) {
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            Matrix4f m = ps.last().pose();
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            float a = 0.88f * dark;
            box(b, m, 50f, 0.02f, 0.0f, 0.03f, a);
            BufferUploader.drawWithShader(b.buildOrThrow());
        }

        // Repère du soleil (même rotation que le rendu vanilla).
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-90.0F));
        ps.mulPose(Axis.XP.rotationDegrees(level.getTimeOfDay(partial) * 360.0F));
        Matrix4f sun = ps.last().pose();

        // 2) Couronne lumineuse (additive) quand la lune couvre le soleil.
        if (dark > 0f) {
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, CORONA);
            RenderSystem.setShaderColor(1f, 0.92f, 0.75f, dark * rain);
            quad(sun, 0f, 0f, CORONA_SIZE, 100f);
        }

        // 3) La lune : disque sombre qui glisse vers le soleil.
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, MOON);
        float visible = Math.min(1f, (1f - Math.abs(sep)) * 6f + 0.35f); // apparaît en fondu au début
        RenderSystem.setShaderColor(1f, 1f, 1f, Math.max(0.2f, visible * Math.max(0.35f, rain)));
        float ox = sep * MAX_SEPARATION;         // le long de la trajectoire du soleil
        float oz = sep * MAX_SEPARATION * 0.22f; // légère diagonale, comme une vraie éclipse
        quad(sun, ox, oz, MOON_SIZE, 99f);

        ps.popPose();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
    }

    private static void quad(Matrix4f m, float cx, float cz, float s, float y) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(m, cx - s, y, cz - s).setUv(0f, 0f);
        b.addVertex(m, cx + s, y, cz - s).setUv(1f, 0f);
        b.addVertex(m, cx + s, y, cz + s).setUv(1f, 1f);
        b.addVertex(m, cx - s, y, cz + s).setUv(0f, 1f);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static void box(BufferBuilder b, Matrix4f m, float s, float r, float g, float bl, float a) {
        float[][] v = {
                {-s, -s, -s}, {s, -s, -s}, {s, s, -s}, {-s, s, -s},
                {-s, -s, s}, {s, -s, s}, {s, s, s}, {-s, s, s}};
        int[][] faces = {{0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2}, {3, 2, 6, 7}, {4, 5, 1, 0}};
        for (int[] f : faces) {
            for (int i : f) b.addVertex(m, v[i][0], v[i][1], v[i][2]).setColor(r, g, bl, a);
        }
    }
}
