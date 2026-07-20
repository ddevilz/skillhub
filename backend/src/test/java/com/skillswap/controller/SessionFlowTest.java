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

    // The "test" profile disables Flyway (Hibernate create-drop builds the schema instead), so
    // V3__seed_skills.sql never runs against this H2 instance. Reproduce its insert order here and
    // look up the generated id, instead of hardcoding it, so "Python" plays the same role the brief
    // describes for the real seeded catalog (V3 seeds it 4th) without depending on identity-column luck.
    private Long seedCatalogAndGetPythonId() {
        // Guard: this test class has two @Test methods that both need the catalog, and the
        // cached Spring context (no @Transactional/@DirtiesContext) means the H2 schema and its
        // rows outlive a single test method. Only seed once per context so a second call (or a
        // sibling test class sharing the same cached context) finds Python already there instead
        // of inserting a duplicate row and breaking the lookup below.
        Long alreadySeeded = jdbc.queryForObject(
                "SELECT COUNT(*) FROM skill WHERE skill_name = ?", Long.class, "Python");
        if (alreadySeeded == null || alreadySeeded == 0) {
            jdbc.update("INSERT INTO skill(skill_name, category, description) VALUES (?,?,?)",
                    "Guitar", "Music", "Acoustic and electric guitar");
            jdbc.update("INSERT INTO skill(skill_name, category, description) VALUES (?,?,?)",
                    "Piano", "Music", "Keyboard fundamentals");
            jdbc.update("INSERT INTO skill(skill_name, category, description) VALUES (?,?,?)",
                    "Web Development", "Technology", "HTML, CSS, JavaScript");
            jdbc.update("INSERT INTO skill(skill_name, category, description) VALUES (?,?,?)",
                    "Python", "Technology", "Python programming");
            jdbc.update("INSERT INTO skill(skill_name, category, description) VALUES (?,?,?)",
                    "Spoken English", "Languages", "Conversational English");
            jdbc.update("INSERT INTO skill(skill_name, category, description) VALUES (?,?,?)",
                    "Spanish", "Languages", "Beginner Spanish");
            jdbc.update("INSERT INTO skill(skill_name, category, description) VALUES (?,?,?)",
                    "Sketching", "Arts", "Pencil sketching");
            jdbc.update("INSERT INTO skill(skill_name, category, description) VALUES (?,?,?)",
                    "Public Speaking", "Business", "Presentation skills");
        }
        return jdbc.queryForObject("SELECT id FROM skill WHERE skill_name = ?", Long.class, "Python");
    }

    private Long createSession(String token, Long matchId, Long teacherId, Long skillId) throws Exception {
        String body = json.writeValueAsString(Map.of(
                "matchId", matchId, "teacherUserId", teacherId, "skillId", skillId,
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
        Long skillId = seedCatalogAndGetPythonId();

        Long sessionId = createSession(learnerToken, matchId, teacherId, skillId);

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

        Long skillId = seedCatalogAndGetPythonId();
        String body = json.writeValueAsString(Map.of(
                "matchId", matchId, "teacherUserId", teacherId, "skillId", skillId,
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
