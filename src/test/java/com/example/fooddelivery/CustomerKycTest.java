package com.example.fooddelivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public class CustomerKycTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createCustomer_withValidKyc_returns201AndResponseContainsKyc() throws Exception {
        String email = "kyc-" + System.currentTimeMillis() + "@example.com";
        String json = "{"
                + "\"name\":\"Alice\"," 
                + "\"email\":\"" + email + "\"," 
                + "\"phone\":\"111\"," 
                + "\"address\":\"Addr\"," 
                + "\"idType\":\"PASSPORT\"," 
                + "\"idNumber\":\"ABC123\"," 
                + "\"dateOfBirth\":\"" + LocalDate.now().minusYears(20) + "\""
                + "}";

        mockMvc.perform(post("/api/customers")
                        .contentType(APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.idType").value("PASSPORT"))
                .andExpect(jsonPath("$.idNumber").value("ABC123"))
                .andExpect(jsonPath("$.dateOfBirth").value(LocalDate.now().minusYears(20).toString()));
    }

    @Test
    void createCustomer_missingRequiredKycFields_returns400() throws Exception {
        String email = "missing-kyc-" + System.currentTimeMillis() + "@example.com";
        String json = "{"
                + "\"name\":\"Bob\"," 
                + "\"email\":\"" + email + "\"," 
                + "\"phone\":\"111\"," 
                + "\"address\":\"Addr\""
                + "}";

        mockMvc.perform(post("/api/customers")
                        .contentType(APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCustomer_futureDateOfBirth_returns400() throws Exception {
        String email = "future-dob-" + System.currentTimeMillis() + "@example.com";
        String json = "{"
                + "\"name\":\"Carol\"," 
                + "\"email\":\"" + email + "\"," 
                + "\"phone\":\"111\"," 
                + "\"address\":\"Addr\"," 
                + "\"idType\":\"NATIONAL_ID\"," 
                + "\"idNumber\":\"NI123\"," 
                + "\"dateOfBirth\":\"" + LocalDate.now().plusDays(1) + "\""
                + "}";

        mockMvc.perform(post("/api/customers")
                        .contentType(APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCustomer_idNumberTooLong_returns400() throws Exception {
        String email = "long-id-" + System.currentTimeMillis() + "@example.com";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            sb.append('a');
        }

        String json = "{"
                + "\"name\":\"Dave\"," 
                + "\"email\":\"" + email + "\"," 
                + "\"phone\":\"111\"," 
                + "\"address\":\"Addr\"," 
                + "\"idType\":\"DRIVER_LICENSE\"," 
                + "\"idNumber\":\"" + sb + "\"," 
                + "\"dateOfBirth\":\"" + LocalDate.now().minusYears(30) + "\""
                + "}";

        mockMvc.perform(post("/api/customers")
                        .contentType(APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCustomer_invalidIdType_returns400() throws Exception {
        String email = "invalid-idtype-" + System.currentTimeMillis() + "@example.com";
        String json = "{"
                + "\"name\":\"Eve\"," 
                + "\"email\":\"" + email + "\"," 
                + "\"phone\":\"111\"," 
                + "\"address\":\"Addr\"," 
                + "\"idType\":\"NOT_A_REAL_TYPE\"," 
                + "\"idNumber\":\"X123\"," 
                + "\"dateOfBirth\":\"" + LocalDate.now().minusYears(18) + "\""
                + "}";

        mockMvc.perform(post("/api/customers")
                        .contentType(APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
}
