package com.mineclone;

/**
 * Raycast em grade (voxel) idêntico ao original: marcha célula a célula ao
 * longo da direção da câmera até achar um bloco sólido ou cruzar o "chão
 * infinito" (faixa Y entre 24.9 e 35).
 */
public class Raycast {

    private static final double SIZE = World.SIZE;

    public static class RayHit {
        public boolean isFloor;
        public int blockX, blockY, blockZ;
        public int placeX, placeY, placeZ;
    }

    private static int worldToGrid(double coord) { return (int) Math.floor((coord + SIZE / 2) / SIZE); }

    public static RayHit cast(World world, double camX, double camY, double camZ,
                               double dirX, double dirY, double dirZ, double maxReach) {
        double step = 1.0;
        int prevGX = worldToGrid(camX), prevGY = worldToGrid(camY), prevGZ = worldToGrid(camZ);
        for (double t = step; t <= maxReach; t += step) {
            double px = camX + dirX * t, py = camY + dirY * t, pz = camZ + dirZ * t;
            int gx = worldToGrid(px), gy = worldToGrid(py), gz = worldToGrid(pz);
            if (world.contains(gx, gy, gz)) {
                RayHit hit = new RayHit();
                hit.isFloor = false;
                hit.blockX = gx; hit.blockY = gy; hit.blockZ = gz;
                hit.placeX = prevGX; hit.placeY = prevGY; hit.placeZ = prevGZ;
                return hit;
            }
            if (py >= 24.9 && py <= 35) {
                RayHit hit = new RayHit();
                hit.isFloor = true;
                hit.placeX = gx; hit.placeZ = gz;
                return hit;
            }
            prevGX = gx; prevGY = gy; prevGZ = gz;
        }
        return null;
    }
}
