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
class UserProfileFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String register(String email) throws Exception {
        String body = json.writeValueAsString(Map.of("fullName", email, "email", email, "password", "password1"));
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(res, "$.token");
    }

    private Long meId(String token) throws Exception {
        String res = mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(res, "$.id")).longValue();
    }

    @Test
    void anyAuthenticatedUserCanLookUpAnotherUsersPublicProfile() throws Exception {
        String viewerToken = register("profile-viewer@example.com");
        String targetToken = register("profile-target@example.com");
        Long targetId = meId(targetToken);

        mvc.perform(get("/api/users/{id}", targetId).header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetId.intValue()))
                .andExpect(jsonPath("$.fullName").value("profile-target@example.com"));
    }

    @Test
    void unknownUserIdReturns404() throws Exception {
        String viewerToken = register("profile-viewer-2@example.com");
        mvc.perform(get("/api/users/{id}", 999999L).header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresAuth() throws Exception {
        mvc.perform(get("/api/users/{id}", 1L)).andExpect(status().isUnauthorized());
    }
}
