package com.mineclone;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renderização 3D usando o pipeline fixed-function do OpenGL (glBegin/glLight)
 * em vez de shaders — é o jeito mais direto de reproduzir o modelo de luz do
 * JavaFX original (AmbientLight + PointLight + PhongMaterial por cor sólida),
 * já que o mapeamento é quase 1:1 com glLightModel/glLight/glColorMaterial.
 */
public class Renderer {

    private static final double SIZE = World.SIZE;

    public void setupGLState() {
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_LIGHTING);
        glEnable(GL_LIGHT0); // sol
        glEnable(GL_LIGHT1); // lua
        glEnable(GL_LIGHT2); // luz de preenchimento (fillLight original)
        glEnable(GL_COLOR_MATERIAL);
        glColorMaterial(GL_FRONT_AND_BACK, GL_AMBIENT_AND_DIFFUSE);
        glEnable(GL_NORMALIZE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glShadeModel(GL_SMOOTH);
    }

    /** Configura viewport + projeção + câmera para a área 3D (entre a toolbar e a barra inferior). */
    public void beginScene(int winWidth, int winHeight, int topBarH, int bottomBarH, Player player) {
        int vpH = Math.max(1, winHeight - topBarH - bottomBarH);
        glViewport(0, bottomBarH, winWidth, vpH);

        glMatrixMode(GL_PROJECTION);
        float[] proj = Mat4.perspective(Math.toRadians(70), (double) winWidth / vpH, 1.0, 20000.0);
        glLoadMatrixf(proj);

        glMatrixMode(GL_MODELVIEW);
        double ex = player.eyeX(), ey = player.eyeY(), ez = player.eyeZ();
        float[] view = Mat4.lookAt(ex, ey, ez, ex + player.dirX(), ey + player.dirY(), ez + player.dirZ(), 0, -1, 0);
        glLoadMatrixf(view);
    }

    public void applyLighting(DayNight dn, double playerX, double playerZ) {
        glLightModelfv(GL_LIGHT_MODEL_AMBIENT, new float[]{dn.ambient[0] * 0.55f, dn.ambient[1] * 0.55f, dn.ambient[2] * 0.55f, 1f});

        float sx = (float) (dn.sunX + playerX), sy = (float) dn.sunY;
        glLight(GL_LIGHT0, GL_POSITION, buf(sx, sy, 0, 1));
        glLight(GL_LIGHT0, GL_DIFFUSE, buf(dn.sunColor[0], dn.sunColor[1], dn.sunColor[2], 1));
        if (dn.isDay) glEnable(GL_LIGHT0); else glDisable(GL_LIGHT0);

        glLight(GL_LIGHT1, GL_POSITION, buf(-sx, -sy, 0, 1));
        glLight(GL_LIGHT1, GL_DIFFUSE, buf(dn.moonColor[0], dn.moonColor[1], dn.moonColor[2], 1));
        if (!dn.isDay) glEnable(GL_LIGHT1); else glDisable(GL_LIGHT1);

        glLight(GL_LIGHT2, GL_POSITION, buf(-500, -100, 600, 1));
        glLight(GL_LIGHT2, GL_DIFFUSE, buf(dn.fillColor[0], dn.fillColor[1], dn.fillColor[2], 1));

        glClearColor(dn.sky[0], dn.sky[1], dn.sky[2], 1f);
    }

    private static void glLight(int light, int pname, java.nio.FloatBuffer v) { glLightfv(light, pname, v); }
    private static java.nio.FloatBuffer buf(float a, float b, float c, float d) {
        java.nio.FloatBuffer fb = java.nio.ByteBuffer.allocateDirect(16).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(a).put(b).put(c).put(d); fb.flip(); return fb;
    }

    public void drawFloor(double playerX, double playerZ) {
        drawBox(playerX, 30, playerZ, 1500, 5, 1500, 90 / 255f, 140 / 255f, 60 / 255f);
    }

    public void drawBlocks(World world) {
        for (Block b : world.blocks()) {
            double cx = b.worldX(), cy = b.worldY(), cz = b.worldZ();
            BlockType t = b.getType();
            if (t == BlockType.AGUA) {
                float[] c = t.getTopColor();
                glColor4f(c[0], c[1], c[2], 0.55f);
                drawBoxRaw(cx, cy, cz, (SIZE - 0.5)/2, 6, (SIZE - 0.5)/2);
                continue;
            }
            if (t == BlockType.GRAMA) {
                float[] side = t.getSideColor();
                drawBox(cx, cy, cz, (SIZE - 0.5) / 2, (SIZE - 0.5) / 2, (SIZE - 0.5) / 2, side[0], side[1], side[2]);
                float[] top = t.getTopColor();
                double capH = 12;
                double capCy = cy - SIZE / 2 + capH / 2 - 0.3;
                drawBox(cx, capCy, cz, SIZE / 2, capH / 2, SIZE / 2, top[0], top[1], top[2]);
            } else {
                float[] top = t.getTopColor();
                drawBox(cx, cy, cz, (SIZE - 0.5) / 2, (SIZE - 0.5) / 2, (SIZE - 0.5) / 2, top[0], top[1], top[2]);
            }
        }
    }

    public void drawClouds(Clouds clouds) {
        glDisable(GL_LIGHTING);
        glColor4f(1f, 1f, 1f, 0.92f);
        for (Clouds.Cluster c : clouds.clusters) {
            for (Clouds.Part p : c.parts) {
                drawBoxNoLight(c.baseX + p.ox, c.baseY, c.baseZ + p.oz, p.sx / 2, p.h / 2, p.sz / 2);
            }
        }
        glEnable(GL_LIGHTING);
    }

    public void drawSunMoon(DayNight dn, double playerX, double playerZ) {
        glDisable(GL_LIGHTING);
        double gx = playerX + dn.sunX, gz = playerZ;
        if (dn.isDay) {
            glColor3f(255 / 255f, 220 / 255f, 0);
            drawBoxNoLight(gx, dn.sunY, gz, 55, 55, 55);
        } else {
            glColor3f(210 / 255f, 210 / 255f, 220 / 255f);
            drawBoxNoLight(playerX - dn.sunX, -dn.sunY, gz, 37.5, 37.5, 37.5);
        }
        glEnable(GL_LIGHTING);
    }

    private void drawBoxNoLight(double cx, double cy, double cz, double hx, double hy, double hz) {
        boolean wasLit = glIsEnabled(GL_LIGHTING);
        if (wasLit) glDisable(GL_LIGHTING);
        drawBoxRaw(cx, cy, cz, hx, hy, hz);
        if (wasLit) glEnable(GL_LIGHTING);
    }

    private void drawBox(double cx, double cy, double cz, double hx, double hy, double hz, float r, float g, float b) {
        glColor3f(r, g, b);
        drawBoxRaw(cx, cy, cz, hx, hy, hz);
    }

    private void drawBoxRaw(double cx, double cy, double cz, double hx, double hy, double hz) {
        float x0 = (float) (cx - hx), x1 = (float) (cx + hx);
        float y0 = (float) (cy - hy), y1 = (float) (cy + hy);
        float z0 = (float) (cz - hz), z1 = (float) (cz + hz);
        glBegin(GL_QUADS);
        // +Z
        glNormal3f(0, 0, 1);
        glVertex3f(x0, y0, z1); glVertex3f(x1, y0, z1); glVertex3f(x1, y1, z1); glVertex3f(x0, y1, z1);
        // -Z
        glNormal3f(0, 0, -1);
        glVertex3f(x1, y0, z0); glVertex3f(x0, y0, z0); glVertex3f(x0, y1, z0); glVertex3f(x1, y1, z0);
        // +X
        glNormal3f(1, 0, 0);
        glVertex3f(x1, y0, z1); glVertex3f(x1, y0, z0); glVertex3f(x1, y1, z0); glVertex3f(x1, y1, z1);
        // -X
        glNormal3f(-1, 0, 0);
        glVertex3f(x0, y0, z0); glVertex3f(x0, y0, z1); glVertex3f(x0, y1, z1); glVertex3f(x0, y1, z0);
        // -Y (topo visual, já que Y+ é "para baixo" nessa convenção, igual ao original)
        glNormal3f(0, -1, 0);
        glVertex3f(x0, y0, z0); glVertex3f(x1, y0, z0); glVertex3f(x1, y0, z1); glVertex3f(x0, y0, z1);
        // +Y (base)
        glNormal3f(0, 1, 0);
        glVertex3f(x0, y1, z1); glVertex3f(x1, y1, z1); glVertex3f(x1, y1, z0); glVertex3f(x0, y1, z0);
        glEnd();
    }
}
