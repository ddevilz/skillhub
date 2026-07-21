package com.skillswap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCatalogFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private String register(String email) throws Exception {
        String body = json.writeValueAsString(Map.of("fullName", email, "email", email, "password", "password1"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String login(String email) throws Exception {
        String body = json.writeValueAsString(Map.of("email", email, "password", "password1"));
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(res, "$.token");
    }

    private Long meId(String token) throws Exception {
        String res = mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(res, "$.id")).longValue();
    }

    @Test
    void adminCreatesSkillThenGrantsVerifiedBadge() throws Exception {
        String userToken = register("catalog-user@example.com");
        Long userId = meId(userToken);
        register("catalog-admin@example.com");
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", "catalog-admin@example.com");
        String adminToken = login("catalog-admin@example.com");

        String skillBody = json.writeValueAsString(Map.of("skillName", "Chess", "category", "Games", "description", "Strategy board game"));
        String skillRes = mvc.perform(post("/api/admin/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(skillBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long skillId = ((Number) com.jayway.jsonpath.JsonPath.read(skillRes, "$.id")).longValue();

        mvc.perform(post("/api/admin/users/{id}/skills/{skillId}/verify", userId, skillId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mvc.perform(get("/api/users/{id}/badges", userId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].badgeType").value("VERIFIED"));
    }

    @Test
    void adminManagesForumCategory() throws Exception {
        register("category-admin@example.com");
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", "category-admin@example.com");
        String adminToken = login("category-admin@example.com");

        String body = json.writeValueAsString(Map.of("categoryName", "E-Sports " + System.identityHashCode(this), "description", "Competitive gaming"));
        mvc.perform(post("/api/admin/forum/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }
}
