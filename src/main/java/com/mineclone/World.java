package com.mineclone;

import java.io.*;
import java.util.*;

/**
 * Estado do mundo: blocos em RAM + cache de chunks em disco (para não
 * estourar memória com mundo "infinito"), presets (plataforma/casa/torre),
 * e save/load do arquivo de mundo (mesmo formato do projeto original:
 * "x,y,z,TIPO" por linha, com uma linha "TIME ..." opcional no topo).
 *
 * Toda a lógica aqui é a mesma do App.java original — só não depende mais
 * de javafx.scene.Group.
 */
public class World {

    public static final double SIZE = Block.SIZE;
    private static final int CHUNK_SIZE = 16;
    private static final int RENDER_DIST = 1; // 3x3 chunks

    private final Map<String, Block> blocks = new HashMap<>();
    private final File chunkDir;
    private long lastChunkTick = 0;

    public World(File chunkDir) {
        this.chunkDir = chunkDir;
        if (!chunkDir.exists()) chunkDir.mkdirs();
    }

    public Collection<Block> blocks() { return blocks.values(); }
    public int blockCount() { return blocks.size(); }
    public Block get(int x, int y, int z) { return blocks.get(Block.key(x, y, z)); }
    public boolean contains(int x, int y, int z) { return blocks.containsKey(Block.key(x, y, z)); }

    public void addBlock(int x, int y, int z, BlockType t) {
        if (y < -64 || y > 5) return;
        String k = Block.key(x, y, z);
        if (blocks.containsKey(k)) return;
        if (blocks.size() > 20000) return;
        blocks.put(k, new Block(x, y, z, t));
    }

    public void removeBlock(Block b) {
        if (b == null) return;
        blocks.remove(b.key());
    }

    public void clear() { blocks.clear(); }

    // ---- presets ----
    public void createPlatform() {
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) addBlock(x, 0, z, BlockType.GRAMA);
    }

    public void createHugeWorldInitial() {
        createPlatform();
        for (int cx = -4; cx <= 4; cx++) for (int cz = -4; cz <= 4; cz++) {
            String ck = cx + "_" + cz;
            File f = new File(chunkDir, ck + ".txt");
            if (!f.exists()) {
                try (PrintWriter w = new PrintWriter(f)) { /* chunk vazio */ } catch (Exception ignored) { }
            }
        }
    }

    public void createTower() {
        clear(); createPlatform();
        for (int y = -1; y >= -10; y--) addBlock(0, y, 0, y % 2 == 0 ? BlockType.PEDRA : BlockType.MADEIRA);
        for (int y = -1; y >= -5; y--) addBlock(3, y, 3, BlockType.AREIA);
    }

    public void createDemoHouse() {
        clear(); createPlatform();
        for (int y = -1; y >= -3; y--)
            for (int x = -2; x <= 2; x++)
                for (int z = -2; z <= 2; z++)
                    if (x == -2 || x == 2 || z == -2 || z == 2) {
                        if (y == -1 && z == 2 && x == 0) continue;
                        addBlock(x, y, z, y == -3 ? BlockType.MADEIRA : BlockType.PEDRA);
                    }
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) addBlock(x, -4, z, BlockType.MADEIRA);
    }

    // ---- chunks em disco ----
    private static int worldToGrid(double coord) { return (int) Math.floor((coord + SIZE / 2) / SIZE); }
    private String chunkKey(int gx, int gz) {
        int cx = Math.floorDiv(gx, CHUNK_SIZE), cz = Math.floorDiv(gz, CHUNK_SIZE);
        return cx + "_" + cz;
    }

    private void saveChunk(String ck, List<Block> list) {
        if (list.isEmpty()) { new File(chunkDir, ck + ".txt").delete(); return; }
        try (PrintWriter w = new PrintWriter(new File(chunkDir, ck + ".txt"))) {
            for (Block b : list) w.println(b.getGridX() + "," + b.getGridY() + "," + b.getGridZ() + "," + b.getType().name());
        } catch (Exception ignored) { }
    }

    private void loadChunk(String ck) {
        File f = new File(chunkDir, ck + ".txt");
        if (!f.exists()) return;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String l;
            while ((l = r.readLine()) != null) {
                l = l.trim();
                if (l.isEmpty() || l.startsWith("#")) continue;
                String[] c = l.split(",");
                if (c.length != 4) continue;
                int x = Integer.parseInt(c[0]), y = Integer.parseInt(c[1]), z = Integer.parseInt(c[2]);
                BlockType t = BlockType.valueOf(c[3]);
                if (!blocks.containsKey(Block.key(x, y, z))) blocks.put(Block.key(x, y, z), new Block(x, y, z, t));
            }
        } catch (Exception ignored) { }
    }

    /** Chamar a cada frame; só faz trabalho a cada ~0.8s (igual ao original). */
    public void tickChunks(double playerX, double playerZ) {
        long now = System.nanoTime();
        if (now - lastChunkTick < 800_000_000L) return;
        lastChunkTick = now;
        int pcx = Math.floorDiv(worldToGrid(playerX), CHUNK_SIZE), pcz = Math.floorDiv(worldToGrid(playerZ), CHUNK_SIZE);

        Map<String, List<Block>> byChunk = new HashMap<>();
        for (Block b : blocks.values()) byChunk.computeIfAbsent(chunkKey(b.getGridX(), b.getGridZ()), k -> new ArrayList<>()).add(b);

        for (Map.Entry<String, List<Block>> e : byChunk.entrySet()) {
            String ck = e.getKey();
            String[] s = ck.split("_");
            int cx = Integer.parseInt(s[0]), cz = Integer.parseInt(s[1]);
            int dist = Math.max(Math.abs(cx - pcx), Math.abs(cz - pcz));
            if (dist > RENDER_DIST) {
                saveChunk(ck, e.getValue());
                for (Block b : e.getValue()) blocks.remove(b.key());
            }
        }
        for (int dx = -RENDER_DIST; dx <= RENDER_DIST; dx++)
            for (int dz = -RENDER_DIST; dz <= RENDER_DIST; dz++) {
                String ck = (pcx + dx) + "_" + (pcz + dz);
                if (!byChunk.containsKey(ck)) loadChunk(ck);
            }
    }

    // ---- save/load do arquivo de mundo completo ----
    public static class SavedPlayerState {
        public double worldTime, playerX, feetY, playerZ, yaw, pitch;
        public int dayCount;
    }

    public void saveWorld(File f, String headerComment, double worldTime, int dayCount,
                           double playerX, double feetY, double playerZ, double yaw, double pitch) throws IOException {
        try (PrintWriter w = new PrintWriter(new BufferedWriter(new FileWriter(f)))) {
            w.println("# Minecraft Clone - " + headerComment);
            w.println("TIME " + (int) worldTime + " " + dayCount + " " + playerX + " " + feetY + " " + playerZ + " " + yaw + " " + pitch);
            for (Block b : blocks.values()) w.println(b.getGridX() + "," + b.getGridY() + "," + b.getGridZ() + "," + b.getType().name());
        }
    }

    /** Retorna o estado do jogador salvo no arquivo (ou null se não havia linha TIME). */
    public SavedPlayerState loadWorld(File f) throws IOException {
        clear();
        SavedPlayerState state = null;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("TIME")) {
                    String[] p = line.split("\\s+");
                    if (p.length >= 7) {
                        state = new SavedPlayerState();
                        state.worldTime = Double.parseDouble(p[1]);
                        state.dayCount = Integer.parseInt(p[2]);
                        state.playerX = Double.parseDouble(p[3]);
                        state.feetY = Double.parseDouble(p[4]);
                        state.playerZ = Double.parseDouble(p[5]);
                        state.yaw = Double.parseDouble(p[6]);
                        state.pitch = p.length > 7 ? Double.parseDouble(p[7]) : 0;
                    }
                    continue;
                }
                String[] c = line.split(",");
                if (c.length != 4) continue;
                int x = Integer.parseInt(c[0]), y = Integer.parseInt(c[1]), z = Integer.parseInt(c[2]);
                addBlock(x, y, z, BlockType.valueOf(c[3]));
            }
        }
        return state;
    }
}
