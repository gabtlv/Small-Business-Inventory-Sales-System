package com.team.inventory.service;

import com.team.inventory.dto.SalesReport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Story 7: sales report for a date range, grouped by day, week (Mon-Sun) or month.
 *
 * Grouping is done in Java rather than with SQL date functions, because MySQL (the app) and
 * H2 (the tests) name those functions differently. A small shop's sales fit in memory easily.
 */
@Service
public class SalesReportService {

    public enum Period { DAILY, WEEKLY, MONTHLY }

    /** Longest range we allow in one report. */
    static final int MAX_DAYS = 366 * 3;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE MMM d, yyyy", Locale.CANADA);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.CANADA);

    private final JdbcTemplate jdbc;

    public SalesReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static Period parsePeriod(String value) {
        if (value == null || value.isBlank()) return Period.DAILY;
        try {
            return Period.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Period must be daily, weekly or monthly.");
        }
    }

    /**
     * from/to are inclusive and optional. Defaults: to = today; from = last 7 days (daily),
     * last 8 weeks (weekly) or last 6 months (monthly).
     */
    public SalesReport report(Period period, LocalDate from, LocalDate to, LocalDate today) {
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : defaultStart(period, end);
        if (start.isAfter(end)) {
            throw ApiException.badRequest("The start date must be on or before the end date.");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_DAYS) {
            throw ApiException.badRequest("Pick a range of 3 years or less.");
        }

        // One bucket per period, in order, including empty ones
        Map<LocalDate, Bucket> buckets = new LinkedHashMap<>();
        for (LocalDate b = bucketStart(period, start); !b.isAfter(end); b = next(period, b)) {
            buckets.put(b, new Bucket());
        }

        Timestamp fromTs = Timestamp.valueOf(start.atStartOfDay());
        Timestamp toTs = Timestamp.valueOf(end.plusDays(1).atStartOfDay());

        jdbc.query("""
            SELECT s.created_at, s.total,
                   (SELECT COALESCE(SUM(si.quantity), 0) FROM sale_items si WHERE si.sale_id = s.id) AS items
            FROM sales s
            WHERE s.created_at >= ? AND s.created_at < ?
            """, rs -> {
                LocalDate day = rs.getTimestamp("created_at").toLocalDateTime().toLocalDate();
                Bucket bucket = buckets.get(bucketStart(period, day));
                bucket.sales++;
                bucket.items += rs.getLong("items");
                bucket.revenue = bucket.revenue.add(rs.getBigDecimal("total"));
            }, fromTs, toTs);

        List<SalesReport.Row> rows = new ArrayList<>();
        long saleCount = 0;
        long itemsSold = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, Bucket> e : buckets.entrySet()) {
            LocalDate bStart = e.getKey();
            LocalDate bEnd = next(period, bStart).minusDays(1);
            LocalDate rowStart = bStart.isBefore(start) ? start : bStart;
            LocalDate rowEnd = bEnd.isAfter(end) ? end : bEnd;
            Bucket b = e.getValue();
            rows.add(new SalesReport.Row(label(period, bStart), rowStart, rowEnd, b.sales, b.items, money(b.revenue)));
            saleCount += b.sales;
            itemsSold += b.items;
            revenue = revenue.add(b.revenue);
        }

        BigDecimal average = saleCount == 0
            ? money(BigDecimal.ZERO)
            : revenue.divide(BigDecimal.valueOf(saleCount), 2, RoundingMode.HALF_UP);

        List<SalesReport.TopProduct> top = jdbc.query("""
            SELECT si.product_id, p.name, SUM(si.quantity) AS qty, SUM(si.quantity * si.unit_price) AS revenue
            FROM sale_items si
            JOIN sales s ON s.id = si.sale_id
            JOIN products p ON p.id = si.product_id
            WHERE s.created_at >= ? AND s.created_at < ?
            GROUP BY si.product_id, p.name
            ORDER BY qty DESC, revenue DESC, p.name
            LIMIT 5
            """, (rs, n) -> new SalesReport.TopProduct(
                rs.getLong("product_id"), rs.getString("name"), rs.getLong("qty"), money(rs.getBigDecimal("revenue"))),
            fromTs, toTs);

        return new SalesReport(period.name().toLowerCase(Locale.ROOT), start, end, money(revenue),
            saleCount, itemsSold, average, rows, top);
    }

    private static LocalDate defaultStart(Period period, LocalDate end) {
        return switch (period) {
            case DAILY -> end.minusDays(6);
            case WEEKLY -> bucketStart(Period.WEEKLY, end).minusWeeks(7);
            case MONTHLY -> end.withDayOfMonth(1).minusMonths(5);
        };
    }

    static LocalDate bucketStart(Period period, LocalDate day) {
        return switch (period) {
            case DAILY -> day;
            case WEEKLY -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> day.withDayOfMonth(1);
        };
    }

    private static LocalDate next(Period period, LocalDate bucketStart) {
        return switch (period) {
            case DAILY -> bucketStart.plusDays(1);
            case WEEKLY -> bucketStart.plusWeeks(1);
            case MONTHLY -> bucketStart.plusMonths(1);
        };
    }

    private static String label(Period period, LocalDate bucketStart) {
        return switch (period) {
            case DAILY -> DAY.format(bucketStart);
            case WEEKLY -> "Week of " + DAY.format(bucketStart);
            case MONTHLY -> MONTH.format(bucketStart);
        };
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static final class Bucket {
        long sales;
        long items;
        BigDecimal revenue = BigDecimal.ZERO;
    }
}
