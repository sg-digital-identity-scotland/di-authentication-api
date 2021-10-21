package uk.gov.di.authentication.shared.matchers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.core.LogEvent;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.HashMap;
import java.util.Map;

public class LogEventMatcher {

    public static Matcher<LogEvent> hasMDCProperty(String key, String value) {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(LogEvent item) {
                ObjectMapper mapper = new ObjectMapper();
                TypeReference<HashMap<String, String>> typeRef = new TypeReference<>() {};

                Map<String, String> properties = null;
                try {
                    properties = mapper.readValue(item.toString(), typeRef);
                } catch (JsonProcessingException ex) {
                    throw new RuntimeException(ex);
                }

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
