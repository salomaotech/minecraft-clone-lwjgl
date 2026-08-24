package com.mineclone;

/**
 * Jogador: posição, física (gravidade/pulo/step-up) e câmera FPS (yaw/pitch).
 * Toda a matemática é copiada do App.java original (SubScene + PerspectiveCamera);
 * a convenção de eixos é mantida idêntica: Y POSITIVO = PARA BAIXO.
 */
public class Player {

    private static final double SIZE = World.SIZE;

    public double playerX = 0;
    public double playerZ = 0;
    public double feetY = -30;
    public static final double PLAYER_WIDTH = 30;
    public static final double PLAYER_HEIGHT = 82;
    public static final double EYE_HEIGHT = 68;
    private static final double STEP_HEIGHT = 52;

    private double vy = 0;
    private static final double GRAVITY = 1600;
    private static final double JUMP_VEL = 440;
    private static final double TERMINAL_VEL = 1100;
    public static final double WALK_SPEED = 215;
    public static final double SPRINT_SPEED = 300;
    public boolean onGround = true;

    public double yaw = -18;
    public double pitch = 14;

    public void resetPlayer() {
        playerX = 0; playerZ = 0; feetY = -30; vy = 0; yaw = -18; pitch = 14; onGround = true;
    }

    /** Direção do olhar (mesma fórmula do raycast original). */
    public double dirX() { return Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)); }
    public double dirY() { return -Math.sin(Math.toRadians(pitch)); }
    public double dirZ() { return Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)); }

    public double eyeX() { return playerX; }
    public double eyeY() { return feetY - EYE_HEIGHT; }
    public double eyeZ() { return playerZ; }

    /** forward>0 anda pra frente, strafe>0 anda pra direita; speed já inclui sprint. */
    public void update(double dt, World world, double forward, double strafe, boolean sprint, boolean jumpHeld) {
        boolean inWater = isInWater(world);
        double len = Math.hypot(forward, strafe);
        if (len > 0) { forward /= len; strafe /= len; }
        double speed = sprint ? SPRINT_SPEED : WALK_SPEED;
        if(inWater) speed *= 0.45;
        double yawRad = Math.toRadians(yaw);
        double fX = Math.sin(yawRad), fZ = Math.cos(yawRad);
        double rX = Math.cos(yawRad), rZ = -Math.sin(yawRad);
        double wishX = (fX * forward + rX * strafe) * speed * dt;
        double wishZ = (fZ * forward + rZ * strafe) * speed * dt;

        if (wishX != 0) {
            double newX = playerX + wishX;
            if (isCollidingAt(world, newX, playerZ, feetY)) {
                double stepFeet = tryStepUp(world, newX, playerZ, feetY);
                if (!Double.isNaN(stepFeet)) { playerX = newX; feetY = stepFeet; vy = 0; onGround = true; }
            } else playerX = newX;
        }
        if (wishZ != 0) {
            double newZ = playerZ + wishZ;
            if (isCollidingAt(world, playerX, newZ, feetY)) {
                double stepFeet = tryStepUp(world, playerX, newZ, feetY);
                if (!Double.isNaN(stepFeet)) { playerZ = newZ; feetY = stepFeet; vy = 0; onGround = true; }
            } else playerZ = newZ;
        }

        boolean grounded = isOnGround(world);
        if (grounded) { onGround = true; if (vy > 0) vy = 0; } else onGround = false;
        if(inWater){
            vy += GRAVITY * 0.2 * dt;
            if(vy > 3) vy=3;
            if(jumpHeld) vy = -4;
        }
        if (jumpHeld && onGround && !inWater) { vy = -JUMP_VEL; onGround = false; }

        if(!inWater) vy += GRAVITY * dt;
        if (vy > TERMINAL_VEL) vy = TERMINAL_VEL;
        if (onGround && vy > 0) vy = 0;
        double newFeet = feetY + vy * dt;

        if (vy > 0) {
            double groundY = findGroundY(world, playerX, playerZ, feetY);
            if (newFeet >= groundY) {
                feetY = groundY; vy = 0; onGround = true;
            } else {
                if (isCollidingAt(world, playerX, playerZ, newFeet)) {
                    double snap = findGroundY(world, playerX, playerZ, newFeet - 0.5);
                    if (snap < 1e8 && newFeet >= snap) { feetY = snap; vy = 0; onGround = true; }
                    else { feetY = newFeet; onGround = false; }
                } else { feetY = newFeet; onGround = false; }
            }
        } else if (vy < 0) {
            if (isCollidingAt(world, playerX, playerZ, newFeet)) {
                double ceil = findCeilingY(world, playerX, playerZ, feetY - PLAYER_HEIGHT);
                if (!Double.isNaN(ceil)) feetY = ceil + PLAYER_HEIGHT + 0.2;
                vy = 0; onGround = false;
            } else { feetY = newFeet; onGround = false; }
        } else {
            if (!isOnGround(world)) onGround = false;
            else {
                double g = findGroundY(world, playerX, playerZ, feetY);
                if (Math.abs(feetY - g) < 1.0) feetY = g;
                vy = 0; onGround = true;
            }
        }
        if (feetY > 600) resetPlayer();
    }

    private static boolean solido(Block b) {
        return b.getType() != BlockType.AGUA && b.getType() != BlockType.TOCHA;
    }

    private double tryStepUp(World world, double newX, double newZ, double curFeet) {
        if (!isOnGround(world)) return Double.NaN;
        double candidate = Double.MAX_VALUE;
        for (Block b : world.blocks()) {
            if (!solido(b)) continue;
            double bx = b.worldX(), bz = b.worldZ();
            double half = SIZE / 2 + PLAYER_WIDTH / 2 - 0.05;
            if (Math.abs(bx - newX) >= half || Math.abs(bz - newZ) >= half) continue;
            double top = b.worldY() - SIZE / 2;
            double diff = curFeet - top;
            if (diff > 0.1 && diff <= STEP_HEIGHT + 0.3) {
                if (top < candidate) candidate = top;
            }
        }
        if (candidate == Double.MAX_VALUE) return Double.NaN;
        double stepFeet = candidate;
        if (isCollidingAt(world, newX, newZ, stepFeet)) return Double.NaN;
        if (isCollidingAt(world, newX, newZ, stepFeet - 1)) return Double.NaN;
        return stepFeet;
    }

    private boolean isOnGround(World world) {
        double g = findGroundY(world, playerX, playerZ, feetY);
        return Math.abs(feetY - g) < 1.0;
    }

    private double findGroundY(World world, double px, double pz, double curFeet) {
        double best = 25;
        double bestBelow = Double.MAX_VALUE;
        for (Block b : world.blocks()) {
            if (!solido(b)) continue;
            if (!overlapsXZ(b, px, pz)) continue;
            double top = b.worldY() - SIZE / 2;
            if (top >= curFeet - 0.8 && top < bestBelow) bestBelow = top;
        }
        if (bestBelow != Double.MAX_VALUE) best = Math.min(best, bestBelow);
        return best;
    }

    private double findCeilingY(World world, double px, double pz, double headY) {
        double bestBottom = Double.NEGATIVE_INFINITY; boolean f = false;
        for (Block b : world.blocks()) {
            if (!solido(b)) continue;
            if (!overlapsXZ(b, px, pz)) continue;
            double bottom = b.worldY() + SIZE / 2, top = b.worldY() - SIZE / 2;
            if (top < headY + 0.5 && bottom >= headY - 2) { if (!f || bottom > bestBottom) { bestBottom = bottom; f = true; } }
        }
        return f ? bestBottom : Double.NaN;
    }

    private boolean overlapsXZ(Block b, double px, double pz) {
        double bx = b.worldX(), bz = b.worldZ();
        double half = SIZE / 2 + PLAYER_WIDTH / 2 - 0.08;
        return Math.abs(bx - px) < half && Math.abs(bz - pz) < half;
    }

    public boolean isCollidingAt(World world, double px, double pz, double feet) {
        double pMinX = px - PLAYER_WIDTH / 2, pMaxX = px + PLAYER_WIDTH / 2;
        double pMinZ = pz - PLAYER_WIDTH / 2, pMaxZ = pz + PLAYER_WIDTH / 2;
        double pMinY = feet - PLAYER_HEIGHT, pMaxY = feet - 0.05;
        for (Block b : world.blocks()) {
            if(!solido(b)) continue;
            double bx = b.worldX(), by = b.worldY(), bz = b.worldZ();
            double minX = bx - SIZE / 2, maxX = bx + SIZE / 2, minY = by - SIZE / 2, maxY = by + SIZE / 2, minZ = bz - SIZE / 2, maxZ = bz + SIZE / 2;
            boolean ox = pMaxX > minX && pMinX < maxX, oz = pMaxZ > minZ && pMinZ < maxZ, oy = pMaxY > minY && pMinY < maxY;
            if (ox && oz && oy) return true;
        }
        return false;
    }

    private static int gridOf(double coord) { return (int) Math.floor((coord + SIZE / 2) / SIZE); }

    private boolean isInWater(World world){
        int gx=gridOf(playerX), gz=gridOf(playerZ);
        int fy=gridOf(feetY), hy=gridOf(feetY-PLAYER_HEIGHT/2);
        Block b1=world.get(gx,fy,gz), b2=world.get(gx,hy,gz);
        return (b1!=null && b1.getType()==BlockType.AGUA) || (b2!=null && b2.getType()==BlockType.AGUA);
    }
    public boolean isCollidingAtGrid(int gx, int gy, int gz) {
        double bx = gx * SIZE, by = gy * SIZE, bz = gz * SIZE;
        double minX = bx - SIZE / 2, maxX = bx + SIZE / 2, minY = by - SIZE / 2, maxY = by + SIZE / 2, minZ = bz - SIZE / 2, maxZ = bz + SIZE / 2;
        double pMinX = playerX - PLAYER_WIDTH / 2, pMaxX = playerX + PLAYER_WIDTH / 2;
        double pMinZ = playerZ - PLAYER_WIDTH / 2, pMaxZ = playerZ + PLAYER_WIDTH / 2;
        double pMinY = feetY - PLAYER_HEIGHT, pMaxY = feetY;
        return pMaxX > minX && pMinX < maxX && pMaxZ > minZ && pMinZ < maxZ && pMaxY > minY && pMinY < maxY;
    }
}
