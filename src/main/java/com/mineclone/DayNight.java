package com.mineclone;

/**
 * Ciclo de dia/noite: posição do sol/lua, cor do céu, luz ambiente e luz
 * direcional do sol/lua. Matemática copiada de updateDayNight()/lerpColor()
 * do App.java original.
 */
public class DayNight {

    public double worldTime = 6000; // 0=06:00, 6000=12:00, 12000=18:00, 18000=00:00
    public static final double DAY_TICKS = 24000;
    public static final double DAY_SECONDS = 720; // 12 min por dia
    public int dayCount = 1;

    public double sunX, sunY; // posição relativa ao jogador (sunZ sempre 0, como original)
    public boolean isDay;
    public float[] sky = {135 / 255f, 206 / 255f, 235 / 255f};
    public float[] ambient = {1, 1, 1};
    public float[] sunColor = {1, 1, 1};
    public float[] moonColor = {130 / 255f, 145 / 255f, 190 / 255f};
    public float[] fillColor = {180 / 255f, 200 / 255f, 255 / 255f};

    public void update(double dt) {
        worldTime += (DAY_TICKS / DAY_SECONDS) * dt;
        if (worldTime >= DAY_TICKS) { worldTime -= DAY_TICKS; dayCount++; }

        double angle = (worldTime / DAY_TICKS) * 360;
        double rad = Math.toRadians(angle);
        double R = 1900;
        sunX = Math.cos(rad) * R;
        sunY = -Math.sin(rad) * R;

        double norm = worldTime / DAY_TICKS;
        float[] skyC, ambC, sunC;
        if (norm < 0.08) {
            double t = norm / 0.08;
            skyC = lerp(rgb(15, 10, 30), rgb(255, 130, 70), t);
            ambC = lerp(rgb(50, 50, 70), rgb(255, 180, 100), t);
            sunC = lerp(rgb(255, 100, 30), rgb(255, 230, 120), t);
        } else if (norm < 0.25) {
            double t = (norm - 0.08) / 0.17;
            skyC = lerp(rgb(255, 130, 70), rgb(135, 206, 235), t);
            ambC = lerp(rgb(255, 180, 100), rgb(255, 255, 255), t);
            sunC = rgb(255, 245, 210);
        } else if (norm < 0.5) {
            skyC = rgb(135, 206, 235); ambC = rgb(255, 255, 255); sunC = rgb(255, 255, 255);
        } else if (norm < 0.54) {
            double t = (norm - 0.5) / 0.04;
            skyC = lerp(rgb(135, 206, 235), rgb(255, 80, 30), t);
            ambC = lerp(rgb(255, 255, 255), rgb(255, 120, 70), t);
            sunC = lerp(rgb(255, 255, 255), rgb(255, 60, 20), t);
        } else if (norm < 0.58) {
            double t = (norm - 0.54) / 0.04;
            skyC = lerp(rgb(255, 80, 30), rgb(5, 8, 20), t);
            ambC = lerp(rgb(255, 120, 70), rgb(35, 35, 50), t);
            sunC = lerp(rgb(255, 60, 20), rgb(50, 50, 65), t);
        } else {
            skyC = rgb(5, 8, 18); ambC = rgb(22, 22, 34); sunC = rgb(40, 40, 55);
        }
        sky = skyC; ambient = ambC; sunColor = sunC;

        isDay = sunY < 200;
        fillColor = isDay ? rgb(180, 200, 255) : rgb(35, 40, 65);
    }

    public String formatTime() {
        int totalMin = (int) ((worldTime / DAY_TICKS) * 24 * 60);
        int h = (totalMin / 60 + 6) % 24;
        int m = totalMin % 60;
        String phase = (worldTime < 12000) ? "DIA" : "NOITE";
        if (worldTime >= 5000 && worldTime < 7000) phase = "MEIO-DIA";
        else if (worldTime < 500 || worldTime > 23500) phase = "AMANHECER";
        else if (worldTime >= 11500 && worldTime < 13500) phase = "ENTARDECER";
        return String.format("Dia %d - %02d:%02d %s", dayCount, h, m, phase);
    }

    private static float[] rgb(int r, int g, int b) { return new float[]{r / 255f, g / 255f, b / 255f}; }

    private static float[] lerp(float[] a, float[] b, double t) {
        float tt = (float) Math.max(0, Math.min(1, t));
        return new float[]{a[0] + (b[0] - a[0]) * tt, a[1] + (b[1] - a[1]) * tt, a[2] + (b[2] - a[2]) * tt};
    }
}
