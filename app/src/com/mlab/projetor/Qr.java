package com.mlab.projetor;

import android.graphics.Bitmap;

import java.nio.charset.StandardCharsets;

/**
 * Gerador de QR code minimo (modo byte, correcao M, versoes 1 a 10), sem bibliotecas.
 * Segue o algoritmo de referencia do padrao ISO/IEC 18004 (mesma estrutura do qrcodegen de Nayuki).
 */
final class Qr {
    private static final int MAX_VERSION = 10;
    // Nivel M, indice = versao.
    private static final int[] ECC_PER_BLOCK = {-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26};
    private static final int[] NUM_BLOCKS = {-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5};
    private static final int FORMAT_M = 0;

    private final int size;
    private final boolean[][] mod;
    private final boolean[][] fn;

    private Qr(int version) {
        size = version * 4 + 17;
        mod = new boolean[size][size];
        fn = new boolean[size][size];
    }

    /** Matriz do QR (true = escuro), ou null se o texto nao couber. */
    static boolean[][] encode(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int ver = 1;
        for (; ver <= MAX_VERSION; ver++) {
            int bits = 4 + (ver <= 9 ? 8 : 16) + data.length * 8;
            if (bits <= dataCodewords(ver) * 8) break;
        }
        if (ver > MAX_VERSION) return null;

        int capacity = dataCodewords(ver);
        BitBuf bb = new BitBuf();
        bb.add(0x4, 4);
        bb.add(data.length, ver <= 9 ? 8 : 16);
        for (byte b : data) bb.add(b & 0xFF, 8);
        bb.add(0, Math.min(4, capacity * 8 - bb.len));
        bb.add(0, (8 - bb.len % 8) % 8);
        for (int pad = 0xEC; bb.len < capacity * 8; pad ^= 0xEC ^ 0x11) bb.add(pad, 8);
        byte[] cw = new byte[capacity];
        for (int i = 0; i < bb.len; i++) if (bb.get(i)) cw[i >>> 3] |= (byte) (1 << (7 - (i & 7)));

        Qr q = new Qr(ver);
        q.drawFunctionPatterns(ver);
        q.drawCodewords(addEcc(cw, ver));
        int best = 0;
        long bestPenalty = Long.MAX_VALUE;
        for (int m = 0; m < 8; m++) {
            q.applyMask(m);
            q.drawFormat(m);
            long p = q.penalty();
            if (p < bestPenalty) {
                best = m;
                bestPenalty = p;
            }
            q.applyMask(m);
        }
        q.applyMask(best);
        q.drawFormat(best);
        return q.mod;
    }

    /** Desenha o QR em preto sobre branco, com margem de 'border' modulos e escala inteira (sem borrar). */
    static Bitmap bitmap(String text, int targetPx, int border) {
        boolean[][] m = encode(text);
        if (m == null) return null;
        int n = m.length + border * 2;
        int scale = Math.max(1, targetPx / n);
        int px = n * scale;
        int[] pixels = new int[px * px];
        for (int y = 0; y < px; y++) {
            int my = y / scale - border;
            for (int x = 0; x < px; x++) {
                int mx = x / scale - border;
                boolean dark = my >= 0 && mx >= 0 && my < m.length && mx < m.length && m[my][mx];
                pixels[y * px + x] = dark ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        return Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888);
    }

    // ---- Padroes fixos ----

    private void set(int x, int y, boolean dark) {
        mod[y][x] = dark;
        fn[y][x] = true;
    }

    private void drawFunctionPatterns(int ver) {
        for (int i = 0; i < size; i++) {
            set(6, i, i % 2 == 0);
            set(i, 6, i % 2 == 0);
        }
        finder(3, 3);
        finder(size - 4, 3);
        finder(3, size - 4);
        int[] al = alignmentPositions(ver);
        for (int i = 0; i < al.length; i++) {
            for (int j = 0; j < al.length; j++) {
                boolean corner = (i == 0 && j == 0) || (i == 0 && j == al.length - 1) || (i == al.length - 1 && j == 0);
                if (!corner) alignment(al[i], al[j]);
            }
        }
        drawFormat(0);
        if (ver >= 7) {
            int rem = ver;
            for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
            int bits = ver << 12 | rem;
            for (int i = 0; i < 18; i++) {
                boolean bit = ((bits >>> i) & 1) != 0;
                int a = size - 11 + i % 3, b = i / 3;
                set(a, b, bit);
                set(b, a, bit);
            }
        }
    }

    private void finder(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int d = Math.max(Math.abs(dx), Math.abs(dy));
                int x = cx + dx, y = cy + dy;
                if (x >= 0 && x < size && y >= 0 && y < size) set(x, y, d != 2 && d != 4);
            }
        }
    }

    private void alignment(int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++) set(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
    }

    private int[] alignmentPositions(int ver) {
        if (ver == 1) return new int[0];
        int num = ver / 7 + 2;
        int step = (ver * 8 + num * 3 + 5) / (num * 4 - 4) * 2;
        int[] r = new int[num];
        r[0] = 6;
        for (int i = num - 1, pos = size - 7; i >= 1; i--, pos -= step) r[i] = pos;
        return r;
    }

    private void drawFormat(int mask) {
        int data = FORMAT_M << 3 | mask;
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = (data << 10 | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) set(8, i, bit(bits, i));
        set(8, 7, bit(bits, 6));
        set(8, 8, bit(bits, 7));
        set(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) set(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) set(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) set(8, size - 15 + i, bit(bits, i));
        set(8, size - 8, true);
    }

    private static boolean bit(int x, int i) {
        return ((x >>> i) & 1) != 0;
    }

    // ---- Dados ----

    private static int rawModules(int ver) {
        int r = (16 * ver + 128) * ver + 64;
        if (ver >= 2) {
            int num = ver / 7 + 2;
            r -= (25 * num - 10) * num - 55;
            if (ver >= 7) r -= 36;
        }
        return r;
    }

    private static int dataCodewords(int ver) {
        return rawModules(ver) / 8 - ECC_PER_BLOCK[ver] * NUM_BLOCKS[ver];
    }

    private static byte[] addEcc(byte[] data, int ver) {
        int numBlocks = NUM_BLOCKS[ver], eccLen = ECC_PER_BLOCK[ver];
        int raw = rawModules(ver) / 8;
        int numShort = numBlocks - raw % numBlocks;
        int shortLen = raw / numBlocks;
        byte[] div = rsDivisor(eccLen);
        byte[][] blocks = new byte[numBlocks][];
        for (int i = 0, k = 0; i < numBlocks; i++) {
            int len = shortLen - eccLen + (i < numShort ? 0 : 1);
            byte[] dat = new byte[len];
            System.arraycopy(data, k, dat, 0, len);
            k += len;
            byte[] block = new byte[shortLen + 1];
            System.arraycopy(dat, 0, block, 0, len);
            byte[] ecc = rsRemainder(dat, div);
            System.arraycopy(ecc, 0, block, block.length - eccLen, eccLen);
            blocks[i] = block;
        }
        byte[] out = new byte[raw];
        for (int i = 0, k = 0; i < blocks[0].length; i++) {
            for (int j = 0; j < numBlocks; j++) {
                if (i != shortLen - eccLen || j >= numShort) out[k++] = blocks[j][i];
            }
        }
        return out;
    }

    private static byte[] rsDivisor(int degree) {
        byte[] r = new byte[degree];
        r[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < r.length; j++) {
                r[j] = (byte) gfMul(r[j] & 0xFF, root);
                if (j + 1 < r.length) r[j] ^= r[j + 1];
            }
            root = gfMul(root, 0x02);
        }
        return r;
    }

    private static byte[] rsRemainder(byte[] data, byte[] div) {
        byte[] r = new byte[div.length];
        for (byte b : data) {
            int f = (b ^ r[0]) & 0xFF;
            System.arraycopy(r, 1, r, 0, r.length - 1);
            r[r.length - 1] = 0;
            for (int i = 0; i < r.length; i++) r[i] ^= (byte) gfMul(div[i] & 0xFF, f);
        }
        return r;
    }

    private static int gfMul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    private void drawCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean up = ((right + 1) & 2) == 0;
                    int y = up ? size - 1 - vert : vert;
                    if (!fn[y][x] && i < data.length * 8) {
                        mod[y][x] = bit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private void applyMask(int m) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean inv;
                switch (m) {
                    case 0: inv = (x + y) % 2 == 0; break;
                    case 1: inv = y % 2 == 0; break;
                    case 2: inv = x % 3 == 0; break;
                    case 3: inv = (x + y) % 3 == 0; break;
                    case 4: inv = (x / 3 + y / 2) % 2 == 0; break;
                    case 5: inv = x * y % 2 + x * y % 3 == 0; break;
                    case 6: inv = (x * y % 2 + x * y % 3) % 2 == 0; break;
                    default: inv = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
                }
                if (inv && !fn[y][x]) mod[y][x] = !mod[y][x];
            }
        }
    }

    /** Penalidade do padrao (regras 1 a 4): o mascara com menor valor fica mais facil de ler. */
    private long penalty() {
        long p = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int a = 0; a < size; a++) {
                int run = 1;
                for (int b = 1; b < size; b++) {
                    boolean prev = pass == 0 ? mod[a][b - 1] : mod[b - 1][a];
                    boolean cur = pass == 0 ? mod[a][b] : mod[b][a];
                    if (cur == prev) {
                        run++;
                    } else {
                        if (run >= 5) p += 3 + run - 5;
                        run = 1;
                    }
                }
                if (run >= 5) p += 3 + run - 5;
                for (int b = 0; b + 10 < size; b++) {
                    if (finderLike(pass, a, b)) p += 40;
                }
            }
        }
        int dark = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (mod[y][x]) dark++;
                if (x + 1 < size && y + 1 < size) {
                    boolean c = mod[y][x];
                    if (c == mod[y][x + 1] && c == mod[y + 1][x] && c == mod[y + 1][x + 1]) p += 3;
                }
            }
        }
        int total = size * size;
        int k = Math.abs(dark * 20 - total * 10) / total;
        p += k * 10L;
        return p;
    }

    private static final boolean[] PAT_A = {true, false, true, true, true, false, true, false, false, false, false};
    private static final boolean[] PAT_B = {false, false, false, false, true, false, true, true, true, false, true};

    private boolean finderLike(int pass, int a, int b) {
        boolean ma = true, mb = true;
        for (int i = 0; i < 11; i++) {
            boolean v = pass == 0 ? mod[a][b + i] : mod[b + i][a];
            if (v != PAT_A[i]) ma = false;
            if (v != PAT_B[i]) mb = false;
        }
        return ma || mb;
    }

    private static final class BitBuf {
        boolean[] bits = new boolean[64];
        int len;

        void add(int val, int n) {
            for (int i = n - 1; i >= 0; i--) {
                if (len == bits.length) bits = java.util.Arrays.copyOf(bits, len * 2);
                bits[len++] = ((val >>> i) & 1) != 0;
            }
        }

        boolean get(int i) {
            return bits[i];
        }
    }
}
