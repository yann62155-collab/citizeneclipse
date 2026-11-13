package fr.citizen.eclipse.client.render;

import fr.citizen.eclipse.CitizenEclipse;
import fr.citizen.eclipse.entity.DarkMonk;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class DarkMonkRenderer extends MobRenderer<DarkMonk, DarkMonkModel> {
    private static final ResourceLocation TEXTURE = CitizenEclipse.id("textures/entity/dark_monk.png");
    private static final ResourceLocation EYES = CitizenEclipse.id("textures/entity/dark_monk_eyes.png");

    public DarkMonkRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DarkMonkModel(ctx.bakeLayer(DarkMonkModel.LAYER)), 0.7F);
        this.addLayer(new GlowEyesLayer<>(this, EYES));
    }

    @Override
    public ResourceLocation getTextureLocation(DarkMonk entity) {
        return TEXTURE;
    }
}
