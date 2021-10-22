package uk.gov.di.authentication.audit.lambda;

import org.apache.logging.log4j.message.ObjectMessage;
import uk.gov.di.audit.AuditPayload.AuditEvent;
import uk.gov.di.authentication.shared.services.ConfigurationService;
import uk.gov.di.authentication.shared.services.KmsConnectionService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;

public class CounterFraudAuditLambda extends BaseAuditHandler {

    private final SecretKeySpec secretKey;

    private static final String KEY_ALGORITHM = "HmacSHA256";
    private static final String MAC_ALGORITHM = "HmacSHA256";

    CounterFraudAuditLambda(
            KmsConnectionService kmsConnectionService, ConfigurationService configService) {
        super(kmsConnectionService, configService);

        secretKey = new SecretKeySpec(configService.getCounterFraudAuditSecretKey(), KEY_ALGORITHM);
    }

    public CounterFraudAuditLambda() {
        super();
        secretKey = new SecretKeySpec(configService.getCounterFraudAuditSecretKey(), KEY_ALGORITHM);
    }

    @Override
    void handleAuditEvent(AuditEvent auditEvent) {
        var eventData = new HashMap<String, String>();

        eventData.put("event-id", auditEvent.getEventId());
        eventData.put("request-id", auditEvent.getRequestId());
        eventData.put("session-id", auditEvent.getSessionId());
        eventData.put("client-id", auditEvent.getClientId());
        eventData.put("timestamp", auditEvent.getTimestamp());
        eventData.put("event-name", auditEvent.getEventName());

        AuditEvent.User user = auditEvent.getUser();
        eventData.put("user.id", hashString(user.getId()));
        eventData.put("user.email", hashString(user.getEmail()));
        eventData.put("user.phone-number", hashString(user.getPhoneNumber()));
        eventData.put("user.ip-address", user.getIpAddress());

        LOG.info(new ObjectMessage(eventData));
    }

    private String hashString(String input) {
        var hmac = makeHmac(secretKey);
        var hashedBytes = hmac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        var base64Bytes = Base64.getEncoder().encode(hashedBytes);

        return new String(base64Bytes, StandardCharsets.UTF_8);
    }

    private static Mac makeHmac(SecretKeySpec key) {
        Mac hmac;
        try {
            hmac = Mac.getInstance(MAC_ALGORITHM);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }

        try {
            hmac.init(key);
        } catch (InvalidKeyException ex) {
            throw new RuntimeException(ex);
        }

        return hmac;
    }
}
