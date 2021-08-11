package uk.gov.di.actions;

import uk.gov.di.entity.Session;
import uk.gov.di.entity.SessionState;
import uk.gov.di.services.ValidationService;

import java.util.Optional;

public class UserEnteredCodeFromPhone implements UserAction {

    private final SessionState blockedState;
    private final ValidationService validationService;
    private final String input;
    private final Optional<String> storedCode;
    private final int maxRetries;
    private final boolean isCodeBlockedForSession;

    public UserEnteredCodeFromPhone(
            SessionState blockedState,
            ValidationService validationService,
            String input,
            Optional<String> storedCode,
            int maxRetries,
            boolean isCodeBlockedForSession) {
        this.blockedState = blockedState;
        this.validationService = validationService;
        this.input = input;
        this.storedCode = storedCode;
        this.maxRetries = maxRetries;
        this.isCodeBlockedForSession = isCodeBlockedForSession;
    }

    @Override
    public SessionState evaluateNextStep(Session session) {
        if (isCodeBlockedForSession) {
            return blockedState;
        }
        return validationService.validatePhoneVerificationCode(
                storedCode, input, session, maxRetries);
    }
}
