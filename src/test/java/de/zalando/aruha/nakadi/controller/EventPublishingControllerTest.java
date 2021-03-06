package de.zalando.aruha.nakadi.controller;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.zalando.aruha.nakadi.config.JsonConfig;
import de.zalando.aruha.nakadi.domain.BatchItemResponse;
import de.zalando.aruha.nakadi.domain.EventPublishResult;
import static de.zalando.aruha.nakadi.domain.EventPublishingStatus.ABORTED;
import static de.zalando.aruha.nakadi.domain.EventPublishingStatus.FAILED;
import static de.zalando.aruha.nakadi.domain.EventPublishingStatus.SUBMITTED;
import static de.zalando.aruha.nakadi.domain.EventPublishingStep.PARTITIONING;
import static de.zalando.aruha.nakadi.domain.EventPublishingStep.PUBLISHING;
import static de.zalando.aruha.nakadi.domain.EventPublishingStep.VALIDATING;
import de.zalando.aruha.nakadi.exceptions.InternalNakadiException;
import de.zalando.aruha.nakadi.exceptions.NakadiException;
import de.zalando.aruha.nakadi.exceptions.NoSuchEventTypeException;
import de.zalando.aruha.nakadi.metrics.EventTypeMetricRegistry;
import de.zalando.aruha.nakadi.metrics.EventTypeMetrics;
import de.zalando.aruha.nakadi.service.EventPublisher;
import de.zalando.aruha.nakadi.utils.JsonTestHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import org.json.JSONArray;
import org.junit.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import org.mockito.Mockito;
import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

public class EventPublishingControllerTest {

    public static final String TOPIC = "my-topic";
    private static final String EVENT_BATCH = "[{\"payload\": \"My Event Payload\"}]";

    private final ObjectMapper objectMapper = new JsonConfig().jacksonObjectMapper();
    private final MetricRegistry metricRegistry;
    private final JsonTestHelper jsonHelper;
    private final EventPublisher publisher;

    private final MockMvc mockMvc;
    private final EventTypeMetricRegistry eventTypeMetricRegistry;

    public EventPublishingControllerTest() throws NakadiException, ExecutionException {
        jsonHelper = new JsonTestHelper(objectMapper);
        metricRegistry = new MetricRegistry();
        publisher = mock(EventPublisher.class);
        eventTypeMetricRegistry = new EventTypeMetricRegistry(metricRegistry);

        final EventPublishingController controller = new EventPublishingController(publisher, eventTypeMetricRegistry);

        final MappingJackson2HttpMessageConverter jackson2HttpMessageConverter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = standaloneSetup(controller)
                .setMessageConverters(new StringHttpMessageConverter(), jackson2HttpMessageConverter)
                .build();
    }

    @Test
    public void whenResultIsSubmittedThen200() throws Exception {
        final EventPublishResult result = new EventPublishResult(SUBMITTED, null, null);

        Mockito
                .doReturn(result)
                .when(publisher)
                .publish(any(JSONArray.class), eq(TOPIC));

        postBatch(TOPIC, EVENT_BATCH)
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    public void whenInvalidPostBodyThen400() throws Exception {
        postBatch(TOPIC, "invalid json array").andExpect(status().isBadRequest());
    }

    @Test
    public void whenResultIsAbortedThen422() throws Exception {
        final EventPublishResult result = new EventPublishResult(ABORTED, PARTITIONING, responses());

        Mockito
                .doReturn(result)
                .when(publisher)
                .publish(any(JSONArray.class), eq(TOPIC));

        postBatch(TOPIC, EVENT_BATCH)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(jsonHelper.matchesObject(responses())));
    }

    @Test
    public void whenResultIsAbortedThen207() throws Exception {
        final EventPublishResult result = new EventPublishResult(FAILED, PUBLISHING, responses());

        Mockito
                .doReturn(result)
                .when(publisher)
                .publish(any(JSONArray.class), eq(TOPIC));

        postBatch(TOPIC, EVENT_BATCH)
                .andExpect(status().isMultiStatus())
                .andExpect(content().string(jsonHelper.matchesObject(responses())));
    }

    @Test
    public void whenEventTypeNotFoundThen404() throws Exception {
        Mockito
                .doThrow(NoSuchEventTypeException.class)
                .when(publisher)
                .publish(any(JSONArray.class), eq(TOPIC));

        postBatch(TOPIC, EVENT_BATCH)
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void publishedEventsAreReportedPerEventType() throws Exception {
        final EventPublishResult success = new EventPublishResult(SUBMITTED, null, null);
        Mockito
                .doReturn(success)
                .doReturn(success)
                .doThrow(InternalNakadiException.class)
                .when(publisher)
                .publish(any(), any());

        postBatch(TOPIC, EVENT_BATCH);
        postBatch(TOPIC, EVENT_BATCH);
        postBatch(TOPIC, EVENT_BATCH);

        final EventTypeMetrics eventTypeMetrics = eventTypeMetricRegistry.metricsFor(TOPIC);

        assertThat(eventTypeMetrics.getResponseCount(200), equalTo(2L));
        assertThat(eventTypeMetrics.getResponseCount(500), equalTo(1L));
    }

    private List<BatchItemResponse> responses() {
        final BatchItemResponse response = new BatchItemResponse();
        response.setPublishingStatus(ABORTED);
        response.setStep(VALIDATING);

        final List<BatchItemResponse> responses = new ArrayList<>();
        responses.add(response);

        return responses;
    }

    private ResultActions postBatch(final String eventType, final String batch) throws Exception {
        final String url = "/event-types/" + eventType + "/events";
        final MockHttpServletRequestBuilder requestBuilder = post(url)
                .contentType(APPLICATION_JSON)
                .content(batch);

        return mockMvc.perform(requestBuilder);
    }
}