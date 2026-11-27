package com.team.inventory.service;

import com.team.inventory.dto.SalesReport;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Story 10: turns a sales report into a PDF file to print or keep as an external record.
 *
 * Same numbers as the CSV export: a summary, one row per day/week/month with a Total row, then
 * the top products. Long reports continue on more pages, with the table header repeated.
 *
 * Written with plain Java (no PDF library): a PDF is a text-like file of numbered objects plus
 * an index of where each one starts. Only the PDF's built-in Helvetica font is used, so nothing
 * needs to be embedded. Characters it can't show are replaced with "?".
 */
public final class SalesReportPdf {

    // US Letter, in points (1/72 inch)
    private static final float PAGE_WIDTH = 612;
    private static final float PAGE_HEIGHT = 792;
    private static final float MARGIN = 50;
    private static final float ROW_HEIGHT = 20;

    // Table columns: left edge of each column (text in numeric columns is right-aligned to the next edge)
    private static final float[] REPORT_COLUMNS = {MARGIN, 230, 310, 390, 440, 490, PAGE_WIDTH - MARGIN};
    private static final String[] REPORT_HEADERS = {"Period", "Start", "End", "Sales", "Items", "Revenue"};
    private static final float[] TOP_COLUMNS = {MARGIN, 400, 480, PAGE_WIDTH - MARGIN};
    private static final String[] TOP_HEADERS = {"Product", "Sold", "Revenue"};

    private static final String GREEN = "0.086 0.396 0.204";     // #166534, the app's accent
    private static final String HEADER_FILL = "0.902 0.957 0.918"; // #e6f4ea
    private static final String RULE = "0.894 0.922 0.902";        // #e4ebe6
    private static final String MUTED = "0.365 0.420 0.384";       // #5d6b62

    private final List<StringBuilder> pages = new ArrayList<>();
    private StringBuilder page;
    private float y;

    private SalesReportPdf() {
    }

    public static byte[] toPdf(SalesReport report, LocalDate generatedOn) {
        SalesReportPdf pdf = new SalesReportPdf();
        pdf.draw(report, generatedOn);
        return pdf.build();
    }

    // ---------- Layout ----------

    private void draw(SalesReport report, LocalDate generatedOn) {
        newPage();
        text(MARGIN, y, "Helvetica-Bold", 22, GREEN, "Sales report");
        y -= 22;
        text(MARGIN, y, "Helvetica", 11, MUTED,
            capitalize(report.period()) + " totals, " + report.from() + " to " + report.to()
                + "   |   Generated " + generatedOn);
        y -= 34;

        // Summary: four numbers side by side
        String[][] summary = {
            {"Total revenue", money(report.totalRevenue())},
            {"Number of sales", String.valueOf(report.saleCount())},
            {"Items sold", String.valueOf(report.itemsSold())},
            {"Average sale", money(report.averageSale())},
        };
        float boxWidth = (PAGE_WIDTH - 2 * MARGIN - 3 * 10) / 4;
        for (int i = 0; i < summary.length; i++) {
            float x = MARGIN + i * (boxWidth + 10);
            rectangle(x, y - 46, boxWidth, 56, null, RULE);
            text(x + 10, y - 6, "Helvetica", 9, MUTED, summary[i][0]);
            text(x + 10, y - 30, "Helvetica-Bold", 16, "0 0 0", summary[i][1]);
        }
        y -= 80;

        // One row per day / week / month, then the total
        sectionTitle("Sales by " + periodWord(report.period()));
        tableHeader(REPORT_COLUMNS, REPORT_HEADERS);
        for (SalesReport.Row row : report.rows()) {
            if (needsNewPage()) {
                newPage();
                tableHeader(REPORT_COLUMNS, REPORT_HEADERS);
            }
            tableRow(REPORT_COLUMNS, false, row.label(), String.valueOf(row.start()), String.valueOf(row.end()),
                String.valueOf(row.saleCount()), String.valueOf(row.itemsSold()), money(row.revenue()));
        }
        if (needsNewPage()) {
            newPage();
            tableHeader(REPORT_COLUMNS, REPORT_HEADERS);
        }
        tableRow(REPORT_COLUMNS, true, "Total", String.valueOf(report.from()), String.valueOf(report.to()),
            String.valueOf(report.saleCount()), String.valueOf(report.itemsSold()), money(report.totalRevenue()));

        // Best sellers
        y -= 24;
        if (y - 3 * ROW_HEIGHT < MARGIN + 20) newPage();
        sectionTitle("Top products");
        if (report.topProducts().isEmpty()) {
            text(MARGIN, y - 4, "Helvetica", 10, MUTED, "No sales in this range.");
        } else {
            tableHeader(TOP_COLUMNS, TOP_HEADERS);
            for (SalesReport.TopProduct p : report.topProducts()) {
                if (needsNewPage()) {
                    newPage();
                    tableHeader(TOP_COLUMNS, TOP_HEADERS);
                }
                tableRow(TOP_COLUMNS, false, p.name(), String.valueOf(p.quantitySold()), money(p.revenue()));
            }
        }
    }

    private void sectionTitle(String title) {
        text(MARGIN, y, "Helvetica-Bold", 13, "0 0 0", title);
        y -= 12;
    }

    private void tableHeader(float[] columns, String[] headers) {
        rectangle(MARGIN, y - ROW_HEIGHT, PAGE_WIDTH - 2 * MARGIN, ROW_HEIGHT, HEADER_FILL, null);
        cells(columns, "Helvetica-Bold", GREEN, headers);
        y -= ROW_HEIGHT;
    }

    private void tableRow(float[] columns, boolean bold, String... values) {
        if (bold) rectangle(MARGIN, y - ROW_HEIGHT, PAGE_WIDTH - 2 * MARGIN, ROW_HEIGHT, HEADER_FILL, null);
        cells(columns, bold ? "Helvetica-Bold" : "Helvetica", "0 0 0", values);
        y -= ROW_HEIGHT;
        line(MARGIN, y, PAGE_WIDTH - MARGIN, y, RULE);
    }

    /** First column left-aligned (cut to fit); the others right-aligned, like numbers in a spreadsheet. */
    private void cells(float[] columns, String font, String color, String... values) {
        float baseline = y - ROW_HEIGHT + 6.5f;
        for (int i = 0; i < values.length; i++) {
            if (i == 0) {
                float room = columns[1] - columns[0] - 16;
                text(columns[0] + 6, baseline, font, 10, color, fit(values[0], font, 10, room));
            } else {
                float right = columns[i + 1] - 6;
                text(right - width(values[i], font, 10), baseline, font, 10, color, values[i]);
            }
        }
    }

    private boolean needsNewPage() {
        return y - ROW_HEIGHT < MARGIN + 20;   // keep room for the page number
    }

    private void newPage() {
        page = new StringBuilder();
        pages.add(page);
        y = PAGE_HEIGHT - MARGIN - 10;
    }

    // ---------- Drawing commands (PDF content stream operators) ----------

    private void text(float x, float yPos, String font, float size, String rgb, String value) {
        page.append("BT /").append(font.equals("Helvetica-Bold") ? "F2" : "F1").append(' ')
            .append(num(size)).append(" Tf ").append(rgb).append(" rg ")
            .append(num(x)).append(' ').append(num(yPos)).append(" Td (")
            .append(escape(value)).append(") Tj ET\n");
    }

    private void rectangle(float x, float yPos, float w, float h, String fill, String stroke) {
        if (fill != null) {
            page.append(fill).append(" rg ").append(num(x)).append(' ').append(num(yPos)).append(' ')
                .append(num(w)).append(' ').append(num(h)).append(" re f\n");
        }
        if (stroke != null) {
            page.append(stroke).append(" RG 0.8 w ").append(num(x)).append(' ').append(num(yPos)).append(' ')
                .append(num(w)).append(' ').append(num(h)).append(" re S\n");
        }
    }

    private void line(float x1, float y1, float x2, float y2, String rgb) {
        page.append(rgb).append(" RG 0.6 w ").append(num(x1)).append(' ').append(num(y1)).append(" m ")
            .append(num(x2)).append(' ').append(num(y2)).append(" l S\n");
    }

    // ---------- Building the file ----------

    private byte[] build() {
        int pageCount = pages.size();
        // Object numbers: 1 catalog, 2 page tree, 3-4 fonts, then (page, content) pairs
        List<String> objects = new ArrayList<>();
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageCount; i++) {
            kids.append(5 + 2 * i).append(" 0 R ");
        }
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [" + kids.toString().trim() + "] /Count " + pageCount + " >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
        for (int i = 0; i < pageCount; i++) {
            StringBuilder content = pages.get(i);
            String footer = "Page " + (i + 1) + " of " + pageCount;
            page = content;
            text(PAGE_WIDTH - MARGIN - width(footer, "Helvetica", 9), MARGIN - 10, "Helvetica", 9, MUTED, footer);
            text(MARGIN, MARGIN - 10, "Helvetica", 9, MUTED, "Inventory System");

            byte[] stream = content.toString().getBytes(StandardCharsets.ISO_8859_1);
            objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + num(PAGE_WIDTH) + " " + num(PAGE_HEIGHT) + "]"
                + " /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents " + (6 + 2 * i) + " 0 R >>");
            objects.add("<< /Length " + stream.length + " >>\nstream\n" + content + "endstream");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n%âãÏÓ\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        int xref = out.size();
        StringBuilder index = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            index.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        }
        index.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
            .append(xref).append("\n%%EOF\n");
        write(out, index.toString());
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    // ---------- Text helpers ----------

    /** Escapes ( ) \ for a PDF string; characters outside Latin-1 (which the font can't show) become "?". */
    static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') out.append('\\').append(c);
            else if (c < 32) out.append(' ');
            else if (c > 255) out.append('?');
            else out.append(c);
        }
        return out.toString();
    }

    /** Shortens text with "..." so it fits in the given width. */
    private static String fit(String value, String font, float size, float room) {
        if (width(value, font, size) <= room) return value;
        String cut = value;
        while (!cut.isEmpty() && width(cut + "...", font, size) > room) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    /** Approximate text width in points, using Helvetica's character widths (per 1000 units). */
    static float width(String value, String font, float size) {
        boolean bold = font.equals("Helvetica-Bold");
        float units = 0;
        for (char c : value.toCharArray()) {
            units += charWidth(c, bold);
        }
        return units * size / 1000f;
    }

    private static int charWidth(char c, boolean bold) {
        if (c >= '0' && c <= '9' || c == '$') return 556;
        if (c == ' ') return 278;
        if (c == ',' || c == '.') return 278;
        if (c == '-') return 333;
        if (c == '(' || c == ')') return 333;
        if (c == 'i' || c == 'l' || c == 'j' || c == '\'' || c == '|') return bold ? 278 : 222;
        if (c == 'm' || c == 'w') return 833;
        if (c == 'M' || c == 'W') return 900;
        if (c >= 'A' && c <= 'Z') return bold ? 722 : 667;
        return bold ? 611 : 556;
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "$0.00" : String.format(Locale.CANADA, "$%,.2f", amount);
    }

    private static String num(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) return "";
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String periodWord(String period) {
        return switch (period == null ? "" : period.toLowerCase(Locale.ROOT)) {
            case "weekly" -> "week";
            case "monthly" -> "month";
            default -> "day";
        };
    }
}
