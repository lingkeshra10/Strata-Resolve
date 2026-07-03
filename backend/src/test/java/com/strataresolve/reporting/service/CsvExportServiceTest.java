package com.strataresolve.reporting.service;

import com.strataresolve.reporting.dto.AgeBracket;
import com.strataresolve.reporting.dto.AgeingBracketEntry;
import com.strataresolve.reporting.dto.AgeingReportResponse;
import com.strataresolve.reporting.dto.SlaComplianceReportResponse;
import com.strataresolve.reporting.dto.SlaComplianceReportResponse.CategoryBreakdown;
import com.strataresolve.reporting.dto.VendorPerformanceReportResponse;
import com.strataresolve.reporting.dto.VendorPerformanceReportResponse.VendorMetrics;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CsvExportService}.
 * Validates CSV generation for ageing, SLA, and vendor performance reports.
 */
class CsvExportServiceTest {

    private CsvExportService csvExportService;

    @BeforeEach
    void setUp() {
        csvExportService = new CsvExportService();
    }

    // ========== Ageing Report CSV ==========

    @Test
    void exportAgeingReport_shouldContainHeaderRow() {
        AgeingReportResponse report = createEmptyAgeingReport();
        String csv = csvExportService.exportAgeingReport(report);

        String[] lines = csv.split("\r\n");
        assertThat(lines[0]).isEqualTo("Bracket,Label,Ticket Count,Ticket IDs");
    }

    @Test
    void exportAgeingReport_shouldContainAllBrackets() {
        AgeingReportResponse report = createEmptyAgeingReport();
        String csv = csvExportService.exportAgeingReport(report);

        String[] lines = csv.split("\r\n");
        // Header + 5 brackets
        assertThat(lines).hasSize(6);
        assertThat(lines[1]).startsWith("ZERO_TO_THREE,0-3 days,0,");
        assertThat(lines[2]).startsWith("FOUR_TO_SEVEN,4-7 days,0,");
        assertThat(lines[3]).startsWith("EIGHT_TO_FOURTEEN,8-14 days,0,");
        assertThat(lines[4]).startsWith("FIFTEEN_TO_THIRTY,15-30 days,0,");
        assertThat(lines[5]).startsWith("OVER_THIRTY,Over 30 days,0,");
    }

    @Test
    void exportAgeingReport_shouldIncludeTicketIds() {
        UUID ticketId1 = UUID.randomUUID();
        UUID ticketId2 = UUID.randomUUID();

        List<AgeingBracketEntry> entries = List.of(
                AgeingBracketEntry.of(AgeBracket.ZERO_TO_THREE, List.of(ticketId1, ticketId2)),
                AgeingBracketEntry.of(AgeBracket.FOUR_TO_SEVEN, List.of()),
                AgeingBracketEntry.of(AgeBracket.EIGHT_TO_FOURTEEN, List.of()),
                AgeingBracketEntry.of(AgeBracket.FIFTEEN_TO_THIRTY, List.of()),
                AgeingBracketEntry.of(AgeBracket.OVER_THIRTY, List.of())
        );

        AgeingReportResponse report = new AgeingReportResponse(
                UUID.randomUUID(), Instant.now(), 2, entries);

        String csv = csvExportService.exportAgeingReport(report);
        String[] lines = csv.split("\r\n");

        // The ticket IDs should be semicolon-separated in the last field
        String expectedIds = ticketId1 + ";" + ticketId2;
        assertThat(lines[1]).contains("2");
        assertThat(lines[1]).contains(expectedIds);
    }

    // ========== SLA Report CSV ==========

    @Test
    void exportSlaReport_shouldContainSummarySection() {
        SlaComplianceReportResponse report = new SlaComplianceReportResponse(
                null, null, null, null,
                10, 8, 2, 80.0,
                7, 3, 70.0,
                List.of()
        );

        String csv = csvExportService.exportSlaReport(report);
        String[] lines = csv.split("\r\n");

        assertThat(lines[0]).isEqualTo("Metric,Value");
        assertThat(lines[1]).isEqualTo("Total Tickets,10");
        assertThat(lines[2]).isEqualTo("Acknowledgement Compliant,8");
        assertThat(lines[3]).isEqualTo("Acknowledgement Breached,2");
        assertThat(lines[4]).isEqualTo("Acknowledgement Compliance %,80.00");
        assertThat(lines[5]).isEqualTo("Resolution Compliant,7");
        assertThat(lines[6]).isEqualTo("Resolution Breached,3");
        assertThat(lines[7]).isEqualTo("Resolution Compliance %,70.00");
    }

    @Test
    void exportSlaReport_shouldContainCategoryBreakdownSection() {
        List<CategoryBreakdown> breakdowns = List.of(
                new CategoryBreakdown(Category.PLUMBING, 5, 90.0, 80.0),
                new CategoryBreakdown(Category.ELECTRICAL, 3, 100.0, 66.67)
        );

        SlaComplianceReportResponse report = new SlaComplianceReportResponse(
                null, null, null, null,
                8, 7, 1, 87.5,
                6, 2, 75.0,
                breakdowns
        );

        String csv = csvExportService.exportSlaReport(report);
        String[] lines = csv.split("\r\n");

        // Find the category breakdown header
        int breakdownHeaderIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("Category,Total Tickets")) {
                breakdownHeaderIdx = i;
                break;
            }
        }

        assertThat(breakdownHeaderIdx).isGreaterThan(0);
        assertThat(lines[breakdownHeaderIdx + 1]).isEqualTo("PLUMBING,5,90.00,80.00");
        assertThat(lines[breakdownHeaderIdx + 2]).isEqualTo("ELECTRICAL,3,100.00,66.67");
    }

    // ========== Vendor Performance Report CSV ==========

    @Test
    void exportVendorPerformanceReport_shouldContainHeader() {
        VendorPerformanceReportResponse report = new VendorPerformanceReportResponse(
                UUID.randomUUID(), 0, List.of());

        String csv = csvExportService.exportVendorPerformanceReport(report);
        String[] lines = csv.split("\r\n");

        assertThat(lines[0]).isEqualTo(
                "Vendor ID,Vendor Name,Total Work Orders,Completed Work Orders," +
                "Avg Resolution Time (hours),SLA Compliance %,SLA Compliant,SLA Breached");
    }

    @Test
    void exportVendorPerformanceReport_shouldContainVendorData() {
        UUID vendorId = UUID.randomUUID();
        VendorMetrics metrics = new VendorMetrics(
                vendorId, "Acme Plumbing", 10, 8, 24.5, 90.0, 9, 1);

        VendorPerformanceReportResponse report = new VendorPerformanceReportResponse(
                UUID.randomUUID(), 1, List.of(metrics));

        String csv = csvExportService.exportVendorPerformanceReport(report);
        String[] lines = csv.split("\r\n");

        assertThat(lines).hasSize(2); // header + 1 data row
        assertThat(lines[1]).contains(vendorId.toString());
        assertThat(lines[1]).contains("Acme Plumbing");
        assertThat(lines[1]).contains("10");
        assertThat(lines[1]).contains("8");
        assertThat(lines[1]).contains("24.50");
        assertThat(lines[1]).contains("90.00");
        assertThat(lines[1]).contains("9");
        assertThat(lines[1]).contains("1");
    }

    @Test
    void exportVendorPerformanceReport_shouldEscapeVendorNameWithComma() {
        UUID vendorId = UUID.randomUUID();
        VendorMetrics metrics = new VendorMetrics(
                vendorId, "Smith, Jones & Partners", 5, 3, 12.0, 100.0, 5, 0);

        VendorPerformanceReportResponse report = new VendorPerformanceReportResponse(
                UUID.randomUUID(), 1, List.of(metrics));

        String csv = csvExportService.exportVendorPerformanceReport(report);

        // The vendor name with comma should be enclosed in double quotes
        assertThat(csv).contains("\"Smith, Jones & Partners\"");
    }

    // ========== CSV Escaping ==========

    @Test
    void escapeCsv_shouldReturnValueUnchangedWhenNoSpecialChars() {
        assertThat(CsvExportService.escapeCsv("simple text")).isEqualTo("simple text");
    }

    @Test
    void escapeCsv_shouldWrapInQuotesWhenContainsComma() {
        assertThat(CsvExportService.escapeCsv("one,two")).isEqualTo("\"one,two\"");
    }

    @Test
    void escapeCsv_shouldWrapInQuotesAndDoubleQuotesWhenContainsQuotes() {
        assertThat(CsvExportService.escapeCsv("say \"hello\"")).isEqualTo("\"say \"\"hello\"\"\"");
    }

    @Test
    void escapeCsv_shouldWrapInQuotesWhenContainsNewline() {
        assertThat(CsvExportService.escapeCsv("line1\nline2")).isEqualTo("\"line1\nline2\"");
    }

    @Test
    void escapeCsv_shouldReturnEmptyStringForNull() {
        assertThat(CsvExportService.escapeCsv(null)).isEqualTo("");
    }

    // ========== Helper Methods ==========

    private AgeingReportResponse createEmptyAgeingReport() {
        List<AgeingBracketEntry> entries = List.of(
                AgeingBracketEntry.of(AgeBracket.ZERO_TO_THREE, List.of()),
                AgeingBracketEntry.of(AgeBracket.FOUR_TO_SEVEN, List.of()),
                AgeingBracketEntry.of(AgeBracket.EIGHT_TO_FOURTEEN, List.of()),
                AgeingBracketEntry.of(AgeBracket.FIFTEEN_TO_THIRTY, List.of()),
                AgeingBracketEntry.of(AgeBracket.OVER_THIRTY, List.of())
        );
        return new AgeingReportResponse(UUID.randomUUID(), Instant.now(), 0, entries);
    }
}
