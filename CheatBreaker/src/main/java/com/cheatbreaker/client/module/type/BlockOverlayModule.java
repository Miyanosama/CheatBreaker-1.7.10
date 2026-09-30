package com.cheatbreaker.client.module.type;

import com.cheatbreaker.client.config.Setting;
import com.cheatbreaker.client.module.AbstractModule;
import com.cheatbreaker.client.module.ToggleKeybindModule;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockReed;
import net.minecraft.block.BlockVine;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

/** Configurable replacement for the vanilla targeted-block outline. */
public class BlockOverlayModule extends AbstractModule implements ToggleKeybindModule {
    private final Setting outline;
    private final Setting overlay;
    private final Setting dashedLines;
    private final Setting lineWidth;
    private final Setting gap;
    private final Setting renderWithoutDepth;
    private final Setting hidePlants;
    private final Setting outlineColor;
    private final Setting overlayColor;
    private final Setting toggleKeybind;

    public BlockOverlayModule() {
        super("Block Overlay");
        this.setDefaultState(false);
        this.setPreviewLabel("Block Overlay", 1.0F);
        this.toggleKeybind = new Setting(this, "Toggle Mod Keybind").setValue(0).setMinMax(0, 255);
        new Setting(this, "label").setValue("General Options");
        this.outline = new Setting(this, "Outline").setValue(true);
        this.overlay = new Setting(this, "Overlay").setValue(false);
        this.dashedLines = new Setting(this, "Dashed Lines").setValue(false);
        this.lineWidth = new Setting(this, "Line Width").setValue(2.0F).setMinMax(0.5F, 5.0F);
        this.gap = new Setting(this, "Gap").setValue(1.0F).setMinMax(0.0F, 5.0F);
        this.renderWithoutDepth = new Setting(this, "Render Without Depth").setValue(false);
        this.hidePlants = new Setting(this, "Hide Plants").setValue(false);
        new Setting(this, "label").setValue("Color Options");
        this.outlineColor = new Setting(this, "Outline Color").setValue(0x66000000)
                .setMinMax(Integer.MIN_VALUE, Integer.MAX_VALUE);
        this.overlayColor = new Setting(this, "Overlay Color").setValue(0x33000000)
                .setMinMax(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public Setting getToggleKeybind() {
        return this.toggleKeybind;
    }

    public void toggleFromKey(int key) {
        if (key != 0 && key == (Integer)this.toggleKeybind.getValue()) {
            this.setState(!this.isEnabled());
        }
    }

    public void renderSelectionBox(World world, EntityPlayer player, MovingObjectPosition hit, float partialTicks) {
        Block block = world.getBlock(hit.blockX, hit.blockY, hit.blockZ);
        if (block.getMaterial() == net.minecraft.block.material.Material.air
                || (Boolean)this.hidePlants.getValue()
                && (block instanceof BlockBush || block instanceof BlockReed || block instanceof BlockVine)) {
            return;
        }

        boolean drawOutline = (Boolean)this.outline.getValue();
        boolean drawOverlay = (Boolean)this.overlay.getValue();
        if (!drawOutline && !drawOverlay) return;

        block.setBlockBoundsBasedOnState(world, hit.blockX, hit.blockY, hit.blockZ);
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
        // Keep the vanilla clearance even at Gap = 0; a coplanar face fights
        // with the block texture in the depth buffer.
        double expansion = 0.002D * (1.0D + (Float)this.gap.getValue());
        AxisAlignedBB bounds = block.getSelectedBoundingBoxFromPool(world, hit.blockX, hit.blockY, hit.blockZ)
                .expand(expansion, expansion, expansion).getOffsetBoundingBox(-x, -y, -z);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LINE_BIT | GL11.GL_POLYGON_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(770, 771, 1, 0);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDepthMask(false);
            if ((Boolean)this.renderWithoutDepth.getValue()) GL11.glDisable(GL11.GL_DEPTH_TEST);

            if (drawOverlay) {
                // Push the filled face toward the camera so it does not flicker
                // against either the targeted block or adjacent coplanar faces.
                GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
                GL11.glPolygonOffset(-1.0F, -1.0F);
                setColor(this.overlayColor.getColorValue());
                Render.renderAABB(bounds);
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            }
            if (drawOutline) {
                GL11.glLineWidth((Float)this.lineWidth.getValue());
                if ((Boolean)this.dashedLines.getValue()) {
                    GL11.glEnable(GL11.GL_LINE_STIPPLE);
                    GL11.glLineStipple(1, (short)0x00FF);
                }
                setColor(this.outlineColor.getColorValue());
                RenderGlobal.drawOutlinedBoundingBox(bounds, -1);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void setColor(int color) {
        GL11.glColor4f((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F,
                (color & 255) / 255.0F, (color >>> 24) / 255.0F);
    }
}
