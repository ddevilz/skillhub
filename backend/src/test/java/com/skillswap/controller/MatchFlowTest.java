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
class MatchFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private String register(String email) throws Exception {
        String body = json.writeValueAsString(Map.of("fullName", email, "email", email, "password", "password1"));
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(res, "$.token");
    }

    private Long addSkill(String token, long skillId, String type) throws Exception {
        String body = json.writeValueAsString(Map.of("skillId", skillId, "skillType", type));
        String res = mvc.perform(post("/api/me/skills").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(res, "$.id")).longValue();
    }

    // The "test" profile disables Flyway (Hibernate create-drop builds the schema instead), so
    // V3__seed_skills.sql never runs against this H2 instance. Reproduce its insert order here and
    // look up the generated id, instead of hardcoding it, so "Python" plays the same role the brief
    // describes for the real seeded catalog (V3 seeds it 4th) without depending on identity-column luck.
    private Long seedCatalogAndGetPythonId() {
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
        return jdbc.queryForObject("SELECT id FROM skill WHERE skill_name = ?", Long.class, "Python");
    }

    @Test
    void learnerSeesTeacherAndCanRequestMatch() throws Exception {
        // Seeded skill catalog (V3): Python is the 4th skill. Learner wants it, teacher teaches it.
        Long pythonId = seedCatalogAndGetPythonId();
        String learner = register("learner@example.com");
        String teacher = register("teacher@example.com");
        addSkill(learner, pythonId, "WANT_TO_LEARN");
        addSkill(teacher, pythonId, "CAN_TEACH");

        // teacher's numeric id (via /api/me)
        String teacherMe = mvc.perform(get("/api/me").header("Authorization", "Bearer " + teacher))
                .andReturn().getResponse().getContentAsString();
        Long teacherId = ((Number) com.jayway.jsonpath.JsonPath.read(teacherMe, "$.id")).longValue();

        mvc.perform(get("/api/matches/suggestions").header("Authorization", "Bearer " + learner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(teacherId.intValue()))
                .andExpect(jsonPath("$[0].compatibilityScore").value(100));

        String reqBody = json.writeValueAsString(Map.of("targetUserId", teacherId));
        mvc.perform(post("/api/matches/request").header("Authorization", "Bearer " + learner)
                        .contentType(MediaType.APPLICATION_JSON).content(reqBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void suggestionsRequireAuth() throws Exception {
        mvc.perform(get("/api/matches/suggestions")).andExpect(status().isUnauthorized());
    }
}
