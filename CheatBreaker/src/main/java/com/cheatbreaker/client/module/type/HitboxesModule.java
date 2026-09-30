package com.cheatbreaker.client.module.type;

import com.cheatbreaker.client.config.Setting;
import com.cheatbreaker.client.module.AbstractModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.item.EntityEnderEye;
import net.minecraft.entity.item.EntityFireworkRocket;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

/** Configurable entity hitboxes drawn only for entities already selected by RenderGlobal. */
public class HitboxesModule extends AbstractModule {
    private final Setting showPlayer;
    private final Setting playerOutline;
    private final Setting playerOverlay;
    private final Setting playerEye;
    private final Setting playerLook;
    private final Setting showOwn;
    private final Setting playerDashed;
    private final Setting playerWidth;
    private final Setting ownColor;
    private final Setting playerColor;
    private final Setting playerEyeColor;
    private final Setting playerLookColor;
    private final Setting showMob;
    private final Setting mobOutline;
    private final Setting mobOverlay;
    private final Setting mobEye;
    private final Setting mobLook;
    private final Setting mobDashed;
    private final Setting mobWidth;
    private final Setting mobColor;
    private final Setting mobEyeColor;
    private final Setting mobLookColor;
    private final Setting showItem;
    private final Setting showProjectile;
    private final Setting showXpOrb;

    public HitboxesModule() {
        super("Hitboxes");
        this.setDefaultState(false);
        this.setPreviewLabel("Hitboxes", 1.0F);
        new Setting(this, "label").setValue("Player Hitbox Options");
        this.showPlayer = new Setting(this, "Show Player Hitbox").setValue(true);
        this.playerOutline = new Setting(this, "Show Player Hitbox Outline").setValue(true);
        this.playerOverlay = new Setting(this, "Show Player Hitbox Overlay").setValue(false);
        this.playerEye = new Setting(this, "Show Player Eye Height").setValue(true);
        this.playerLook = new Setting(this, "Show Player Look Vector").setValue(true);
        this.showOwn = new Setting(this, "Show Own Hitbox").setValue(false);
        this.playerDashed = new Setting(this, "Show Player Dashed Line").setValue(false);
        this.playerWidth = new Setting(this, "Player Line Thickness").setValue(1.0F).setMinMax(1.0F, 5.0F);
        this.ownColor = color("Own Hitbox Outline Color", 0xFFFFFFFF);
        this.playerColor = color("Player Outline Color", 0xFFFFFFFF);
        this.playerEyeColor = color("Player Eye Height Color", 0xFFFF0000);
        this.playerLookColor = color("Player Look Vector Color", 0xFF0000FF);

        new Setting(this, "label").setValue("Mob Hitbox Options");
        this.showMob = new Setting(this, "Show Mob Hitbox").setValue(true);
        this.mobOutline = new Setting(this, "Show Mob Hitbox Outline").setValue(true);
        this.mobOverlay = new Setting(this, "Show Mob Hitbox Overlay").setValue(false);
        this.mobEye = new Setting(this, "Show Mob Eye Height").setValue(true);
        this.mobLook = new Setting(this, "Show Mob Look Vector").setValue(true);
        this.mobDashed = new Setting(this, "Show Mob Dashed Line").setValue(false);
        this.mobWidth = new Setting(this, "Mob Line Thickness").setValue(1.0F).setMinMax(1.0F, 5.0F);
        this.mobColor = color("Mob Outline Color", 0xFFFFFFFF);
        this.mobEyeColor = color("Mob Eye Height Color", 0xFFFF0000);
        this.mobLookColor = color("Mob Look Vector Color", 0xFF0000FF);

        new Setting(this, "label").setValue("Item Hitbox Options");
        this.showItem = new Setting(this, "Show Item Hitbox").setValue(false);
        new Setting(this, "label").setValue("Projectile Hitbox Options");
        this.showProjectile = new Setting(this, "Show Projectile Hitbox").setValue(false);
        new Setting(this, "label").setValue("Exp Orb Hitbox Options");
        this.showXpOrb = new Setting(this, "Show Exp Orb Hitbox").setValue(false);
    }

    private Setting color(String label, int value) {
        return new Setting(this, label).setValue(value).setMinMax(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public void renderHitbox(Entity entity, double x, double y, double z, float partialTicks) {
        boolean player = entity instanceof EntityPlayer;
        boolean mob = !player && entity instanceof EntityLivingBase;
        boolean own = player && entity == Minecraft.getMinecraft().thePlayer;
        if (own && Minecraft.getMinecraft().gameSettings.thirdPersonView == 0) return;
        if (player) {
            if (!bool(this.showPlayer) || own && !bool(this.showOwn)) return;
        } else if (mob) {
            if (!bool(this.showMob)) return;
        } else if (entity instanceof EntityItem) {
            if (!bool(this.showItem)) return;
        } else if (entity instanceof EntityXPOrb) {
            if (!bool(this.showXpOrb)) return;
        } else if (entity instanceof IProjectile || entity instanceof EntityFireball
                || entity instanceof EntityFishHook || entity instanceof EntityEnderEye
                || entity instanceof EntityFireworkRocket) {
            if (!bool(this.showProjectile)) return;
        } else {
            return;
        }

        boolean outline = player ? bool(this.playerOutline) : mob ? bool(this.mobOutline) : true;
        boolean overlay = player ? bool(this.playerOverlay) : mob && bool(this.mobOverlay);
        boolean eye = player ? bool(this.playerEye) : mob && bool(this.mobEye);
        boolean look = player ? bool(this.playerLook) : mob && bool(this.mobLook);
        if (!outline && !overlay && !eye && !look) return;

        AxisAlignedBB bounds = entity.boundingBox.getOffsetBoundingBox(
                x - entity.posX, y - entity.posY, z - entity.posZ);
        int outlineColor = player ? (own ? this.ownColor.getColorValue() : this.playerColor.getColorValue())
                : mob ? this.mobColor.getColorValue() : 0xFFFFFFFF;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LINE_BIT | GL11.GL_POLYGON_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(770, 771, 1, 0);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(false);
            GL11.glLineWidth(player ? (Float)this.playerWidth.getValue()
                    : mob ? (Float)this.mobWidth.getValue() : 1.0F);

            if (overlay) {
                GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
                GL11.glPolygonOffset(-1.0F, -1.0F);
                setColor(0x33000000 | (outlineColor & 0xFFFFFF));
                Render.renderAABB(bounds.expand(0.002D, 0.002D, 0.002D));
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            }
            if (outline) {
                if (player ? bool(this.playerDashed) : mob && bool(this.mobDashed)) {
                    GL11.glEnable(GL11.GL_LINE_STIPPLE);
                    GL11.glLineStipple(1, (short)0x00FF);
                }
                setColor(outlineColor);
                RenderGlobal.drawOutlinedBoundingBox(bounds, -1);
                GL11.glDisable(GL11.GL_LINE_STIPPLE);
            }
            if (eye || look) {
                double eyeY = y + entity.getEyeHeight();
                if (eye) {
                    setColor(player ? this.playerEyeColor.getColorValue() : this.mobEyeColor.getColorValue());
                    GL11.glBegin(GL11.GL_LINES);
                    GL11.glVertex3d(bounds.minX, eyeY, z);
                    GL11.glVertex3d(bounds.maxX, eyeY, z);
                    GL11.glVertex3d(x, eyeY, bounds.minZ);
                    GL11.glVertex3d(x, eyeY, bounds.maxZ);
                    GL11.glEnd();
                }
                if (look && entity instanceof EntityLivingBase) {
                    Vec3 direction = ((EntityLivingBase)entity).getLook(partialTicks);
                    setColor(player ? this.playerLookColor.getColorValue() : this.mobLookColor.getColorValue());
                    GL11.glBegin(GL11.GL_LINES);
                    GL11.glVertex3d(x, eyeY, z);
                    GL11.glVertex3d(x + direction.xCoord * 2.0D,
                            eyeY + direction.yCoord * 2.0D, z + direction.zCoord * 2.0D);
                    GL11.glEnd();
                }
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static boolean bool(Setting setting) {
        return Boolean.TRUE.equals(setting.getValue());
    }

    private static void setColor(int color) {
        GL11.glColor4f((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F,
                (color & 255) / 255.0F, (color >>> 24) / 255.0F);
    }
}
