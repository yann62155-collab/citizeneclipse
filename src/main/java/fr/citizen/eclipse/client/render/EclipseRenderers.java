package fr.citizen.eclipse.client.render;

import fr.citizen.eclipse.CitizenEclipse;
import fr.citizen.eclipse.entity.DuskSpider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PhantomRenderer;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;

/** Rendus des créatures d'éclipse : modèles vanilla, teinte sombre et yeux lumineux. */
public final class EclipseRenderers {
    private EclipseRenderers() {}

    public static final ResourceLocation HUMANOID_EYES = CitizenEclipse.id("textures/entity/eclipse_eyes_humanoid.png");
    public static final ResourceLocation SKELETON_EYES = CitizenEclipse.id("textures/entity/eclipse_eyes_skeleton.png");

    // Couleurs ARGB de la teinte (alpha = intensité).
    public static final int SHADOW_ZOMBIE = 0xD8050308;
    public static final int COLOSSUS = 0xC8400606;
    public static final int VOID_SKELETON = 0xC0240A3C;
    public static final int DUSK_SPIDER = 0xC8180306;
    public static final int WRAITH = 0xB0300848;

    public static class Zombie extends ZombieRenderer {
        public Zombie(EntityRendererProvider.Context ctx, int tint) {
            super(ctx);
            this.addLayer(new TintLayer<>(this, tint));
            this.addLayer(new GlowEyesLayer<>(this, HUMANOID_EYES));
        }
    }

    public static class Skeleton extends SkeletonRenderer {
        public Skeleton(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.addLayer(new TintLayer<>(this, VOID_SKELETON));
            this.addLayer(new GlowEyesLayer<>(this, SKELETON_EYES));
        }
    }

    public static class Spider extends SpiderRenderer<DuskSpider> {
        public Spider(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.addLayer(new TintLayer<>(this, DUSK_SPIDER));
        }
    }

    public static class Wraith extends PhantomRenderer {
        public Wraith(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.addLayer(new TintLayer<>(this, WRAITH));
        }
    }
}
