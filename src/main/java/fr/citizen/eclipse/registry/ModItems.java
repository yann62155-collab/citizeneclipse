package fr.citizen.eclipse.registry;

import fr.citizen.eclipse.CitizenEclipse;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, CitizenEclipse.MODID);

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CitizenEclipse.MODID);

    /** Matériau du casque d'observation : protection proche du cuir, réparable avec du verre teinté. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> ECLIPSE_MATERIAL = ARMOR_MATERIALS.register("eclipse",
            () -> new ArmorMaterial(
                    Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                        map.put(ArmorItem.Type.BOOTS, 1);
                        map.put(ArmorItem.Type.LEGGINGS, 2);
                        map.put(ArmorItem.Type.CHESTPLATE, 3);
                        map.put(ArmorItem.Type.HELMET, 2);
                        map.put(ArmorItem.Type.BODY, 3);
                    }),
                    12,
                    SoundEvents.ARMOR_EQUIP_LEATHER,
                    () -> Ingredient.of(Items.TINTED_GLASS),
                    List.of(new ArmorMaterial.Layer(CitizenEclipse.id("eclipse"))),
                    0.0F,
                    0.0F));

    /** Casque d'observation d'éclipse. */
    public static final DeferredItem<ArmorItem> ECLIPSE_HELMET = ITEMS.register("eclipse_helmet",
            () -> new ArmorItem(ECLIPSE_MATERIAL, ArmorItem.Type.HELMET,
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(20))));
}
