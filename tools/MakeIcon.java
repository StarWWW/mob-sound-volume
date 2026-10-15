import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * Generates the mod's pixel-art images:
 * <ul>
 *   <li>icon.png: a creeper head sending out sound waves, with a volume slider underneath. Drawn at 64x64 and
 *       scaled to 128x128 so it stays crisp in Mod Menu;</li>
 *   <li>the "back to 100%" button sprite (a circular arrow) and its greyed-out copy.</li>
 * </ul>
 * Run from the project folder: java tools/MakeIcon.java
 */
public class MakeIcon {
    static final int N = 64;
    static final int[] PX = new int[N * N];
    static final Random RND = new Random(2612);

    public static void main(String[] args) throws Exception {
        badgeBackground(0xFF2E3558, 0xFF181B2E);
        glow(22, 33, 26, 0x2A8FE0A0);
        fill(8, 20, 40, 52, 0x55000000); // drop shadow of the head
        creeperHead(5, 17, 4);
        soundWaves(38, 33);
        slider(9, 55, 54, 0.66);
        badgeFrame();
        write(scale(2), "src/main/resources/assets/mobvolume/icon.png");

        writeSprite("reset", resetArrow(0xFFFFFF, 0x3F3F3F));
        writeSprite("reset_disabled", resetArrow(0xA0A0A0, 0x2A2A2A));
    }

    // ---------------------------------------------------------------- picture

    /** 8x8 creeper face, every face pixel drawn as s x s, with shading and a dark outline. */
    static void creeperHead(int x0, int y0, int s) {
        String[] face = {
            "........",
            "........",
            ".XX..XX.",
            ".XX..XX.",
            "...XX...",
            "..XXXX..",
            "..XXXX..",
            "..X..X..",
        };
        int size = 8 * s;
        int[] greens = {0xFF5DB34A, 0xFF4E9E3E, 0xFF6CC255, 0xFF478F38, 0xFF7ACB62, 0xFF58A845};
        // like the real skin: the texture's own pixels are 2x2 here
        int[][] cells = new int[size / 2][size / 2];
        for (int[] row : cells) {
            for (int i = 0; i < row.length; i++) row[i] = greens[RND.nextInt(greens.length)];
        }
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int c = cells[y / 2][x / 2];
                if (face[y / s].charAt(x / s) == 'X') {
                    // the face is a hole: near-black, a bit lighter at its top edge
                    boolean top = y % s == 0 && (y / s == 0 || face[y / s - 1].charAt(x / s) != 'X');
                    c = top ? 0xFF2A2A2A : (RND.nextInt(5) == 0 ? 0xFF1C1C1C : 0xFF111111);
                } else {
                    // light from the top left
                    if (x == 0 || y == 0) c = mix(c, 0xFFFFFFFF, 0.28);
                    else if (x == size - 1 || y == size - 1) c = mix(c, 0xFF000000, 0.35);
                    else if (x == size - 2 || y == size - 2) c = mix(c, 0xFF000000, 0.15);
                }
                set(x0 + x, y0 + y, c);
            }
        }
        outlineRect(x0 - 1, y0 - 1, x0 + size, y0 + size, 0xFF0C2410);
    }

    /** Three arcs to the right of the head, getting louder (warmer) outwards. */
    static void soundWaves(int cx, int cy) {
        int[][] waves = {{7, 0xFFFFFFFF}, {13, 0xFFFFD84A}, {19, 0xFFFF8A3D}};
        // dark outline first so neighbouring arcs keep a gap
        for (int[] w : waves) arc(cx, cy, w[0], 4.4, -54, 54, 0xFF101225);
        for (int[] w : waves) arc(cx, cy, w[0], 2.6, -50, 50, w[1]);
        // light on the inner edge, shade on the outer edge
        for (int[] w : waves) arc(cx, cy, w[0] - 0.9, 0.8, -50, 50, mix(w[1], 0xFFFFFFFF, 0.45));
        for (int[] w : waves) arc(cx, cy, w[0] + 0.9, 0.8, -50, 50, mix(w[1], 0xFF000000, 0.25));
    }

    /** A volume slider: dark track, coloured fill up to the knob, bevelled knob. */
    static void slider(int x0, int y, int x1, double level) {
        int knob = x0 + (int) Math.round((x1 - x0) * level);
        fill(x0 - 1, y - 2, x1 + 1, y + 3, 0xFF0E1020);
        fill(x0, y - 1, x1, y + 2, 0xFF3A3F5E);
        for (int x = x0; x < knob; x++) {
            double t = (x - x0) / (double) (x1 - x0);
            int c = mix(0xFF4FD16A, 0xFFFFD84A, t / level);
            fill(x, y - 1, x + 1, y + 2, c);
            set(x, y - 1, mix(c, 0xFFFFFFFF, 0.35));
        }
        // knob
        fill(knob - 3, y - 5, knob + 4, y + 6, 0xFF0E1020);
        fill(knob - 2, y - 4, knob + 3, y + 5, 0xFFD9DCE8);
        fill(knob - 2, y - 4, knob + 3, y - 3, 0xFFFFFFFF);
        fill(knob - 2, y - 4, knob - 1, y + 5, 0xFFFFFFFF);
        fill(knob - 2, y + 4, knob + 3, y + 5, 0xFF8C90A6);
        fill(knob + 2, y - 3, knob + 3, y + 5, 0xFF8C90A6);
    }

    /** Soft radial light. */
    static void glow(double cx, double cy, double r, int argb) {
        int a = argb >>> 24;
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy) / r;
                if (d >= 1 || !inBadge(x, y, 2)) continue;
                int alpha = (int) Math.round(a * (1 - d) * (1 - d));
                set(x, y, (alpha << 24) | (argb & 0xFFFFFF));
            }
        }
    }

    // ---------------------------------------------------------------- badge

    static final int CORNER = 8;

    /** Is the pixel inside the rounded square, shrunk by {@code inset} pixels? */
    static boolean inBadge(int x, int y, double inset) {
        double px = x + 0.5, py = y + 0.5;
        double lo = inset, hi = N - inset, r = Math.max(1, CORNER - inset);
        if (px < lo || py < lo || px > hi || py > hi) return false;
        double cx = Math.max(lo + r, Math.min(hi - r, px));
        double cy = Math.max(lo + r, Math.min(hi - r, py));
        return (px - cx) * (px - cx) + (py - cy) * (py - cy) <= r * r;
    }

    static void badgeBackground(int top, int bottom) {
        for (int y = 0; y < N; y++) {
            int c = mix(top, bottom, y / (double) (N - 1));
            for (int x = 0; x < N; x++) {
                if (inBadge(x, y, 0)) PX[y * N + x] = c;
            }
        }
        // a few faint dots so the background is not flat
        for (int i = 0; i < 40; i++) {
            int x = RND.nextInt(N), y = RND.nextInt(N);
            if (inBadge(x, y, 3)) set(x, y, 0x18FFFFFF);
        }
    }

    /** 2px dark rim, a lit inner edge at the top left and a shaded one at the bottom right; outside is cleared. */
    static void badgeFrame() {
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                int i = y * N + x;
                if (!inBadge(x, y, 0)) {
                    PX[i] = 0;
                } else if (!inBadge(x, y, 2)) {
                    PX[i] = 0xFF0A0B12;
                } else if (!inBadge(x, y, 3)) {
                    boolean lit = x + y < N - 1;
                    PX[i] = blend(PX[i], lit ? 0x40FFFFFF : 0x50000000);
                }
            }
        }
    }

    // ---------------------------------------------------------------- drawing helpers

    static void set(int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= N || y >= N) return;
        PX[y * N + x] = blend(PX[y * N + x], argb);
    }

    static void fill(int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) set(x, y, argb);
        }
    }

    static void outlineRect(int x0, int y0, int x1, int y1, int argb) {
        for (int x = x0; x <= x1; x++) {
            set(x, y0, argb);
            set(x, y1, argb);
        }
        for (int y = y0 + 1; y < y1; y++) {
            set(x0, y, argb);
            set(x1, y, argb);
        }
    }

    /** Pixels whose centre is within {@code width/2} of the circle of radius r, between two angles (degrees, 0 = right, y down). */
    static void arc(double cx, double cy, double r, double width, double fromDeg, double toDeg, int argb) {
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                double dx = x + 0.5 - cx, dy = y + 0.5 - cy;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (Math.abs(d - r) > width / 2) continue;
                double a = Math.toDegrees(Math.atan2(dy, dx));
                if (a >= fromDeg && a <= toDeg) set(x, y, argb);
            }
        }
    }

    static int blend(int dst, int src) {
        int sa = src >>> 24;
        if (sa == 255) return src;
        if (sa == 0) return dst;
        int da = dst >>> 24;
        double a = sa / 255.0, b = da / 255.0 * (1 - a), out = a + b;
        if (out <= 0) return 0;
        int r = (int) Math.round((((src >> 16) & 255) * a + ((dst >> 16) & 255) * b) / out);
        int g = (int) Math.round((((src >> 8) & 255) * a + ((dst >> 8) & 255) * b) / out);
        int bl = (int) Math.round(((src & 255) * a + (dst & 255) * b) / out);
        return ((int) Math.round(out * 255) << 24) | (r << 16) | (g << 8) | bl;
    }

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (a & 0xFF000000) | (r << 16) | (g << 8) | bl;
    }

    static BufferedImage scale(int factor) {
        BufferedImage img = new BufferedImage(N * factor, N * factor, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < N * factor; y++) {
            for (int x = 0; x < N * factor; x++) img.setRGB(x, y, PX[(y / factor) * N + x / factor]);
        }
        return img;
    }

    static void write(BufferedImage img, String path) throws Exception {
        File out = new File(path);
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Wrote " + out);
    }

    // ---------------------------------------------------------------- button sprite

    /** Clockwise "refresh" arrow: a radius-4 ring open at the top right, with a head pointing right at the top. */
    static BufferedImage resetArrow(int color, int shadow) {
        java.util.List<int[]> pts = new java.util.ArrayList<>();
        // midpoint circle, radius 4
        int[][] octant = {{0, 4}, {1, 4}, {2, 3}, {3, 3}};
        for (int[] p : octant) {
            for (int sx : new int[]{1, -1}) {
                for (int sy : new int[]{1, -1}) {
                    pts.add(new int[]{p[0] * sx, p[1] * sy});
                    pts.add(new int[]{p[1] * sx, p[0] * sy});
                }
            }
        }
        // the gap: top-right part of the ring (y is down)
        pts.removeIf(p -> p[0] > 0 && p[1] < 0 && !(p[0] == 4 && p[1] == 0));
        // arrow head, tip at (2, -4)
        int[][] head = {{0, -6}, {0, -5}, {0, -4}, {0, -3}, {0, -2}, {1, -5}, {1, -4}, {1, -3}, {2, -4}};
        pts.addAll(java.util.List.of(head));

        int cx = 5, cy = 6;
        BufferedImage img = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
        for (int[] p : pts) img.setRGB(cx + p[0] + 1, cy + p[1] + 1, 0xFF000000 | shadow);
        for (int[] p : pts) img.setRGB(cx + p[0], cy + p[1], 0xFF000000 | color);
        return img;
    }

    static void writeSprite(String name, BufferedImage img) throws Exception {
        write(img, "src/main/resources/assets/mobvolume/textures/gui/sprites/" + name + ".png");
    }
}
