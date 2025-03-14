package uk.gov.di.authentication.accountmigration;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.nimbusds.oauth2.sdk.id.Subject;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.gov.di.authentication.shared.entity.TermsAndConditions;
import uk.gov.di.authentication.shared.entity.UserCredentials;
import uk.gov.di.authentication.shared.entity.UserProfile;
import uk.gov.di.authentication.shared.services.AuthenticationService;
import uk.gov.di.authentication.shared.services.ConfigurationService;
import uk.gov.di.authentication.shared.services.DynamoService;

import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class DataMigrationHandler implements RequestHandler<S3Event, String> {

    private static final Logger LOG = LogManager.getLogger(DataMigrationHandler.class);
    private static final int BATCH_SIZE = 1000;

    private final AuthenticationService authenticationService;
    private final ConfigurationService configurationService;
    private final AmazonS3 client;

    public DataMigrationHandler(
            AuthenticationService authenticationService,
            ConfigurationService configurationService,
            AmazonS3 client) {
        this.authenticationService = authenticationService;
        this.configurationService = configurationService;
        this.client = client;
    }

    public DataMigrationHandler() {
        this.configurationService = ConfigurationService.getInstance();
        this.authenticationService = new DynamoService(configurationService);
        this.client =
                AmazonS3ClientBuilder.standard()
                        .withRegion(configurationService.getAwsRegion())
                        .build();
    }

    @Override
    public String handleRequest(S3Event input, Context context) {
        for (S3EventNotification.S3EventNotificationRecord record : input.getRecords()) {
            String s3Key = record.getS3().getObject().getKey();
            String s3Bucket = record.getS3().getBucket().getName();

            LOG.info("New data transfer file {} detected", s3Key);

            S3Object object = client.getObject(s3Bucket, s3Key);

            InputStreamReader reader = new InputStreamReader(object.getObjectContent());
            CsvToBean<ImportRecord> importRecords =
                    new CsvToBeanBuilder<ImportRecord>(reader).withType(ImportRecord.class).build();
            var records = importRecords.parse();

            int skip = 0;
            int count;
            do {
                var importBatch =
                        records.stream().skip(skip).limit(BATCH_SIZE).collect(Collectors.toList());
                count = importBatch.size();
                skip = skip + count;
                LOG.info("Read {} records starting at {}", count, skip);
                var batch =
                        buildImportBatch(
                                importBatch, configurationService.getTermsAndConditionsVersion());

                authenticationService.bulkAdd(
                        batch.stream().map(p -> p.getLeft()).collect(Collectors.toList()),
                        batch.stream().map(p -> p.getRight()).collect(Collectors.toList()));

            } while (count == BATCH_SIZE);
            LOG.info("Imported {} records", skip);
        }

        return "Complete";
    }

    private List<Pair<UserCredentials, UserProfile>> buildImportBatch(
            List<ImportRecord> importRecords, String termsAndConditionsVersion) {
        return importRecords.stream()
                .map(
                        i -> {
                            Subject subject = new Subject();
                            String now = LocalDateTime.now().toString();
                            UserCredentials userCredentials =
                                    new UserCredentials()
                                            .setEmail(i.getEmail())
                                            .setMigratedPassword(i.getEncryptedPassword())
                                            .setCreated(i.getCreatedAt().toString())
                                            .setUpdated(now)
                                            .setSubjectID(subject.toString());

                            TermsAndConditions termsAndConditions = new TermsAndConditions();
                            termsAndConditions.setVersion(termsAndConditionsVersion);
                            termsAndConditions.setTimestamp(now);
                            UserProfile userProfile =
                                    new UserProfile()
                                            .setEmail(i.getEmail())
                                            .setEmailVerified(true)
                                            .setPhoneNumber(i.getPhone())
                                            .setPhoneNumberVerified(true)
                                            .setSubjectID(subject.toString())
                                            .setEmailVerified(true)
                                            .setCreated(i.getCreatedAt().toString())
                                            .setUpdated(userCredentials.getUpdated())
                                            .setPublicSubjectID((new Subject()).toString())
                                            .setTermsAndConditions(termsAndConditions)
                                            .setLegacySubjectID(i.getSubjectIdentifier());

                            return Pair.of(userCredentials, userProfile);
                        })
                .collect(Collectors.toList());
    }
}
