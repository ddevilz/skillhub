package com.skillswap.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityChainTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;

    @Test
    void protectedRequestWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/secure-check"))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.error").value(401))
           .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void validTokenForUnknownUserReturns401NotServerError() throws Exception {
        String token = jwtService.generateToken("ghost@example.com");
        mvc.perform(get("/api/secure-check").header("Authorization", "Bearer " + token))
           .andExpect(status().isUnauthorized());
    }
}
