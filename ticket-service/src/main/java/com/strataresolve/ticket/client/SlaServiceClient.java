package com.strataresolve.ticket.client;

import com.strataresolve.ticket.client.model.SlaCalculationRequest;
import com.strataresolve.ticket.client.model.SlaTargets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class SlaServiceClient {

    private final RestClient restClient;

    private static final Logger logger = LoggerFactory.getLogger(PropertyServiceClient.class);

    public SlaServiceClient(RestClient.Builder restClientBuilder,
                                 @Value("${services.identity.base-url:http://localhost:8080}") String slaBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(slaBaseUrl).build();
    }

    /**
     * Calculates SLA acknowledgement and resolution targets
     * for the supplied property, category and priority.
     *
     * @param propertyId property context
     * @param timezone property timezone
     * @param category ticket category
     * @param priority ticket priority
     * @return calculated SLA targets
     */
    public SlaTargets calculateTargets(UUID propertyId, String timezone,
                                       String category, String priority) {

        logger.debug("Calculating SLA targets for property={}, category={}, priority={}", propertyId,
                category, priority);

        SlaCalculationRequest request = new SlaCalculationRequest(propertyId, timezone,
                category, priority);

        SlaTargets response = restClient
                        .post()
                        .uri("/api/internal/sla/targets")
                        .body(request)
                        .retrieve()
                        .body(SlaTargets.class);

        if (response == null) {
            throw new IllegalStateException(
                    "SLA Service returned an empty response while calculating SLA targets"
            );
        }

        return response;
    }
}
