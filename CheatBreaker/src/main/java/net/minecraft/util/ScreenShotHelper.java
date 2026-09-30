package net.minecraft.util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.shader.FrameBuffer;
import net.minecraft.event.ClickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class ScreenShotHelper {
    private static final Logger logger = LogManager.getLogger();
    private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
    private static final Set<String> reservedNames = new HashSet<String>();
    // Limit retained pixel arrays when the screenshot key is pressed repeatedly.
    private static final Semaphore pendingCaptures = new Semaphore(2);
    private static final ExecutorService imageWriter = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "Screenshot Writer");
            thread.setDaemon(true);
            return thread;
        }
    });
    private static IntBuffer pixelBuffer;

    public static IChatComponent saveScreenshot(File gameDirectory, int width, int height, FrameBuffer frameBuffer) {
        return saveScreenshot(gameDirectory, null, width, height, frameBuffer);
    }

    public static IChatComponent saveScreenshot(File gameDirectory, String fileName, int width, int height,
                                                 FrameBuffer frameBuffer) {
        if (!pendingCaptures.tryAcquire()) {
            return new ChatComponentText("Screenshot queue is full; please wait for the previous save.");
        }

        boolean submitted = false;
        File destination = null;
        try {
            final File directory = new File(gameDirectory, "screenshots");
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Could not create screenshots directory");
            }
            final boolean framebufferEnabled = OpenGlHelper.isFramebufferEnabled();
            final int textureWidth = framebufferEnabled ? frameBuffer.framebufferTextureWidth : width;
            final int textureHeight = framebufferEnabled ? frameBuffer.framebufferTextureHeight : height;
            final int imageWidth = framebufferEnabled ? frameBuffer.framebufferWidth : width;
            final int imageHeight = framebufferEnabled ? frameBuffer.framebufferHeight : height;
            if (textureWidth <= 0 || textureHeight <= 0 || imageWidth <= 0 || imageHeight <= 0
                    || imageWidth > textureWidth || imageHeight > textureHeight
                    || (long)textureWidth * textureHeight > Integer.MAX_VALUE) {
                throw new IOException("Invalid screenshot dimensions");
            }
            final int count = textureWidth * textureHeight;
            if (pixelBuffer == null || pixelBuffer.capacity() < count) {
                pixelBuffer = BufferUtils.createIntBuffer(count);
            }

            // OpenGL calls must remain on the render thread. All later pixel work is offloaded.
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            pixelBuffer.clear();
            if (framebufferEnabled) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, frameBuffer.framebufferTexture);
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA,
                        GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer);
            } else {
                GL11.glReadPixels(0, 0, textureWidth, textureHeight, GL12.GL_BGRA,
                        GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer);
            }
            final int[] pixels = new int[count];
            pixelBuffer.get(pixels);
            destination = reserveName(directory, fileName);
            final File output = destination;
            imageWriter.execute(new Runnable() {
                @Override
                public void run() {
                    File temporary = null;
                    try {
                        TextureUtil.func_147953_a(pixels, textureWidth, textureHeight);
                        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
                        int offset = framebufferEnabled ? (textureHeight - imageHeight) * textureWidth : 0;
                        image.setRGB(0, 0, imageWidth, imageHeight, pixels, offset, textureWidth);
                        temporary = File.createTempFile(".screenshot-", ".png", directory);
                        if (!ImageIO.write(image, "png", temporary)) {
                            throw new IOException("PNG writer unavailable");
                        }
                        Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        postResult(success(output));
                    } catch (Exception error) {
                        logger.warn("Couldn't save screenshot", error);
                        postResult(failure(error));
                    } finally {
                        if (temporary != null) temporary.delete();
                        releaseName(output);
                        pendingCaptures.release();
                    }
                }
            });
            submitted = true;
            return new ChatComponentText("Saving screenshot...");
        } catch (Exception error) {
            if (destination != null) releaseName(destination);
            logger.warn("Couldn't capture screenshot", error);
            return failure(error);
        } finally {
            if (!submitted) pendingCaptures.release();
        }
    }

    private static void postResult(final IChatComponent result) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {
            @Override
            public void run() {
                Minecraft.getMinecraft().ingameGUI.getChatGUI().func_146227_a(result);
            }
        });
    }

    private static IChatComponent success(File file) {
        ChatComponentText name = new ChatComponentText(file.getName());
        name.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getAbsolutePath()));
        name.getChatStyle().setUnderlined(Boolean.TRUE);
        return new ChatComponentTranslation("screenshot.success", name);
    }

    private static IChatComponent failure(Exception error) {
        return new ChatComponentTranslation("screenshot.failure", error.getMessage());
    }

    private static synchronized File reserveName(File directory, String fileName) throws IOException {
        if (fileName != null) {
            File file = new File(directory, fileName);
            if (!reservedNames.add(file.getAbsolutePath())) {
                throw new IOException("Screenshot is already being saved");
            }
            return file;
        }
        String timestamp = dateFormat.format(new Date());
        for (int index = 1; ; ++index) {
            File file = new File(directory, timestamp + (index == 1 ? "" : "_" + index) + ".png");
            if (!file.exists() && reservedNames.add(file.getAbsolutePath())) return file;
        }
    }

    private static synchronized void releaseName(File file) {
        reservedNames.remove(file.getAbsolutePath());
    }
}
