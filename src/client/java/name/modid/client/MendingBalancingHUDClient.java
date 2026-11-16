package name.modid.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MendingBalancingHUDClient implements ClientModInitializer {

    private static final int DURA_PER_BOTTLE = 14;

    private static class ArmorTarget {
        int maxDura;
        int targetDura;

        ArmorTarget(ItemStack stack) {
            this.maxDura = stack.getMaxDamage();
            int currentDura = this.maxDura - stack.getDamageValue();
            this.targetDura = currentDura;
        }
    }

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(this::onHudRender);
    }

    private void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;

        // 1. Experience Bottles im gesamten Inventar zählen
        int bottleCount = 0;
        for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
            ItemStack invStack = client.player.getInventory().getItem(i);
            if (invStack.is(Items.EXPERIENCE_BOTTLE)) {
                bottleCount += invStack.getCount();
            }
        }

        if (bottleCount == 0) return;
        int availableDura = bottleCount * DURA_PER_BOTTLE;

        // 2. Angelegte UND ausgezogene Rüstungsteile mit Mending erfassen
        List<ArmorTarget> targets = new ArrayList<>();
        var enchantmentRegistry = client.player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var mendingHolder = enchantmentRegistry.getOrThrow(Enchantments.MENDING);

        // Angelegte Rüstung prüfen
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot slot : slots) {
            ItemStack armor = client.player.getItemBySlot(slot);
            if (!armor.isEmpty() && armor.isDamageableItem() && EnchantmentHelper.getItemEnchantmentLevel(mendingHolder, armor) > 0) {
                targets.add(new ArmorTarget(armor));
            }
        }

        // Ausgezogene Rüstung im restlichen Inventar prüfen (Slots 0-35 + Offhand 40)
        for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
            // Rüstungs-Slots (36-39) überspringen, da oben bereits erfasst
            if (i >= 36 && i <= 39) continue;

            ItemStack stack = client.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ArmorItem && stack.isDamageableItem()
                    && EnchantmentHelper.getItemEnchantmentLevel(mendingHolder, stack) > 0) {
                targets.add(new ArmorTarget(stack));
            }
        }

        if (targets.isEmpty()) return;

        // Niedrigste maximale Haltbarkeit aller Teile bestimmen (Deckel für die Gleichverteilung)
        int lowestMaxDura = targets.stream()
                .mapToInt(t -> t.maxDura)
                .min()
                .orElse(0);

        // 3. Gleichverteilung (Water-Filling), gedeckelt bei lowestMaxDura
        int remainingDura = availableDura;
        while (remainingDura > 0) {
            ArmorTarget lowest = targets.stream()
                    .filter(t -> t.targetDura < lowestMaxDura)
                    .min(Comparator.comparingInt(t -> t.targetDura))
                    .orElse(null);

            if (lowest == null) break;

            lowest.targetDura++;
            remainingDura--;
        }

        int finalTarget = targets.stream()
                .mapToInt(t -> Math.min(t.targetDura, lowestMaxDura))
                .max()
                .orElse(0);

        // 4. Zahl in Weiß (0xFFFFFFFF) unten rechts rendern
        String text = String.valueOf(finalTarget);
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        int x = screenWidth - client.font.width(text) - 10;
        int y = screenHeight - 15;

        graphics.drawString(client.font, Component.literal(text), x, y, 0xFFFFFFFF, true);
    }
}
