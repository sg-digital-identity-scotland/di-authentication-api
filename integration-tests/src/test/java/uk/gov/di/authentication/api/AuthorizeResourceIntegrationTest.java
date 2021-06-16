package uk.gov.di.authentication.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AuthorizeResourceIntegrationTest extends AuthorizationAPIResourceIntegrationTest {

    private static final String AUTHORIZE_RESOURCE = "/authorize";
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void shouldCallAuthorizeResourceAndReturn302() throws JsonProcessingException {
        Client client = ClientBuilder.newClient();
        WebTarget webTarget =
                client.property(ClientProperties.FOLLOW_REDIRECTS, Boolean.FALSE).target(ROOT_RESOURCE_URL + AUTHORIZE_RESOURCE);

        Invocation.Builder invocationBuilder =
                webTarget
                        .queryParam("client_id", "test-id")
                        .queryParam("response_type", "code")
                        .queryParam("redirect_uri", "http://localhost:8080")
                        .queryParam("scope", "openid")
                        .queryParam("state", "T7HEWmyE8GS2LCClCgFzAEuESAtC5p2tf00Te_O5-4w")
                        .queryParam("client_name", "client-name")
                        .request();

        Response response = invocationBuilder.get();

        assertEquals(302, response.getStatus());
        assertEquals(true, response.getHeaders().containsKey("Location"));
        assertEquals(true, response.getHeaders().get("Location").get(0).toString().contains("scope=openid"));

    }
}
