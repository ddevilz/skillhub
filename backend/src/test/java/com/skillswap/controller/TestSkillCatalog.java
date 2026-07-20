package com.skillswap.controller;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The "test" Spring profile disables Flyway (Hibernate ddl-auto=create-drop builds the schema
 * from entities instead), so V3__seed_skills.sql never runs against the H2 test database.
 * Multiple @SpringBootTest classes share one cached Spring context (and therefore one H2
 * instance) when their configuration matches, so this seeds the catalog at most once regardless
 * of which flow test class runs first, and every caller gets the same real, generated skill id
 * for "Python" instead of assuming a hardcoded value.
 */
final class TestSkillCatalog {

    private TestSkillCatalog() {}

    static Long seedCatalogAndGetPythonId(JdbcTemplate jdbc) {
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
}
