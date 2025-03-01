package uk.gov.di.authentication.shared.services;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import uk.gov.di.authentication.shared.entity.ErrorResponse;
import uk.gov.di.authentication.shared.entity.NotificationType;
import uk.gov.di.authentication.shared.entity.Session;
import uk.gov.di.authentication.shared.entity.SessionAction;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType.MOBILE;
import static uk.gov.di.authentication.shared.entity.SessionAction.USER_ENTERED_INVALID_EMAIL_VERIFICATION_CODE;
import static uk.gov.di.authentication.shared.entity.SessionAction.USER_ENTERED_INVALID_EMAIL_VERIFICATION_CODE_TOO_MANY_TIMES;
import static uk.gov.di.authentication.shared.entity.SessionAction.USER_ENTERED_INVALID_MFA_CODE;
import static uk.gov.di.authentication.shared.entity.SessionAction.USER_ENTERED_INVALID_MFA_CODE_TOO_MANY_TIMES;
import static uk.gov.di.authentication.shared.entity.SessionAction.USER_ENTERED_INVALID_PHONE_VERIFICATION_CODE;
import static uk.gov.di.authentication.shared.entity.SessionAction.USER_ENTERED_INVALID_PHONE_VERIFICATION_CODE_TOO_MANY_TIMES;
import static uk.gov.di.authentication.shared.entity.SessionAction.USER_ENTERED_VALID_EMAIL_VERIFICATION_CODE;
import static uk.gov.di.authentication.shared.entity.SessionAction.USER_ENTERED_VALID_MFA_CODE;
import static uk.gov.di.authentication.shared.entity.SessionAction.USER_ENTERED_VALID_PHONE_VERIFICATION_CODE;

public class ValidationService {

    private static final Pattern EMAIL_NOTIFY_REGEX =
            Pattern.compile("^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~\\-]+@([^.@][^@\\s]+)$");
    private static final Pattern HOSTNAME_REGEX =
            Pattern.compile("^(xn|[a-z0-9]+)(-?-[a-z0-9]+)*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TLD_PART_REGEX =
            Pattern.compile("^([a-z]{2,63}|xn--([a-z0-9]+-)*[a-z0-9]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PASSWORD_REGEX = Pattern.compile(".*\\d.*");

    public Optional<ErrorResponse> validateEmailAddressUpdate(
            String existingEmail, String replacementEmail) {
        if (existingEmail.equals(replacementEmail)) {
            return Optional.of(ErrorResponse.ERROR_1019);
        }
        Optional<ErrorResponse> existingEmailError = validateEmailAddress(existingEmail);
        if (existingEmailError.isPresent()) {
            return existingEmailError;
        }
        return validateEmailAddress(replacementEmail);
    }

    public Optional<ErrorResponse> validateEmailAddress(String email) {
        if (email == null || email.isBlank()) {
            return Optional.of(ErrorResponse.ERROR_1003);
        }
        Matcher matcher = EMAIL_NOTIFY_REGEX.matcher(email);
        if (!matcher.matches()) {
            return Optional.of(ErrorResponse.ERROR_1004);
        }
        if (email.contains("..")) {
            return Optional.of(ErrorResponse.ERROR_1004);
        }
        var hostname = matcher.group(1);
        String[] parts = hostname.split("\\.");
        if (parts.length < 2) {
            return Optional.of(ErrorResponse.ERROR_1004);
        }
        for (String part : parts) {
            if (!HOSTNAME_REGEX.matcher(part).matches()) {
                return Optional.of(ErrorResponse.ERROR_1004);
            }
        }
        if (!TLD_PART_REGEX.matcher(parts[parts.length - 1]).matches()) {
            return Optional.of(ErrorResponse.ERROR_1004);
        }
        return Optional.empty();
    }

    public Optional<ErrorResponse> validatePassword(String password) {
        if (password == null || password.isBlank()) {
            return Optional.of(ErrorResponse.ERROR_1005);
        }
        if (password.length() < 8) {
            return Optional.of(ErrorResponse.ERROR_1006);
        }
        if (!PASSWORD_REGEX.matcher(password).matches()) {
            return Optional.of(ErrorResponse.ERROR_1007);
        }
        return Optional.empty();
    }

    public Optional<ErrorResponse> validatePhoneNumber(String phoneNumberInput) {
        if ((!phoneNumberInput.matches("[0-9]+")) || (phoneNumberInput.length() < 10)) {
            return Optional.of(ErrorResponse.ERROR_1012);
        }
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        try {
            var phoneNumber = phoneUtil.parse(phoneNumberInput, "GB");
            if (!phoneUtil.getNumberType(phoneNumber).equals(MOBILE)) {
                return Optional.of(ErrorResponse.ERROR_1012);
            }
            if (phoneUtil.isValidNumber(phoneNumber)) {
                return Optional.empty();
            }
            return Optional.of(ErrorResponse.ERROR_1012);
        } catch (NumberParseException e) {
            return Optional.of(ErrorResponse.ERROR_1012);
        }
    }

    public SessionAction validateVerificationCode(
            NotificationType type,
            Optional<String> code,
            String input,
            Session session,
            int maxRetries) {

        if (code.filter(input::equals).isPresent()) {
            session.resetCodeRequestCount();

            switch (type) {
                case MFA_SMS:
                    return USER_ENTERED_VALID_MFA_CODE;
                case VERIFY_EMAIL:
                    return USER_ENTERED_VALID_EMAIL_VERIFICATION_CODE;
                case VERIFY_PHONE_NUMBER:
                    return USER_ENTERED_VALID_PHONE_VERIFICATION_CODE;
            }
        }

        session.incrementRetryCount();

        if (session.getRetryCount() > maxRetries) {
            switch (type) {
                case MFA_SMS:
                    return USER_ENTERED_INVALID_MFA_CODE_TOO_MANY_TIMES;
                case VERIFY_EMAIL:
                    return USER_ENTERED_INVALID_EMAIL_VERIFICATION_CODE_TOO_MANY_TIMES;
                case VERIFY_PHONE_NUMBER:
                    return USER_ENTERED_INVALID_PHONE_VERIFICATION_CODE_TOO_MANY_TIMES;
            }
        }

        switch (type) {
            case MFA_SMS:
                return USER_ENTERED_INVALID_MFA_CODE;
            case VERIFY_EMAIL:
                return USER_ENTERED_INVALID_EMAIL_VERIFICATION_CODE;
            case VERIFY_PHONE_NUMBER:
                return USER_ENTERED_INVALID_PHONE_VERIFICATION_CODE;
        }

        return null;
    }
}
