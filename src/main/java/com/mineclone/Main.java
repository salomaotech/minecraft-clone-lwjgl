package com.mineclone;

import org.lwjgl.Version;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Ponto de entrada. Substitui a Application/Stage do JavaFX: cria a janela
 * via GLFW, monta o contexto OpenGL e roda o game loop (equivalente ao
 * AnimationTimer + os handlers de teclado/mouse do App.java original).
 */
public class Main {

    private long window;
    private int winWidth = 1180, winHeight = 760;

    private final World world = new World(new File("chunk_cache"));
    private final Player player = new Player();
    private final DayNight dayNight = new DayNight();
    private final Clouds clouds = new Clouds();
    private final Renderer renderer = new Renderer();
    private final UIOverlay ui = new UIOverlay();

    private BlockType selectedType = BlockType.GRAMA;
    private boolean mouseLocked = false;
    private boolean firstMouse = true;
    private double lastMouseX, lastMouseY, mouseX, mouseY;
    private static final double MOUSE_SENS = 0.16;

    private final Set<Integer> pressed = new HashSet<>();
    private double lastTime;

    public static void main(String[] args) {
        new Main().run();
    }

    public void run() {
        System.out.println("LWJGL " + Version.getVersion());
        init();
        world.createHugeWorldInitial();
        loop();
        cleanup();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Nao foi possivel iniciar o GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES, 4);

        window = glfwCreateWindow(winWidth, winHeight, "Minecraft Clone FPS - LWJGL [WASD + Mouse + Colisao]", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Falha ao criar a janela GLFW");

        glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            if (w > 0 && h > 0) { winWidth = w; winHeight = h; }
        });

        glfwSetKeyCallback(window, this::onKey);
        glfwSetCursorPosCallback(window, this::onCursorPos);
        glfwSetMouseButtonCallback(window, this::onMouseButton);
        glfwSetScrollCallback(window, this::onScroll);

        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            java.nio.IntBuffer pW = stack.mallocInt(1), pH = stack.mallocInt(1);
            glfwGetWindowSize(window, pW, pH);
            GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidMode != null) glfwSetWindowPos(window, (vidMode.width() - pW.get(0)) / 2, (vidMode.height() - pH.get(0)) / 2);
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // vsync
        glfwShowWindow(window);
        GL.createCapabilities();

        renderer.setupGLState();
        lastTime = glfwGetTime();
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            double dt = now - lastTime;
            lastTime = now;
            if (dt > 0.05) dt = 0.05;
            if (dt < 0.0001) dt = 0.0001;

            glfwPollEvents();
            update(dt);
            render();
            glfwSwapBuffers(window);
        }
    }

    private void update(double dt) {
        double forward = 0, strafe = 0;
        if (isDown(GLFW_KEY_W) || isDown(GLFW_KEY_UP)) forward += 1;
        if (isDown(GLFW_KEY_S) || isDown(GLFW_KEY_DOWN)) forward -= 1;
        if (isDown(GLFW_KEY_A)) strafe -= 1;
        if (isDown(GLFW_KEY_D)) strafe += 1;
        boolean sprint = isDown(GLFW_KEY_LEFT_SHIFT) || isDown(GLFW_KEY_RIGHT_SHIFT);
        boolean jumpHeld = isDown(GLFW_KEY_SPACE) || isDown(GLFW_KEY_BACKSPACE);

        player.update(dt, world, forward, strafe, sprint, jumpHeld);
        world.tickWater();
        world.tickChunks(player.playerX, player.playerZ);
        dayNight.update(dt);
        clouds.update(dt);
    }

    private void render() {
        renderer.applyLighting(dayNight, player.playerX, player.playerZ);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        renderer.beginScene(winWidth, winHeight, UIOverlay.TOP_BAR_H, UIOverlay.BOTTOM_BAR_H, player);
        renderer.drawFloor(player.playerX, player.playerZ);
        renderer.drawBlocks(world);
        renderer.drawClouds(clouds);
        renderer.drawSunMoon(dayNight, player.playerX, player.playerZ);

        ui.draw(winWidth, winHeight, selectedType, world.blockCount(), dayNight, mouseLocked, player.onGround,
                player.playerX, player.feetY, player.playerZ, actions);
    }

    private final UIOverlay.Actions actions = new UIOverlay.Actions() {
        public void selectBlock(BlockType t) { selectedType = t; }
        public void plataforma() { world.createPlatform(); }
        public void casa() { world.createDemoHouse(); }
        public void torre() { world.createTower(); }
        public void limpar() { world.clear(); }
        public void salvar() { doSave(); }
        public void carregar() { doLoad(); }
        public void toggleLock() { setMouseLocked(!mouseLocked); }
    };

    // ---- input callbacks ----
    private boolean isDown(int key) { return pressed.contains(key); }

    private void onKey(long win, int key, int scancode, int action, int mods) {
        if (action == GLFW_PRESS) pressed.add(key);
        else if (action == GLFW_RELEASE) pressed.remove(key);
        if (action != GLFW_PRESS) return;

        boolean ctrl = (mods & GLFW_MOD_CONTROL) != 0;
        if (ctrl && key == GLFW_KEY_S) { doSave(); return; }
        if (ctrl && key == GLFW_KEY_O) { doLoad(); return; }
        if (key == GLFW_KEY_ESCAPE) setMouseLocked(false);
        if (key == GLFW_KEY_TAB) setMouseLocked(!mouseLocked);
        if (key == GLFW_KEY_R) player.resetPlayer();
        if (key == GLFW_KEY_T) dayNight.worldTime = (dayNight.worldTime + 1000) % DayNight.DAY_TICKS;
        if (key == GLFW_KEY_Y) dayNight.worldTime = (dayNight.worldTime - 1000 + DayNight.DAY_TICKS) % DayNight.DAY_TICKS;
        if (key == GLFW_KEY_N) dayNight.worldTime = 18000;
        if (key == GLFW_KEY_H) ui.helpVisible = !ui.helpVisible;

        int idx = -1;
        if (key >= GLFW_KEY_1 && key <= GLFW_KEY_9) idx = key - GLFW_KEY_1;
        else if (key == GLFW_KEY_0) idx = 9;
        if (idx >= 0 && idx < BlockType.values().length) selectedType = BlockType.values()[idx];
    }

    private void onCursorPos(long win, double x, double y) {
        mouseX = x; mouseY = y;
        if (!mouseLocked) return;
        if (firstMouse) { lastMouseX = x; lastMouseY = y; firstMouse = false; return; }
        double dx = x - lastMouseX, dy = y - lastMouseY;
        lastMouseX = x; lastMouseY = y;
        player.yaw += dx * MOUSE_SENS;
        player.pitch -= dy * MOUSE_SENS;
        if (player.pitch > 89.5) player.pitch = 89.5;
        if (player.pitch < -89.5) player.pitch = -89.5;
        if (player.yaw > 360) player.yaw -= 360;
        if (player.yaw < -360) player.yaw += 360;
    }

    private void onMouseButton(long win, int button, int action, int mods) {
        if (action != GLFW_PRESS) return;
        if (mouseY <= UIOverlay.TOP_BAR_H) {
            if (ui.handleClick(mouseX, mouseY)) return;
        }
        if (!mouseLocked) { setMouseLocked(true); return; }
        handleBlockRaycast(button);
    }

    private void onScroll(long win, double dx, double dy) {
        if (mouseLocked) return;
        int dir = dy > 0 ? -1 : 1;
        int idx = selectedType.ordinal() + dir;
        if (idx < 0) idx = BlockType.values().length - 1;
        if (idx >= BlockType.values().length) idx = 0;
        selectedType = BlockType.values()[idx];
    }

    private void setMouseLocked(boolean locked) {
        mouseLocked = locked;
        firstMouse = true;
        glfwSetInputMode(window, GLFW_CURSOR, locked ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
    }

    // ---- raycast (colocar/quebrar blocos) ----
    private void handleBlockRaycast(int button) {
        double maxReach = 250;
        double camX = player.eyeX(), camY = player.eyeY(), camZ = player.eyeZ();
        Raycast.RayHit hit = Raycast.cast(world, camX, camY, camZ, player.dirX(), player.dirY(), player.dirZ(), maxReach);
        if (hit == null) return;

        if (hit.isFloor) {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                int gx = hit.placeX, gz = hit.placeZ, gy = 0;
                while (world.contains(gx, gy, gz) && gy > -64) gy--;
                if (!player.isCollidingAtGrid(gx, gy, gz)) world.addBlock(gx, gy, gz, selectedType);
            }
            return;
        }

        Block target = world.get(hit.blockX, hit.blockY, hit.blockZ);
        if (target == null) return;

        if (button == GLFW_MOUSE_BUTTON_RIGHT) { world.removeBlock(target); return; }
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            int nx = hit.placeX, ny = hit.placeY, nz = hit.placeZ;
            if (player.isCollidingAtGrid(nx, ny, nz)) return;
            world.addBlock(nx, ny, nz, selectedType);
        }
    }

    // ---- salvar/carregar ----
    private void doSave() {
        File savesDir = new File("saves");
        if (!savesDir.exists()) savesDir.mkdirs();
        WorldIO.saveDialog(savesDir, (file, isSave) -> {
            try {
                world.saveWorld(file, dayNight.formatTime(), dayNight.worldTime, dayNight.dayCount,
                        player.playerX, player.feetY, player.playerZ, player.yaw, player.pitch);
                WorldIO.showInfo("Mundo salvo: " + file.getName() + " (" + world.blockCount() + " blocos)");
            } catch (Exception ex) {
                WorldIO.showError("Erro ao salvar: " + ex.getMessage());
            }
        });
    }

    private void doLoad() {
        File savesDir = new File("saves");
        WorldIO.loadDialog(savesDir, (file, isSave) -> {
            try {
                World.SavedPlayerState st = world.loadWorld(file);
                if (st != null) {
                    dayNight.worldTime = st.worldTime; dayNight.dayCount = st.dayCount;
                    player.playerX = st.playerX; player.feetY = st.feetY; player.playerZ = st.playerZ;
                    player.yaw = st.yaw; player.pitch = st.pitch;
                }
                WorldIO.showInfo("Mundo carregado: " + file.getName() + " (" + world.blockCount() + " blocos)");
            } catch (Exception ex) {
                WorldIO.showError("Erro ao carregar: " + ex.getMessage());
            }
        });
    }

    private void cleanup() {
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        GLFWErrorCallback prev = glfwSetErrorCallback(null);
        if (prev != null) prev.free();
    }
}
