package com.mineclone;

/** Pequeno utilitário de matrizes 4x4 (column-major, formato que glLoadMatrixf espera). */
public class Mat4 {

    public static float[] perspective(double fovyRadians, double aspect, double near, double far) {
        double f = 1.0 / Math.tan(fovyRadians / 2.0);
        float[] m = new float[16];
        m[0] = (float) (f / aspect);
        m[5] = (float) f;
        m[10] = (float) ((far + near) / (near - far));
        m[11] = -1f;
        m[14] = (float) ((2 * far * near) / (near - far));
        return m;
    }

    /**
     * Equivalente ao gluLookAt clássico. Usamos isso para a câmera em vez de
     * glRotate/glTranslate manuais, porque assim garantimos que a direção
     * exibida na tela é EXATAMENTE a mesma direção (dirX,dirY,dirZ) usada
     * pelo raycast e pelo movimento do jogador — sem risco de sinal trocado.
     */
    public static float[] lookAt(double eyeX, double eyeY, double eyeZ,
                                  double centerX, double centerY, double centerZ,
                                  double upX, double upY, double upZ) {
        double zx = eyeX - centerX, zy = eyeY - centerY, zz = eyeZ - centerZ;
        double zl = Math.sqrt(zx * zx + zy * zy + zz * zz);
        if (zl < 1e-9) zl = 1e-9;
        zx /= zl; zy /= zl; zz /= zl;

        // xaxis = normalize(cross(up, zaxis))
        double xx = upY * zz - upZ * zy;
        double xy = upZ * zx - upX * zz;
        double xz = upX * zy - upY * zx;
        double xl = Math.sqrt(xx * xx + xy * xy + xz * xz);
        if (xl < 1e-9) xl = 1e-9;
        xx /= xl; xy /= xl; xz /= xl;

        // yaxis = cross(zaxis, xaxis)
        double yx = zy * xz - zz * xy;
        double yy = zz * xx - zx * xz;
        double yz = zx * xy - zy * xx;

        float[] m = new float[16];
        m[0] = (float) xx; m[4] = (float) xy; m[8] = (float) xz; m[12] = (float) -(xx * eyeX + xy * eyeY + xz * eyeZ);
        m[1] = (float) yx; m[5] = (float) yy; m[9] = (float) yz; m[13] = (float) -(yx * eyeX + yy * eyeY + yz * eyeZ);
        m[2] = (float) zx; m[6] = (float) zy; m[10] = (float) zz; m[14] = (float) -(zx * eyeX + zy * eyeY + zz * eyeZ);
        m[3] = 0; m[7] = 0; m[11] = 0; m[15] = 1;
        return m;
    }
}
