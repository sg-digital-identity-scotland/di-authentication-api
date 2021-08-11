package uk.gov.di.actions;

import uk.gov.di.entity.Session;
import uk.gov.di.entity.SessionState;

public interface UserAction {
    SessionState evaluateNextStep(Session session);
}
