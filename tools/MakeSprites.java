import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Generates the "back to 100%" button sprite (a circular arrow) and its greyed-out copy, 12 x 12 pixels with a drop
 * shadow like the game's own buttons. The mod's icon has its own tool, MakeIcon.java.
 * Run from the project folder with a JDK that has AWT: java tools/MakeSprites.java
 */
public class MakeSprites {
    public static void main(String[] args) throws Exception {
        writeSprite("reset", resetArrow(0xFFFFFF, 0x3F3F3F));
        writeSprite("reset_disabled", resetArrow(0xA0A0A0, 0x2A2A2A));
    }

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
        File out = new File("src/main/resources/assets/mobvolume/textures/gui/sprites/" + name + ".png");
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Wrote " + out);
    }
}
