package com.strataresolve.ticket.client;

import com.strataresolve.ticket.client.model.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

@Component
public class PropertyServiceClient {

    private final RestClient restClient;

    private static final Logger logger = LoggerFactory.getLogger(PropertyServiceClient.class);

    public PropertyServiceClient(RestClient.Builder restClientBuilder,
                                 @Value("${services.identity.base-url:http://localhost:8080}") String propertyBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(propertyBaseUrl).build();
    }

    /**
     * Retrieves a property from Property Service.
     *
     * @param propertyId property identifier
     * @return property information if the property exists
     */
    public Optional<Property> findById(UUID propertyId) {
        logger.debug("Retrieving property {} from Property Service", propertyId);
        try {

            Property response =
                    restClient
                            .get()
                            .uri("/api/internal/properties/{propertyId}", propertyId)
                            .retrieve()
                            .body(Property.class);

            return Optional.ofNullable(response);

        } catch (HttpClientErrorException.NotFound ex) {
            logger.debug("Property {} was not found in Property Service", propertyId);
            return Optional.empty();
        }
    }

}
