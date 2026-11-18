package dk.mineconed.analytics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

/**
 * Made by Mineconed
 *
 * Draws a PNG bar chart: average players per hour.
 */
public final class Graf {

    private Graf() {
    }

    public static byte[] tegn(DagData d, String titel, String bund) {
        try {
            System.setProperty("java.awt.headless", "true");
            int w = 960;
            int h = 400;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, new Color(30, 31, 46), 0, h, new Color(18, 18, 28)));
            g.fillRoundRect(0, 0, w, h, 28, 28);

            int venstre = 60;
            int top = 70;
            int bundY = Math.subtractExact(h, 50);
            int bredde = Math.subtractExact(w, venstre + 30);
            double max = 1;
            for (int t = 0; t < 24; t++) {
                max = Math.max(max, d.snit(t));
            }
            max = Math.ceil(max);

            g.setColor(new Color(235, 235, 245));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            g.drawString(titel, venstre, 42);

            // grid
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            for (int i = 0; i <= 4; i++) {
                int y = bundY + (int) Math.round((top + Math.negateExact(bundY)) * (i / 4.0));
                g.setColor(new Color(255, 255, 255, 25));
                g.setStroke(new BasicStroke(1f));
                g.drawLine(venstre, y, venstre + bredde, y);
                g.setColor(new Color(160, 160, 180));
                String lbl = String.valueOf((int) Math.round(max * i / 4.0));
                g.drawString(lbl, venstre + Math.negateExact(10 + g.getFontMetrics().stringWidth(lbl)), y + 5);
            }

            int travl = d.travlesteTime();
            double felt = bredde / 24.0;
            for (int t = 0; t < 24; t++) {
                double v = d.snit(t);
                int bh = (int) Math.round((bundY + Math.negateExact(top)) * (v / max));
                int x = venstre + (int) Math.round(t * felt + felt * 0.15);
                int bw = (int) Math.round(felt * 0.7);
                int y = bundY + Math.negateExact(bh);
                Color c1 = t == travl ? new Color(255, 196, 70) : new Color(122, 104, 255);
                Color c2 = t == travl ? new Color(245, 140, 40) : new Color(80, 60, 220);
                if (bh > 0) {
                    g.setPaint(new GradientPaint(0, y, c1, 0, bundY, c2));
                    g.fillRoundRect(x, y, bw, bh, 8, 8);
                }
                if (t % 2 == 0) {
                    g.setColor(new Color(160, 160, 180));
                    String lbl = (t < 10 ? "0" : "") + t;
                    g.drawString(lbl, x + (bw + Math.negateExact(g.getFontMetrics().stringWidth(lbl))) / 2, bundY + 20);
                }
            }
            g.setColor(new Color(140, 140, 160));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g.drawString(bund, venstre, h + Math.negateExact(14));
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    /** ▁▂▃▅▇ line for chat and Discord when there is no picture. */
    public static String linje(DagData d) {
        char[] b = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};
        double max = 0;
        for (int t = 0; t < 24; t++) {
            max = Math.max(max, d.snit(t));
        }
        StringBuilder s = new StringBuilder();
        for (int t = 0; t < 24; t++) {
            int i = max <= 0 ? 0 : (int) Math.round(d.snit(t) / max * 7);
            s.append(b[Math.max(0, Math.min(7, i))]);
        }
        return s.toString();
    }
}
