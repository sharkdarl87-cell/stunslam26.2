package de.sharkus.stunslam;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Auto Stun Slam: Du faellst mit der Mace auf einen Gegner zu und zielst auf ihn.
 * Die Mod schaltet auf die Axt, schlaegt (Schild wird deaktiviert), schaltet sofort
 * auf die Mace zurueck und schlaegt den Smash.
 * Mit Taste G an/aus.
 */
public class StunSlam implements ClientModInitializer {
    private static KeyMapping toggleKey;
    private static boolean enabled = false;

    // Ablauf: 0 = wartet, 1 = Axt-Schlag, 2 = Mace-Schlag
    private static int stage = 0;
    private static int axeSlot = -1;
    private static int maceSlot = -1;
    private static int cooldown = 0;

    private static final double MIN_FALL = 1.5;   // ab dieser Fallhoehe wird ausgeloest
    private static final double REACH = 3.0;      // maximale Distanz zum Gegner

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("stunslam", "main"));
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.stunslam.toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_G, category));

        ClientTickEvents.END_CLIENT_TICK.register(StunSlam::tick);
    }

    private static void say(Minecraft mc, String text) {
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(text));
    }

    private static void tick(Minecraft mc) {
        while (toggleKey.consumeClick()) {
            enabled = !enabled;
            stage = 0;
            say(mc, "Auto Stun Slam: " + (enabled ? "§aAN" : "§cAUS"));
        }
        LocalPlayer p = mc.player;
        if (!enabled || p == null || mc.level == null || mc.gameMode == null) return;
        if (cooldown > 0) { cooldown--; return; }

        Entity target = mc.crosshairPickEntity;
        boolean valid = target instanceof LivingEntity le && le.isAlive() && p.distanceTo(target) <= REACH;

        if (stage == 0) {
            if (!valid) return;
            if (p.onGround() || p.fallDistance < MIN_FALL) return;
            findSlots(p);
            if (axeSlot < 0 || maceSlot < 0) return;
            stage = 1;
        }

        if (stage == 1) {
            if (!valid) { stage = 0; return; }
            select(p, axeSlot);
            mc.gameMode.attack(p, target);
            p.swing(InteractionHand.MAIN_HAND);
            stage = 2;
            return;
        }

        if (stage == 2) {
            select(p, maceSlot);
            if (valid) {
                mc.gameMode.attack(p, target);
                p.swing(InteractionHand.MAIN_HAND);
            }
            stage = 0;
            cooldown = 10; // kurze Pause, damit nicht dauernd ausgeloest wird
        }
    }

    private static void findSlots(LocalPlayer p) {
        axeSlot = -1;
        maceSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            if (axeSlot < 0 && s.is(ItemTags.AXES)) axeSlot = i;
            if (maceSlot < 0 && s.is(Items.MACE)) maceSlot = i;
        }
    }

    private static void select(LocalPlayer p, int slot) {
        p.getInventory().setSelectedSlot(slot);
    }
}
