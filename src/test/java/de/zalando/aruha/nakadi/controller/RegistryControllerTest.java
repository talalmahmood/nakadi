package de.zalando.aruha.nakadi.controller;

import org.junit.Test;
import org.springframework.test.web.servlet.MockMvc;

import static de.zalando.aruha.nakadi.service.Registry.AVAILABLE_PARTITIONING_STRATEGIES;
import static de.zalando.aruha.nakadi.utils.TestUtils.mockMvcForController;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class RegistryControllerTest {

    private static final MockMvc mockMvc = mockMvcForController(new RegistryController());

    @Test
    public void canExposePartitioningStrategies() throws Exception {
        mockMvc.perform(get("/registry/partitioning-strategies"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(AVAILABLE_PARTITIONING_STRATEGIES.size())))
                .andExpect(jsonPath("$[0:3].name").exists())
                .andExpect(jsonPath("$[0:3].doc").exists());
    }

}