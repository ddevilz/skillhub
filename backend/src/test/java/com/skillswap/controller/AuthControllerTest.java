package com.skillswap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String body(Map<String, Object> m) throws Exception { return json.writeValueAsString(m); }

    @Test
    void registerThenLoginThenAccessMe() throws Exception {
        String reg = body(Map.of("fullName", "Deva", "email", "deva@example.com", "password", "password1"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(reg))
           .andExpect(status().isCreated());

        String login = body(Map.of("email", "deva@example.com", "password", "password1"));
        String token = com.jayway.jsonpath.JsonPath.read(
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.token").exists())
               .andExpect(jsonPath("$.email").value("deva@example.com"))
               .andReturn().getResponse().getContentAsString(), "$.token");

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.email").value("deva@example.com"));
    }

    @Test
    void duplicateRegistrationReturns409() throws Exception {
        String reg = body(Map.of("fullName", "Deva", "email", "dup@example.com", "password", "password1"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(reg))
           .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(reg))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.error").value(409))
           .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void invalidBodyReturns400() throws Exception {
        String bad = body(Map.of("fullName", "", "email", "notanemail", "password", "x"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(bad))
           .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        String reg = body(Map.of("fullName", "Deva", "email", "wrongpw@example.com", "password", "password1"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(reg))
           .andExpect(status().isCreated());
        String login = body(Map.of("email", "wrongpw@example.com", "password", "nope"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }
}
