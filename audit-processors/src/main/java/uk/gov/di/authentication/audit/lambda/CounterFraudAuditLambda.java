package uk.gov.di.authentication.audit.lambda;

import org.slf4j.MDC;
import uk.gov.di.audit.AuditPayload.AuditEvent;
import uk.gov.di.authentication.shared.services.ConfigurationService;
import uk.gov.di.authentication.shared.services.KmsConnectionService;

public class CounterFraudAuditLambda extends BaseAuditHandler {

    CounterFraudAuditLambda(
            KmsConnectionService kmsConnectionService, ConfigurationService service) {
        super(kmsConnectionService, service);
    }

    CounterFraudAuditLambda() {
        super();
    }

    @Override
    void handleAuditEvent(AuditEvent auditEvent) {
        MDC.put("event-id", auditEvent.getEventId());
        MDC.put("request-id", auditEvent.getRequestId());
        MDC.put("session-id", auditEvent.getSessionId());
        MDC.put("client-id", auditEvent.getClientId());
        MDC.put("timestamp", auditEvent.getTimestamp());
        MDC.put("event-name", auditEvent.getEventName());

        AuditEvent.User user = auditEvent.getUser();
        // TODO - hash other field from the user object and include them too.
        MDC.put("user.ip-address", user.getIpAddress());

        LOG.info("Logging audit event");

        MDC.clear();
    }
}
