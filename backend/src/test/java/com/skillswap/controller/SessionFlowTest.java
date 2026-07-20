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
class SessionFlowTest {

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

    private Long acceptedMatch(Long userAId, Long userBId) {
        jdbc.update("INSERT INTO matches(user_a_id,user_b_id,status) VALUES (?,?,?)",
                userAId, userBId, "ACCEPTED");
        return jdbc.queryForObject(
                "SELECT id FROM matches WHERE user_a_id = ? AND user_b_id = ?", Long.class, userAId, userBId);
    }

    private Long createSession(String token, Long matchId, Long teacherId) throws Exception {
        String body = json.writeValueAsString(Map.of(
                "matchId", matchId, "teacherUserId", teacherId, "skillId", 4,
                "sessionDate", "2026-08-01", "startTime", "10:00:00", "endTime", "11:00:00",
                "mode", "ONLINE", "locationOrLink", "https://meet.example/abc"));
        String res = mvc.perform(post("/api/sessions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(res, "$.id")).longValue();
    }

    @Test
    void bookConfirmCompleteSettlesCredits() throws Exception {
        String teacherToken = register("teacher-flow@example.com");
        String learnerToken = register("learner-flow@example.com");
        Long teacherId = meId(teacherToken);
        Long learnerId = meId(learnerToken);
        Long matchId = acceptedMatch(learnerId, teacherId);

        Long sessionId = createSession(learnerToken, matchId, teacherId);

        mvc.perform(put("/api/sessions/{id}/confirm", sessionId).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mvc.perform(put("/api/sessions/{id}/complete", sessionId).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mvc.perform(get("/api/me/credits").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCredits").value(11))
                .andExpect(jsonPath("$.creditsEarned").value(1));

        mvc.perform(get("/api/me/credits").header("Authorization", "Bearer " + learnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCredits").value(9))
                .andExpect(jsonPath("$.creditsSpent").value(1));

        mvc.perform(get("/api/me/credits/transactions").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionType").value("EARNED"));
    }

    @Test
    void bookingBlockedWhenLearnerHasNoCredits() throws Exception {
        String teacherToken = register("teacher-poor@example.com");
        String learnerToken = register("learner-poor@example.com");
        Long teacherId = meId(teacherToken);
        Long learnerId = meId(learnerToken);
        Long matchId = acceptedMatch(learnerId, teacherId);

        jdbc.update("UPDATE skill_credit SET total_credits = 0 WHERE user_id = ?", learnerId);

        String body = json.writeValueAsString(Map.of(
                "matchId", matchId, "teacherUserId", teacherId, "skillId", 4,
                "sessionDate", "2026-08-01", "startTime", "10:00:00", "endTime", "11:00:00",
                "mode", "ONLINE"));
        mvc.perform(post("/api/sessions").header("Authorization", "Bearer " + learnerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPaymentRequired());
    }

    @Test
    void sessionsRequireAuth() throws Exception {
        mvc.perform(get("/api/sessions")).andExpect(status().isUnauthorized());
    }
}
