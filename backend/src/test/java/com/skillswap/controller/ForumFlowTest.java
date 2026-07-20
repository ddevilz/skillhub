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
class ForumFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private String register(String email) throws Exception {
        String body = json.writeValueAsString(Map.of("fullName", email, "email", email, "password", "password1"));
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(res, "$.token");
    }

    private Long seedCategory(String name) {
        // NOTE: forum_categories.created_date has no DB-level default in the H2
        // test schema (Hibernate ddl-auto=create-drop builds it from entity
        // annotations, and ForumCategory.createdDate has none — unlike
        // User.createdDate, which carries @ColumnDefault for this exact reason).
        // Supplying CURRENT_TIMESTAMP here keeps this raw-JDBC seed working
        // without touching the Task 1 entity.
        jdbc.update("INSERT INTO forum_categories(category_name, created_date) VALUES (?, CURRENT_TIMESTAMP)", name);
        return jdbc.queryForObject("SELECT id FROM forum_categories WHERE category_name = ?", Long.class, name);
    }

    @Test
    void createPostCommentAndUpvoteFlow() throws Exception {
        String authorToken = register("forum-author@example.com");
        String otherToken = register("forum-other@example.com");
        Long categoryId = seedCategory("Test Category " + System.identityHashCode(this));

        String postBody = json.writeValueAsString(Map.of("title", "Hello forum", "content", "First post!"));
        String postRes = mvc.perform(post("/api/forum/categories/{id}/posts", categoryId)
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(postBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorName").exists())
                .andReturn().getResponse().getContentAsString();
        Long postId = ((Number) com.jayway.jsonpath.JsonPath.read(postRes, "$.id")).longValue();

        mvc.perform(get("/api/forum/categories/{id}/posts", categoryId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(postId.intValue()));

        String commentBody = json.writeValueAsString(Map.of("commentText", "Nice first post!"));
        mvc.perform(post("/api/forum/posts/{id}/comments", postId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content(commentBody))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/forum/posts/{id}/upvote", postId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.upvoteCount").value(1))
                .andExpect(jsonPath("$.commentCount").value(1));

        // Duplicate upvote rejected
        mvc.perform(post("/api/forum/posts/{id}/upvote", postId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isConflict());

        // Author got a FORUM notification for the comment
        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("FORUM"));
    }

    @Test
    void deletingSomeoneElsesPostReturns404() throws Exception {
        String authorToken = register("forum-owner@example.com");
        String otherToken = register("forum-intruder@example.com");
        Long categoryId = seedCategory("Delete Test " + System.identityHashCode(this));

        String postBody = json.writeValueAsString(Map.of("title", "Mine", "content", "Do not delete"));
        String postRes = mvc.perform(post("/api/forum/categories/{id}/posts", categoryId)
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(postBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long postId = ((Number) com.jayway.jsonpath.JsonPath.read(postRes, "$.id")).longValue();

        mvc.perform(delete("/api/forum/posts/{id}", postId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void forumEndpointsRequireAuth() throws Exception {
        mvc.perform(get("/api/forum/categories")).andExpect(status().isUnauthorized());
    }
}
