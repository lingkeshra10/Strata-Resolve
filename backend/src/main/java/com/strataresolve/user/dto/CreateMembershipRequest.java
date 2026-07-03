package com.strataresolve.user.dto;

import com.strataresolve.user.domain.Role;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for creating a new membership (role assignment) linking a user to a property.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMembershipRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Property ID is required")
    private UUID propertyId;

    @NotNull(message = "Role is required")
    private Role role;

    /**
     * Optional unit ID for resident roles (RESIDENT_OWNER, RESIDENT_TENANT).
     */
    private UUID unitId;

    /**
     * The date from which this membership is effective. Defaults to today if not provided.
     */
    private LocalDate effectiveFrom;
}
