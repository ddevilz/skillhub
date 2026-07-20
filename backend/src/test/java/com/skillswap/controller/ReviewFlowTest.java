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
class ReviewFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

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

    private Long completedSession(Long teacherId, Long learnerId) {
        jdbc.update("INSERT INTO matches(user_a_id,user_b_id,status) VALUES (?,?,?)", learnerId, teacherId, "ACCEPTED");
        Long matchId = jdbc.queryForObject(
                "SELECT id FROM matches WHERE user_a_id = ? AND user_b_id = ?", Long.class, learnerId, teacherId);
        // note: created_date is supplied explicitly here (unlike the matches insert above) because
        // Session, unlike Match, has no @ColumnDefault on createdDate — the H2 test schema (Hibernate
        // ddl-auto=create-drop) has no DB-level default for that NOT NULL column, so a raw-JDBC insert
        // bypassing @PrePersist must set it directly. Session.java is a frozen Plan 3 file, left untouched.
        jdbc.update("""
            INSERT INTO sessions(match_id, skill_id, teacher_user_id, learner_user_id, scheduled_by_user_id,
                                 session_date, start_time, end_time, mode, status, created_date)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            """, matchId, 1L, teacherId, learnerId, learnerId,
                java.sql.Date.valueOf("2026-08-01"), java.sql.Time.valueOf("10:00:00"),
                java.sql.Time.valueOf("11:00:00"), "ONLINE", "COMPLETED",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
        return jdbc.queryForObject("SELECT id FROM sessions WHERE match_id = ?", Long.class, matchId);
    }

    @Test
    void reviewCompletedSessionThenViewRating() throws Exception {
        String teacherToken = register("teacher-review@example.com");
        String learnerToken = register("learner-review@example.com");
        Long teacherId = meId(teacherToken);
        Long learnerId = meId(learnerToken);
        Long sessionId = completedSession(teacherId, learnerId);

        String body = json.writeValueAsString(Map.of("rating", 5, "comments", "Excellent teacher!"));
        mvc.perform(post("/api/sessions/{id}/review", sessionId)
                        .header("Authorization", "Bearer " + learnerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ratedUserId").value(teacherId.intValue()))
                .andExpect(jsonPath("$.rating").value(5));

        // note: Authorization header added here (brief's verbatim test omits it) — SecurityConfig
        // (Plan 1, frozen, out of this task's file list) requires auth on every non-/api/auth/** route,
        // same as every other GET in this codebase (see AuthControllerTest#meWithoutTokenReturns401,
        // MatchFlowTest#suggestionsRequireAuth, SessionFlowTest#sessionsRequireAuth). Making the rating
        // endpoint public would mean editing SecurityConfig, which this task must not touch.
        mvc.perform(get("/api/users/{id}/rating", teacherId).header("Authorization", "Bearer " + learnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.reviewCount").value(1));
    }

    @Test
    void duplicateReviewReturns409() throws Exception {
        String teacherToken = register("teacher-dup@example.com");
        String learnerToken = register("learner-dup@example.com");
        Long teacherId = meId(teacherToken);
        Long learnerId = meId(learnerToken);
        Long sessionId = completedSession(teacherId, learnerId);

        String body = json.writeValueAsString(Map.of("rating", 4, "comments", "Good"));
        mvc.perform(post("/api/sessions/{id}/review", sessionId)
                        .header("Authorization", "Bearer " + learnerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/sessions/{id}/review", sessionId)
                        .header("Authorization", "Bearer " + learnerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRatingReturns400() throws Exception {
        String teacherToken = register("teacher-badrating@example.com");
        String learnerToken = register("learner-badrating@example.com");
        Long teacherId = meId(teacherToken);
        Long learnerId = meId(learnerToken);
        Long sessionId = completedSession(teacherId, learnerId);

        String body = json.writeValueAsString(Map.of("rating", 9, "comments", "x"));
        mvc.perform(post("/api/sessions/{id}/review", sessionId)
                        .header("Authorization", "Bearer " + learnerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
