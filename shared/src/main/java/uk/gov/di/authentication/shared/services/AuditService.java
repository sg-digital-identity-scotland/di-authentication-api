package uk.gov.di.authentication.shared.services;

import uk.gov.di.audit.AuditPayload.AuditEvent;
import uk.gov.di.audit.AuditPayload.SignedAuditEvent;
import uk.gov.di.authentication.shared.domain.AuditableEvent;

import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public class AuditService {

    private final Clock clock;
    private final SnsService snsService;

    public AuditService(Clock clock, SnsService snsService) {
        this.clock = clock;
        this.snsService = snsService;
    }

    public AuditService() {
        this.clock = Clock.systemUTC();
        this.snsService = new SnsService(new ConfigurationService());
    }

    public void submitAuditEvent(AuditableEvent event, MetadataPair... metadataPairs) {
        snsService.publishAuditMessage(generateLogLine(event, metadataPairs));
    }

    String generateLogLine(AuditableEvent eventEnum, MetadataPair... metadataPairs) {
        var timestamp = clock.instant().toString();

        var eventBuilder = AuditEvent.newBuilder();
        eventBuilder.setEventName(eventEnum.toString());
        eventBuilder.setTimestamp(timestamp);
        eventBuilder.setEventId(UUID.randomUUID().toString());

        Arrays.stream(metadataPairs)
                .forEach(
                        metadataPair -> {
                            switch (metadataPair.key) {
                                case "clientId":
                                    {
                                        eventBuilder.setClientId(metadataPair.value.toString());
                                        break;
                                    }
                                case "requestId":
                                    {
                                        eventBuilder.setRequestId(metadataPair.value.toString());
                                        break;
                                    }
                                default:
                                    {
                                        // TODO: Two possible choices here.
                                        // Either we should never have an unrecognised key, in which
                                        // case we should throw an exception (or just not have a
                                        // default case).
                                        // Or, we add the data to the builder in some generic way,
                                        // maybe via setField or setUnknownFields.  I don't
                                        // understand this though...
                                        break;
                                    }
                            }
                        });

        var signedEventBuilder = SignedAuditEvent.newBuilder();
        signedEventBuilder.setPayload(eventBuilder.build().toByteString());
        // TODO - We need to sign the event at this point, but we don't yet have the infrastructure
        // in place to do that.

        return new String(signedEventBuilder.build().toByteArray());
    }

    public static class MetadataPair {
        private final String key;
        private final Object value;

        private MetadataPair(String key, Object value) {
            this.key = key;
            this.value = value;
        }

        public static MetadataPair pair(String key, Object value) {
            return new MetadataPair(key, value);
        }

        @Override
        public String toString() {
            return String.format("[%s: %s]", key, value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MetadataPair that = (MetadataPair) o;
            return Objects.equals(key, that.key) && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, value);
        }
    }
}
