package name.modid.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
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

        // 2. Rüstungsteile mit Mending erfassen
        List<ArmorTarget> targets = new ArrayList<>();
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

        var enchantmentRegistry = client.player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var mendingHolder = enchantmentRegistry.getOrThrow(Enchantments.MENDING);

        for (EquipmentSlot slot : slots) {
            ItemStack armor = client.player.getItemBySlot(slot);
            if (!armor.isEmpty() && armor.isDamageableItem() && EnchantmentHelper.getItemEnchantmentLevel(mendingHolder, armor) > 0) {
                targets.add(new ArmorTarget(armor));
            }
        }

        if (targets.isEmpty()) return;

        // 3. Absolute Gleichverteilung (Water-Filling Algorithmus)
        int remainingDura = availableDura;
        while (remainingDura > 0) {
            ArmorTarget lowest = targets.stream()
                    .filter(t -> t.targetDura < t.maxDura)
                    .min(Comparator.comparingInt(t -> t.targetDura))
                    .orElse(null);

            if (lowest == null) break;

            lowest.targetDura++;
            remainingDura--;
        }

        // Höchsten berechneten Zielwert ermitteln
        int finalTarget = targets.stream().mapToInt(t -> t.targetDura).max().orElse(0);

        // 4. Exakt die gleiche Position wie in deiner ersten Version, nur als eine Zahl + sichtbarer Alpha-Farbcode (0xFF...)
        int x = 12;
        int y = client.getWindow().getGuiScaledHeight() / 2 - 40;

        graphics.drawString(client.font,
                Component.literal(String.valueOf(finalTarget)),
                x, y, 0xFF55FF55, true);
    }
}
