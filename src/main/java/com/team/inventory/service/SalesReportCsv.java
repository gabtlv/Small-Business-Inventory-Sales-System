package com.team.inventory.service;

import com.team.inventory.dto.SalesReport;

/**
 * Story 10: turns a sales report into CSV text (opens in Excel / Google Sheets).
 *
 * One header row, one row per day/week/month, then a Total row. Fields that contain a comma,
 * a quote or a line break are wrapped in quotes (labels like "Mon Oct 12, 2026" have a comma).
 */
public final class SalesReportCsv {

    private static final String NEWLINE = "\r\n";

    private SalesReportCsv() {
    }

    public static String toCsv(SalesReport report) {
        StringBuilder out = new StringBuilder();
        line(out, "Period", "Start", "End", "Sales", "Items sold", "Revenue");
        for (SalesReport.Row row : report.rows()) {
            line(out, row.label(), row.start(), row.end(), row.saleCount(), row.itemsSold(), row.revenue());
        }
        line(out, "Total", report.from(), report.to(), report.saleCount(), report.itemsSold(), report.totalRevenue());
        return out.toString();
    }

    private static void line(StringBuilder out, Object... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) out.append(',');
            out.append(escape(String.valueOf(fields[i])));
        }
        out.append(NEWLINE);
    }

    static String escape(String value) {
        boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        return needsQuotes ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
