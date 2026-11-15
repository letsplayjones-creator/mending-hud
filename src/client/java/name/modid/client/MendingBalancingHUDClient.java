package name.modid.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
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
        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.options.hideGui) return;

            String statusText = "Mod Active";

            // 1. XP-Flaschen zählen
            int bottleCount = 0;
            for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
                ItemStack invStack = client.player.getInventory().getItem(i);
                if (invStack.is(Items.EXPERIENCE_BOTTLE)) {
                    bottleCount += invStack.getCount();
                }
            }

            int availableDura = bottleCount * DURA_PER_BOTTLE;

            // 2. Mending-Rüstung prüfen
            List<ArmorTarget> targets = new ArrayList<>();
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

            try {
                var enchantmentRegistry = client.player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                var mendingHolder = enchantmentRegistry.getOrThrow(Enchantments.MENDING);

                for (EquipmentSlot slot : slots) {
                    ItemStack armor = client.player.getItemBySlot(slot);
                    if (!armor.isEmpty() && armor.isDamageableItem() && EnchantmentHelper.getItemEnchantmentLevel(mendingHolder, armor) > 0) {
                        targets.add(new ArmorTarget(armor));
                    }
                }
            } catch (Exception e) {
                statusText = "Error: Registry";
            }

            if (targets.isEmpty() && !statusText.startsWith("Error")) {
                statusText = "No Mending Armor";
            } else if (bottleCount == 0 && !statusText.startsWith("Error")) {
                statusText = "No XP Bottles";
            } else if (!targets.isEmpty()) {
                // 3. Ziel-Durability berechnen
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

                int finalTarget = targets.stream().mapToInt(t -> t.targetDura).max().orElse(0);
                statusText = "Mend Ziel: " + finalTarget;
            }

            // 4. Absolut sicher oben links anzeigen (X: 10, Y: 10)
            graphics.drawString(client.font, Component.literal(statusText), 10, 10, 0x55FF55, true);
        });
    }
}
