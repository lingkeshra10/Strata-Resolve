package com.strataresolve.reporting.controller;

import com.strataresolve.reporting.dto.AgeBracket;
import com.strataresolve.reporting.dto.AgeingBracketEntry;
import com.strataresolve.reporting.dto.AgeingReportResponse;
import com.strataresolve.reporting.dto.SlaComplianceReportResponse;
import com.strataresolve.reporting.dto.VendorPerformanceReportResponse;
import com.strataresolve.reporting.dto.VendorPerformanceReportResponse.VendorMetrics;
import com.strataresolve.reporting.service.AgeingReportService;
import com.strataresolve.reporting.service.CsvExportService;
import com.strataresolve.reporting.service.SlaReportService;
import com.strataresolve.reporting.service.VendorPerformanceService;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReportController}.
 * Tests endpoint logic, response structure, and CSV download headers.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportController")
class ReportControllerTest {

    @Mock
    private AgeingReportService ageingReportService;

    @Mock
    private SlaReportService slaReportService;

    @Mock
    private VendorPerformanceService vendorPerformanceService;

    @Mock
    private CsvExportService csvExportService;

    private ReportController reportController;

    private final UUID propertyId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        reportController = new ReportController(
                ageingReportService, slaReportService,
                vendorPerformanceService, csvExportService);
        authentication = new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
    }

    // ========== Ageing Report JSON ==========

    @Test
    @DisplayName("getAgeingReport should return ageing report as JSON")
    void getAgeingReport_shouldReturnJsonReport() {
        AgeingReportResponse report = createAgeingReport();
        when(ageingReportService.generateReport(propertyId, userId)).thenReturn(report);

        ResponseEntity<AgeingReportResponse> response = reportController.getAgeingReport(propertyId, authentication);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(report);
        assertThat(response.getBody().totalOpenTickets()).isEqualTo(0);
        verify(ageingReportService).generateReport(propertyId, userId);
    }

    // ========== Ageing Report CSV ==========

    @Test
    @DisplayName("getAgeingReportCsv should return CSV with correct content type and disposition")
    void getAgeingReportCsv_shouldReturnCsvWithCorrectHeaders() {
        AgeingReportResponse report = createAgeingReport();
        when(ageingReportService.generateReport(propertyId, userId)).thenReturn(report);
        when(csvExportService.exportAgeingReport(report)).thenReturn("Bracket,Label,Ticket Count,Ticket IDs\r\n");

        ResponseEntity<byte[]> response = reportController.getAgeingReportCsv(propertyId, authentication);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType().toString()).contains("text/csv");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"ageing-report.csv\"");
        assertThat(new String(response.getBody())).isEqualTo("Bracket,Label,Ticket Count,Ticket IDs\r\n");
    }

    // ========== SLA Report JSON ==========

    @Test
    @DisplayName("getSlaReport should return SLA compliance report as JSON")
    void getSlaReport_shouldReturnJsonReport() {
        SlaComplianceReportResponse report = createSlaReport();
        when(slaReportService.generateReport(propertyId, null, null, null, null)).thenReturn(report);

        ResponseEntity<SlaComplianceReportResponse> response = reportController.getSlaReport(
                propertyId, null, null, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(report);
        assertThat(response.getBody().totalTickets()).isEqualTo(5);
    }

    @Test
    @DisplayName("getSlaReport should pass filters to service")
    void getSlaReport_shouldPassFilters() {
        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-31T23:59:59Z");
        SlaComplianceReportResponse report = createSlaReport();
        when(slaReportService.generateReport(propertyId, from, to, Category.PLUMBING, Priority.HIGH))
                .thenReturn(report);

        ResponseEntity<SlaComplianceReportResponse> response = reportController.getSlaReport(
                propertyId, from, to, Category.PLUMBING, Priority.HIGH);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(slaReportService).generateReport(propertyId, from, to, Category.PLUMBING, Priority.HIGH);
    }

    // ========== SLA Report CSV ==========

    @Test
    @DisplayName("getSlaReportCsv should return CSV with correct headers")
    void getSlaReportCsv_shouldReturnCsvWithCorrectHeaders() {
        SlaComplianceReportResponse report = createSlaReport();
        when(slaReportService.generateReport(propertyId, null, null, null, null)).thenReturn(report);
        when(csvExportService.exportSlaReport(report)).thenReturn("Metric,Value\r\n");

        ResponseEntity<byte[]> response = reportController.getSlaReportCsv(
                propertyId, null, null, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType().toString()).contains("text/csv");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"sla-compliance-report.csv\"");
    }

    // ========== Vendor Performance Report JSON ==========

    @Test
    @DisplayName("getVendorPerformanceReport should return vendor metrics as JSON")
    void getVendorPerformanceReport_shouldReturnJsonReport() {
        VendorPerformanceReportResponse report = createVendorReport();
        when(vendorPerformanceService.generateReport(propertyId)).thenReturn(report);

        ResponseEntity<VendorPerformanceReportResponse> response =
                reportController.getVendorPerformanceReport(propertyId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(report);
        assertThat(response.getBody().totalVendors()).isEqualTo(1);
    }

    // ========== Vendor Performance Report CSV ==========

    @Test
    @DisplayName("getVendorPerformanceReportCsv should return CSV with correct headers")
    void getVendorPerformanceReportCsv_shouldReturnCsvWithCorrectHeaders() {
        VendorPerformanceReportResponse report = createVendorReport();
        when(vendorPerformanceService.generateReport(propertyId)).thenReturn(report);
        when(csvExportService.exportVendorPerformanceReport(report)).thenReturn("Vendor ID,Vendor Name\r\n");

        ResponseEntity<byte[]> response = reportController.getVendorPerformanceReportCsv(propertyId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType().toString()).contains("text/csv");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"vendor-performance-report.csv\"");
    }

    // ========== CSV Response Structure ==========

    @Test
    @DisplayName("CSV responses should include Content-Length header")
    void csvResponse_shouldIncludeContentLength() {
        AgeingReportResponse report = createAgeingReport();
        String csvContent = "test,csv,data\r\n";
        when(ageingReportService.generateReport(propertyId, userId)).thenReturn(report);
        when(csvExportService.exportAgeingReport(report)).thenReturn(csvContent);

        ResponseEntity<byte[]> response = reportController.getAgeingReportCsv(propertyId, authentication);

        assertThat(response.getHeaders().getContentLength()).isEqualTo(csvContent.getBytes().length);
    }

    // ========== Helpers ==========

    private AgeingReportResponse createAgeingReport() {
        List<AgeingBracketEntry> entries = List.of(
                AgeingBracketEntry.of(AgeBracket.ZERO_TO_THREE, List.of()),
                AgeingBracketEntry.of(AgeBracket.FOUR_TO_SEVEN, List.of()),
                AgeingBracketEntry.of(AgeBracket.EIGHT_TO_FOURTEEN, List.of()),
                AgeingBracketEntry.of(AgeBracket.FIFTEEN_TO_THIRTY, List.of()),
                AgeingBracketEntry.of(AgeBracket.OVER_THIRTY, List.of())
        );
        return new AgeingReportResponse(propertyId, Instant.now(), 0, entries);
    }

    private SlaComplianceReportResponse createSlaReport() {
        return new SlaComplianceReportResponse(
                null, null, null, null,
                5, 4, 1, 80.0,
                3, 2, 60.0,
                List.of()
        );
    }

    private VendorPerformanceReportResponse createVendorReport() {
        VendorMetrics metrics = new VendorMetrics(
                UUID.randomUUID(), "Test Vendor", 10, 8, 24.0, 90.0, 9, 1);
        return new VendorPerformanceReportResponse(propertyId, 1, List.of(metrics));
    }
}
