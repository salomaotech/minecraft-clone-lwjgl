package com.mineclone;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBEasyFont;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * HUD 2D: toolbar (título + paleta de blocos + botões de ação), barra
 * inferior (status) e crosshair — equivalente à HBox/toolbar/bottomBar do
 * JavaFX original. Desenhado com glOrtho + quads coloridos + texto via
 * stb_easy_font (nada de texturas/atlas — suficiente para um HUD simples).
 */
public class UIOverlay {

    public static final int TOP_BAR_H = 44;
    public static final int BOTTOM_BAR_H = 30;

    public interface Actions {
        void selectBlock(BlockType t);
        void plataforma();
        void casa();
        void torre();
        void limpar();
        void salvar();
        void carregar();
        void toggleLock();
    }

    private static class Btn {
        int x, y, w, h;
        Runnable action;
    }

    private final List<Btn> buttons = new ArrayList<>();
    private final ByteBuffer textBuf = BufferUtils.createByteBuffer(200_000);
    public boolean helpVisible = false;

    private String toast;
    private long toastUntilMs;

    public void showToast(String msg) { toast = msg; toastUntilMs = System.currentTimeMillis() + 3500; }

    public boolean isOverUI(double mx, double my, int winW) {
        return my <= TOP_BAR_H; // toolbar é a única área realmente "clicável" fora do jogo
    }

    /** Testa clique contra os botões desenhados no último frame; executa a ação se acertar. */
    public boolean handleClick(double mx, double my) {
        for (Btn b : buttons) {
            if (mx >= b.x && mx <= b.x + b.w && my >= b.y && my <= b.y + b.h) {
                b.action.run();
                return true;
            }
        }
        return false;
    }

    public void draw(int winW, int winH, BlockType selected, int blockCount, DayNight dn,
                      boolean mouseLocked, boolean onGround, double playerX, double feetY, double playerZ,
                      Actions actions) {
        buttons.clear();

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, winW, winH, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_LIGHTING);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glViewport(0, 0, winW, winH);

        drawToolbar(winW, selected, actions);
        drawBottomBar(winW, winH, blockCount, dn, mouseLocked, onGround, playerX, feetY, playerZ, selected);
        drawCrosshair(winW, winH);
        if (helpVisible) drawHelpPanel(winW, winH, dn);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_LIGHTING);
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }

    private void drawToolbar(int winW, BlockType selected, Actions actions) {
        fillRect(0, 0, winW, TOP_BAR_H, 0.169f, 0.169f, 0.169f, 1f);
        drawText(8, 16, "MINECRAFT FPS (LWJGL)", 1, 1, 1);

        int x = 180, y = 8, w = 68, h = 28, gap = 6;
        for (BlockType t : BlockType.values()) {
            boolean sel = t == selected;
            float[] c = t.getTopColor();
            fillRect(x, y, w, h, c[0], c[1], c[2], 1f);
            if (sel) strokeRect(x, y, w, h, 1, 1, 1);
            drawText(x + 4, y + h / 2 + 3, t.getNome() + " " + (t.ordinal() + 1), 0, 0, 0);
            addButton(x, y, w, h, () -> actions.selectBlock(t));
            x += w + gap;
        }
        x += 10;
        x = addTextButton(x, y, "Plataforma", 0.35f, 0.35f, 0.35f, actions::plataforma);
        x = addTextButton(x, y, "Casa", 0.35f, 0.35f, 0.35f, actions::casa);
        x = addTextButton(x, y, "Torre", 0.35f, 0.35f, 0.35f, actions::torre);
        x = addTextButton(x, y, "Limpar", 0.62f, 0.24f, 0.24f, actions::limpar);
        x += 10;
        x = addTextButton(x, y, "Salvar", 0.20f, 0.55f, 0.20f, actions::salvar);
        x = addTextButton(x, y, "Carregar", 0.13f, 0.35f, 0.66f, actions::carregar);
        x += 10;
        addTextButton(x, y, "Travar Mouse (TAB)", 0.30f, 0.30f, 0.30f, actions::toggleLock);
    }

    private int addTextButton(int x, int y, String label, float r, float g, float b, Runnable action) {
        int w = 14 + label.length() * 7, h = 28;
        fillRect(x, y, w, h, r, g, b, 1f);
        drawText(x + 6, y + h / 2 + 3, label, 1, 1, 1);
        addButton(x, y, w, h, action);
        return x + w + 6;
    }

    private void addButton(int x, int y, int w, int h, Runnable action) {
        Btn b = new Btn(); b.x = x; b.y = y; b.w = w; b.h = h; b.action = action;
        buttons.add(b);
    }

    private void drawBottomBar(int winW, int winH, int blockCount, DayNight dn, boolean mouseLocked,
                                boolean onGround, double playerX, double feetY, double playerZ, BlockType selected) {
        int y0 = winH - BOTTOM_BAR_H;
        fillRect(0, y0, winW, BOTTOM_BAR_H, 0.118f, 0.118f, 0.118f, 1f);
        drawText(10, y0 + 19, "CLIQUE para travar - WASD - SPACE pular", 0.78f, 0.78f, 0.78f);
        drawText(winW / 2 - 60, y0 + 19, dn.formatTime(), 1f, 0.84f, 0f);
        String coords = String.format("%s [%d] - XYZ %.0f %.0f %.0f - %s %s",
                selected.getNome(), selected.ordinal() + 1, playerX, feetY, playerZ,
                onGround ? "CHAO" : "AR", mouseLocked ? "[MIRANDO]" : "[TAB]");
        drawText(winW - 430, y0 + 19, coords, 1f, 0.65f, 0f);
        drawText(winW - 110, y0 + 19, "Blocos: " + blockCount, 0.56f, 0.93f, 0.56f);
        if (toast != null) {
            if (System.currentTimeMillis() > toastUntilMs) toast = null;
            else drawText(winW / 2 - toast.length() * 3.5, y0 - 18, toast, 1f, 0.45f, 0.25f);
        }
    }

    private void drawCrosshair(int winW, int winH) {
        double cx = winW / 2.0;
        double cy = TOP_BAR_H + (winH - TOP_BAR_H - BOTTOM_BAR_H) / 2.0;
        glColor4f(1, 1, 1, 0.85f);
        glLineWidth(2f);
        glBegin(GL_LINES);
        glVertex2d(cx - 9, cy); glVertex2d(cx + 9, cy);
        glVertex2d(cx, cy - 9); glVertex2d(cx, cy + 9);
        glEnd();
    }

    private void drawHelpPanel(int winW, int winH, DayNight dn) {
        int w = 420, h = 150, x = (winW - w) / 2, y = (winH - h) / 2;
        fillRect(x, y, w, h, 0.05f, 0.05f, 0.05f, 0.85f);
        strokeRect(x, y, w, h, 1, 1, 1);
        drawText(x + 14, y + 22, "Horario do Mundo", 1, 1, 1);
        drawText(x + 14, y + 44, dn.formatTime(), 1f, 0.84f, 0f);
        drawText(x + 14, y + 70, "T = +1h   Y = -1h   N = noite", 0.85f, 0.85f, 0.85f);
        drawText(x + 14, y + 90, "1-8 seleciona bloco, R reseta posicao", 0.85f, 0.85f, 0.85f);
        drawText(x + 14, y + 110, "TAB trava/destrava o mouse, ESC destrava", 0.85f, 0.85f, 0.85f);
        drawText(x + 14, y + 132, "Pressione H para fechar", 0.6f, 0.6f, 0.6f);
    }

    private void fillRect(double x, double y, double w, double h, float r, float g, float b, float a) {
        glColor4f(r, g, b, a);
        glBegin(GL_QUADS);
        glVertex2d(x, y); glVertex2d(x + w, y); glVertex2d(x + w, y + h); glVertex2d(x, y + h);
        glEnd();
    }

    private void strokeRect(double x, double y, double w, double h, float r, float g, float b) {
        glColor3f(r, g, b);
        glLineWidth(2f);
        glBegin(GL_LINE_LOOP);
        glVertex2d(x, y); glVertex2d(x + w, y); glVertex2d(x + w, y + h); glVertex2d(x, y + h);
        glEnd();
    }

    private void drawText(double x, double y, String s, float r, float g, float b) {
        textBuf.clear();
        int quads = STBEasyFont.stb_easy_font_print((float) x, (float) y, s, null, textBuf);
        textBuf.flip();
        glColor3f(r, g, b);
        glEnableClientState(GL_VERTEX_ARRAY);
        glVertexPointer(3, GL_FLOAT, 16, textBuf);
        glDrawArrays(GL_QUADS, 0, quads * 4);
        glDisableClientState(GL_VERTEX_ARRAY);
    }
}
