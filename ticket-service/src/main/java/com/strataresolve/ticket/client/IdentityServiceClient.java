package com.strataresolve.ticket.client;

import com.strataresolve.ticket.client.model.Membership;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class IdentityServiceClient {

    private final RestClient restClient;

    private static final Logger logger = LoggerFactory.getLogger(IdentityServiceClient.class);

    public IdentityServiceClient(RestClient.Builder restClientBuilder,
                                 @Value("${services.identity.base-url:http://localhost:8080}") String identityBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(identityBaseUrl).build();
    }

    /**
     * Returns all active memberships belonging to a user
     * for a specific property.
     */
    public List<Membership> findActiveMemberships(UUID userId, UUID propertyId) {
        logger.debug("Retrieving active memberships for user={} property={}", userId, propertyId);

        Membership[] response = restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/internal/memberships/active")
                        .queryParam("userId", userId)
                        .queryParam("propertyId", propertyId)
                        .build())
                .retrieve()
                .body(Membership[].class);

        if (response == null) {
            return List.of();
        }

        return Arrays.asList(response);
    }
}