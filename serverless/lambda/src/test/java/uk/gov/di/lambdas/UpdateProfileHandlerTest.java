package uk.gov.di.lambdas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationRequest;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.openid.connect.sdk.AuthenticationSuccessResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.di.entity.ClientSession;
import uk.gov.di.entity.ErrorResponse;
import uk.gov.di.entity.Session;
import uk.gov.di.exceptions.ClientNotFoundException;
import uk.gov.di.services.AuthenticationService;
import uk.gov.di.services.AuthorisationCodeService;
import uk.gov.di.services.AuthorizationService;
import uk.gov.di.services.ClientSessionService;
import uk.gov.di.services.SessionService;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.di.entity.UpdateProfileType.ADD_PHONE_NUMBER;
import static uk.gov.di.entity.UpdateProfileType.CAPTURE_CONSENT;
import static uk.gov.di.matchers.APIGatewayProxyResponseEventMatcher.hasJsonBody;
import static uk.gov.di.matchers.APIGatewayProxyResponseEventMatcher.hasStatus;

class UpdateProfileHandlerTest {

    private static final String TEST_EMAIL_ADDRESS = "joe.bloggs@digital.cabinet-office.gov.uk";
    private static final String PHONE_NUMBER = "01234567891";
    private static final boolean CONSENT_VALUE = true;
    private static final String SESSION_ID = "a-session-id";
    private static final String CLIENT_SESSION_ID = "client-session-id";
    private static final String COOKIE = "Cookie";
    private static final URI REDIRECT_URI = URI.create("http://localhost/redirect");
    private final Context context = mock(Context.class);
    private UpdateProfileHandler handler;
    private final AuthenticationService authenticationService = mock(AuthenticationService.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final ClientSessionService clientSessionService = mock(ClientSessionService.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final AuthorisationCodeService authorisationCodeService =
            mock(AuthorisationCodeService.class);

    @BeforeEach
    public void setUp() {
        handler =
                new UpdateProfileHandler(
                        authenticationService, sessionService, clientSessionService);
    }

    @Test
    public void shouldReturn200WhenUpdatingPhoneNumber() throws JsonProcessingException {
        when(authenticationService.userExists(eq("joe.bloggs@test.com"))).thenReturn(false);
        usingValidSession();
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setHeaders(Map.of("Session-Id", SESSION_ID));
        event.setBody(
                format(
                        "{ \"email\": \"%s\", \"updateProfileType\": \"%s\", \"profileInformation\": \"%s\" }",
                        TEST_EMAIL_ADDRESS, ADD_PHONE_NUMBER, PHONE_NUMBER));
        APIGatewayProxyResponseEvent result = handler.handleRequest(event, context);

        verify(authenticationService).updatePhoneNumber(eq(TEST_EMAIL_ADDRESS), eq(PHONE_NUMBER));

        assertThat(result, hasStatus(200));
    }

    @Test
    public void shouldReturn200WhenUpdatingProfileWithConsent()
            throws JsonProcessingException, ClientNotFoundException {
        when(authenticationService.userExists(eq(TEST_EMAIL_ADDRESS))).thenReturn(false);
        usingValidSession();
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        ClientID clientID = new ClientID();
        AuthorizationCode authorizationCode = new AuthorizationCode();
        AuthorizationRequest authRequest = generateValidSessionAndAuthRequest(clientID);

        Map<String, List<String>> consentMap = new HashMap<>();
        List<String> clientConsents = new ArrayList<>();
        String consent = String.valueOf(true);
        clientConsents.add(consent);

        String clientId = authRequest.getClientID().getValue();
        consentMap.put(clientId, clientConsents);

        AuthenticationSuccessResponse authSuccessResponse =
                new AuthenticationSuccessResponse(
                        authRequest.getRedirectionURI(),
                        authorizationCode,
                        null,
                        null,
                        authRequest.getState(),
                        null,
                        null);

        when(authorizationService.isClientRedirectUriValid(eq(clientID), eq(REDIRECT_URI)))
                .thenReturn(true);
        when(authorisationCodeService.generateAuthorisationCode(
                        eq(CLIENT_SESSION_ID), eq(TEST_EMAIL_ADDRESS)))
                .thenReturn(authorizationCode);
        when(authorizationService.generateSuccessfulAuthResponse(
                        any(AuthorizationRequest.class), any(AuthorizationCode.class)))
                .thenReturn(authSuccessResponse);

        event.setHeaders(Map.of(COOKIE, buildCookieString()));
        event.setBody(
                format(
                        "{ \"email\": \"%s\", \"updateProfileType\": \"%s\", \"profileInformation\": \"%s\" }",
                        TEST_EMAIL_ADDRESS, CAPTURE_CONSENT, CONSENT_VALUE));

        APIGatewayProxyResponseEvent result = handler.handleRequest(event, context);

        verify(authenticationService).updateConsent(eq(TEST_EMAIL_ADDRESS), eq(consentMap));

        assertThat(result, hasStatus(200));
    }

    @Test
    public void shouldReturn400WhenRequestIsMissingParameters() throws JsonProcessingException {
        when(authenticationService.userExists(eq("joe.bloggs@test.com"))).thenReturn(false);
        usingValidSession();
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setHeaders(Map.of("Session-Id", SESSION_ID));
        event.setBody(
                format(
                        "{ \"email\": \"%s\", \"updateProfileType\": \"%s\"}",
                        TEST_EMAIL_ADDRESS, ADD_PHONE_NUMBER));
        APIGatewayProxyResponseEvent result = handler.handleRequest(event, context);

        verify(authenticationService, never())
                .updatePhoneNumber(eq(TEST_EMAIL_ADDRESS), eq(PHONE_NUMBER));

        assertThat(result, hasStatus(400));
        assertThat(result, hasJsonBody(ErrorResponse.ERROR_1001));
    }

    private void usingValidSession() {
        when(sessionService.getSessionFromRequestHeaders(anyMap()))
                .thenReturn(
                        Optional.of(new Session(SESSION_ID).setEmailAddress(TEST_EMAIL_ADDRESS)));
    }

    private String buildCookieString() {
        return format(
                "%s=%s.%s; Max-Age=%d; %s",
                "gs", SESSION_ID, CLIENT_SESSION_ID, 1800, "Secure; HttpOnly;");
    }

    private AuthorizationRequest generateValidSessionAndAuthRequest(ClientID clientID) {
        ResponseType responseType = new ResponseType(ResponseType.Value.CODE);
        State state = new State();
        AuthorizationRequest authorizationRequest =
                new AuthorizationRequest.Builder(responseType, clientID)
                        .redirectionURI(REDIRECT_URI)
                        .state(state)
                        .build();
        generateValidSession(authorizationRequest.toParameters());
        return authorizationRequest;
    }

    private void generateValidSession(Map<String, List<String>> authRequest) {
        when(sessionService.readSessionFromRedis(SESSION_ID))
                .thenReturn(
                        Optional.of(
                                new Session(SESSION_ID)
                                        .addClientSession(CLIENT_SESSION_ID)
                                        .setEmailAddress(TEST_EMAIL_ADDRESS)));
        when(clientSessionService.getClientSession(CLIENT_SESSION_ID))
                .thenReturn(new ClientSession(authRequest, LocalDateTime.now()));
    }
}
