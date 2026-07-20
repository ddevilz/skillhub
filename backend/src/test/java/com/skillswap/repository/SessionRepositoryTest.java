package com.skillswap.repository;

import com.skillswap.entity.Session;
import com.skillswap.entity.SessionMode;
import com.skillswap.entity.SessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SessionRepositoryTest {

    @Autowired SessionRepository sessionRepository;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Long insertUser(String email) {
        jdbc.update("INSERT INTO users(full_name,email,password_hash,role,active) VALUES (?,?,?,?,?)",
                email, email, "hash", "USER", true);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private Long insertAcceptedMatch(Long a, Long b) {
        jdbc.update("INSERT INTO matches(user_a_id,user_b_id,status) VALUES (?,?,?)", a, b, "ACCEPTED");
        return jdbc.queryForObject(
                "SELECT id FROM matches WHERE user_a_id = ? AND user_b_id = ?", Long.class, a, b);
    }

    @Test
    void savesAndFindsByTeacherOrLearner() {
        Long teacher = insertUser("teacher@example.com");
        Long learner = insertUser("learner@example.com");
        Long matchId = insertAcceptedMatch(learner, teacher);

        Session s = new Session();
        s.setMatchId(matchId);
        s.setSkillId(1L);
        s.setTeacherUserId(teacher);
        s.setLearnerUserId(learner);
        s.setScheduledByUserId(learner);
        s.setSessionDate(LocalDate.of(2026, 8, 1));
        s.setStartTime(LocalTime.of(10, 0));
        s.setEndTime(LocalTime.of(11, 0));
        s.setMode(SessionMode.ONLINE);
        s.setStatus(SessionStatus.PENDING);
        sessionRepository.save(s);

        assertThat(sessionRepository.findByTeacherUserIdOrLearnerUserId(teacher, teacher)).hasSize(1);
        assertThat(sessionRepository.findByTeacherUserIdOrLearnerUserId(learner, learner)).hasSize(1);
        assertThat(sessionRepository.findByTeacherUserIdOrLearnerUserId(999L, 999L)).isEmpty();
    }
}
