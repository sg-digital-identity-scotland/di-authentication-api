package uk.gov.di.authentication.shared.entity;

public enum NotificationType {
    VERIFY_EMAIL,
    VERIFY_PHONE_NUMBER,
    MFA_SMS,
    RESET_PASSWORD,
    PASSWORD_RESET_CONFIRMATION,
    ACCOUNT_CREATED_CONFIRMATION
}
