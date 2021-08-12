package uk.gov.di.services;

import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationRequest;
import com.nimbusds.oauth2.sdk.ErrorObject;
import com.nimbusds.oauth2.sdk.OAuth2Error;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.openid.connect.sdk.AuthenticationSuccessResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.di.authentication.shared.services.ConfigurationService;
import uk.gov.di.entity.ClientRegistry;
import uk.gov.di.entity.ValidScopes;
import uk.gov.di.exceptions.ClientNotFoundException;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;

public class AuthorizationService {

    private final DynamoClientService dynamoClientService;
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationService.class);

    public AuthorizationService(DynamoClientService dynamoClientService) {
        this.dynamoClientService = dynamoClientService;
    }

    public AuthorizationService(ConfigurationService configurationService) {
        this(
                new DynamoClientService(
                        configurationService.getAwsRegion(),
                        configurationService.getEnvironment(),
                        configurationService.getDynamoEndpointUri()));
    }

    public boolean isClientRedirectUriValid(ClientID clientID, URI redirectURI)
            throws ClientNotFoundException {
        Optional<ClientRegistry> client = dynamoClientService.getClient(clientID.toString());
        if (client.isEmpty()) {
            throw new ClientNotFoundException(clientID.toString());
        }
        return client.get().getRedirectUrls().contains(redirectURI.toString());
    }

    public AuthenticationSuccessResponse generateSuccessfulAuthResponse(
            AuthorizationRequest authRequest, AuthorizationCode authorizationCode) {
        return new AuthenticationSuccessResponse(
                authRequest.getRedirectionURI(),
                authorizationCode,
                null,
                null,
                authRequest.getState(),
                null,
                authRequest.getResponseMode());
    }

    public Optional<ErrorObject> validateAuthRequest(AuthorizationRequest authRequest) {
        Optional<ClientRegistry> client =
                dynamoClientService.getClient(authRequest.getClientID().toString());
        if (client.isEmpty()) {
            LOGGER.error("Invalid client: {}", authRequest.getClientID());
            return Optional.of(OAuth2Error.UNAUTHORIZED_CLIENT);
        }
        if (!client.get().getRedirectUrls().contains(authRequest.getRedirectionURI().toString())) {
            LOGGER.error(
                    "Invalid Redirect URI for Client {}. Redirect URI in request {}",
                    client.get().getClientID(),
                    authRequest.getRedirectionURI().toString());
            throw new RuntimeException(
                    format(
                            "Invalid Redirect in request %s",
                            authRequest.getRedirectionURI().toString()));
        }
        if (!authRequest.getResponseType().toString().equals("code")) {
            return Optional.of(OAuth2Error.UNSUPPORTED_RESPONSE_TYPE);
        }
        if (!areScopesValid(authRequest.getScope().toStringList())
                || !client.get().getScopes().containsAll(authRequest.getScope().toStringList())) {
            return Optional.of(OAuth2Error.INVALID_SCOPE);
        }
        return Optional.empty();
    }

    private boolean areScopesValid(List<String> scopes) {
        for (String scope : scopes) {
            if (Arrays.stream(ValidScopes.values())
                    .noneMatch((t) -> t.scopesLowerCase().equals(scope))) {
                return false;
            }
        }
        return true;
    }
}
