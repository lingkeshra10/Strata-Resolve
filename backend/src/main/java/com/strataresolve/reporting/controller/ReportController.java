package com.strataresolve.reporting.controller;

import com.strataresolve.reporting.dto.AgeingReportResponse;
import com.strataresolve.reporting.dto.SlaComplianceReportResponse;
import com.strataresolve.reporting.dto.VendorPerformanceReportResponse;
import com.strataresolve.reporting.service.AgeingReportService;
import com.strataresolve.reporting.service.CsvExportService;
import com.strataresolve.reporting.service.SlaReportService;
import com.strataresolve.reporting.service.VendorPerformanceService;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * REST controller for generating reports.
 *
 * <p>Provides endpoints for ageing, SLA compliance, and vendor performance
 * reports in both JSON and CSV formats.
 *
 * <p>Access is restricted to Property Managers and Committee Members
 * with active Membership for the target Property.
 *
 * <p>Validates: Requirements 15.4, 15.5
 */
@RestController
@RequestMapping("/api/properties/{propertyId}/reports")
@PreAuthorize("hasAnyRole('PROPERTY_MANAGER', 'COMMITTEE_MEMBER')")
public class ReportController {

    private static final MediaType TEXT_CSV = MediaType.parseMediaType("text/csv");

    private final AgeingReportService ageingReportService;
    private final SlaReportService slaReportService;
    private final VendorPerformanceService vendorPerformanceService;
    private final CsvExportService csvExportService;

    public ReportController(AgeingReportService ageingReportService,
                            SlaReportService slaReportService,
                            VendorPerformanceService vendorPerformanceService,
                            CsvExportService csvExportService) {
        this.ageingReportService = ageingReportService;
        this.slaReportService = slaReportService;
        this.vendorPerformanceService = vendorPerformanceService;
        this.csvExportService = csvExportService;
    }

    // ========== Ageing Report ==========

    /**
     * Generates an ageing report as JSON.
     *
     * @param propertyId     the property to report on
     * @param authentication the authenticated user
     * @return the ageing report with tickets grouped by age brackets
     */
    @GetMapping("/ageing")
    public ResponseEntity<AgeingReportResponse> getAgeingReport(
            @PathVariable UUID propertyId,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        AgeingReportResponse report = ageingReportService.generateReport(propertyId, userId);
        return ResponseEntity.ok(report);
    }

    /**
     * Generates an ageing report as a downloadable CSV file.
     *
     * @param propertyId     the property to report on
     * @param authentication the authenticated user
     * @return CSV file content with Content-Disposition header
     */
    @GetMapping("/ageing/csv")
    public ResponseEntity<byte[]> getAgeingReportCsv(
            @PathVariable UUID propertyId,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        AgeingReportResponse report = ageingReportService.generateReport(propertyId, userId);
        String csv = csvExportService.exportAgeingReport(report);
        return buildCsvResponse(csv, "ageing-report.csv");
    }

    // ========== SLA Compliance Report ==========

    /**
     * Generates an SLA compliance report as JSON.
     *
     * @param propertyId the property to report on
     * @param from       optional start of date range (ISO-8601 instant)
     * @param to         optional end of date range (ISO-8601 instant)
     * @param category   optional category filter
     * @param priority   optional priority filter
     * @return the SLA compliance report
     */
    @GetMapping("/sla")
    public ResponseEntity<SlaComplianceReportResponse> getSlaReport(
            @PathVariable UUID propertyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) Priority priority) {
        SlaComplianceReportResponse report = slaReportService.generateReport(
                propertyId, from, to, category, priority);
        return ResponseEntity.ok(report);
    }

    /**
     * Generates an SLA compliance report as a downloadable CSV file.
     *
     * @param propertyId the property to report on
     * @param from       optional start of date range (ISO-8601 instant)
     * @param to         optional end of date range (ISO-8601 instant)
     * @param category   optional category filter
     * @param priority   optional priority filter
     * @return CSV file content with Content-Disposition header
     */
    @GetMapping("/sla/csv")
    public ResponseEntity<byte[]> getSlaReportCsv(
            @PathVariable UUID propertyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) Priority priority) {
        SlaComplianceReportResponse report = slaReportService.generateReport(
                propertyId, from, to, category, priority);
        String csv = csvExportService.exportSlaReport(report);
        return buildCsvResponse(csv, "sla-compliance-report.csv");
    }

    // ========== Vendor Performance Report ==========

    /**
     * Generates a vendor performance report as JSON.
     *
     * @param propertyId the property to report on
     * @return the vendor performance report with per-vendor metrics
     */
    @GetMapping("/vendor-performance")
    public ResponseEntity<VendorPerformanceReportResponse> getVendorPerformanceReport(
            @PathVariable UUID propertyId) {
        VendorPerformanceReportResponse report = vendorPerformanceService.generateReport(propertyId);
        return ResponseEntity.ok(report);
    }

    /**
     * Generates a vendor performance report as a downloadable CSV file.
     *
     * @param propertyId the property to report on
     * @return CSV file content with Content-Disposition header
     */
    @GetMapping("/vendor-performance/csv")
    public ResponseEntity<byte[]> getVendorPerformanceReportCsv(
            @PathVariable UUID propertyId) {
        VendorPerformanceReportResponse report = vendorPerformanceService.generateReport(propertyId);
        String csv = csvExportService.exportVendorPerformanceReport(report);
        return buildCsvResponse(csv, "vendor-performance-report.csv");
    }

    // ========== Helper ==========

    /**
     * Builds a ResponseEntity for CSV download with appropriate headers.
     */
    private ResponseEntity<byte[]> buildCsvResponse(String csvContent, String filename) {
        byte[] bytes = csvContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentLength(bytes.length)
                .body(bytes);
    }
}
