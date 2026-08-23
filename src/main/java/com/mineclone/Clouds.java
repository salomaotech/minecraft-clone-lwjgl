package com.mineclone;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Nuvens: clusters de "voxels" brancos, geradas com a mesma seed (42) do original. */
public class Clouds {

    public static class Part { double ox, oz, sx, sz, h; }
    public static class Cluster {
        double baseX, baseY, baseZ;
        List<Part> parts = new ArrayList<>();
    }

    public final List<Cluster> clusters = new ArrayList<>();

    public Clouds() {
        Random rnd = new Random(42);
        for (int i = 0; i < 18; i++) {
            double baseX = (rnd.nextDouble() - 0.5) * 5000;
            double baseZ = (rnd.nextDouble() - 0.5) * 5000;
            double baseY = -850 - rnd.nextDouble() * 100;
            Cluster cl = new Cluster();
            cl.baseX = baseX; cl.baseY = baseY; cl.baseZ = baseZ;
            int parts = 5 + rnd.nextInt(3);
            for (int p = 0; p < parts; p++) {
                Part part = new Part();
                part.ox = (rnd.nextDouble() - 0.5) * 140;
                part.oz = (rnd.nextDouble() - 0.5) * 80;
                part.sx = 70 + rnd.nextDouble() * 50;
                part.sz = 45 + rnd.nextDouble() * 35;
                part.h = 18;
                cl.parts.add(part);
            }
            clusters.add(cl);
        }
        for (int i = 0; i < 14; i++) {
            double baseX = (rnd.nextDouble() - 0.5) * 9000;
            double baseZ = (rnd.nextDouble() - 0.5) * 9000;
            double baseY = -950 - rnd.nextDouble() * 80;
            Cluster cl = new Cluster();
            cl.baseX = baseX; cl.baseY = baseY; cl.baseZ = baseZ;
            int parts = 6 + rnd.nextInt(2);
            for (int p = 0; p < parts; p++) {
                Part part = new Part();
                part.ox = (rnd.nextDouble() - 0.5) * 180;
                part.oz = (rnd.nextDouble() - 0.5) * 100;
                part.sx = 90 + rnd.nextDouble() * 60;
                part.sz = 55 + rnd.nextDouble() * 40;
                part.h = 16;
                cl.parts.add(part);
            }
            clusters.add(cl);
        }
    }

    public void update(double dt) {
        for (Cluster c : clusters) {
            c.baseX += 6 * dt;
            if (c.baseX > 4500) c.baseX = -4500;
            if (c.baseX < -4500) c.baseX = 4500;
        }
    }
}
