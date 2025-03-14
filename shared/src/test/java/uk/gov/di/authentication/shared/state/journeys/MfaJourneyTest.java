package uk.gov.di.authentication.shared.state.journeys;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.di.authentication.shared.entity.ClientRegistry;
import uk.gov.di.authentication.shared.entity.ClientSession;
import uk.gov.di.authentication.shared.entity.CredentialTrustLevel;
import uk.gov.di.authentication.shared.entity.Session;
import uk.gov.di.authentication.shared.entity.SessionAction;
import uk.gov.di.authentication.shared.entity.SessionState;
import uk.gov.di.authentication.shared.entity.UserProfile;
import uk.gov.di.authentication.shared.entity.VectorOfTrust;
import uk.gov.di.authentication.shared.helpers.IdGenerator;
import uk.gov.di.authentication.shared.services.ConfigurationService;
import uk.gov.di.authentication.shared.state.StateMachine;
import uk.gov.di.authentication.shared.state.UserContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.di.authentication.shared.entity.SessionAction.SYSTEM_HAS_ISSUED_AUTHORIZATION_CODE;
import static uk.gov.di.authentication.shared.entity.SessionAction.SYSTEM_HAS_SENT_MFA_CODE;
import static uk.gov.di.authentication.shared.entity.SessionAction.SYSTEM_HAS_SENT_TOO_MANY_MFA_CODES;
import static uk.gov.di.authentication.shared.entity.SessionAction.SYSTEM_IS_BLOCKED_FROM_SENDING_ANY_MFA_VERIFICATION_CODES;
import static uk.gov.di.authentication.shared.entity.SessionAction.USER_ENTERED_INVALID_MFA_CODE;
import static uk.gov.di.authentication.shared.entity.SessionAction.USER_ENTERED_INVALID_MFA_CODE_TOO_MANY_TIMES;
import static uk.gov.di.authentication.shared.entity.SessionAction.USER_ENTERED_REGISTERED_EMAIL_ADDRESS;
import static uk.gov.di.authentication.shared.entity.SessionAction.USER_ENTERED_UNREGISTERED_EMAIL_ADDRESS;
import static uk.gov.di.authentication.shared.entity.SessionAction.USER_ENTERED_VALID_CREDENTIALS;
import static uk.gov.di.authentication.shared.entity.SessionAction.USER_ENTERED_VALID_MFA_CODE;
import static uk.gov.di.authentication.shared.entity.SessionState.AUTHENTICATED;
import static uk.gov.di.authentication.shared.entity.SessionState.AUTHENTICATION_REQUIRED;
import static uk.gov.di.authentication.shared.entity.SessionState.LOGGED_IN;
import static uk.gov.di.authentication.shared.entity.SessionState.MFA_CODE_MAX_RETRIES_REACHED;
import static uk.gov.di.authentication.shared.entity.SessionState.MFA_CODE_NOT_VALID;
import static uk.gov.di.authentication.shared.entity.SessionState.MFA_CODE_REQUESTS_BLOCKED;
import static uk.gov.di.authentication.shared.entity.SessionState.MFA_CODE_VERIFIED;
import static uk.gov.di.authentication.shared.entity.SessionState.MFA_SMS_CODE_SENT;
import static uk.gov.di.authentication.shared.entity.SessionState.MFA_SMS_MAX_CODES_SENT;
import static uk.gov.di.authentication.shared.entity.SessionState.NEW;
import static uk.gov.di.authentication.shared.entity.SessionState.USER_NOT_FOUND;
import static uk.gov.di.authentication.shared.state.StateMachineJourneyTest.CLIENT_ID;
import static uk.gov.di.authentication.shared.state.StateMachineJourneyTest.generateAuthRequest;
import static uk.gov.di.authentication.shared.state.StateMachineJourneyTest.generateUserProfile;

public class MfaJourneyTest {
    private final ConfigurationService mockConfigurationService = mock(ConfigurationService.class);

    private final Session session = new Session(IdGenerator.generate());

    private StateMachine<SessionState, SessionAction, UserContext> stateMachine;

    @BeforeEach
    void setup() {
        when(mockConfigurationService.getTermsAndConditionsVersion()).thenReturn("1.0");

        stateMachine = StateMachine.userJourneyStateMachine(mockConfigurationService);
    }

    @Test
    public void testCanSignInWithMfa() {
        UserProfile userProfile =
                generateUserProfile(
                        true,
                        "1.0",
                        new HashSet<String>(
                                Arrays.asList(
                                        "phone_number",
                                        "phone_number_verified",
                                        "email",
                                        "email_verified",
                                        "sub")));

        UserContext userContext =
                UserContext.builder(
                                session.setCurrentCredentialStrength(
                                        CredentialTrustLevel.LOW_LEVEL))
                        .withClientSession(
                                new ClientSession(
                                                generateAuthRequest("Cl.Cm").toParameters(),
                                                null,
                                                null)
                                        .setEffectiveVectorOfTrust(VectorOfTrust.getDefaults()))
                        .withUserProfile(userProfile)
                        .withClient(new ClientRegistry().setClientID(CLIENT_ID.toString()))
                        .build();

        List<JourneyTransition> transitions =
                Arrays.asList(
                        new JourneyTransition(
                                userContext,
                                USER_ENTERED_REGISTERED_EMAIL_ADDRESS,
                                AUTHENTICATION_REQUIRED),
                        new JourneyTransition(
                                userContext, USER_ENTERED_VALID_CREDENTIALS, LOGGED_IN),
                        new JourneyTransition(
                                userContext, SYSTEM_HAS_SENT_MFA_CODE, MFA_SMS_CODE_SENT),
                        new JourneyTransition(
                                userContext, USER_ENTERED_VALID_MFA_CODE, MFA_CODE_VERIFIED),
                        new JourneyTransition(
                                userContext, SYSTEM_HAS_ISSUED_AUTHORIZATION_CODE, AUTHENTICATED));

        SessionState currentState = NEW;

        for (JourneyTransition transition : transitions) {
            currentState =
                    stateMachine.transition(
                            currentState,
                            transition.getSessionAction(),
                            transition.getUserContext());
            assertThat(currentState, equalTo(transition.getExpectedSessionState()));
        }
    }

    @Test
    public void testCanReachMfaBlockedStatusIfTooManyCodesAreRequested() {
        UserProfile userProfile =
                generateUserProfile(
                        true,
                        "1.0",
                        new HashSet<String>(
                                Arrays.asList(
                                        "phone_number",
                                        "phone_number_verified",
                                        "email",
                                        "email_verified",
                                        "sub")));

        UserContext userContext =
                UserContext.builder(
                                session.setCurrentCredentialStrength(
                                        CredentialTrustLevel.LOW_LEVEL))
                        .withClientSession(
                                new ClientSession(
                                                generateAuthRequest("Cl.Cm").toParameters(),
                                                null,
                                                null)
                                        .setEffectiveVectorOfTrust(VectorOfTrust.getDefaults()))
                        .withUserProfile(userProfile)
                        .withClient(new ClientRegistry().setClientID(CLIENT_ID.toString()))
                        .build();

        List<JourneyTransition> transitions =
                Arrays.asList(
                        new JourneyTransition(
                                userContext,
                                USER_ENTERED_REGISTERED_EMAIL_ADDRESS,
                                AUTHENTICATION_REQUIRED),
                        new JourneyTransition(
                                userContext, USER_ENTERED_VALID_CREDENTIALS, LOGGED_IN),
                        new JourneyTransition(
                                userContext, SYSTEM_HAS_SENT_MFA_CODE, MFA_SMS_CODE_SENT),
                        new JourneyTransition(
                                userContext,
                                SYSTEM_HAS_SENT_TOO_MANY_MFA_CODES,
                                MFA_SMS_MAX_CODES_SENT),
                        new JourneyTransition(
                                userContext,
                                SYSTEM_IS_BLOCKED_FROM_SENDING_ANY_MFA_VERIFICATION_CODES,
                                MFA_CODE_REQUESTS_BLOCKED));

        SessionState currentState = NEW;

        for (JourneyTransition transition : transitions) {
            currentState =
                    stateMachine.transition(
                            currentState,
                            transition.getSessionAction(),
                            transition.getUserContext());
            assertThat(currentState, equalTo(transition.getExpectedSessionState()));
        }
    }

    @Test
    public void testCanReachMfaBlockedByRequestingTooManyCodesButEnterANewEmailAndStartAgain() {
        UserProfile userProfile =
                generateUserProfile(
                        true,
                        "1.0",
                        new HashSet<String>(
                                Arrays.asList(
                                        "phone_number",
                                        "phone_number_verified",
                                        "email",
                                        "email_verified",
                                        "sub")));

        UserContext userContext =
                UserContext.builder(
                                session.setCurrentCredentialStrength(
                                        CredentialTrustLevel.LOW_LEVEL))
                        .withClientSession(
                                new ClientSession(
                                                generateAuthRequest("Cl.Cm").toParameters(),
                                                null,
                                                null)
                                        .setEffectiveVectorOfTrust(VectorOfTrust.getDefaults()))
                        .withUserProfile(userProfile)
                        .withClient(new ClientRegistry().setClientID(CLIENT_ID.toString()))
                        .build();

        List<JourneyTransition> transitions =
                Arrays.asList(
                        new JourneyTransition(
                                userContext,
                                USER_ENTERED_REGISTERED_EMAIL_ADDRESS,
                                AUTHENTICATION_REQUIRED),
                        new JourneyTransition(
                                userContext, USER_ENTERED_VALID_CREDENTIALS, LOGGED_IN),
                        new JourneyTransition(
                                userContext, SYSTEM_HAS_SENT_MFA_CODE, MFA_SMS_CODE_SENT),
                        new JourneyTransition(
                                userContext,
                                SYSTEM_HAS_SENT_TOO_MANY_MFA_CODES,
                                MFA_SMS_MAX_CODES_SENT),
                        new JourneyTransition(
                                userContext,
                                USER_ENTERED_UNREGISTERED_EMAIL_ADDRESS,
                                USER_NOT_FOUND),
                        new JourneyTransition(
                                userContext,
                                USER_ENTERED_REGISTERED_EMAIL_ADDRESS,
                                AUTHENTICATION_REQUIRED),
                        new JourneyTransition(
                                userContext, USER_ENTERED_VALID_CREDENTIALS, LOGGED_IN));

        SessionState currentState = NEW;

        for (JourneyTransition transition : transitions) {
            currentState =
                    stateMachine.transition(
                            currentState,
                            transition.getSessionAction(),
                            transition.getUserContext());
            assertThat(currentState, equalTo(transition.getExpectedSessionState()));
        }
    }

    @Test
    public void testCanReachMfaBlockedStatusIfTooManyIncorrectAttemptsAreMade() {
        UserProfile userProfile =
                generateUserProfile(
                        true,
                        "1.0",
                        new HashSet<String>(
                                Arrays.asList(
                                        "phone_number",
                                        "phone_number_verified",
                                        "email",
                                        "email_verified",
                                        "sub")));

        UserContext userContext =
                UserContext.builder(
                                session.setCurrentCredentialStrength(
                                        CredentialTrustLevel.LOW_LEVEL))
                        .withClientSession(
                                new ClientSession(
                                                generateAuthRequest("Cl.Cm").toParameters(),
                                                null,
                                                null)
                                        .setEffectiveVectorOfTrust(VectorOfTrust.getDefaults()))
                        .withUserProfile(userProfile)
                        .withClient(new ClientRegistry().setClientID(CLIENT_ID.toString()))
                        .build();

        List<JourneyTransition> transitions =
                Arrays.asList(
                        new JourneyTransition(
                                userContext,
                                USER_ENTERED_REGISTERED_EMAIL_ADDRESS,
                                AUTHENTICATION_REQUIRED),
                        new JourneyTransition(
                                userContext, USER_ENTERED_VALID_CREDENTIALS, LOGGED_IN),
                        new JourneyTransition(
                                userContext, SYSTEM_HAS_SENT_MFA_CODE, MFA_SMS_CODE_SENT),
                        new JourneyTransition(
                                userContext, USER_ENTERED_INVALID_MFA_CODE, MFA_CODE_NOT_VALID),
                        new JourneyTransition(
                                userContext,
                                USER_ENTERED_INVALID_MFA_CODE_TOO_MANY_TIMES,
                                MFA_CODE_MAX_RETRIES_REACHED));

        SessionState currentState = NEW;

        for (JourneyTransition transition : transitions) {
            currentState =
                    stateMachine.transition(
                            currentState,
                            transition.getSessionAction(),
                            transition.getUserContext());
            assertThat(currentState, equalTo(transition.getExpectedSessionState()));
        }
    }

    @Test
    public void testCanReachMfaBlockedByIncorrectCodeTooManyTimesAndEnterANewEmailAndStartAgain() {
        UserProfile userProfile =
                generateUserProfile(
                        true,
                        "1.0",
                        new HashSet<String>(
                                Arrays.asList(
                                        "phone_number",
                                        "phone_number_verified",
                                        "email",
                                        "email_verified",
                                        "sub")));

        UserContext userContext =
                UserContext.builder(
                                session.setCurrentCredentialStrength(
                                        CredentialTrustLevel.LOW_LEVEL))
                        .withClientSession(
                                new ClientSession(
                                                generateAuthRequest("Cl.Cm").toParameters(),
                                                null,
                                                null)
                                        .setEffectiveVectorOfTrust(VectorOfTrust.getDefaults()))
                        .withUserProfile(userProfile)
                        .withClient(new ClientRegistry().setClientID(CLIENT_ID.toString()))
                        .build();

        List<JourneyTransition> transitions =
                Arrays.asList(
                        new JourneyTransition(
                                userContext,
                                USER_ENTERED_REGISTERED_EMAIL_ADDRESS,
                                AUTHENTICATION_REQUIRED),
                        new JourneyTransition(
                                userContext, USER_ENTERED_VALID_CREDENTIALS, LOGGED_IN),
                        new JourneyTransition(
                                userContext, SYSTEM_HAS_SENT_MFA_CODE, MFA_SMS_CODE_SENT),
                        new JourneyTransition(
                                userContext, USER_ENTERED_INVALID_MFA_CODE, MFA_CODE_NOT_VALID),
                        new JourneyTransition(
                                userContext,
                                USER_ENTERED_INVALID_MFA_CODE_TOO_MANY_TIMES,
                                MFA_CODE_MAX_RETRIES_REACHED),
                        new JourneyTransition(
                                userContext,
                                USER_ENTERED_UNREGISTERED_EMAIL_ADDRESS,
                                USER_NOT_FOUND),
                        new JourneyTransition(
                                userContext,
                                USER_ENTERED_REGISTERED_EMAIL_ADDRESS,
                                AUTHENTICATION_REQUIRED),
                        new JourneyTransition(
                                userContext, USER_ENTERED_VALID_CREDENTIALS, LOGGED_IN));

        SessionState currentState = NEW;

        for (JourneyTransition transition : transitions) {
            currentState =
                    stateMachine.transition(
                            currentState,
                            transition.getSessionAction(),
                            transition.getUserContext());
            assertThat(currentState, equalTo(transition.getExpectedSessionState()));
        }
    }
}
