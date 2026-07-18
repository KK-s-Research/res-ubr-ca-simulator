package in.ac.caluniv.cse.ubrca.artifact;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ChartWriter {
    public record Series(String name, List<Double> x, List<Double> y, Color color) {}

    private static final int WIDTH = 1_600;
    private static final int HEIGHT = 960;
    private static final int LEFT = 185;
    private static final int RIGHT = 95;
    private static final int TOP = 125;
    private static final int BOTTOM = 190;
    private static final Color NAVY = new Color(31, 78, 121);
    private static final Color ORANGE = new Color(230, 126, 34);
    private static final Color GREEN = new Color(39, 174, 96);
    private static final Color GRID = new Color(220, 225, 230);

    private ChartWriter() {}

    public static List<Color> palette() {
        return List.of(NAVY, ORANGE, GREEN, new Color(142, 68, 173),
                new Color(192, 57, 43), new Color(22, 160, 133),
                new Color(52, 73, 94), new Color(127, 140, 141),
                new Color(241, 196, 15));
    }

    public static void bar(Path basePath, String title, String yLabel,
                           List<String> labels, List<Double> values,
                           List<Double> errors) throws IOException {
        Files.createDirectories(basePath.getParent());
        double max = 0.0;
        for (int i = 0; i < values.size(); i++) {
            max = Math.max(max, values.get(i) + (errors.isEmpty() ? 0.0 : errors.get(i)));
        }
        max = niceMaximum(max * 1.12);
        BufferedImage image = canvas();
        Graphics2D g = image.createGraphics();
        setup(g);
        axes(g, title, "", yLabel, 0.0, max, labels.size(), labels);
        int plotWidth = WIDTH - LEFT - RIGHT;
        int plotHeight = HEIGHT - TOP - BOTTOM;
        double slot = plotWidth / (double) labels.size();
        double barWidth = slot * 0.62;
        List<Color> colors = palette();
        for (int i = 0; i < values.size(); i++) {
            int x = (int) (LEFT + slot * i + (slot - barWidth) / 2);
            int y = mapY(values.get(i), 0.0, max, plotHeight);
            int h = TOP + plotHeight - y;
            g.setColor(colors.get(i % colors.size()));
            g.fillRoundRect(x, y, (int) barWidth, h, 12, 12);
            if (!errors.isEmpty()) drawError(g, x + (int) barWidth / 2,
                    values.get(i), errors.get(i), 0.0, max, plotHeight);
            g.setColor(Color.DARK_GRAY);
            g.setFont(new Font("SansSerif", Font.BOLD, 28));
            String value = String.format(Locale.ROOT, "%.3f", values.get(i));
            centered(g, value, x + (int) barWidth / 2, y - 16);
        }
        g.dispose();
        ImageIO.write(image, "png", Path.of(basePath + ".png").toFile());
        writeBarSvg(Path.of(basePath + ".svg"), title, yLabel, labels, values, errors, max);
    }

    public static void line(Path basePath, String title, String xLabel,
                            String yLabel, List<Series> series,
                            boolean logarithmicX) throws IOException {
        Files.createDirectories(basePath.getParent());
        double xMin = series.stream().flatMap(s -> s.x.stream())
                .mapToDouble(Double::doubleValue).min().orElse(0.0);
        double xMax = series.stream().flatMap(s -> s.x.stream())
                .mapToDouble(Double::doubleValue).max().orElse(1.0);
        double yMin = Math.min(0.0, series.stream().flatMap(s -> s.y.stream())
                .mapToDouble(Double::doubleValue).min().orElse(0.0));
        double yMax = niceMaximum(series.stream().flatMap(s -> s.y.stream())
                .mapToDouble(Double::doubleValue).max().orElse(1.0) * 1.10);
        if (logarithmicX) {
            xMin = Math.log10(Math.max(1e-9, xMin));
            xMax = Math.log10(Math.max(1e-9, xMax));
        }
        BufferedImage image = canvas();
        Graphics2D g = image.createGraphics();
        setup(g);
        numericAxes(g, title, xLabel, yLabel, xMin, xMax, yMin, yMax,
                logarithmicX);
        drawLines(g, series, xMin, xMax, yMin, yMax, logarithmicX);
        legend(g, series);
        g.dispose();
        ImageIO.write(image, "png", Path.of(basePath + ".png").toFile());
        writeLineSvg(Path.of(basePath + ".svg"), title, xLabel, yLabel, series,
                xMin, xMax, yMin, yMax, logarithmicX);
    }

    public static void grid(Path basePath, String title, List<Path> imagePaths,
                            List<String> captions) throws IOException {
        Files.createDirectories(basePath.getParent());
        int width = 1_800;
        int height = 1_320;
        int margin = 70;
        int titleHeight = 20;
        int gap = 35;
        int captionHeight = 0;
        int cellWidth = (width - 2 * margin - gap) / 2;
        int cellHeight = (height - titleHeight - margin - gap
                - 2 * captionHeight) / 2;
        BufferedImage image = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        setup(g);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        for (int i = 0; i < imagePaths.size(); i++) {
            BufferedImage panel = ImageIO.read(imagePaths.get(i).toFile());
            if (panel == null) continue;
            int row = i / 2;
            int column = i % 2;
            int x = margin + column * (cellWidth + gap);
            int y = titleHeight + row * (cellHeight + captionHeight + gap);
            g.drawImage(panel, x, y, cellWidth, cellHeight, null);
        }
        g.dispose();
        ImageIO.write(image, "png", Path.of(basePath + ".png").toFile());
        writeGridSvg(Path.of(basePath + ".svg"), title, imagePaths, captions,
                width, height, margin, titleHeight, gap, captionHeight,
                cellWidth, cellHeight);
    }

    private static BufferedImage canvas() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.dispose();
        return image;
    }

    private static void setup(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static void axes(Graphics2D g, String title, String xLabel,
                             String yLabel, double yMin, double yMax,
                             int categories, List<String> labels) {
        title(g, title);
        int plotHeight = HEIGHT - TOP - BOTTOM;
        int plotWidth = WIDTH - LEFT - RIGHT;
        gridY(g, yMin, yMax, plotHeight);
        g.setColor(Color.DARK_GRAY);
        g.setStroke(new BasicStroke(2.0f));
        g.drawLine(LEFT, TOP, LEFT, TOP + plotHeight);
        g.drawLine(LEFT, TOP + plotHeight, LEFT + plotWidth, TOP + plotHeight);
        double slot = plotWidth / (double) categories;
        g.setFont(new Font("SansSerif", Font.PLAIN, 28));
        for (int i = 0; i < categories; i++) {
            centered(g, labels.get(i), (int) (LEFT + (i + 0.5) * slot),
                    TOP + plotHeight + 57);
        }
        axisLabels(g, xLabel, yLabel);
    }

    private static void numericAxes(Graphics2D g, String title, String xLabel,
                                    String yLabel, double xMin, double xMax,
                                    double yMin, double yMax, boolean logX) {
        title(g, title);
        int plotHeight = HEIGHT - TOP - BOTTOM;
        int plotWidth = WIDTH - LEFT - RIGHT;
        gridY(g, yMin, yMax, plotHeight);
        g.setColor(Color.DARK_GRAY);
        g.setStroke(new BasicStroke(2.0f));
        g.drawLine(LEFT, TOP, LEFT, TOP + plotHeight);
        g.drawLine(LEFT, TOP + plotHeight, LEFT + plotWidth, TOP + plotHeight);
        g.setFont(new Font("SansSerif", Font.PLAIN, 27));
        for (int i = 0; i <= 5; i++) {
            double value = xMin + (xMax - xMin) * i / 5.0;
            String label = logX
                    ? compact(Math.pow(10.0, value)) : compact(value);
            centered(g, label, LEFT + plotWidth * i / 5,
                    TOP + plotHeight + 54);
        }
        axisLabels(g, xLabel, yLabel);
    }

    private static void gridY(Graphics2D g, double yMin, double yMax,
                              int plotHeight) {
        int plotWidth = WIDTH - LEFT - RIGHT;
        g.setFont(new Font("SansSerif", Font.PLAIN, 27));
        for (int i = 0; i <= 5; i++) {
            int y = TOP + plotHeight - plotHeight * i / 5;
            g.setColor(GRID);
            g.setStroke(new BasicStroke(1.2f));
            g.drawLine(LEFT, y, LEFT + plotWidth, y);
            g.setColor(Color.DARK_GRAY);
            String label = compact(yMin + (yMax - yMin) * i / 5.0);
            g.drawString(label, LEFT - 18 - g.getFontMetrics().stringWidth(label),
                    y + 9);
        }
    }

    private static void drawLines(Graphics2D g, List<Series> series,
                                  double xMin, double xMax, double yMin,
                                  double yMax, boolean logX) {
        int plotWidth = WIDTH - LEFT - RIGHT;
        int plotHeight = HEIGHT - TOP - BOTTOM;
        for (Series item : series) {
            g.setColor(item.color);
            g.setStroke(new BasicStroke(4.0f));
            for (int i = 1; i < item.x.size(); i++) {
                double x1v = logX ? Math.log10(item.x.get(i - 1)) : item.x.get(i - 1);
                double x2v = logX ? Math.log10(item.x.get(i)) : item.x.get(i);
                int x1 = LEFT + (int) ((x1v - xMin) / (xMax - xMin) * plotWidth);
                int x2 = LEFT + (int) ((x2v - xMin) / (xMax - xMin) * plotWidth);
                int y1 = mapY(item.y.get(i - 1), yMin, yMax, plotHeight);
                int y2 = mapY(item.y.get(i), yMin, yMax, plotHeight);
                g.drawLine(x1, y1, x2, y2);
            }
            for (int i = 0; i < item.x.size(); i++) {
                double xv = logX ? Math.log10(item.x.get(i)) : item.x.get(i);
                int x = LEFT + (int) ((xv - xMin) / (xMax - xMin) * plotWidth);
                int y = mapY(item.y.get(i), yMin, yMax, plotHeight);
                g.fillOval(x - 6, y - 6, 12, 12);
            }
        }
    }

    private static void title(Graphics2D g, String title) {
        // Titles are intentionally omitted from generated figures because the
        // manuscript supplies captions through LaTeX.
    }

    private static void axisLabels(Graphics2D g, String xLabel, String yLabel) {
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font("SansSerif", Font.BOLD, 31));
        centered(g, xLabel, WIDTH / 2, HEIGHT - 45);
        g.rotate(-Math.PI / 2.0);
        centered(g, yLabel, -HEIGHT / 2, 52);
        g.rotate(Math.PI / 2.0);
    }

    private static void legend(Graphics2D g, List<Series> series) {
        int x = LEFT + 20;
        int y = TOP + 28;
        g.setFont(new Font("SansSerif", Font.PLAIN, 27));
        for (Series item : series) {
            g.setColor(item.color);
            g.fillRect(x, y - 18, 38, 10);
            g.setColor(Color.DARK_GRAY);
            g.drawString(item.name, x + 52, y);
            x += 80 + g.getFontMetrics().stringWidth(item.name);
        }
    }

    private static void drawError(Graphics2D g, int x, double value,
                                  double error, double min, double max,
                                  int plotHeight) {
        int top = mapY(value + error, min, max, plotHeight);
        int bottom = mapY(Math.max(min, value - error), min, max, plotHeight);
        g.setColor(Color.DARK_GRAY);
        g.setStroke(new BasicStroke(2.5f));
        g.drawLine(x, top, x, bottom);
        g.drawLine(x - 10, top, x + 10, top);
        g.drawLine(x - 10, bottom, x + 10, bottom);
    }

    private static int mapY(double value, double min, double max, int plotHeight) {
        return TOP + plotHeight
                - (int) ((value - min) / Math.max(1e-12, max - min) * plotHeight);
    }

    private static void centered(Graphics2D g, String text, int x, int baseline) {
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, x - metrics.stringWidth(text) / 2, baseline);
    }

    private static String compact(double value) {
        if (Math.abs(value) >= 1_000) return String.format(Locale.ROOT, "%.0f", value);
        if (Math.abs(value) >= 10) return String.format(Locale.ROOT, "%.1f", value);
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static double niceMaximum(double value) {
        if (value <= 0.0) return 1.0;
        double power = Math.pow(10.0, Math.floor(Math.log10(value)));
        double scaled = value / power;
        double nice = scaled <= 1.0 ? 1.0 : scaled <= 2.0 ? 2.0
                : scaled <= 5.0 ? 5.0 : 10.0;
        return nice * power;
    }

    private static void writeBarSvg(Path path, String title, String yLabel,
                                    List<String> labels, List<Double> values,
                                    List<Double> errors, double max) throws IOException {
        int plotWidth = WIDTH - LEFT - RIGHT;
        int plotHeight = HEIGHT - TOP - BOTTOM;
        double slot = plotWidth / (double) labels.size();
        double barWidth = slot * 0.62;
        StringBuilder svg = svgStart(title);
        svg.append(svgAxes(yLabel, "", 0.0, max, labels));
        List<Color> colors = palette();
        for (int i = 0; i < values.size(); i++) {
            double x = LEFT + slot * i + (slot - barWidth) / 2;
            double y = mapY(values.get(i), 0.0, max, plotHeight);
            double h = TOP + plotHeight - y;
            svg.append("<rect x=\"").append(f(x)).append("\" y=\"").append(f(y))
                    .append("\" width=\"").append(f(barWidth)).append("\" height=\"")
                    .append(f(h)).append("\" rx=\"10\" fill=\"")
                    .append(hex(colors.get(i % colors.size()))).append("\"/>\n");
        }
        svg.append("</svg>\n");
        Files.writeString(path, svg, StandardCharsets.UTF_8);
    }

    private static void writeLineSvg(Path path, String title, String xLabel,
                                     String yLabel, List<Series> series,
                                     double xMin, double xMax, double yMin,
                                     double yMax, boolean logX) throws IOException {
        int plotWidth = WIDTH - LEFT - RIGHT;
        int plotHeight = HEIGHT - TOP - BOTTOM;
        StringBuilder svg = svgStart(title);
        svg.append("<line x1=\"").append(LEFT).append("\" y1=\"").append(TOP)
                .append("\" x2=\"").append(LEFT).append("\" y2=\"")
                .append(TOP + plotHeight).append("\" stroke=\"#333\" stroke-width=\"2\"/>\n");
        svg.append("<line x1=\"").append(LEFT).append("\" y1=\"")
                .append(TOP + plotHeight).append("\" x2=\"").append(LEFT + plotWidth)
                .append("\" y2=\"").append(TOP + plotHeight)
                .append("\" stroke=\"#333\" stroke-width=\"2\"/>\n");
        for (Series item : series) {
            List<String> points = new ArrayList<>();
            for (int i = 0; i < item.x.size(); i++) {
                double xv = logX ? Math.log10(item.x.get(i)) : item.x.get(i);
                double x = LEFT + (xv - xMin) / (xMax - xMin) * plotWidth;
                double y = mapY(item.y.get(i), yMin, yMax, plotHeight);
                points.add(f(x) + "," + f(y));
            }
            svg.append("<polyline fill=\"none\" stroke=\"").append(hex(item.color))
                    .append("\" stroke-width=\"4\" points=\"")
                    .append(String.join(" ", points)).append("\"/>\n");
        }
        svg.append("<text x=\"").append(WIDTH / 2).append("\" y=\"")
                .append(HEIGHT - 45).append("\" text-anchor=\"middle\" font-size=\"31\">")
                .append(escape(xLabel)).append("</text>\n");
        svg.append("<text transform=\"translate(52 ").append(HEIGHT / 2)
                .append(") rotate(-90)\" text-anchor=\"middle\" font-size=\"31\">")
                .append(escape(yLabel)).append("</text>\n</svg>\n");
        Files.writeString(path, svg, StandardCharsets.UTF_8);
    }

    private static void writeGridSvg(Path path, String title, List<Path> imagePaths,
                                     List<String> captions, int width, int height,
                                     int margin, int titleHeight, int gap,
                                     int captionHeight, int cellWidth,
                                     int cellHeight) throws IOException {
        StringBuilder svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
                .append(width).append("\" height=\"").append(height)
                .append("\" viewBox=\"0 0 ").append(width).append(' ').append(height)
                .append("\">\n<rect width=\"100%\" height=\"100%\" fill=\"white\"/>\n");
        for (int i = 0; i < imagePaths.size(); i++) {
            int row = i / 2;
            int column = i % 2;
            int x = margin + column * (cellWidth + gap);
            int y = titleHeight + row * (cellHeight + captionHeight + gap);
            Path image = imagePaths.get(i);
            String href = path.getParent().relativize(image).toString()
                    .replace('\\', '/');
            svg.append("<image href=\"").append(escape(href)).append("\" x=\"")
                    .append(x).append("\" y=\"").append(y)
                    .append("\" width=\"").append(cellWidth)
                    .append("\" height=\"").append(cellHeight)
                    .append("\" preserveAspectRatio=\"none\"/>\n");
        }
        svg.append("</svg>\n");
        Files.writeString(path, svg, StandardCharsets.UTF_8);
    }

    private static StringBuilder svgStart(String title) {
        return new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
                .append(WIDTH).append("\" height=\"").append(HEIGHT)
                .append("\" viewBox=\"0 0 ").append(WIDTH).append(' ').append(HEIGHT)
                .append("\">\n<rect width=\"100%\" height=\"100%\" fill=\"white\"/>\n");
    }

    private static String svgAxes(String yLabel, String xLabel, double min,
                                  double max, List<String> labels) {
        int plotWidth = WIDTH - LEFT - RIGHT;
        int plotHeight = HEIGHT - TOP - BOTTOM;
        StringBuilder svg = new StringBuilder();
        for (int i = 0; i <= 5; i++) {
            int y = TOP + plotHeight - plotHeight * i / 5;
            svg.append("<line x1=\"").append(LEFT).append("\" y1=\"").append(y)
                    .append("\" x2=\"").append(LEFT + plotWidth).append("\" y2=\"")
                    .append(y).append("\" stroke=\"#dce1e6\"/>\n");
        }
        svg.append("<line x1=\"").append(LEFT).append("\" y1=\"").append(TOP)
                .append("\" x2=\"").append(LEFT).append("\" y2=\"")
                .append(TOP + plotHeight).append("\" stroke=\"#333\" stroke-width=\"2\"/>\n");
        double slot = plotWidth / (double) labels.size();
        for (int i = 0; i < labels.size(); i++) {
            svg.append("<text x=\"").append(f(LEFT + (i + 0.5) * slot))
                    .append("\" y=\"").append(TOP + plotHeight + 57)
                    .append("\" text-anchor=\"middle\" font-family=\"sans-serif\" ")
                    .append("font-size=\"27\">").append(escape(labels.get(i)))
                    .append("</text>\n");
        }
        svg.append("<text transform=\"translate(36 ").append(HEIGHT / 2)
                .append(") rotate(-90)\" text-anchor=\"middle\" font-size=\"31\">")
                .append(escape(yLabel)).append("</text>\n");
        return svg.toString();
    }

    private static String f(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String hex(Color color) {
        return "#%02x%02x%02x".formatted(color.getRed(), color.getGreen(), color.getBlue());
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
