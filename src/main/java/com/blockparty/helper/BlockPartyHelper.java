package com.blockparty.helper;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

@Mod(modid = "blockpartyhelper", name = "Block Party Helper", version = "1.0")
public class BlockPartyHelper {
    
    private BlockPos nearestBlock = null;
    private IBlockState targetBlockState = null;
    private int searchRadius = 50;
    private int tickCounter = 0;
    private boolean autoWalkEnabled = true;
    
    // Toggle keybind
    private static KeyBinding toggleKey;
    
    // Mouse smoothing
    private float targetYaw = 0;
    private float targetPitch = 0;
    private float currentYawVelocity = 0;
    private float currentPitchVelocity = 0;
    
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        
        // Register toggle keybind (R key)
        toggleKey = new KeyBinding("Toggle Block Party Helper", Keyboard.KEY_R, "Block Party Helper");
        ClientRegistry.registerKeyBinding(toggleKey);
    }
    
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (toggleKey.isPressed()) {
            autoWalkEnabled = !autoWalkEnabled;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                String status = autoWalkEnabled ? "§aEnabled" : "§cDisabled";
                mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("§6Block Party Helper: " + status));
            }
        }
    }
    
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        
        // Update every 2 ticks for smooth performance
        tickCounter++;
        if (tickCounter < 2) return;
        tickCounter = 0;
        
        EntityPlayer player = mc.thePlayer;
        ItemStack held = player.getHeldItem();
        
        if (held != null && held.getItem() instanceof ItemBlock) {
            // Get the exact block state (includes metadata/color)
            ItemBlock itemBlock = (ItemBlock) held.getItem();
            Block block = itemBlock.block;
            int metadata = held.getMetadata();
            IBlockState heldState = block.getStateFromMeta(metadata);
            
            targetBlockState = heldState;
            // Always search for nearest matching block
            nearestBlock = findNearestBlock(player, heldState);
            
            // Auto-walk to the nearest block if enabled
            if (autoWalkEnabled && nearestBlock != null) {
                autoWalkToBlock(player, nearestBlock);
            }
        } else {
            targetBlockState = null;
            nearestBlock = null;
        }
    }
    
    private BlockPos findNearestBlock(EntityPlayer player, IBlockState targetState) {
        BlockPos playerPos = player.getPosition();
        BlockPos nearest = null;
        double minDist = Double.MAX_VALUE;
        
        // Search in a cube around the player
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -searchRadius; y <= searchRadius; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    IBlockState state = Minecraft.getMinecraft().theWorld.getBlockState(pos);
                    
                    // Match both block type AND metadata (for wool colors, etc.)
                    if (state.getBlock() == targetState.getBlock() && 
                        state.getBlock().getMetaFromState(state) == targetState.getBlock().getMetaFromState(targetState)) {
                        
                        double dist = playerPos.distanceSq(pos);
                        if (dist < minDist) {
                            minDist = dist;
                            nearest = pos;
                        }
                    }
                }
            }
        }
        
        return nearest;
    }
    
    private void autoWalkToBlock(EntityPlayer player, BlockPos target) {
        Minecraft mc = Minecraft.getMinecraft();
        
        // Check if already on the target block
        BlockPos playerBlockPos = new BlockPos(player.posX, player.posY - 1, player.posZ);
        IBlockState blockBelow = mc.theWorld.getBlockState(playerBlockPos);
        boolean onTargetBlock = blockBelow.getBlock() == targetBlockState.getBlock() && 
                                blockBelow.getBlock().getMetaFromState(blockBelow) == targetBlockState.getBlock().getMetaFromState(targetBlockState);
        
        if (onTargetBlock) {
            // Stop ALL movement keys including jump
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            return;
        }
        
        // Calculate direction to target (center of block)
        double targetX = target.getX() + 0.5;
        double targetY = target.getY() + 1.0;
        double targetZ = target.getZ() + 0.5;
        
        double deltaX = targetX - player.posX;
        double deltaY = targetY - player.posY;
        double deltaZ = targetZ - player.posZ;
        double horizontalDist = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        
        // Calculate target yaw and pitch for smooth camera movement
        targetYaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0f;
        targetPitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontalDist));
        
        // Smooth camera rotation (human-like)
        smoothRotateCamera(player);
        
        // Calculate angle to target
        double angleToTarget = Math.atan2(deltaZ, deltaX);
        
        // Get player's yaw in radians
        double playerYaw = Math.toRadians(player.rotationYaw + 90);
        
        // Calculate relative angle
        double relativeAngle = angleToTarget - playerYaw;
        
        // Normalize angle to -PI to PI
        while (relativeAngle > Math.PI) relativeAngle -= 2 * Math.PI;
        while (relativeAngle < -Math.PI) relativeAngle += 2 * Math.PI;
        
        // Determine which keys to press based on angle
        boolean pressForward = false;
        boolean pressBack = false;
        boolean pressLeft = false;
        boolean pressRight = false;
        boolean pressJump = false;
        boolean pressSprint = true;
        
        // If very close (within 1.5 blocks), slow down - don't sprint
        if (horizontalDist < 1.5) {
            pressSprint = false;
        }
        
        // Forward/Back
        if (Math.abs(relativeAngle) < Math.PI / 2) {
            pressForward = true; // Moving forward
        } else {
            pressBack = true; // Moving backward
        }
        
        // Left/Right strafe
        if (relativeAngle > Math.PI / 4 && relativeAngle < 3 * Math.PI / 4) {
            pressRight = true;
        } else if (relativeAngle < -Math.PI / 4 && relativeAngle > -3 * Math.PI / 4) {
            pressLeft = true;
        }
        
        // Smart jumping - only when needed, not when close to target
        if (shouldJump(player, target, deltaY, horizontalDist)) {
            pressJump = true;
        }
        
        // Apply key presses
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), pressForward);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), pressBack);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), pressLeft);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), pressRight);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), pressJump);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), pressSprint);
    }
    
    private void smoothRotateCamera(EntityPlayer player) {
        // Calculate the difference between current and target rotation
        float yawDiff = targetYaw - player.rotationYaw;
        float pitchDiff = targetPitch - player.rotationPitch;
        
        // Normalize yaw difference to -180 to 180
        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;
        
        // Smooth acceleration/deceleration for human-like movement
        float smoothness = 0.3f;
        float maxSpeed = 15.0f;
        
        // Apply smooth acceleration
        currentYawVelocity += yawDiff * smoothness;
        currentPitchVelocity += pitchDiff * smoothness;
        
        // Apply damping
        currentYawVelocity *= 0.7f;
        currentPitchVelocity *= 0.7f;
        
        // Clamp velocities
        currentYawVelocity = Math.max(-maxSpeed, Math.min(maxSpeed, currentYawVelocity));
        currentPitchVelocity = Math.max(-maxSpeed, Math.min(maxSpeed, currentPitchVelocity));
        
        // Apply rotation
        player.rotationYaw += currentYawVelocity;
        player.rotationPitch += currentPitchVelocity;
        
        // Clamp pitch to minecraft limits
        player.rotationPitch = Math.max(-90, Math.min(90, player.rotationPitch));
    }
    
    private boolean shouldJump(EntityPlayer player, BlockPos target, double deltaY, double horizontalDist) {
        // DON'T jump if very close to target (within 2 blocks) - stability first!
        if (horizontalDist < 2.0) {
            return false;
        }
        
        // Jump if target is significantly higher (more than 1 block)
        if (deltaY > 1.0 && horizontalDist < 4.0) {
            return true;
        }
        
        // Check for solid blocks in the way that need jumping over
        Vec3 playerPos = new Vec3(player.posX, player.posY, player.posZ);
        Vec3 targetPos = new Vec3(target.getX() + 0.5, player.posY, target.getZ() + 0.5);
        Vec3 direction = targetPos.subtract(playerPos).normalize();
        
        // Check 1 block ahead for obstacles
        BlockPos checkPos = new BlockPos(
            player.posX + direction.xCoord,
            player.posY,
            player.posZ + direction.zCoord
        );
        
        Block blockAhead = Minecraft.getMinecraft().theWorld.getBlockState(checkPos).getBlock();
        
        // Only jump if there's a solid block directly in the way AND we're on ground
        if (blockAhead.getMaterial().isSolid() && player.onGround) {
            return true;
        }
        
        // Sprint-jump for speed ONLY if far away (more than 3 blocks) and on ground
        if (player.onGround && horizontalDist > 3.0) {
            return (tickCounter % 5 == 0);
        }
        
        return false;
    }
    
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (targetBlockState == null) return;
        
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        
        // Get screen dimensions
        ScaledResolution sr = new ScaledResolution(mc);
        int centerX = sr.getScaledWidth() / 2;
        int centerY = sr.getScaledHeight() / 2;
        int screenWidth = sr.getScaledWidth();
        int screenHeight = sr.getScaledHeight();
        
        // Check if player is standing on the target block
        BlockPos playerBlockPos = new BlockPos(player.posX, player.posY - 1, player.posZ);
        IBlockState blockBelow = mc.theWorld.getBlockState(playerBlockPos);
        boolean onTargetBlock = blockBelow.getBlock() == targetBlockState.getBlock() && 
                                blockBelow.getBlock().getMetaFromState(blockBelow) == targetBlockState.getBlock().getMetaFromState(targetBlockState);
        
        // Draw border outline - green if on block, red if not
        drawScreenBorder(screenWidth, screenHeight, onTargetBlock);
        
        // Only draw arrow if NOT on the target block and nearest block exists
        if (!onTargetBlock && nearestBlock != null) {
            // Calculate direction to target block
            Vec3 target = new Vec3(nearestBlock.getX() + 0.5, player.posY, nearestBlock.getZ() + 0.5);
            Vec3 playerVec = new Vec3(player.posX, player.posY, player.posZ);
            Vec3 dir = target.subtract(playerVec);
            
            // Calculate angle to target (only horizontal, ignore Y)
            double angleToTarget = Math.atan2(dir.zCoord, dir.xCoord);
            
            // Get player's yaw in radians
            double playerYaw = Math.toRadians(player.rotationYaw + 90); // +90 to align with MC coords
            
            // Calculate relative angle (how much to rotate the arrow)
            double relativeAngle = angleToTarget - playerYaw;
            
            // Draw the NFSU2-style arrow (moved up to avoid blocking crosshair)
            int arrowY = centerY - 50; // 50 pixels above center
            drawNFSU2Arrow(centerX, arrowY, relativeAngle);
            
            // Draw distance below arrow
            double distance = Math.sqrt(dir.xCoord * dir.xCoord + dir.zCoord * dir.zCoord);
            String distText = String.format("%.1fm", distance);
            int textWidth = mc.fontRendererObj.getStringWidth(distText);
            mc.fontRendererObj.drawStringWithShadow(distText, centerX - textWidth / 2, arrowY + 35, 0xFFFFFF);
        }
    }
    
    private void drawNFSU2Arrow(int centerX, int centerY, double angle) {
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        
        // Translate to screen center
        GlStateManager.translate(centerX, centerY, 0);
        
        // Rotate based on direction
        GlStateManager.rotate((float) Math.toDegrees(angle), 0, 0, 1);
        
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        
        // NFSU2-style arrow dimensions (pointing up before rotation)
        float arrowLength = 25;
        float arrowWidth = 12;
        float tailWidth = 6;
        float tailLength = 8;
        
        // Bright cyan/blue color like NFSU2 GPS
        float r = 0.0f;
        float g = 0.7f;
        float b = 1.0f;
        float a = 0.95f;
        
        // Draw filled arrow
        wr.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        
        // Arrow head pointing up (before rotation)
        wr.pos(0, -arrowLength, 0).color(r, g, b, a).endVertex(); // tip
        wr.pos(-arrowWidth, -5, 0).color(r, g, b, a).endVertex(); // left wing
        wr.pos(-tailWidth, -5, 0).color(r, g, b, a).endVertex(); // left tail start
        wr.pos(-tailWidth, tailLength, 0).color(r, g, b, a).endVertex(); // left tail end
        wr.pos(tailWidth, tailLength, 0).color(r, g, b, a).endVertex(); // right tail end
        wr.pos(tailWidth, -5, 0).color(r, g, b, a).endVertex(); // right tail start
        wr.pos(arrowWidth, -5, 0).color(r, g, b, a).endVertex(); // right wing
        
        tess.draw();
        
        // Draw outline for definition
        GL11.glLineWidth(2.0f);
        wr.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        
        wr.pos(0, -arrowLength, 0).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
        wr.pos(-arrowWidth, -5, 0).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
        wr.pos(-tailWidth, -5, 0).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
        wr.pos(-tailWidth, tailLength, 0).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
        wr.pos(tailWidth, tailLength, 0).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
        wr.pos(tailWidth, -5, 0).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
        wr.pos(arrowWidth, -5, 0).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
        
        tess.draw();
        GL11.glLineWidth(1.0f);
        
        // Add inner glow effect
        wr.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        float glowSize = 0.6f;
        wr.pos(0, -arrowLength * glowSize, 0).color(1.0f, 1.0f, 1.0f, 0.8f).endVertex();
        wr.pos(-arrowWidth * glowSize, -5 * glowSize, 0).color(r, g, b, 0.6f).endVertex();
        wr.pos(-tailWidth * glowSize, -5 * glowSize, 0).color(r, g, b, 0.6f).endVertex();
        wr.pos(-tailWidth * glowSize, tailLength * glowSize, 0).color(r, g, b, 0.6f).endVertex();
        wr.pos(tailWidth * glowSize, tailLength * glowSize, 0).color(r, g, b, 0.6f).endVertex();
        wr.pos(tailWidth * glowSize, -5 * glowSize, 0).color(r, g, b, 0.6f).endVertex();
        wr.pos(arrowWidth * glowSize, -5 * glowSize, 0).color(r, g, b, 0.6f).endVertex();
        tess.draw();
        
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
    
    private void drawScreenBorder(int screenWidth, int screenHeight, boolean onTargetBlock) {
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        
        // Color based on whether player is on target block
        float r, g, b;
        if (onTargetBlock) {
            // Green
            r = 0.0f;
            g = 1.0f;
            b = 0.0f;
        } else {
            // Red
            r = 1.0f;
            g = 0.0f;
            b = 0.0f;
        }
        float a = 0.8f;
        
        int borderThickness = 4;
        
        GL11.glLineWidth(borderThickness);
        
        // Draw border as four lines forming a rectangle
        wr.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        
        // Top left -> Top right -> Bottom right -> Bottom left
        wr.pos(0, 0, 0).color(r, g, b, a).endVertex();
        wr.pos(screenWidth, 0, 0).color(r, g, b, a).endVertex();
        wr.pos(screenWidth, screenHeight, 0).color(r, g, b, a).endVertex();
        wr.pos(0, screenHeight, 0).color(r, g, b, a).endVertex();
        
        tess.draw();
        
        GL11.glLineWidth(1.0f);
        
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}