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
class AdminModerationFlowTest {

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

    private String promoteAndLogin(String email) throws Exception {
        register(email);
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", email);
        return login(email);
    }

    private Long insertFlaggedReview() {
        jdbc.update("INSERT INTO users(full_name,email,password_hash,role,active) VALUES (?,?,?,?,?)",
                "flag-reviewer", "flag-reviewer@example.com", "hash", "USER", true);
        jdbc.update("INSERT INTO users(full_name,email,password_hash,role,active) VALUES (?,?,?,?,?)",
                "flag-rated", "flag-rated@example.com", "hash", "USER", true);
        Long reviewerId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, "flag-reviewer@example.com");
        Long ratedId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, "flag-rated@example.com");
        jdbc.update("INSERT INTO matches(user_a_id,user_b_id,status) VALUES (?,?,?)", reviewerId, ratedId, "ACCEPTED");
        Long matchId = jdbc.queryForObject("SELECT id FROM matches WHERE user_a_id = ?", Long.class, reviewerId);
        jdbc.update("""
            INSERT INTO sessions(match_id, skill_id, teacher_user_id, learner_user_id, scheduled_by_user_id,
                                 session_date, start_time, end_time, mode, status)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """, matchId, 1L, ratedId, reviewerId, reviewerId,
                java.sql.Date.valueOf("2026-08-01"), java.sql.Time.valueOf("10:00:00"),
                java.sql.Time.valueOf("11:00:00"), "ONLINE", "COMPLETED");
        Long sessionId = jdbc.queryForObject("SELECT id FROM sessions WHERE match_id = ?", Long.class, matchId);
        jdbc.update("INSERT INTO reviews(session_id, reviewer_user_id, rated_user_id, rating, flagged) VALUES (?,?,?,?,?)",
                sessionId, reviewerId, ratedId, 1, true);
        return jdbc.queryForObject("SELECT id FROM reviews WHERE session_id = ?", Long.class, sessionId);
    }

    @Test
    void adminSeesAndResolvesFlaggedReview() throws Exception {
        String adminToken = promoteAndLogin("moderation-admin@example.com");
        Long reviewId = insertFlaggedReview();

        mvc.perform(get("/api/admin/reviews/flagged").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + reviewId + ")]").exists());

        mvc.perform(put("/api/admin/reviews/{id}/unflag", reviewId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/reviews/flagged").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + reviewId + ")]").doesNotExist());
    }

    @Test
    void adminSeesModeratedForumPosts() throws Exception {
        String userToken = register("mod-queue-user@example.com");
        String adminToken = promoteAndLogin("mod-queue-admin@example.com");

        jdbc.update("INSERT INTO forum_categories(category_name) VALUES (?)", "Queue Test " + System.identityHashCode(this));
        Long categoryId = jdbc.queryForObject(
                "SELECT id FROM forum_categories WHERE category_name = ?", Long.class, "Queue Test " + System.identityHashCode(this));

        String postBody = json.writeValueAsString(Map.of("title", "Hide me", "content", "Body"));
        String postRes = mvc.perform(post("/api/forum/categories/{id}/posts", categoryId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(postBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long postId = ((Number) com.jayway.jsonpath.JsonPath.read(postRes, "$.id")).longValue();

        mvc.perform(put("/api/admin/forum/posts/{id}/moderate", postId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/forum/posts/moderated").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + postId + ")]").exists());
    }
}
