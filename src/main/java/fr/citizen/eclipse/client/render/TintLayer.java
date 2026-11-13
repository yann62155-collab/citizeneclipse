package fr.citizen.eclipse.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;

/** Repeint le modèle d'une couleur sombre translucide (zombie noir, squelette violet…). */
public class TintLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private final int argb;

    public TintLayer(RenderLayerParent<T, M> parent, int argb) {
        super(parent);
        this.argb = argb;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(this.getTextureLocation(entity)));
        this.getParentModel().renderToBuffer(poseStack, vc, packedLight, LivingEntityRenderer.getOverlayCoords(entity, 0.0F), argb);
    }
}
