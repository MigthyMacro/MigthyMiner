package com.hyperclient.HyperMiner.baritone.automine;

import com.hyperclient.HyperMiner.HyperMiner;
import com.hyperclient.HyperMiner.baritone.automine.config.AutoMineType;
import com.hyperclient.HyperMiner.baritone.automine.config.MineBehaviour;
import com.hyperclient.HyperMiner.baritone.automine.pathing.config.PathBehaviour;
import com.hyperclient.HyperMiner.baritone.logging.Logger;
import com.hyperclient.HyperMiner.baritone.automine.pathing.AStarPathFinder;
import com.hyperclient.HyperMiner.baritone.structures.BlockNode;
import com.hyperclient.HyperMiner.baritone.structures.BlockType;
import com.hyperclient.HyperMiner.handlers.KeybindHandler;
import com.hyperclient.HyperMiner.player.Rotation;
import com.hyperclient.HyperMiner.render.BlockRenderer;
import com.hyperclient.HyperMiner.utils.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class AutoMineBaritone{

    Minecraft mc = Minecraft.getMinecraft();
    MineBehaviour mineBehaviour;

    LinkedList<BlockNode> blocksToMine = new LinkedList<>();
    LinkedList<BlockNode> minedBlocks = new LinkedList<>();

    boolean inAction = false;
    Rotation rotation = new Rotation();

    int deltaJumpTick = 0;

    enum PlayerState {
        WALKING,
        MINING,
        NONE
    }
    PlayerState currentState;
    Block[] targetBlockType;
    boolean enabled;

    AStarPathFinder pathFinder;

    public AutoMineBaritone(MineBehaviour mineBehaviour){
        this.mineBehaviour = mineBehaviour;
        pathFinder = new AStarPathFinder(getPathBehaviour());

    }


    public void clearBlocksToWalk(){
        blocksToMine.clear();
        BlockRenderer.renderMap.clear();
        minedBlocks.clear();
    }


    public void enableBaritone(Block... blockType){
        enabled = true;
        mc.thePlayer.addChatMessage(new ChatComponentText("Starting automine"));
        targetBlockType = blockType;

        clearBlocksToWalk();

        KeybindHandler.resetKeybindState();

        if(mineBehaviour.isShiftWhenMine())
            KeybindHandler.setKeyBindState(KeybindHandler.keyBindShift, true);


        new Thread(() -> {
            try{
                if(mineBehaviour.isMineWithPreference())
                    blocksToMine = pathFinder.getPathWithPreference(blockType);
                else
                    blocksToMine = pathFinder.getPath(blockType);
            } catch (Throwable e){
                Logger.playerLog("Error when getting path!");
                e.printStackTrace();
            }
            if (!blocksToMine.isEmpty()) {
                for (BlockNode blockNode : blocksToMine) {
                    BlockRenderer.renderMap.put(blockNode.getBlockPos(), Color.ORANGE);
                }
                BlockRenderer.renderMap.put(blocksToMine.getFirst().getBlockPos(), Color.RED);
            } else {
                Logger.playerLog("blocks to mine EMPTY!");
            }
            Logger.log("Starting to mine");
            inAction = true;
            currentState = PlayerState.NONE;
            stuckTickCount = 0;
        }).start();
    }


    public void disableBaritone() {
        pauseMacro();
        enabled = false;
    }
    private void pauseMacro() {
        inAction = false;
        currentState = PlayerState.NONE;
        KeybindHandler.resetKeybindState();

        if(mineBehaviour.isShiftWhenMine())
            KeybindHandler.setKeyBindState(KeybindHandler.keyBindShift, true);

        if(!blocksToMine.isEmpty())
            pathFinder.addToBlackList(blocksToMine.getLast().getBlockPos());
        clearBlocksToWalk();

    }
    public boolean isEnabled(){
        return enabled;
    }


    public void onOverlayRenderEvent(RenderGameOverlayEvent event){
        if(event.type == RenderGameOverlayEvent.ElementType.TEXT){
            if(blocksToMine != null){
                if(!blocksToMine.isEmpty()){
                    for(int i = 0; i < blocksToMine.size(); i++){
                        mc.fontRendererObj.drawString(blocksToMine.get(i).getBlockPos().toString() + " " + blocksToMine.get(i).getBlockType().toString() , 5, 5 + 10 * i, -1);
                    }
                }
            }
            if(currentState != null)
                mc.fontRendererObj.drawString(currentState.toString(), 300, 5, -1);
        }
    }


    int stuckTickCount = 0;
    public void onTickEvent(TickEvent.Phase phase){

        if(phase != TickEvent.Phase.START || !inAction || blocksToMine.isEmpty())
            return;


        if (shouldRemoveFromList(blocksToMine.getLast())) {
            stuckTickCount = 0;
            minedBlocks.add(blocksToMine.getLast());
            BlockRenderer.renderMap.remove(blocksToMine.getLast().getBlockPos());
            blocksToMine.removeLast();
        } else {
            //stuck handling
            stuckTickCount++;
            if(stuckTickCount > 20 * mineBehaviour.getRestartTimeThreshold()){
                new Thread(restartBaritone).start();
                return;
            }
        }

        if(blocksToMine.isEmpty() || BlockUtils.isPassable(blocksToMine.getFirst().getBlockPos())){
            mc.thePlayer.addChatMessage(new ChatComponentText("Finished baritone"));
            disableBaritone();
            return;
        }

        updateState();

        BlockPos lastMinedBlockPos = minedBlocks.isEmpty() ? null : minedBlocks.getLast().getBlockPos();
        BlockPos targetMineBlock = blocksToMine.getLast().getBlockPos();

        switch (currentState){
            case WALKING:
                KeybindHandler.updateKeys(
                        (lastMinedBlockPos != null || BlockUtils.isPassable(targetMineBlock)),
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        deltaJumpTick > 0);

                if(BlockUtils.isPassable(targetMineBlock))
                    rotation.intLockAngle(AngleUtils.getRequiredYaw(targetMineBlock), mc.thePlayer.rotationPitch, mineBehaviour.getRotationTime());
                else if(lastMinedBlockPos != null)
                    rotation.intLockAngle(AngleUtils.getRequiredYaw(lastMinedBlockPos), mc.thePlayer.rotationPitch, mineBehaviour.getRotationTime());


                if(lastMinedBlockPos != null){
                    if(blocksToMine.getLast().getBlockType() == BlockType.WALK){
                        if (targetMineBlock.getY() >= (int) mc.thePlayer.posY + 1)
                            deltaJumpTick = 3;

                    } else {
                        if (minedBlocks.getLast().getBlockType() == BlockType.MINE) {
                            if (lastMinedBlockPos.getY() >= (int) mc.thePlayer.posY + 1 && !(BlockUtils.onTheSameXZ(lastMinedBlockPos, BlockUtils.getPlayerLoc()))
                                    && (BlockUtils.fitsPlayer(lastMinedBlockPos.down()) || BlockUtils.fitsPlayer(lastMinedBlockPos.down(2))))
                                deltaJumpTick = 3;
                        }
                    }
                } else {
                    if(BlockUtils.isPassable(targetMineBlock) && targetMineBlock.getY() == (int) mc.thePlayer.posY + 1)
                        deltaJumpTick = 3;
                }
                break;

            case MINING:
                mc.thePlayer.inventory.currentItem = PlayerUtils.getItemInHotbar("Pick", "Drill", "Gauntlet");
                KeybindHandler.updateKeys(
                        false,
                        false,
                        false,
                        false,
                        mc.objectMouseOver != null && mc.objectMouseOver.getBlockPos() != null &&
                                mc.objectMouseOver.getBlockPos().equals(targetMineBlock) && PlayerUtils.hasStoppedMoving(),
                        false,
                        mineBehaviour.isShiftWhenMine(),
                        false);


                if(mc.objectMouseOver != null && mc.objectMouseOver.getBlockPos() != null){
                    if (!BlockUtils.isPassable(targetMineBlock) && !rotation.rotating)
                        rotation.intLockAngle(AngleUtils.getRequiredYaw(targetMineBlock), AngleUtils.getRequiredPitch(targetMineBlock), mineBehaviour.getRotationTime());
                }
                break;
        }

        if (deltaJumpTick > 0)
            deltaJumpTick--;
    }

    public void onRenderEvent(){
        if(rotation.rotating)
            rotation.update();

    }

    private void updateState(){

        if(mineBehaviour.getMineType() == AutoMineType.STATIC) {
            currentState = PlayerState.MINING;
            return;
        }

        if(blocksToMine.isEmpty())
            return;

        if(minedBlocks.isEmpty()){
            currentState =  blocksToMine.getLast().getBlockType().equals(BlockType.MINE) ? PlayerState.MINING : PlayerState.WALKING;
            return;
        }
        if(blocksToMine.getLast().getBlockType() == BlockType.WALK) {
            currentState = PlayerState.WALKING;
            return;
        }

        if(currentState == PlayerState.WALKING){
            if(minedBlocks.getLast().getBlockType() == BlockType.WALK){
                if(blocksToMine.getLast().getBlockType() == BlockType.MINE)
                    currentState = PlayerState.MINING;

            } else if(minedBlocks.getLast().getBlockType() == BlockType.MINE) {
                if (BlockUtils.onTheSameXZ(minedBlocks.getLast().getBlockPos(), BlockUtils.getPlayerLoc()))
                    currentState = PlayerState.MINING;
            }

        } else if(currentState == PlayerState.MINING){
            if (blocksToMine.getLast().getBlockType() == BlockType.MINE) {
                if( (BlockUtils.fitsPlayer(minedBlocks.getLast().getBlockPos().down()) || BlockUtils.fitsPlayer(minedBlocks.getLast().getBlockPos().down(2)))
                        && !BlockUtils.onTheSameXZ(minedBlocks.getLast().getBlockPos(), BlockUtils.getPlayerLoc())) {
                    currentState = PlayerState.WALKING;
                }
            }

        } else if(currentState == PlayerState.NONE){
            currentState = blocksToMine.getLast().getBlockType().equals(BlockType.MINE) ? PlayerState.MINING : PlayerState.WALKING;
        }
    }

    private final Runnable restartBaritone = () -> {
        try {
            pauseMacro();
            mc.thePlayer.addChatMessage(new ChatComponentText("Restarting baritone"));
            Thread.sleep(200);
            KeybindHandler.setKeyBindState(KeybindHandler.keybindS, true);
            Thread.sleep(100);
            enableBaritone(targetBlockType);
        } catch (InterruptedException ignored) {}
    };

    private boolean shouldRemoveFromList(BlockNode lastBlockNode){
        if(lastBlockNode.getBlockType() == BlockType.MINE)
            return BlockUtils.isPassable(lastBlockNode.getBlockPos()) || BlockUtils.getBlock(lastBlockNode.getBlockPos()).equals(Blocks.bedrock);
        else
            return BlockUtils.onTheSameXZ(lastBlockNode.getBlockPos(), BlockUtils.getPlayerLoc()) || !BlockUtils.fitsPlayer(lastBlockNode.getBlockPos().down());
    }

    private PathBehaviour getPathBehaviour(){
        return new PathBehaviour(
                mineBehaviour.getForbiddenMiningBlocks() == null ? null : mineBehaviour.getForbiddenMiningBlocks(),
                mineBehaviour.getAllowedMiningBlocks() == null ? null : mineBehaviour.getAllowedMiningBlocks(),
                mineBehaviour.getMaxY(),
                mineBehaviour.getMinY(),
                mineBehaviour.getMineType() == AutoMineType.DYNAMIC ? 30 : 4,
                mineBehaviour.getMineType() == AutoMineType.STATIC
        );
    }


}
