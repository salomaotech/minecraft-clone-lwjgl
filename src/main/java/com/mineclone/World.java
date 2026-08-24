package com.mineclone;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Estado do mundo: blocos em RAM + cache de chunks em disco (para não
 * estourar memória com mundo "infinito"), presets (plataforma/casa/torre),
 * e save/load do arquivo de mundo (mesmo formato do projeto original:
 * "x,y,z,TIPO" por linha, com uma linha "TIME ..." opcional no topo).
 *
 * O terreno é gerado de forma DETERMINÍSTICA POR CHUNK (seed derivada das
 * coordenadas da coluna), então cada chunk pode ser gerado sozinho, sob
 * demanda, numa thread de fundo — sem travar o game loop.
 */
public class World {

    public static final double SIZE = Block.SIZE;
    private static final int CHUNK_SIZE = 16;
    private static final int RENDER_DIST = 1; // 3x3 chunks carregados ao redor do jogador
    private static final int MAX_BLOCKS_IN_RAM = 40_000; // limite duro: acima disso descarrega fora do raio na hora

    private final Map<String, Block> blocks = new HashMap<>();
    private final File chunkDir;

    private final Set<String> loadedChunks = new HashSet<>();
    private long lastLoadCheck = 0;
    private long lastUnloadTick = 0;

    // comunicação com a thread de fundo que leu/gerou chunks no disco
    private final Queue<String> chunkRequests = new ConcurrentLinkedQueue<>();
    private final Set<String> requestedChunks = ConcurrentHashMap.newKeySet();
    private final Queue<Map.Entry<String, List<Block>>> chunkResults = new ConcurrentLinkedQueue<>();
    private Thread loaderThread;
    private volatile boolean loaderStopped = false;

    public World(File chunkDir) {
        this.chunkDir = chunkDir;
        if (!chunkDir.exists()) chunkDir.mkdirs();
    }

    public Collection<Block> blocks() { return blocks.values(); }
    public int blockCount() { return blocks.size(); }
    public Block get(int x, int y, int z) { return blocks.get(Block.key(x, y, z)); }
    public boolean contains(int x, int y, int z) { return blocks.containsKey(Block.key(x, y, z)); }

    public boolean addBlock(int x, int y, int z, BlockType t) {
        if (y < -64 || y > 5) return false;
        String k = Block.key(x, y, z);
        if (blocks.containsKey(k)) return false;
        blocks.put(k, new Block(x, y, z, t));
        return true;
    }

    public void removeBlock(Block b) {
        if (b == null) return;
        int x=b.getGridX(), y=b.getGridY(), z=b.getGridZ();
        blocks.remove(b.key());
        // escoamento: apenas água diretamente ACIMA cai para preencher o vazio
        // (Y negativo = para cima nesta convenção). Água lateral espalha pelo
        // tickWater; nunca criamos água do nada aqui.
        Block acima = get(x, y - 1, z);
        if (acima != null && acima.getType() == BlockType.AGUA) {
            blocks.remove(acima.key());
            blocks.put(b.key(), new Block(x, y, z, BlockType.AGUA));
        }
    }

    public void clear() { blocks.clear(); loadedChunks.clear(); }

    // ---- presets ----
    public void createPlatform() {
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) addBlock(x, 0, z, BlockType.GRAMA);
    }

    /** Pede os 3x3 chunks do spawn ao loader e espera chegar (uma única vez, na abertura). */
    public void createInitialWorld() {
        requestMissingChunks(0, 0);
        int expected = (2 * RENDER_DIST + 1) * (2 * RENDER_DIST + 1);
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (loadedChunks.size() < expected && System.nanoTime() < deadline) {
            applyLoadedChunks();
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
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

    // ---- chunks: carga assíncrona + geração sob demanda ----
    private static int worldToGrid(double coord) { return (int) Math.floor((coord + SIZE / 2) / SIZE); }
    private String chunkKey(int gx, int gz) {
        int cx = Math.floorDiv(gx, CHUNK_SIZE), cz = Math.floorDiv(gz, CHUNK_SIZE);
        return cx + "_" + cz;
    }

    /** Seed estável por coluna (mesma coordenada -> mesmo terreno, sempre). */
    private static long columnSeed(long x, long z, long salt) {
        long h = x * 374761393L + z * 668265263L + salt * 1274126177L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return h ^ (h >>> 16);
    }

    private void ensureLoaderThread() {
        if (loaderThread != null) return;
        loaderThread = new Thread(this::loaderLoop, "chunk-loader");
        loaderThread.setDaemon(true);
        loaderThread.start();
    }

    private void loaderLoop() {
        while (!loaderStopped) {
            String ck = chunkRequests.poll();
            if (ck != null) {
                if (loaderStopped) return;
                List<Block> list = loadOrGenerateChunk(ck);
                if (loaderStopped) return;
                chunkResults.add(new AbstractMap.SimpleEntry<>(ck, list));
                requestedChunks.remove(ck);
                continue;
            }
            try { Thread.sleep(2); } catch (InterruptedException e) { return; }
        }
    }

    private List<Block> loadOrGenerateChunk(String ck) {
        List<Block> list = new ArrayList<>();
        File f = new File(chunkDir, ck + ".txt");
        if (f.exists()) {
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String l;
                while ((l = r.readLine()) != null) {
                    l = l.trim();
                    if (l.isEmpty() || l.startsWith("#")) continue;
                    String[] c = l.split(",");
                    if (c.length != 4) continue;
                    try {
                        list.add(new Block(Integer.parseInt(c[0]), Integer.parseInt(c[1]),
                                Integer.parseInt(c[2]), BlockType.valueOf(c[3])));
                    } catch (RuntimeException badLine) {
                        System.err.println("Linha invalida no chunk " + ck + ": \"" + l + "\"");
                    }
                }
            } catch (IOException e) {
                System.err.println("Erro ao carregar chunk " + ck + ": " + e);
            }
        } else {
            generateChunkContent(ck, list);
            try (PrintWriter w = new PrintWriter(f)) {
                for (Block b : list) w.println(b.getGridX() + "," + b.getGridY() + "," + b.getGridZ() + "," + b.getType().name());
            } catch (IOException e) {
                System.err.println("Erro ao gravar chunk novo " + ck + ": " + e);
            }
        }
        return list;
    }

    /** Terreno idêntico em estilo ao original (morros/vales/água/árvores), mas por chunk. */
    private void generateChunkContent(String ck, List<Block> out) {
        String[] s = ck.split("_");
        int cx = Integer.parseInt(s[0]), cz = Integer.parseInt(s[1]);
        int minX = cx * CHUNK_SIZE, maxX = minX + CHUNK_SIZE - 1;
        int minZ = cz * CHUNK_SIZE, maxZ = minZ + CHUNK_SIZE - 1;
        Set<String> seen = new HashSet<>();
        // varre 1 coluna além da borda para capturar copas de árvores vizinhas
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                Random rnd = new Random(columnSeed(x, z, 1337));
                double n = Math.sin(x*0.09)*2.2 + Math.cos(z*0.09)*2.2 + Math.sin((x+z)*0.05)*1.5 + (rnd.nextDouble()-0.5)*1.2;
                int h = (int)Math.round(n);
                h = Math.max(-4, Math.min(2, h));
                if (h <= 0) {
                    for (int y = 0; y >= h; y--) {
                        BlockType t = (y == h ? (rnd.nextDouble()<0.04?BlockType.AREIA:BlockType.GRAMA) : y>=h+2 ? BlockType.TERRA : BlockType.PEDRA);
                        putGenerated(out, seen, x, y, z, t, minX, maxX, minZ, maxZ);
                    }
                    Random rf = new Random(columnSeed(x, z, 4242));
                    if(rf.nextDouble()<0.015 && h<1) putGenerated(out, seen, x, h-1, z, BlockType.PEDRA, minX, maxX, minZ, maxZ);
                    if(rf.nextDouble()<0.015) emitTree(out, seen, x, z, h, minX, maxX, minZ, maxZ);
                } else {
                    putGenerated(out, seen, x, h, z, BlockType.AREIA, minX, maxX, minZ, maxZ);
                    if(h>=2) putGenerated(out, seen, x, h-1, z, BlockType.TERRA, minX, maxX, minZ, maxZ);
                    for(int y=0; y<h; y++) putGenerated(out, seen, x, y, z, BlockType.AGUA, minX, maxX, minZ, maxZ);
                }
            }
        }
    }

    private static void putGenerated(List<Block> out, Set<String> seen, int x, int y, int z, BlockType t,
                                     int minX, int maxX, int minZ, int maxZ) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) return;
        if (!seen.add(Block.key(x, y, z))) return;
        out.add(new Block(x, y, z, t));
    }

    private void emitTree(List<Block> out, Set<String> seen, int x, int z, int th,
                          int minX, int maxX, int minZ, int maxZ) {
        for(int y=th-1;y>=th-5;y--) putGenerated(out, seen, x, y, z, BlockType.MADEIRA, minX, maxX, minZ, maxZ);
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) if(!(dx==0&&dz==0))
            putGenerated(out, seen, x+dx, th-5, z+dz, BlockType.FOLHA, minX, maxX, minZ, maxZ);
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) for(int dy=th-7;dy>=th-6;dy--)
            putGenerated(out, seen, x+dx, dy, z+dz, BlockType.FOLHA, minX, maxX, minZ, maxZ);
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) if(Math.abs(dx)+Math.abs(dz)<=1)
            putGenerated(out, seen, x+dx, th-8, z+dz, BlockType.FOLHA, minX, maxX, minZ, maxZ);
    }

    /** Consome na thread do jogo os chunks que o loader terminou de preparar. Barato: chamar todo frame. */
    public void applyLoadedChunks() {
        Map.Entry<String, List<Block>> e;
        while ((e = chunkResults.poll()) != null) {
            loadedChunks.add(e.getKey());
            for (Block b : e.getValue()) {
                String k = b.key();
                if (!blocks.containsKey(k)) blocks.put(k, b);
            }
        }
    }

    private void requestMissingChunks(int pcx, int pcz) {
        ensureLoaderThread();
        for (int dx = -RENDER_DIST; dx <= RENDER_DIST; dx++)
            for (int dz = -RENDER_DIST; dz <= RENDER_DIST; dz++) {
                String ck = (pcx + dx) + "_" + (pcz + dz);
                if (!loadedChunks.contains(ck) && requestedChunks.add(ck)) chunkRequests.add(ck);
            }
    }

    /**
     * Chamar a cada frame: pede chunks novos a cada ~300ms (transição suave ao
     * andar) e salva/descarrega chunks distantes a cada ~800ms.
     */
    public void tickChunks(double playerX, double playerZ) {
        long now = System.nanoTime();
        if (now - lastLoadCheck < 300_000_000L) return;
        lastLoadCheck = now;
        int pcx = Math.floorDiv(worldToGrid(playerX), CHUNK_SIZE), pcz = Math.floorDiv(worldToGrid(playerZ), CHUNK_SIZE);

        requestMissingChunks(pcx, pcz);

        boolean estouro = blocks.size() > MAX_BLOCKS_IN_RAM;
        if (!estouro && now - lastUnloadTick < 800_000_000L) return;
        lastUnloadTick = now;

        unloadFarChunks(pcx, pcz);
    }

    /**
     * Descarrega tudo fora do raio agrupando pelos PRÓPRIOS blocos (não por uma
     * lista de chunks carregados) — assim nenhum bloco fica órfão na RAM, nem
     * água que escorreu para chunk vizinho, nem mundo carregado de arquivo.
     */
    private void unloadFarChunks(int pcx, int pcz) {
        Map<String, List<Block>> byChunk = new HashMap<>();
        for (Block b : blocks.values()) byChunk.computeIfAbsent(chunkKey(b.getGridX(), b.getGridZ()), k -> new ArrayList<>()).add(b);

        Set<String> kept = new HashSet<>();
        for (Map.Entry<String, List<Block>> e : byChunk.entrySet()) {
            String ck = e.getKey();
            String[] s = ck.split("_");
            int cx = Integer.parseInt(s[0]), cz = Integer.parseInt(s[1]);
            if (Math.max(Math.abs(cx - pcx), Math.abs(cz - pcz)) > RENDER_DIST) {
                saveChunk(ck, e.getValue());
                for (Block b : e.getValue()) blocks.remove(b.key());
            } else {
                kept.add(ck);
            }
        }
        loadedChunks.clear();
        loadedChunks.addAll(kept);
    }

    /**
     * Salva em disco TUDO que está na RAM (incluindo os chunks ao redor do
     * jogador, que só iam para o disco ao ficar distantes) e encerra o loader.
     * Sem isso, fechar o jogo descartava a construção recente. Chamar ao sair.
     */
    public void flushToDiskAndStop() {
        loaderStopped = true;
        chunkRequests.clear();
        requestedChunks.clear();
        Map<String, List<Block>> byChunk = new HashMap<>();
        for (Block b : blocks.values()) byChunk.computeIfAbsent(chunkKey(b.getGridX(), b.getGridZ()), k -> new ArrayList<>()).add(b);
        for (Map.Entry<String, List<Block>> e : byChunk.entrySet()) saveChunk(e.getKey(), e.getValue());
        blocks.clear();
        loadedChunks.clear();
        chunkResults.clear();
    }

    // ---- gravação de chunk em disco ----
    private void saveChunk(String ck, List<Block> list) {
        if (list.isEmpty()) { new File(chunkDir, ck + ".txt").delete(); return; }
        try (PrintWriter w = new PrintWriter(new File(chunkDir, ck + ".txt"))) {
            for (Block b : list) w.println(b.getGridX() + "," + b.getGridY() + "," + b.getGridZ() + "," + b.getType().name());
        } catch (IOException e) {
            System.err.println("Erro ao salvar chunk " + ck + ": " + e);
        }
    }

    private long lastWaterTick=0;
    public void tickWater(){
        long now=System.nanoTime();
        if(now-lastWaterTick < 200_000_000L) return;
        lastWaterTick=now;
        // agrupa por coluna x,z
        Map<String, List<Block>> cols=new HashMap<>();
        for(Block b: blocks.values()) if(b.getType()==BlockType.AGUA){
            String k=b.getGridX()+","+b.getGridZ();
            cols.computeIfAbsent(k, kk->new ArrayList<>()).add(b);
        }
        for(Map.Entry<String, List<Block>> e: cols.entrySet()){
            List<Block> col=e.getValue();
            col.sort((a,b)-> Integer.compare(b.getGridY(), a.getGridY())); // maior Y primeiro = mais baixo visualmente
            Block bottom=col.get(0);
            int x=bottom.getGridX(), y=bottom.getGridY(), z=bottom.getGridZ();
            if(!contains(x,y+1,z)){
                // cai coluna inteira junta
                List<Block> toMove=new ArrayList<>(col);
                for(Block b: toMove){
                    blocks.remove(b.key());
                }
                for(Block b: toMove){
                    Block nb=new Block(b.getGridX(), b.getGridY()+1, b.getGridZ(), BlockType.AGUA);
                    blocks.put(nb.key(), nb);
                }
            } else if(contains(x,y+1,z) && blocks.get(Block.key(x,y+1,z)).getType()!=BlockType.AGUA){
                int[][] dirs={{1,0},{-1,0},{0,1},{0,-1}};
                java.util.Collections.shuffle(Arrays.asList(dirs), new java.util.Random());
                for(int[] d: dirs){
                    int nx=x+d[0], nz=z+d[1];
                    if(!contains(nx,y,nz)){
                        blocks.put(Block.key(nx,y,nz), new Block(nx,y,nz,BlockType.AGUA));
                        break;
                    }
                }
            }
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
