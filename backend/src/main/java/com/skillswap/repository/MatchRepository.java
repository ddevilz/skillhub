package com.skillswap.repository;

import com.skillswap.entity.Match;
import com.skillswap.entity.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {
    boolean existsByUserAIdAndUserBIdAndStatus(Long userAId, Long userBId, MatchStatus status);
    List<Match> findByUserAIdOrUserBId(Long userAId, Long userBId);
    Optional<Match> findByIdAndUserBId(Long id, Long userBId);
}
