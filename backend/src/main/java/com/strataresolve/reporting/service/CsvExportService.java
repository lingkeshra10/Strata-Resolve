package com.strataresolve.reporting.service;

import com.strataresolve.reporting.dto.AgeingBracketEntry;
import com.strataresolve.reporting.dto.AgeingReportResponse;
import com.strataresolve.reporting.dto.SlaComplianceReportResponse;
import com.strataresolve.reporting.dto.VendorPerformanceReportResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for converting report data into CSV format.
 *
 * <p>Generates downloadable CSV content for ageing, SLA compliance,
 * and vendor performance reports.
 *
 * <p>Validates: Requirements 15.4
 */
@Service
public class CsvExportService {

    private static final String NEWLINE = "\r\n";

    /**
     * Converts an ageing report to CSV format.
     *
     * <p>CSV columns: Bracket, Label, Ticket Count, Ticket IDs
     *
     * @param report the ageing report response
     * @return CSV content as a string
     */
    public String exportAgeingReport(AgeingReportResponse report) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bracket,Label,Ticket Count,Ticket IDs").append(NEWLINE);

        for (AgeingBracketEntry entry : report.brackets()) {
            sb.append(escapeCsv(entry.bracket().name()));
            sb.append(',');
            sb.append(escapeCsv(entry.label()));
            sb.append(',');
            sb.append(entry.count());
            sb.append(',');
            sb.append(escapeCsv(joinUuids(entry.ticketIds().stream()
                    .map(Object::toString).toList())));
            sb.append(NEWLINE);
        }

        return sb.toString();
    }

    /**
     * Converts an SLA compliance report to CSV format.
     *
     * <p>The CSV contains a summary section followed by a category breakdown section.
     *
     * @param report the SLA compliance report response
     * @return CSV content as a string
     */
    public String exportSlaReport(SlaComplianceReportResponse report) {
        StringBuilder sb = new StringBuilder();

        // Summary section
        sb.append("Metric,Value").append(NEWLINE);
        sb.append("Total Tickets,").append(report.totalTickets()).append(NEWLINE);
        sb.append("Acknowledgement Compliant,").append(report.acknowledgementCompliant()).append(NEWLINE);
        sb.append("Acknowledgement Breached,").append(report.acknowledgementBreached()).append(NEWLINE);
        sb.append("Acknowledgement Compliance %,").append(formatDouble(report.acknowledgementCompliancePercent())).append(NEWLINE);
        sb.append("Resolution Compliant,").append(report.resolutionCompliant()).append(NEWLINE);
        sb.append("Resolution Breached,").append(report.resolutionBreached()).append(NEWLINE);
        sb.append("Resolution Compliance %,").append(formatDouble(report.resolutionCompliancePercent())).append(NEWLINE);
        sb.append(NEWLINE);

        // Category breakdown section
        sb.append("Category,Total Tickets,Acknowledgement Compliance %,Resolution Compliance %").append(NEWLINE);
        for (SlaComplianceReportResponse.CategoryBreakdown breakdown : report.categoryBreakdowns()) {
            sb.append(escapeCsv(breakdown.category().name()));
            sb.append(',');
            sb.append(breakdown.totalTickets());
            sb.append(',');
            sb.append(formatDouble(breakdown.acknowledgementCompliancePercent()));
            sb.append(',');
            sb.append(formatDouble(breakdown.resolutionCompliancePercent()));
            sb.append(NEWLINE);
        }

        return sb.toString();
    }

    /**
     * Converts a vendor performance report to CSV format.
     *
     * <p>CSV columns: Vendor ID, Vendor Name, Total Work Orders, Completed Work Orders,
     * Avg Resolution Time (hours), SLA Compliance %, SLA Compliant, SLA Breached
     *
     * @param report the vendor performance report response
     * @return CSV content as a string
     */
    public String exportVendorPerformanceReport(VendorPerformanceReportResponse report) {
        StringBuilder sb = new StringBuilder();
        sb.append("Vendor ID,Vendor Name,Total Work Orders,Completed Work Orders,")
                .append("Avg Resolution Time (hours),SLA Compliance %,SLA Compliant,SLA Breached")
                .append(NEWLINE);

        for (VendorPerformanceReportResponse.VendorMetrics metrics : report.vendors()) {
            sb.append(escapeCsv(metrics.vendorId().toString()));
            sb.append(',');
            sb.append(escapeCsv(metrics.vendorName()));
            sb.append(',');
            sb.append(metrics.totalWorkOrders());
            sb.append(',');
            sb.append(metrics.completedWorkOrders());
            sb.append(',');
            sb.append(formatDouble(metrics.averageResolutionTimeHours()));
            sb.append(',');
            sb.append(formatDouble(metrics.slaCompliancePercent()));
            sb.append(',');
            sb.append(metrics.slaCompliant());
            sb.append(',');
            sb.append(metrics.slaBreached());
            sb.append(NEWLINE);
        }

        return sb.toString();
    }

    /**
     * Escapes a value for inclusion in a CSV field.
     * Wraps in double quotes if the value contains commas, quotes, or newlines.
     * Double quotes within the value are escaped by doubling them.
     */
    static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Formats a double value to 2 decimal places.
     */
    static String formatDouble(double value) {
        return String.format("%.2f", value);
    }

    /**
     * Joins a list of strings with semicolons for embedding in a single CSV field.
     */
    private String joinUuids(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(";", values);
    }
}
