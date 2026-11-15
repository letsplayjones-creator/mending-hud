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

    // 1 Bottle o' Enchanting = ca. 7 XP im Schnitt -> 14 Durability bei Mending
    private static final int DURA_PER_BOTTLE = 14;

    private static class ArmorTarget {
        String name;
        int currentDura;
        int maxDura;
        int targetDura;

        ArmorTarget(String name, ItemStack stack) {
            this.name = name;
            this.maxDura = stack.getMaxDamage();
            this.currentDura = this.maxDura - stack.getDamageValue();
            this.targetDura = this.currentDura;
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
        String[] labels = {"Helm", "Brust", "Hose", "Schuhe"};

        var enchantmentRegistry = client.player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var mendingHolder = enchantmentRegistry.getOrThrow(Enchantments.MENDING);

        for (int i = 0; i < slots.length; i++) {
            ItemStack armor = client.player.getItemBySlot(slots[i]);
            if (!armor.isEmpty() && armor.isDamageableItem() && EnchantmentHelper.getItemEnchantmentLevel(mendingHolder, armor) > 0) {
                targets.add(new ArmorTarget(labels[i], armor));
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

            if (lowest == null) break; // Alle Teile auf 100%

            lowest.targetDura++;
            remainingDura--;
        }

        // 4. HUD-Anzeige rendern (links mittig, ideal neben uku's Armor HUD)
        int x = 12;
        int y = client.getWindow().getGuiScaledHeight() / 2 - 40;

        graphics.drawString(client.font,
                Component.literal("§bXP-Bottles: §f" + bottleCount + " §7(+" + availableDura + " Dura)"),
                x, y, 0xFFFFFFFF, true);
        y += 12;

        for (ArmorTarget t : targets) {
            boolean reached = t.currentDura >= t.targetDura;
            String statusColor = reached ? "§a✔ " : "§e➔ ";
            String line = statusColor + t.name + ": §f" + t.currentDura + " §7/ §b" + t.targetDura;

            graphics.drawString(client.font, Component.literal(line), x, y, 0xFFFFFFFF, true);
            y += 11;
        }
    }
}
