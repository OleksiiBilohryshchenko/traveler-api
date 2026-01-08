package ua.sumdu.dds.travelerapi.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LocationControllerIT {

    @Autowired
    private MockMvc mockMvc;

    /* ---------- TESTS ---------- */

    @Test
    void shouldAddLocationToTravelPlan() throws Exception {
        UUID planId = createTestTravelPlan();

        String body = """
            {
              "name": "Paris",
              "address": "France",
              "budget": 1000
            }
            """;

        mockMvc.perform(post("/api/travel-plans/{id}/locations", planId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("Paris"))
            .andExpect(jsonPath("$.visit_order").value(1))
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.travel_plan_id").value(planId.toString()));
    }

    @Test
    void shouldReturn409OnLocationVersionConflict() throws Exception {
        UUID planId = createTestTravelPlan();

        // create location
        MvcResult createResult = mockMvc.perform(
                post("/api/travel-plans/{id}/locations", planId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Berlin"
                        }
                        """)
        ).andExpect(status().isCreated())
         .andReturn();

        String json = createResult.getResponse().getContentAsString();
        UUID locationId = UUID.fromString(JsonPath.read(json, "$.id"));
        int version = JsonPath.read(json, "$.version");

        // update with WRONG version
        mockMvc.perform(
                put("/api/travel-plans/{planId}/locations/{locationId}",
                        planId, locationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {
                  "name": "Berlin Updated",
                  "version": %d
                }
                """.formatted(version + 1))
        ).andExpect(status().isConflict());

    }

    /* ---------- HELPERS ---------- */

    private UUID createTestTravelPlan() throws Exception {
        MvcResult result = mockMvc.perform(
                post("/api/travel-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "title": "Test Plan",
                          "startDate": "2025-01-01",
                          "endDate": "2025-01-10"
                        }
                        """)
        ).andExpect(status().isCreated())
         .andReturn();

        return UUID.fromString(
                JsonPath.read(result.getResponse().getContentAsString(), "$.id")
        );
    }
}
