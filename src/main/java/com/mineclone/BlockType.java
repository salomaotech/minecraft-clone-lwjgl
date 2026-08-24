package com.mineclone;

/**
 * Tipos de bloco do mundo. Cada tipo tem uma cor de "topo" e uma cor de
 * "lateral" (usada apenas pela GRAMA, igual ao bloco clássico do Minecraft:
 * topo verde + lados de terra). As outras texturas usam a cor de topo em
 * todas as faces, exatamente como no projeto original (Box com um único
 * PhongMaterial).
 */
public enum BlockType {
    GRAMA("Grama", rgb(95, 159, 53), rgb(121, 85, 45)),
    TERRA("Terra", rgb(134, 97, 42), rgb(101, 67, 33)),
    PEDRA("Pedra", rgb(140, 140, 140), rgb(110, 110, 110)),
    MADEIRA("Madeira", rgb(160, 120, 60), rgb(110, 75, 30)),
    AREIA("Areia", rgb(210, 190, 120), rgb(190, 170, 100)),
    AGUA("Água", rgb(64,164,223), rgb(64,164,223)),
    FOLHA("Folha", rgb(55, 124, 35), rgb(55,124,35)),
    TOCHA("Tocha", rgb(255, 190, 90), rgb(130, 95, 55));

    private final String nome;
    private final float[] topColor;   // {r,g,b} 0..1
    private final float[] sideColor;  // {r,g,b} 0..1

    BlockType(String nome, float[] top, float[] side) {
        this.nome = nome;
        this.topColor = top;
        this.sideColor = side;
    }

    private static float[] rgb(int r, int g, int b) {
        return new float[]{r / 255f, g / 255f, b / 255f};
    }

    public String getNome() { return nome; }
    public float[] getTopColor() { return topColor; }
    public float[] getSideColor() { return sideColor; }
}
