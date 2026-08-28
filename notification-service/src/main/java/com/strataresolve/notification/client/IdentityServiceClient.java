package com.strataresolve.notification.client;

import com.strataresolve.notification.model.NotificationRecipient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class IdentityServiceClient {

    private final RestClient restClient;

    public IdentityServiceClient(RestClient.Builder restClientBuilder,
                                 @Value("${services.identity.base-url:http://localhost:8080}") String identityBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(identityBaseUrl).build();
    }

    public Optional<NotificationRecipient> findUser(UUID userId) {

        NotificationRecipient recipient = restClient.get().uri("/api/internal/users/{userId}", userId).retrieve().body(NotificationRecipient.class);

        return Optional.ofNullable(recipient);
    }

    public List<NotificationRecipient> findPropertyManagers(UUID propertyId) {

        NotificationRecipient[] recipients = restClient.get().uri("/api/internal/properties/{propertyId}/managers", propertyId).retrieve().body(NotificationRecipient[].class);

        if (recipients == null) {
            return List.of();
        }

        return Arrays.asList(recipients);
    }
}
