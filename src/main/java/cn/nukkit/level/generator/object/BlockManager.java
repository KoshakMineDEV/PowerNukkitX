package cn.nukkit.level.generator.object;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockState;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.IChunk;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.UpdateBlockPacket;
import cn.nukkit.network.protocol.UpdateSubChunkBlocksPacket;
import cn.nukkit.network.protocol.types.BlockChangeEntry;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.*;
import java.util.function.Predicate;

public class BlockManager {
    private final Level level;
    private final Int2ObjectOpenHashMap<Block> blocks;

    private int hashXYZ(int x, int y, int z, int layer) {
        return Level.localBlockHash(x, y, z, layer, level);
    }

    public BlockManager(Level level) {
        this.level = level;
        this.blocks = new Int2ObjectOpenHashMap<>();
    }

    public String getBlockIdAt(int x, int y, int z) {
        return this.getBlockIdAt(x, y, z, 0);
    }

    public String getBlockIdAt(int x, int y, int z, int layer) {
        Block block = this.blocks.computeIfAbsent(hashXYZ(x, y, z, layer), k -> level.getBlock(x, y, z));
        return block.getId();
    }

    public Block getBlockAt(int x, int y, int z) {
        return this.blocks.computeIfAbsent(hashXYZ(x, y, z, 0), k -> level.getBlock(x, y, z));
    }

    public void setBlockStateAt(Vector3 blockVector3, BlockState blockState) {
        this.setBlockStateAt(blockVector3.getFloorX(), blockVector3.getFloorY(), blockVector3.getFloorZ(), blockState);
    }

    public void setBlockStateAt(BlockVector3 blockVector3, BlockState blockState) {
        this.setBlockStateAt(blockVector3.getX(), blockVector3.getY(), blockVector3.getZ(), blockState);
    }

    public void setBlockStateAt(int x, int y, int z, BlockState state) {
        blocks.put(hashXYZ(x, y, z, 0), Block.get(state, level, x, y, z, 0));
    }

    public void setBlockStateAt(int x, int y, int z, int layer, BlockState state) {
        blocks.put(hashXYZ(x, y, z, layer), Block.get(state, level, x, y, z, layer));
    }

    public void setBlockStateAt(int x, int y, int z, String blockId) {
        Block block = Block.get(blockId, level, x, y, z, 0);
        blocks.put(hashXYZ(x, y, z, 0), block);
    }

    public IChunk getChunk(int chunkX, int chunkZ) {
        return this.level.getChunk(chunkX, chunkZ);
    }

    public long getSeed() {
        return this.level.getSeed();
    }

    public boolean isOverWorld() {
        return level.isOverWorld();
    }

    public boolean isNether() {
        return level.isNether();
    }

    public boolean isTheEnd() {
        return level.isTheEnd();
    }

    public List<Block> getBlocks() {
        return new ArrayList<>(this.blocks.values());
    }

    public void applyBlockUpdate() {
        for (var b : this.blocks.values()) {
            this.level.setBlock(b, b, true, true);
        }
    }

    public void applySubChunkUpdate() {
        this.applySubChunkUpdate(new ArrayList<>(this.blocks.values()), null);
    }

    public void applySubChunkUpdate(List<Block> blockList) {
        this.applySubChunkUpdate(blockList, b -> !b.isAir());
    }

    public void applySubChunkUpdate(List<Block> blockList, Predicate<Block> predicate) {
        if (predicate != null) {
            blockList = blockList.stream().filter(predicate).toList();
        }
        HashMap<IChunk, ArrayList<Block>> chunks = new HashMap<>();
        HashMap<SubChunkEntry, UpdateSubChunkBlocksPacket> batchs = new HashMap<>();
        for (var b : blockList) {
            ArrayList<Block> chunk = chunks.computeIfAbsent(level.getChunk(b.getChunkX(), b.getChunkZ(), true), c -> new ArrayList<>());
            chunk.add(b);
            UpdateSubChunkBlocksPacket batch = batchs.computeIfAbsent(new SubChunkEntry(b.getChunkX() << 4, (b.getFloorY() >> 4) << 4, b.getChunkZ() << 4), s -> new UpdateSubChunkBlocksPacket(s.x, s.y, s.z));
            if (b.layer == 1) {
                batch.extraBlocks.add(new BlockChangeEntry(b.asBlockVector3(), b.getBlockState().unsignedBlockStateHash(), UpdateBlockPacket.NETWORK_ID, -1, BlockChangeEntry.MessageType.NONE));
            } else {
                batch.standardBlocks.add(new BlockChangeEntry(b.asBlockVector3(), b.getBlockState().unsignedBlockStateHash(), UpdateBlockPacket.NETWORK_ID, -1, BlockChangeEntry.MessageType.NONE));
            }
        }
        chunks.entrySet().parallelStream().forEach(entry -> {
            final var key = entry.getKey();
            final var value = entry.getValue();
            key.batchProcess(unsafeChunk -> {
                value.forEach(b -> {
                    unsafeChunk.setBlockState(b.getFloorX() & 15, b.getFloorY(), b.getFloorZ() & 15, b.getBlockState(), b.layer);
                });
            });
        });
        for (var p : batchs.values()) {
            Server.broadcastPacket(level.getPlayers().values(), p);
        }
        blocks.clear();
    }

    public int getMaxHeight() {
        return level.getMaxHeight();
    }

    public int getMinHeight() {
        return level.getMinHeight();
    }

    private record SubChunkEntry(int x, int y, int z) {
    }
}
