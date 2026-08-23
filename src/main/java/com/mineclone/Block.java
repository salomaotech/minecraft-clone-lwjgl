package com.mineclone;

/**
 * Bloco do mundo. Antes era um javafx.scene.Group com Box(es) dentro;
 * agora é apenas um registro de posição de grade + tipo. A renderização
 * (BlockRenderer) lê essa informação e desenha o cubo em OpenGL.
 */
public class Block {
    public static final double SIZE = 50;

    private final int gridX, gridY, gridZ;
    private final BlockType type;

    public Block(int x, int y, int z, BlockType type) {
        this.gridX = x;
        this.gridY = y;
        this.gridZ = z;
        this.type = type;
    }

    public int getGridX() { return gridX; }
    public int getGridY() { return gridY; }
    public int getGridZ() { return gridZ; }
    public BlockType getType() { return type; }

    /** Posição no mundo (centro do bloco), mesma convenção do original: Y positivo = para baixo. */
    public double worldX() { return gridX * SIZE; }
    public double worldY() { return gridY * SIZE; }
    public double worldZ() { return gridZ * SIZE; }

    public String key() { return key(gridX, gridY, gridZ); }

    public static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
