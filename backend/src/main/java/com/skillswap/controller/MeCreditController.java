package com.skillswap.controller;

import com.skillswap.dto.CreditTransactionDto;
import com.skillswap.dto.SkillCreditDto;
import com.skillswap.entity.SkillCredit;
import com.skillswap.repository.SkillCreditRepository;
import com.skillswap.service.CreditService;
import com.skillswap.service.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/me/credits")
public class MeCreditController {

    private final SkillCreditRepository skillCreditRepository;
    private final CreditService creditService;
    private final CurrentUser currentUser;

    public MeCreditController(SkillCreditRepository skillCreditRepository, CreditService creditService,
                              CurrentUser currentUser) {
        this.skillCreditRepository = skillCreditRepository;
        this.creditService = creditService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public SkillCreditDto balance() {
        Long userId = currentUser.require().getId();
        SkillCredit c = skillCreditRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credit account not found"));
        return new SkillCreditDto(c.getTotalCredits(), c.getCreditsEarned(), c.getCreditsSpent());
    }

    @GetMapping("/transactions")
    public List<CreditTransactionDto> transactions() {
        Long userId = currentUser.require().getId();
        return creditService.history(userId).stream()
                .map(t -> new CreditTransactionDto(t.getId(), t.getSessionId(),
                        t.getTransactionType().name(), t.getAmount(), t.getTransactionDate()))
                .toList();
    }
}
