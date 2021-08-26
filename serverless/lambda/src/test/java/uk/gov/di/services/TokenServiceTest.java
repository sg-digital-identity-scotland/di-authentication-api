package uk.gov.di.services;

import com.amazonaws.services.kms.model.GetPublicKeyRequest;
import com.amazonaws.services.kms.model.GetPublicKeyResult;
import com.amazonaws.services.kms.model.SignRequest;
import com.amazonaws.services.kms.model.SignResult;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.impl.ECDSA;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.ErrorObject;
import com.nimbusds.oauth2.sdk.OAuth2Error;
import com.nimbusds.oauth2.sdk.auth.PrivateKeyJWT;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Subject;
import com.nimbusds.oauth2.sdk.util.URLUtils;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import org.junit.jupiter.api.Test;
import uk.gov.di.authentication.shared.helpers.TokenGeneratorHelper;
import uk.gov.di.authentication.shared.services.ConfigurationService;
import uk.gov.di.authentication.shared.services.KmsConnectionService;
import uk.gov.di.authentication.shared.services.RedisConnectionService;
import uk.gov.di.authentication.shared.services.TokenService;

import java.net.URI;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.text.ParseException;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TokenServiceTest {

    private final ConfigurationService configurationService = mock(ConfigurationService.class);
    private final KmsConnectionService kmsConnectionService = mock(KmsConnectionService.class);
    private final RedisConnectionService redisConnectionService =
            mock(RedisConnectionService.class);
    private final TokenService tokenService =
            new TokenService(configurationService, redisConnectionService, kmsConnectionService);
    private static final Subject SUBJECT = new Subject("some-subject");
    private static final List<String> SCOPES = List.of("openid", "email", "phone");
    private static final String CLIENT_ID = "client-id";
    private static final String AUTH_CODE = new AuthorizationCode().toString();
    private static final String GRANT_TYPE = "authorization_code";
    private static final String REDIRECT_URI = "http://localhost/redirect";
    private static final String BASE_URL = "http://example.com";
    private static final String KEY_ID = "14342354354353";

    @Test
    public void shouldSuccessfullyGenerateTokenResponse() throws ParseException, JOSEException {
        Nonce nonce = new Nonce();
        when(configurationService.getTokenSigningKeyId()).thenReturn(KEY_ID);
        Optional<String> baseUrl = Optional.of(BASE_URL);
        when(configurationService.getBaseURL()).thenReturn(baseUrl);
        when(configurationService.getAccessTokenExpiry()).thenReturn(300L);
        SignedJWT signedIdToken = createSignedIdToken();
        SignedJWT signedAccessToken = createSignedAccessToken();
        SignResult idTokenSignedResult = new SignResult();
        byte[] idTokenSignatureDer =
                ECDSA.transcodeSignatureToDER(signedIdToken.getSignature().decode());
        idTokenSignedResult.setSignature(ByteBuffer.wrap(idTokenSignatureDer));
        idTokenSignedResult.setKeyId(KEY_ID);
        idTokenSignedResult.setSigningAlgorithm(JWSAlgorithm.ES256.getName());

        SignResult accessTokenResult = new SignResult();
        byte[] accessTokenSignatureDer =
                ECDSA.transcodeSignatureToDER(signedAccessToken.getSignature().decode());
        accessTokenResult.setSignature(ByteBuffer.wrap(accessTokenSignatureDer));
        accessTokenResult.setKeyId(KEY_ID);
        accessTokenResult.setSigningAlgorithm(JWSAlgorithm.ES256.getName());
        when(kmsConnectionService.sign(any(SignRequest.class))).thenReturn(accessTokenResult);
        when(kmsConnectionService.sign(any(SignRequest.class))).thenReturn(idTokenSignedResult);
        Map<String, Object> additionalTokenClaims = new HashMap<>();
        additionalTokenClaims.put("nonce", nonce);
        OIDCTokenResponse tokenResponse =
                tokenService.generateTokenResponse(
                        CLIENT_ID, SUBJECT, SCOPES, additionalTokenClaims);

        assertEquals(
                BASE_URL, tokenResponse.getOIDCTokens().getIDToken().getJWTClaimsSet().getIssuer());
        assertEquals(
                SUBJECT.getValue(),
                tokenResponse.getOIDCTokens().getIDToken().getJWTClaimsSet().getClaim("sub"));
        verify(redisConnectionService)
                .saveWithExpiry(
                        tokenResponse.getOIDCTokens().getAccessToken().toJSONString(),
                        SUBJECT.toString(),
                        300L);
        assertEquals(
                nonce.getValue(),
                tokenResponse.getOIDCTokens().getIDToken().getJWTClaimsSet().getClaim("nonce"));
    }

    @Test
    public void shouldSuccessfullyValidatePrivateKeyJWT() throws JOSEException {
        KeyPair keyPair = generateRsaKeyPair();
        String publicKey = Base64.getMimeEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String requestParams = generateSerialisedPrivateKeyJWT(keyPair);
        assertThat(
                tokenService.validatePrivateKeyJWT(
                        requestParams, publicKey, "http://localhost/token"),
                equalTo(Optional.empty()));
    }

    @Test
    public void shouldReturnErrorIfUnableToValidatePrivateKeyJWT() throws JOSEException {
        KeyPair keyPair = generateRsaKeyPair();
        KeyPair keyPairTwo = generateRsaKeyPair();
        String publicKey =
                Base64.getMimeEncoder().encodeToString(keyPairTwo.getPublic().getEncoded());
        String requestParams = generateSerialisedPrivateKeyJWT(keyPair);
        assertThat(
                tokenService.validatePrivateKeyJWT(
                        requestParams, publicKey, "http://localhost/token"),
                equalTo(Optional.of(OAuth2Error.INVALID_CLIENT)));
    }

    @Test
    public void shouldSuccessfullyValidateTokenRequest() {
        Map<String, List<String>> customParams = new HashMap<>();
        customParams.put("grant_type", Collections.singletonList(GRANT_TYPE));
        customParams.put("client_id", Collections.singletonList(CLIENT_ID));
        customParams.put("code", Collections.singletonList(AUTH_CODE));
        customParams.put("redirect_uri", Collections.singletonList(REDIRECT_URI));
        Optional<ErrorObject> errorObject =
                tokenService.validateTokenRequestParams(URLUtils.serializeParameters(customParams));

        assertThat(errorObject, equalTo(Optional.empty()));
    }

    @Test
    public void shouldRetrievePublicKeyfromKmsAndParseToJwk() {
        String keyId = "3423543t5435345";
        byte[] publicKey =
                Base64.getDecoder()
                        .decode(
                                "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEpRm+QZsh2IkUWcqXUhBI9ulOzO8dz0Z8HIS6m77tI4eWoZgKYUcbByshDtN4gWPql7E5mN4uCLsg5+6SDXlQcA==");
        when(configurationService.getTokenSigningKeyId()).thenReturn(keyId);
        GetPublicKeyResult getPublicKeyResult = new GetPublicKeyResult();
        getPublicKeyResult.setKeyUsage("SIGN_VERIFY");
        getPublicKeyResult.setKeyId(keyId);
        getPublicKeyResult.setSigningAlgorithms(
                Collections.singletonList(JWSAlgorithm.ES256.getName()));
        getPublicKeyResult.setPublicKey(ByteBuffer.wrap(publicKey));
        when(kmsConnectionService.getPublicKey(any(GetPublicKeyRequest.class)))
                .thenReturn(getPublicKeyResult);
        JWK publicKeyJwk = tokenService.getPublicKey();
        assertEquals(publicKeyJwk.getKeyID(), keyId);
        assertEquals(publicKeyJwk.getAlgorithm(), JWSAlgorithm.ES256);
        assertEquals(publicKeyJwk.getKeyUse(), KeyUse.SIGNATURE);
    }

    @Test
    public void shouldReturnErrorIfClientIdIsMissingWhenValidatingTokenRequest() {
        Map<String, List<String>> customParams = new HashMap<>();
        customParams.put("grant_type", Collections.singletonList(GRANT_TYPE));
        customParams.put("code", Collections.singletonList(AUTH_CODE));
        customParams.put("redirect_uri", Collections.singletonList(REDIRECT_URI));
        Optional<ErrorObject> errorObject =
                tokenService.validateTokenRequestParams(URLUtils.serializeParameters(customParams));

        assertThat(
                errorObject,
                equalTo(
                        Optional.of(
                                new ErrorObject(
                                        OAuth2Error.INVALID_REQUEST_CODE,
                                        "Request is missing client_id parameter"))));
    }

    @Test
    public void shouldReturnErrorIfRedirectUriIsMissingWhenValidatingTokenRequest() {
        Map<String, List<String>> customParams = new HashMap<>();
        customParams.put("grant_type", Collections.singletonList(GRANT_TYPE));
        customParams.put("client_id", Collections.singletonList(CLIENT_ID));
        customParams.put("code", Collections.singletonList(AUTH_CODE));
        Optional<ErrorObject> errorObject =
                tokenService.validateTokenRequestParams(URLUtils.serializeParameters(customParams));

        assertThat(
                errorObject,
                equalTo(
                        Optional.of(
                                new ErrorObject(
                                        OAuth2Error.INVALID_REQUEST_CODE,
                                        "Request is missing redirect_uri parameter"))));
    }

    @Test
    public void shouldReturnErrorIfGrantTypeIsMissingWhenValidatingTokenRequest() {
        Map<String, List<String>> customParams = new HashMap<>();
        customParams.put("client_id", Collections.singletonList(CLIENT_ID));
        customParams.put("code", Collections.singletonList(AUTH_CODE));
        customParams.put("redirect_uri", Collections.singletonList(REDIRECT_URI));
        Optional<ErrorObject> errorObject =
                tokenService.validateTokenRequestParams(URLUtils.serializeParameters(customParams));

        assertThat(
                errorObject,
                equalTo(
                        Optional.of(
                                new ErrorObject(
                                        OAuth2Error.INVALID_REQUEST_CODE,
                                        "Request is missing grant_type parameter"))));
    }

    @Test
    public void shouldReturnErrorIfCodeIsMissingWhenValidatingTokenRequest() {
        Map<String, List<String>> customParams = new HashMap<>();
        customParams.put("grant_type", Collections.singletonList(GRANT_TYPE));
        customParams.put("client_id", Collections.singletonList(CLIENT_ID));
        customParams.put("redirect_uri", Collections.singletonList(REDIRECT_URI));
        Optional<ErrorObject> errorObject =
                tokenService.validateTokenRequestParams(URLUtils.serializeParameters(customParams));

        assertThat(
                errorObject,
                equalTo(
                        Optional.of(
                                new ErrorObject(
                                        OAuth2Error.INVALID_REQUEST_CODE,
                                        "Request is missing code parameter"))));
    }

    @Test
    public void shouldReturnErrorIfGrantIsInvalidWhenValidatingTokenRequest() {
        Map<String, List<String>> customParams = new HashMap<>();
        customParams.put("grant_type", Collections.singletonList("client_credentials"));
        customParams.put("client_id", Collections.singletonList(CLIENT_ID));
        customParams.put("code", Collections.singletonList(AUTH_CODE));
        customParams.put("redirect_uri", Collections.singletonList(REDIRECT_URI));
        Optional<ErrorObject> errorObject =
                tokenService.validateTokenRequestParams(URLUtils.serializeParameters(customParams));

        assertThat(errorObject, equalTo(Optional.of(OAuth2Error.UNSUPPORTED_GRANT_TYPE)));
    }

    private String generateSerialisedPrivateKeyJWT(KeyPair keyPair) throws JOSEException {
        PrivateKeyJWT privateKeyJWT =
                new PrivateKeyJWT(
                        new ClientID("client-id"),
                        URI.create("http://localhost/token"),
                        JWSAlgorithm.RS256,
                        (RSAPrivateKey) keyPair.getPrivate(),
                        null,
                        null);
        Map<String, List<String>> privateKeyParams = privateKeyJWT.toParameters();
        privateKeyParams.putAll(privateKeyParams);
        return URLUtils.serializeParameters(privateKeyParams);
    }

    private SignedJWT createSignedIdToken() throws JOSEException {
        ECKey ecSigningKey =
                new ECKeyGenerator(Curve.P_256)
                        .keyID(KEY_ID)
                        .algorithm(JWSAlgorithm.ES256)
                        .generate();
        return TokenGeneratorHelper.generateIDToken(CLIENT_ID, SUBJECT, BASE_URL, ecSigningKey);
    }

    private SignedJWT createSignedAccessToken() throws JOSEException {
        ECKey ecSigningKey =
                new ECKeyGenerator(Curve.P_256)
                        .keyID(KEY_ID)
                        .algorithm(JWSAlgorithm.ES256)
                        .generate();
        return TokenGeneratorHelper.generateAccessToken(CLIENT_ID, BASE_URL, SCOPES, ecSigningKey);
    }

    private KeyPair generateRsaKeyPair() {
        KeyPairGenerator kpg;
        try {
            kpg = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException();
        }
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }
}
