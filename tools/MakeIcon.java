import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * Generates icon.png in Minecraft's own item style: a mob's head the way the inventory shows a head (a small block,
 * its face on the front), with note particles rising from it like a note block's. 32 x 32 pixels, each written as
 * 8 x 8.
 * Run from the project folder with a JDK that has AWT: java tools/MakeIcon.java
 */
public class MakeIcon {
    public static void main(String[] args) throws Exception {
        head(10, 9, 22);
        note(1, 9, 0x5BE05B);
        note(6, 1, 0xFFB43C);
        write("src/main/resources/assets/mobvolume/icon.png");
    }

    static final String GREENS = "gGh";
    static final int[] GREEN = {0x5FA048, 0x4D8A3A, 0x6DB054};

    /** A green mob's head: its face on the front (left) side, skin on the top and the other side. */
    static void head(int x, int y, int w) {
        int[] face = skin(new String[] {
            "gGghgGgh",
            "hggGgghg",
            "gGgggGgg",
            "gkkggkkg",
            "gkwggkwg",
            "gggGgggh",
            "hgmmmmgg",
            "gGmggmGg",
        }, "gGhkwm", 0x5FA048, 0x4D8A3A, 0x6DB054, 0x141414, 0x3A3A3A, 0x1D3016);
        int[] top = texture(31, GREEN);
        int[] side = texture(32, GREEN);
        block(x, y, w, top, face, side);
    }

    /** A note particle: a quaver (stem, flag to the right, round head at the bottom left), outlined. */
    static void note(int x, int y, int color) {
        layer();
        sprite(x, y, new String[] {
            "..#...",
            "..##..",
            "..#.#.",
            "..#..#",
            "..#..#",
            "..#...",
            ".##...",
            "###...",
            ".#....",
        }, "#", color);
        sprite(x, y, new String[] {
            "......",
            "......",
            "......",
            "......",
            "......",
            "......",
            ".h....",
        }, "h", shade(color, 1.35));
        flatten(true);
    }

    // ---------------------------------------------------------------- pixel kit (the same in every mod's icon tool)

    /** 32 x 32 pixels: items at the size Minecraft draws them (16 x 16), each pixel written as 8 x 8 (256 x 256). */
    static final int N = 32, SCALE = 8;
    static final int[] PX = new int[N * N];
    /** Where drawing goes: the picture, or a layer that gets an outline before it is put on the picture. */
    static int[] target = PX;

    static void set(int x, int y, int rgb) {
        if (x < 0 || y < 0 || x >= N || y >= N) return;
        target[y * N + x] = 0xFF000000 | rgb;
    }

    static int shade(int rgb, double f) {
        int r = (int) Math.min(255, Math.round(((rgb >> 16) & 255) * f));
        int g = (int) Math.min(255, Math.round(((rgb >> 8) & 255) * f));
        int b = (int) Math.min(255, Math.round((rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Starts a layer: an item drawn on its own, so it can get its dark outline. */
    static void layer() {
        target = new int[N * N];
    }

    /**
     * Puts the layer on the picture. With {@code outline}, every empty pixel next to it first gets a dark shade of
     * its neighbour, the way Minecraft's item sprites are outlined, so the item stands out from what is behind it.
     */
    static void flatten(boolean outline) {
        int[] layer = target;
        target = PX;
        if (outline) {
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    if (layer[y * N + x] != 0) continue;
                    int n = neighbour(layer, x, y);
                    if (n != 0) PX[y * N + x] = 0xFF000000 | shade(n, 0.32);
                }
            }
        }
        for (int i = 0; i < PX.length; i++) if (layer[i] != 0) PX[i] = layer[i];
    }

    static int neighbour(int[] layer, int x, int y) {
        int[][] d = {{0, -1}, {-1, 0}, {1, 0}, {0, 1}};
        for (int[] o : d) {
            int nx = x + o[0], ny = y + o[1];
            if (nx >= 0 && ny >= 0 && nx < N && ny < N && layer[ny * N + nx] != 0) return layer[ny * N + nx];
        }
        return 0;
    }

    /** Pixel rows, one character per pixel: '.' is empty, any other is looked up in {@code keys}/{@code colors}. */
    static void sprite(int x0, int y0, String[] rows, String keys, int... colors) {
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                int k = keys.indexOf(rows[y].charAt(x));
                if (k >= 0) set(x0 + x, y0 + y, colors[k]);
            }
        }
    }

    /** A 16 x 16 texture, every pixel one of the colours at random: the grain of Minecraft's textures. */
    static int[] texture(long seed, int... colors) {
        Random rnd = new Random(seed);
        int[] t = new int[256];
        for (int i = 0; i < 256; i++) t[i] = colors[rnd.nextInt(colors.length)];
        return t;
    }

    /** A 16 x 16 texture from 8 x 8 pixel rows, each pixel doubled (the way mob skins are drawn). */
    static int[] skin(String[] rows, String keys, int... colors) {
        int[] t = new int[256];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) t[y * 16 + x] = colors[keys.indexOf(rows[y / 2].charAt(x / 2))];
        }
        return t;
    }

    /**
     * A block the way Minecraft shows one in the inventory: seen from above at an angle, the top fully lit, the left
     * side a little darker and the right side darker still. (x0, y0) is the top left of a w x w square.
     */
    static void block(int x0, int y0, int w, int[] top, int[] left, int[] right) {
        double hw = w / 2.0, q = w / 4.0;
        for (int py = 0; py < w; py++) {
            for (int px = 0; px < w; px++) {
                double x = px + 0.5, y = py + 0.5;
                double a = (y / q + (x - hw) / hw) / 2, b = (y / q - (x - hw) / hw) / 2;
                if (in(a) && in(b)) {
                    set(x0 + px, y0 + py, sample(top, a, b));
                    continue;
                }
                a = x / hw;
                b = (y - q - a * q) / hw;
                if (in(a) && in(b)) {
                    set(x0 + px, y0 + py, shade(sample(left, a, b), 0.8));
                    continue;
                }
                a = (x - hw) / hw;
                b = (y - hw + a * q) / hw;
                if (in(a) && in(b)) set(x0 + px, y0 + py, shade(sample(right, a, b), 0.6));
            }
        }
    }

    static boolean in(double v) {
        return v >= 0 && v < 1;
    }

    static int sample(int[] tex, double u, double v) {
        return tex[Math.min(15, (int) (v * 16)) * 16 + Math.min(15, (int) (u * 16))];
    }

    /** Writes the picture with every pixel as SCALE x SCALE, so it stays sharp at any size. */
    static void write(String path) throws Exception {
        BufferedImage img = new BufferedImage(N * SCALE, N * SCALE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < N * SCALE; y++) {
            for (int x = 0; x < N * SCALE; x++) img.setRGB(x, y, PX[(y / SCALE) * N + x / SCALE]);
        }
        File out = new File(path);
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Wrote " + out);
    }
}
