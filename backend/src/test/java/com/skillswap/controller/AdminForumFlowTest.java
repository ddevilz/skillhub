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
class AdminForumFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private String register(String email) throws Exception {
        String body = json.writeValueAsString(Map.of("fullName", email, "email", email, "password", "password1"));
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(res, "$.token");
    }

    private String promoteToAdminAndLogin(String email) throws Exception {
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", email);
        String body = json.writeValueAsString(Map.of("email", email, "password", "password1"));
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(res, "$.token");
    }

    private Long seedCategory(String name) {
        jdbc.update("INSERT INTO forum_categories(category_name) VALUES (?)", name);
        return jdbc.queryForObject("SELECT id FROM forum_categories WHERE category_name = ?", Long.class, name);
    }

    @Test
    void nonAdminCannotModerate() throws Exception {
        String userToken = register("plain-user@example.com");
        Long categoryId = seedCategory("Mod Test A " + System.identityHashCode(this));
        String postBody = json.writeValueAsString(Map.of("title", "Post", "content", "Body"));
        String postRes = mvc.perform(post("/api/forum/categories/{id}/posts", categoryId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(postBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long postId = ((Number) com.jayway.jsonpath.JsonPath.read(postRes, "$.id")).longValue();

        mvc.perform(put("/api/admin/forum/posts/{id}/moderate", postId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanModerateAndDeleteAnyonesPost() throws Exception {
        String userToken = register("regular-poster@example.com");
        register("future-admin@example.com");
        String adminToken = promoteToAdminAndLogin("future-admin@example.com");
        Long categoryId = seedCategory("Mod Test B " + System.identityHashCode(this));

        String postBody = json.writeValueAsString(Map.of("title", "Spam", "content", "Buy now"));
        String postRes = mvc.perform(post("/api/forum/categories/{id}/posts", categoryId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(postBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long postId = ((Number) com.jayway.jsonpath.JsonPath.read(postRes, "$.id")).longValue();

        mvc.perform(put("/api/admin/forum/posts/{id}/moderate", postId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Moderated post is now hidden from a normal read
        mvc.perform(get("/api/forum/posts/{id}", postId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/admin/forum/posts/{id}", postId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }
}
