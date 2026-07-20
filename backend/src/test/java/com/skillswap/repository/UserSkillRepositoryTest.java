package com.skillswap.repository;

import com.skillswap.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserSkillRepositoryTest {

    @Autowired UserSkillRepository userSkillRepository;
    @Autowired SkillRepository skillRepository;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Long insertUser(String email, boolean active) {
        jdbc.update("INSERT INTO users(full_name,email,password_hash,role,active) VALUES (?,?,?,?,?)",
                email, email, "hash", "USER", active);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    @Test
    void findsSkillsByUserAndType() {
        Long uid = insertUser("teacher@example.com", true);
        Skill s = new Skill(); s.setSkillName("Guitar"); s.setCategory("Music");
        Long sid = skillRepository.save(s).getId();

        UserSkill us = new UserSkill();
        us.setUserId(uid); us.setSkillId(sid); us.setSkillType(SkillType.CAN_TEACH);
        userSkillRepository.save(us);

        assertThat(userSkillRepository.findByUserId(uid)).hasSize(1);
        assertThat(userSkillRepository.findByUserIdAndSkillType(uid, SkillType.CAN_TEACH)).hasSize(1);
        assertThat(userSkillRepository.countByUserIdAndSkillType(uid, SkillType.WANT_TO_LEARN)).isZero();
        assertThat(userSkillRepository.existsByUserIdAndSkillIdAndSkillType(uid, sid, SkillType.CAN_TEACH)).isTrue();
    }

    @Test
    void findsSuggestionsWhenCandidateTeachesWhatUserWants() {
        Long learner = insertUser("learner@example.com", true);
        Long teacher = insertUser("teacher2@example.com", true);
        Skill s = new Skill(); s.setSkillName("Python"); s.setCategory("Technology");
        Long sid = skillRepository.save(s).getId();

        UserSkill wants = new UserSkill();
        wants.setUserId(learner); wants.setSkillId(sid); wants.setSkillType(SkillType.WANT_TO_LEARN);
        userSkillRepository.save(wants);
        UserSkill teaches = new UserSkill();
        teaches.setUserId(teacher); teaches.setSkillId(sid); teaches.setSkillType(SkillType.CAN_TEACH);
        userSkillRepository.save(teaches);

        List<MatchProjection> hits = userSkillRepository.findSuggestions(learner, "", "");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getUserId()).isEqualTo(teacher);
        assertThat(hits.get(0).getMatchedSkills()).isEqualTo(1L);
    }
}
