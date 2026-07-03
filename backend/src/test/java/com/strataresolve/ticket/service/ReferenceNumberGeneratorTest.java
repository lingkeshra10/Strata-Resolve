package com.strataresolve.ticket.service;

import com.strataresolve.ticket.domain.ReferenceNumberSequence;
import com.strataresolve.ticket.repository.ReferenceNumberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Year;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceNumberGeneratorTest {

    @Mock
    private ReferenceNumberRepository referenceNumberRepository;

    private ReferenceNumberGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ReferenceNumberGenerator(referenceNumberRepository);
    }

    @Test
    void generateReferenceNumber_withExistingSequence_returnsNextNumber() {
        int currentYear = Year.now().getValue();
        ReferenceNumberSequence sequence = new ReferenceNumberSequence(currentYear, 5);

        when(referenceNumberRepository.findByYearForUpdate(currentYear))
                .thenReturn(Optional.of(sequence));

        String result = generator.generateReferenceNumber();

        assertThat(result).isEqualTo(String.format("SR-%d-%06d", currentYear, 6));
        assertThat(sequence.getLastNumber()).isEqualTo(6);
    }

    @Test
    void generateReferenceNumber_withNoExistingSequence_createsNewAndReturnsFirst() {
        int currentYear = Year.now().getValue();
        ReferenceNumberSequence newSequence = new ReferenceNumberSequence(currentYear, 0);

        when(referenceNumberRepository.findByYearForUpdate(currentYear))
                .thenReturn(Optional.empty());
        when(referenceNumberRepository.save(any(ReferenceNumberSequence.class)))
                .thenReturn(newSequence);

        String result = generator.generateReferenceNumber();

        assertThat(result).isEqualTo(String.format("SR-%d-%06d", currentYear, 1));

        ArgumentCaptor<ReferenceNumberSequence> captor =
                ArgumentCaptor.forClass(ReferenceNumberSequence.class);
        verify(referenceNumberRepository).save(captor.capture());
        assertThat(captor.getValue().getYear()).isEqualTo(currentYear);
        assertThat(captor.getValue().getLastNumber()).isEqualTo(0);
    }

    @Test
    void generateReferenceNumber_format_matchesSrYyyyNnnnnn() {
        int currentYear = Year.now().getValue();
        ReferenceNumberSequence sequence = new ReferenceNumberSequence(currentYear, 0);

        when(referenceNumberRepository.findByYearForUpdate(currentYear))
                .thenReturn(Optional.of(sequence));

        String result = generator.generateReferenceNumber();

        assertThat(result).matches("SR-\\d{4}-\\d{6}");
        assertThat(result).startsWith("SR-" + currentYear + "-");
    }

    @Test
    void generateReferenceNumber_zeroPads_sequentialNumber() {
        int currentYear = Year.now().getValue();
        ReferenceNumberSequence sequence = new ReferenceNumberSequence(currentYear, 0);

        when(referenceNumberRepository.findByYearForUpdate(currentYear))
                .thenReturn(Optional.of(sequence));

        String result = generator.generateReferenceNumber();

        // First number should be zero-padded to 6 digits
        assertThat(result).endsWith("000001");
    }

    @Test
    void generateReferenceNumber_largeNumber_formatsCorrectly() {
        int currentYear = Year.now().getValue();
        ReferenceNumberSequence sequence = new ReferenceNumberSequence(currentYear, 999998);

        when(referenceNumberRepository.findByYearForUpdate(currentYear))
                .thenReturn(Optional.of(sequence));

        String result = generator.generateReferenceNumber();

        assertThat(result).isEqualTo(String.format("SR-%d-999999", currentYear));
    }

    @Test
    void generateReferenceNumber_consecutiveCalls_producesSequentialNumbers() {
        int currentYear = Year.now().getValue();
        ReferenceNumberSequence sequence = new ReferenceNumberSequence(currentYear, 10);

        when(referenceNumberRepository.findByYearForUpdate(currentYear))
                .thenReturn(Optional.of(sequence));

        String first = generator.generateReferenceNumber();
        String second = generator.generateReferenceNumber();

        assertThat(first).isEqualTo(String.format("SR-%d-%06d", currentYear, 11));
        assertThat(second).isEqualTo(String.format("SR-%d-%06d", currentYear, 12));
    }
}
