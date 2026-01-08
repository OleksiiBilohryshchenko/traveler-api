package ua.sumdu.dds.travelerapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ua.sumdu.dds.travelerapi.dto.CreateTravelPlanRequest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TravelPlanControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateTravelPlanSuccessfully() throws Exception {
        CreateTravelPlanRequest request = new CreateTravelPlanRequest(
                "Integration Trip",
                "Test description",
                LocalDate.now(),
                LocalDate.now().plusDays(3),
                BigDecimal.valueOf(500),
                "USD",
                true
        );

        mockMvc.perform(
                        post("/api/travel-plans")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Integration Trip"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void shouldReturn409WhenVersionConflict() throws Exception {
        CreateTravelPlanRequest createRequest = new CreateTravelPlanRequest(
                "Conflict Trip",
                "Initial",
                LocalDate.now(),
                LocalDate.now().plusDays(2),
                BigDecimal.valueOf(300),
                "USD",
                true
        );

        String response = mockMvc.perform(
                        post("/api/travel-plans")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = objectMapper.readTree(response).get("id").asText();

        // ПЕРШЕ ОНОВЛЕННЯ (коректне)
        mockMvc.perform(
                        put("/api/travel-plans/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "title": "Updated once",
                                  "version": 1
                                }
                                """)
                )
                .andExpect(status().isOk());

        // ДРУГЕ ОНОВЛЕННЯ ЗІ СТАРОЮ VERSION → CONFLICT
        mockMvc.perform(
                        put("/api/travel-plans/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "title": "Updated again",
                                  "version": 1
                                }
                                """)
                )
                .andExpect(status().isConflict());
    }

}
