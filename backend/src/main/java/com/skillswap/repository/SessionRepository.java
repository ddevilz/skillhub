package com.skillswap.repository;

import com.skillswap.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionRepository extends JpaRepository<Session, Long> {
    List<Session> findByTeacherUserIdOrLearnerUserId(Long teacherUserId, Long learnerUserId);
}
