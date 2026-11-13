package fr.citizen.eclipse.client.render;

import fr.citizen.eclipse.CitizenEclipse;
import fr.citizen.eclipse.entity.DarkMonk;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Modèle du Moine ténébreux : capuche pointue (visage dans l'ombre), longue robe,
 * bras croisés au repos, cape en lambeaux qui flotte. Texture 128×64.
 */
public class DarkMonkModel extends HierarchicalModel<DarkMonk> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(CitizenEclipse.id("dark_monk"), "main");

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart robe;
    private final ModelPart crossedArms;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart cape;
    private final ModelPart mantle;
    private final ModelPart rightFoot;
    private final ModelPart leftFoot;

    public DarkMonkModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.robe = root.getChild("robe");
        this.crossedArms = root.getChild("crossed_arms");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.cape = root.getChild("cape");
        this.mantle = root.getChild("mantle");
        this.rightFoot = root.getChild("right_foot");
        this.leftFoot = root.getChild("left_foot");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Capuche (9×10×9) + pointe.
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.5F, -10.0F, -4.5F, 9.0F, 10.0F, 9.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        head.addOrReplaceChild("hood_tip", CubeListBuilder.create()
                        .texOffs(36, 0).addBox(-2.0F, -2.0F, -1.0F, 4.0F, 2.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, -10.0F, 0.0F, -0.35F, 0.0F, 0.0F));

        // Mantelet sur les épaules (en lambeaux).
        root.addOrReplaceChild("mantle", CubeListBuilder.create()
                        .texOffs(86, 0).addBox(-5.0F, -0.5F, -3.5F, 10.0F, 4.0F, 7.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        // Buste (8×12×6) et robe évasée jusqu'au sol (10×12×7).
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 19).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("robe", CubeListBuilder.create()
                        .texOffs(0, 37).addBox(-5.0F, 0.0F, -3.5F, 10.0F, 12.0F, 7.0F),
                PartPose.offset(0.0F, 12.0F, 0.0F));

        // Bras croisés (au repos) : deux manches depuis les épaules + avant-bras croisés sur la poitrine.
        root.addOrReplaceChild("crossed_arms", CubeListBuilder.create()
                        .texOffs(46, 37).addBox(-7.5F, 0.0F, -2.5F, 3.0F, 9.0F, 4.0F)
                        .texOffs(46, 37).mirror().addBox(4.5F, 0.0F, -2.5F, 3.0F, 9.0F, 4.0F).mirror(false)
                        .texOffs(28, 19).addBox(-7.0F, 5.5F, -6.5F, 14.0F, 4.0F, 4.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        // Bras séparés (en combat).
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(34, 37).addBox(-2.0F, -2.0F, -1.5F, 3.0F, 12.0F, 3.0F),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                        .texOffs(34, 37).mirror().addBox(-1.0F, -2.0F, -1.5F, 3.0F, 12.0F, 3.0F),
                PartPose.offset(5.0F, 2.0F, 0.0F));

        // Pieds (bottes) qui dépassent sous la robe.
        root.addOrReplaceChild("right_foot", CubeListBuilder.create()
                        .texOffs(60, 37).addBox(-1.5F, -3.0F, -3.0F, 3.0F, 3.0F, 4.0F),
                PartPose.offset(-2.2F, 24.0F, -2.5F));
        root.addOrReplaceChild("left_foot", CubeListBuilder.create()
                        .texOffs(60, 37).mirror().addBox(-1.5F, -3.0F, -3.0F, 3.0F, 3.0F, 4.0F),
                PartPose.offset(2.2F, 24.0F, -2.5F));

        // Cape en lambeaux dans le dos.
        root.addOrReplaceChild("cape", CubeListBuilder.create()
                        .texOffs(64, 0).addBox(-5.5F, 0.0F, 0.0F, 11.0F, 23.0F, 1.0F),
                PartPose.offset(0.0F, 0.5F, 3.2F));

        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(DarkMonk entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;

        // Lévitation légère : le moine « flotte » dans sa robe.
        float bob = Mth.sin(ageInTicks * 0.06F) * 0.6F;
        head.y += bob;
        body.y += bob;
        crossedArms.y += bob;
        rightArm.y += bob;
        leftArm.y += bob;
        cape.y += bob;
        mantle.y += bob;
        robe.y += bob * 0.5F;

        // Robe qui ondule en marchant.
        robe.xRot = Mth.cos(limbSwing * 0.6662F) * 0.12F * limbSwingAmount;
        robe.zRot = Mth.sin(ageInTicks * 0.05F) * 0.02F;

        // Pas : les pieds avancent et reculent sous la robe.
        float step = Mth.cos(limbSwing * 0.6662F) * 2.0F * limbSwingAmount;
        rightFoot.z += step;
        leftFoot.z -= step;
        rightFoot.xRot = Mth.cos(limbSwing * 0.6662F) * 0.4F * limbSwingAmount;
        leftFoot.xRot = -rightFoot.xRot;

        // Cape qui flotte au vent, plus fort en mouvement.
        cape.xRot = 0.12F + limbSwingAmount * 0.7F + Mth.sin(ageInTicks * 0.09F) * 0.06F;
        cape.zRot = Mth.sin(ageInTicks * 0.05F) * 0.04F;

        boolean casting = entity.isCasting();
        boolean fighting = casting || entity.isAggressive() || this.attackTime > 0.0F;
        crossedArms.visible = !fighting;
        rightArm.visible = fighting;
        leftArm.visible = fighting;
        if (casting) {
            // Incantation : bras levés vers le ciel, mains qui tremblent.
            float shake = Mth.sin(ageInTicks * 0.9F) * 0.12F;
            rightArm.xRot = -2.7F + shake;
            leftArm.xRot = -2.7F - shake;
            rightArm.zRot = 0.35F;
            leftArm.zRot = -0.35F;
        } else if (fighting) {
            AnimationUtils.animateZombieArms(leftArm, rightArm, true, this.attackTime, ageInTicks);
        }
    }
}
