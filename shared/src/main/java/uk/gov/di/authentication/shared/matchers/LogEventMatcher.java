package uk.gov.di.authentication.shared.matchers;

import org.apache.logging.log4j.core.LogEvent;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.Map;

public class LogEventMatcher {

    public static Matcher<LogEvent> hasMDCProperty(String key, String value) {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(LogEvent item) {
                Map<String, String> properties = item.getMDCPropertyMap();

                return properties.containsKey(key) && properties.get(key).equals(value);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(
                        "a log event with MDC Property [" + key + ", " + value + "]");
            }
        };
    }

}
