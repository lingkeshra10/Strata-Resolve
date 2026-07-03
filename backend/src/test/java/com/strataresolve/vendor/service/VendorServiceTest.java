package com.strataresolve.vendor.service;

import com.strataresolve.property.repository.PropertyRepository;
import com.strataresolve.shared.event.DomainEventPublisher;
import com.strataresolve.shared.event.PropertyConfigChangedEvent;
import com.strataresolve.shared.exception.BusinessRuleViolationException;
import com.strataresolve.shared.exception.DuplicateResourceException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.vendor.domain.Vendor;
import com.strataresolve.vendor.dto.CreateVendorRequest;
import com.strataresolve.vendor.dto.UpdateVendorRequest;
import com.strataresolve.vendor.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendorServiceTest {

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    private VendorService vendorService;

    private static final UUID PROPERTY_ID = UUID.randomUUID();
    private static final UUID VENDOR_ID = UUID.randomUUID();
    private static final UUID ACTING_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        vendorService = new VendorService(vendorRepository, propertyRepository, eventPublisher);
    }

    // --- register tests ---

    @Test
    void register_withValidRequest_createsVendor() {
        CreateVendorRequest request = new CreateVendorRequest(
                "ABC Plumbing", "abc@example.com", "+60123456789"
        );

        when(propertyRepository.existsById(PROPERTY_ID)).thenReturn(true);
        when(vendorRepository.existsByPropertyIdAndName(PROPERTY_ID, "ABC Plumbing")).thenReturn(false);
        when(vendorRepository.save(any(Vendor.class))).thenAnswer(invocation -> {
            Vendor v = invocation.getArgument(0);
            v.setId(VENDOR_ID);
            return v;
        });

        Vendor result = vendorService.register(PROPERTY_ID, request, ACTING_USER_ID);

        assertThat(result.getName()).isEqualTo("ABC Plumbing");
        assertThat(result.getContactEmail()).isEqualTo("abc@example.com");
        assertThat(result.getContactPhone()).isEqualTo("+60123456789");
        assertThat(result.getPropertyId()).isEqualTo(PROPERTY_ID);
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void register_publishesDomainEvent() {
        CreateVendorRequest request = new CreateVendorRequest(
                "ABC Plumbing", "abc@example.com", null
        );

        when(propertyRepository.existsById(PROPERTY_ID)).thenReturn(true);
        when(vendorRepository.existsByPropertyIdAndName(PROPERTY_ID, "ABC Plumbing")).thenReturn(false);
        when(vendorRepository.save(any(Vendor.class))).thenAnswer(invocation -> {
            Vendor v = invocation.getArgument(0);
            v.setId(VENDOR_ID);
            return v;
        });

        vendorService.register(PROPERTY_ID, request, ACTING_USER_ID);

        ArgumentCaptor<PropertyConfigChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(PropertyConfigChangedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());

        PropertyConfigChangedEvent event = eventCaptor.getValue();
        assertThat(event.getEntityType()).isEqualTo("Vendor");
        assertThat(event.getAction()).isEqualTo("CREATED");
        assertThat(event.getActingUserId()).isEqualTo(ACTING_USER_ID);
        assertThat(event.getPropertyId()).isEqualTo(PROPERTY_ID);
    }

    @Test
    void register_withNonExistentProperty_throwsException() {
        CreateVendorRequest request = new CreateVendorRequest(
                "ABC Plumbing", "abc@example.com", null
        );

        when(propertyRepository.existsById(PROPERTY_ID)).thenReturn(false);

        assertThatThrownBy(() -> vendorService.register(PROPERTY_ID, request, ACTING_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Property");

        verify(vendorRepository, never()).save(any());
    }

    @Test
    void register_withDuplicateName_throwsException() {
        CreateVendorRequest request = new CreateVendorRequest(
                "ABC Plumbing", "abc@example.com", null
        );

        when(propertyRepository.existsById(PROPERTY_ID)).thenReturn(true);
        when(vendorRepository.existsByPropertyIdAndName(PROPERTY_ID, "ABC Plumbing")).thenReturn(true);

        assertThatThrownBy(() -> vendorService.register(PROPERTY_ID, request, ACTING_USER_ID))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("ABC Plumbing");

        verify(vendorRepository, never()).save(any());
    }

    // --- update tests ---

    @Test
    void update_withValidRequest_updatesVendor() {
        Vendor existingVendor = createVendor("Old Name", "old@example.com", "+60100000000", true);
        UpdateVendorRequest request = new UpdateVendorRequest(
                "New Name", "new@example.com", "+60199999999"
        );

        when(vendorRepository.findByIdAndPropertyId(VENDOR_ID, PROPERTY_ID))
                .thenReturn(Optional.of(existingVendor));
        when(vendorRepository.existsByPropertyIdAndName(PROPERTY_ID, "New Name")).thenReturn(false);
        when(vendorRepository.save(any(Vendor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Vendor result = vendorService.update(VENDOR_ID, PROPERTY_ID, request, ACTING_USER_ID);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getContactEmail()).isEqualTo("new@example.com");
        assertThat(result.getContactPhone()).isEqualTo("+60199999999");
    }

    @Test
    void update_withSameName_doesNotCheckDuplicate() {
        Vendor existingVendor = createVendor("Same Name", "abc@example.com", null, true);
        UpdateVendorRequest request = new UpdateVendorRequest(
                "Same Name", "newemail@example.com", "+60199999999"
        );

        when(vendorRepository.findByIdAndPropertyId(VENDOR_ID, PROPERTY_ID))
                .thenReturn(Optional.of(existingVendor));
        when(vendorRepository.save(any(Vendor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Vendor result = vendorService.update(VENDOR_ID, PROPERTY_ID, request, ACTING_USER_ID);

        assertThat(result.getContactEmail()).isEqualTo("newemail@example.com");
        // existsByPropertyIdAndName should NOT be called since name didn't change
        verify(vendorRepository, never()).existsByPropertyIdAndName(any(), any());
    }

    @Test
    void update_withDuplicateName_throwsException() {
        Vendor existingVendor = createVendor("Old Name", "abc@example.com", null, true);
        UpdateVendorRequest request = new UpdateVendorRequest(
                "Existing Vendor", "abc@example.com", null
        );

        when(vendorRepository.findByIdAndPropertyId(VENDOR_ID, PROPERTY_ID))
                .thenReturn(Optional.of(existingVendor));
        when(vendorRepository.existsByPropertyIdAndName(PROPERTY_ID, "Existing Vendor")).thenReturn(true);

        assertThatThrownBy(() -> vendorService.update(VENDOR_ID, PROPERTY_ID, request, ACTING_USER_ID))
                .isInstanceOf(DuplicateResourceException.class);

        verify(vendorRepository, never()).save(any());
    }

    @Test
    void update_withNonExistentVendor_throwsException() {
        UpdateVendorRequest request = new UpdateVendorRequest("Name", "a@b.com", null);

        when(vendorRepository.findByIdAndPropertyId(VENDOR_ID, PROPERTY_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> vendorService.update(VENDOR_ID, PROPERTY_ID, request, ACTING_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Vendor");
    }

    // --- deactivate tests ---

    @Test
    void deactivate_withActiveVendor_deactivates() {
        Vendor vendor = createVendor("ABC Plumbing", "abc@example.com", null, true);

        when(vendorRepository.findByIdAndPropertyId(VENDOR_ID, PROPERTY_ID))
                .thenReturn(Optional.of(vendor));
        when(vendorRepository.save(any(Vendor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Vendor result = vendorService.deactivate(VENDOR_ID, PROPERTY_ID, ACTING_USER_ID);

        assertThat(result.isActive()).isFalse();
    }

    @Test
    void deactivate_withAlreadyInactiveVendor_throwsException() {
        Vendor vendor = createVendor("ABC Plumbing", "abc@example.com", null, false);

        when(vendorRepository.findByIdAndPropertyId(VENDOR_ID, PROPERTY_ID))
                .thenReturn(Optional.of(vendor));

        assertThatThrownBy(() -> vendorService.deactivate(VENDOR_ID, PROPERTY_ID, ACTING_USER_ID))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("already inactive");

        verify(vendorRepository, never()).save(any());
    }

    // --- activate tests ---

    @Test
    void activate_withInactiveVendor_activates() {
        Vendor vendor = createVendor("ABC Plumbing", "abc@example.com", null, false);

        when(vendorRepository.findByIdAndPropertyId(VENDOR_ID, PROPERTY_ID))
                .thenReturn(Optional.of(vendor));
        when(vendorRepository.save(any(Vendor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Vendor result = vendorService.activate(VENDOR_ID, PROPERTY_ID, ACTING_USER_ID);

        assertThat(result.isActive()).isTrue();
    }

    @Test
    void activate_withAlreadyActiveVendor_throwsException() {
        Vendor vendor = createVendor("ABC Plumbing", "abc@example.com", null, true);

        when(vendorRepository.findByIdAndPropertyId(VENDOR_ID, PROPERTY_ID))
                .thenReturn(Optional.of(vendor));

        assertThatThrownBy(() -> vendorService.activate(VENDOR_ID, PROPERTY_ID, ACTING_USER_ID))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("already active");

        verify(vendorRepository, never()).save(any());
    }

    // --- findById tests ---

    @Test
    void findById_withExistingVendor_returnsVendor() {
        Vendor vendor = createVendor("ABC Plumbing", "abc@example.com", null, true);

        when(vendorRepository.findByIdAndPropertyId(VENDOR_ID, PROPERTY_ID))
                .thenReturn(Optional.of(vendor));

        Vendor result = vendorService.findById(VENDOR_ID, PROPERTY_ID);

        assertThat(result.getName()).isEqualTo("ABC Plumbing");
    }

    @Test
    void findById_withNonExistentVendor_throwsException() {
        when(vendorRepository.findByIdAndPropertyId(VENDOR_ID, PROPERTY_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> vendorService.findById(VENDOR_ID, PROPERTY_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Vendor");
    }

    // --- findByPropertyId tests ---

    @Test
    void findByPropertyId_returnsAllVendors() {
        Vendor v1 = createVendor("Vendor A", "a@example.com", null, true);
        Vendor v2 = createVendor("Vendor B", "b@example.com", null, false);

        when(vendorRepository.findByPropertyId(PROPERTY_ID)).thenReturn(List.of(v1, v2));

        List<Vendor> result = vendorService.findByPropertyId(PROPERTY_ID);

        assertThat(result).hasSize(2);
    }

    @Test
    void findActiveByPropertyId_returnsOnlyActiveVendors() {
        Vendor v1 = createVendor("Vendor A", "a@example.com", null, true);

        when(vendorRepository.findByPropertyIdAndIsActiveTrue(PROPERTY_ID)).thenReturn(List.of(v1));

        List<Vendor> result = vendorService.findActiveByPropertyId(PROPERTY_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isActive()).isTrue();
    }

    // --- delete tests ---

    @Test
    void delete_withExistingVendor_deletesAndPublishesEvent() {
        Vendor vendor = createVendor("ABC Plumbing", "abc@example.com", null, true);

        when(vendorRepository.findByIdAndPropertyId(VENDOR_ID, PROPERTY_ID))
                .thenReturn(Optional.of(vendor));

        vendorService.delete(VENDOR_ID, PROPERTY_ID, ACTING_USER_ID);

        verify(vendorRepository).delete(vendor);

        ArgumentCaptor<PropertyConfigChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(PropertyConfigChangedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());

        PropertyConfigChangedEvent event = eventCaptor.getValue();
        assertThat(event.getEntityType()).isEqualTo("Vendor");
        assertThat(event.getAction()).isEqualTo("DELETED");
        assertThat(event.getNewValue()).isNull();
    }

    @Test
    void delete_withNonExistentVendor_throwsException() {
        when(vendorRepository.findByIdAndPropertyId(VENDOR_ID, PROPERTY_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> vendorService.delete(VENDOR_ID, PROPERTY_ID, ACTING_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Vendor");

        verify(vendorRepository, never()).delete(any(Vendor.class));
    }

    // --- helper methods ---

    private Vendor createVendor(String name, String email, String phone, boolean active) {
        Vendor vendor = Vendor.builder()
                .name(name)
                .contactEmail(email)
                .contactPhone(phone)
                .isActive(active)
                .build();
        vendor.setId(VENDOR_ID);
        vendor.setPropertyId(PROPERTY_ID);
        return vendor;
    }
}
