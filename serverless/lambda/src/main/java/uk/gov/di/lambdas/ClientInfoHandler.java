package uk.gov.di.lambdas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.nimbusds.oauth2.sdk.AuthorizationRequest;
import com.nimbusds.oauth2.sdk.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.di.entity.*;
import uk.gov.di.services.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static uk.gov.di.helpers.ApiGatewayResponseHelper.generateApiGatewayProxyErrorResponse;
import static uk.gov.di.helpers.ApiGatewayResponseHelper.generateApiGatewayProxyResponse;

public class ClientInfoHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientInfoHandler.class);
    private final ConfigurationService configurationService;
    private final ClientSessionService clientSessionService;
    private final ClientService clientService;

    public ClientInfoHandler(
            ConfigurationService configurationService,
            ClientSessionService clientSessionService,
            ClientService clientService) {
        this.configurationService = configurationService;
        this.clientSessionService = clientSessionService;
        this.clientService = clientService;
    }

    public ClientInfoHandler() {
        configurationService = new ConfigurationService();
        clientSessionService = new ClientSessionService(configurationService);
        clientService =
                new DynamoClientService(
                        configurationService.getAwsRegion(),
                        configurationService.getEnvironment(),
                        configurationService.getDynamoEndpointUri());
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
            APIGatewayProxyRequestEvent input, Context context) {

        Optional<ClientSession> clientSession =
                clientSessionService.getClientSessionFromRequestHeaders(input.getHeaders());

        if (!clientSession.isPresent()) {
            LOGGER.info("ClientSession not found.");
            return generateApiGatewayProxyErrorResponse(400, ErrorResponse.ERROR_1020);
        }

        try {
            Map<String, List<String>> authRequest = clientSession.get().getAuthRequestParams();

            String clientID = AuthorizationRequest.parse(authRequest).getClientID().getValue();

            Optional<ClientRegistry> optionalClientRegistry = clientService.getClient(clientID);

            if (!optionalClientRegistry.isPresent()) {
                LOGGER.info("Client not found in ClientRegistry for ClientID: %s", clientID);
                return generateApiGatewayProxyErrorResponse(403, ErrorResponse.ERROR_1016);
            }

            ClientRegistry clientRegistry = optionalClientRegistry.get();
            ClientInfoResponse clientInfoResponse =
                    new ClientInfoResponse(
                            clientRegistry.getClientID(),
                            clientRegistry.getClientName(),
                            clientRegistry.getScopes());

            LOGGER.info(
                    "Found Client Info for ClientID: %s ClientName %s",
                    clientRegistry.getClientID(), clientRegistry.getClientName());

            return generateApiGatewayProxyResponse(200, clientInfoResponse);

        } catch (ParseException e) {
            return generateApiGatewayProxyErrorResponse(400, ErrorResponse.ERROR_1001);
        } catch (JsonProcessingException e) {
            return generateApiGatewayProxyErrorResponse(400, ErrorResponse.ERROR_1001);
        }
    }
}
