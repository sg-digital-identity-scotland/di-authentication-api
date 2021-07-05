package uk.gov.di.services;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.util.Base64;
import com.nimbusds.oauth2.sdk.id.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.di.entity.UserCredentials;
import uk.gov.di.entity.UserProfile;
import uk.gov.di.helpers.Argon2Helper;

import java.time.LocalDateTime;
import java.util.Optional;

public class DynamoService implements AuthenticationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamoService.class);

    private final AmazonDynamoDB dynamoDB;
    private final DynamoDBMapper userCredentialsMapper;
    private final DynamoDBMapper userProfileMapper;
    private static final String USER_CREDENTIALS_TABLE = "user-credentials";
    private static final String USER_PROFILE_TABLE = "user-profile";

    public DynamoService(String region, String environment, Optional<String> dynamoEndpoint) {
        LOGGER.info("Dynamo Endpoint: " + dynamoEndpoint);
        this.dynamoDB =
                dynamoEndpoint
                        .map(
                                t ->
                                        AmazonDynamoDBClientBuilder.standard()
                                                .withEndpointConfiguration(
                                                        new AwsClientBuilder.EndpointConfiguration(
                                                                t, region)))
                        .orElse(AmazonDynamoDBClientBuilder.standard().withRegion(region))
                        .build();

        DynamoDBMapperConfig userCredentialsConfig =
                new DynamoDBMapperConfig.Builder()
                        .withTableNameOverride(
                                DynamoDBMapperConfig.TableNameOverride.withTableNameReplacement(
                                        environment + "-" + USER_CREDENTIALS_TABLE))
                        .build();
        DynamoDBMapperConfig userProfileConfig =
                new DynamoDBMapperConfig.Builder()
                        .withTableNameOverride(
                                DynamoDBMapperConfig.TableNameOverride.withTableNameReplacement(
                                        environment + "-" + USER_PROFILE_TABLE))
                        .build();
        this.userCredentialsMapper = new DynamoDBMapper(dynamoDB, userCredentialsConfig);
        this.userProfileMapper = new DynamoDBMapper(dynamoDB, userProfileConfig);
    }

    @Override
    public boolean userExists(String email) {
        return userProfileMapper.load(UserProfile.class, email) != null;
    }

    @Override
    public void signUp(String email, String password) {
        String dateTime = LocalDateTime.now().toString();
        Subject subject = new Subject();
        String hashedPassword = hashPassword(password);
        UserCredentials userCredentials =
                new UserCredentials()
                        .setEmail(email)
                        .setSubjectID(subject.toString())
                        .setPassword(hashedPassword)
                        .setCreated(dateTime)
                        .setUpdated(dateTime);
        UserProfile userProfile =
                new UserProfile()
                        .setEmail(email)
                        .setSubjectID(subject.toString())
                        .setEmailVerified(true)
                        .setCreated(dateTime)
                        .setUpdated(dateTime);
        userCredentialsMapper.save(userCredentials);
        userProfileMapper.save(userProfile);
    }

    @Override
    public boolean login(String email, String password) {
        UserCredentials userCredentials = userCredentialsMapper.load(UserCredentials.class, email);
        return verifyPassword(userCredentials.getPassword(), password);
    }

    @Override
    public boolean isEmailVerificationRequired() {
        return false;
    }

    public Subject getSubjectFromEmail(String email) {
        return new Subject(userProfileMapper.load(UserProfile.class, email).getSubjectID());
    }

    @Override
    public void updatePhoneNumber(String email, String phoneNumber) {
        userProfileMapper.load(UserProfile.class, email).setPhoneNumber(phoneNumber);
    }

    private static String hashPassword(String password) {
        return Base64.encodeAsString(Argon2Helper.argon2Hash(password.getBytes()));
    }

    private static boolean verifyPassword(String hashedPassword, String password) {
        return hashedPassword.equals(hashPassword(password));
    }
}
