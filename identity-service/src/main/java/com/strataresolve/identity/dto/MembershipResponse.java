package com.strataresolve.identity.dto;

import com.strataresolve.identity.domain.Membership;
import com.strataresolve.identity.domain.Role;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO representing a membership record in API responses.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipResponse {

    private UUID id;
    private UUID userId;
    private UUID propertyId;
    private UUID unitId;
    private Role role;
    private boolean active;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Instant createdAt;

    /**
     * Maps a Membership entity to its response DTO.
     */
    public static MembershipResponse fromEntity(Membership membership) {
        return MembershipResponse.builder()
                .id(membership.getId())
                .userId(membership.getUserId())
                .propertyId(membership.getPropertyId())
                .unitId(membership.getUnitId())
                .role(membership.getRole())
                .active(membership.isActive())
                .effectiveFrom(membership.getEffectiveFrom())
                .effectiveTo(membership.getEffectiveTo())
                .createdAt(membership.getCreatedAt())
                .build();
    }
}
