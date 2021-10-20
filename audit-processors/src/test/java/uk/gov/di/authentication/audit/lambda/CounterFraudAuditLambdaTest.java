package uk.gov.di.authentication.audit.lambda;

import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.google.protobuf.AbstractMessageLite;
import com.google.protobuf.ByteString;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.di.audit.AuditPayload.AuditEvent;
import uk.gov.di.audit.AuditPayload.SignedAuditEvent;
import uk.gov.di.authentication.shared.services.ConfigurationService;
import uk.gov.di.authentication.shared.services.KmsConnectionService;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CounterFraudAuditLambdaTest {

    private final KmsConnectionService kms = mock(KmsConnectionService.class);
    private final ConfigurationService config = mock(ConfigurationService.class);

    @Test
    void handlesRequestsAppropriately() {
        LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);

        Configuration configuration = loggerContext.getConfiguration();
        LoggerConfig rootLoggerConfig = configuration.getLoggerConfig("");
        Appender appender = mock(Appender.class);

        rootLoggerConfig.addAppender(appender, Level.ALL, null);

        when(config.getAuditSigningKeyAlias()).thenReturn("key_alias");
        when(kms.validateSignature(any(ByteBuffer.class), any(ByteBuffer.class), anyString()))
                .thenReturn(true);

        var handler = new CounterFraudAuditLambda(kms, config);

        var payload =
                SignedAuditEvent.newBuilder()
                        .setSignature(ByteString.copyFrom("signature".getBytes()))
                        .setPayload(
                                AuditEvent.newBuilder()
                                        .setEventId("foo")
                                        .build()
                                        .toByteString())
                        .build();

        handler.handleRequest(inputEvent(payload), null);

        ArgumentCaptor<LogEvent> auditEventArgumentCaptor =
                ArgumentCaptor.forClass(LogEvent.class);
        verify(appender).append(auditEventArgumentCaptor.capture());

        LogEvent logEvent = auditEventArgumentCaptor.getValue();

        assertThat(logEvent, hasMDCProperty("security-breach", "true"));
        assertThat(logEvent, hasFormattedMessage(expectedMessage));
    }

    private SNSEvent inputEvent(SignedAuditEvent payload) {
        return Optional.of(payload)
                .map(AbstractMessageLite::toByteArray)
                .map(Base64.getEncoder()::encodeToString)
                .map(new SNSEvent.SNS()::withMessage)
                .map(new SNSEvent.SNSRecord()::withSns)
                .map(List::of)
                .map(new SNSEvent()::withRecords)
                .get();
    }
}
