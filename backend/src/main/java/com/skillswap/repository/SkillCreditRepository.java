package com.skillswap.repository;

import com.skillswap.entity.SkillCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SkillCreditRepository extends JpaRepository<SkillCredit, Long> {
    Optional<SkillCredit> findByUserId(Long userId);
}
