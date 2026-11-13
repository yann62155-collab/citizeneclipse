package fr.citizen.eclipse.client.render;

import fr.citizen.eclipse.CitizenEclipse;
import fr.citizen.eclipse.entity.DarkMonk;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class DarkMonkRenderer extends MobRenderer<DarkMonk, DarkMonkModel> {
    public static final ResourceLocation GRAND_TEXTURE = CitizenEclipse.id("textures/entity/dark_monk.png");
    public static final ResourceLocation GRAND_EYES = CitizenEclipse.id("textures/entity/dark_monk_eyes.png");
    public static final ResourceLocation DISCIPLE_TEXTURE = CitizenEclipse.id("textures/entity/disciple_monk.png");
    public static final ResourceLocation DISCIPLE_EYES = CitizenEclipse.id("textures/entity/disciple_monk_eyes.png");

    private final ResourceLocation texture;

    /** Grand Moine. */
    public DarkMonkRenderer(EntityRendererProvider.Context ctx) {
        this(ctx, GRAND_TEXTURE, GRAND_EYES, 0.7F);
    }

    public DarkMonkRenderer(EntityRendererProvider.Context ctx, ResourceLocation texture, ResourceLocation eyes, float shadow) {
        super(ctx, new DarkMonkModel(ctx.bakeLayer(DarkMonkModel.LAYER)), shadow);
        this.texture = texture;
        this.addLayer(new GlowEyesLayer<>(this, eyes));
    }

    @Override
    public ResourceLocation getTextureLocation(DarkMonk entity) {
        return texture;
    }
}
