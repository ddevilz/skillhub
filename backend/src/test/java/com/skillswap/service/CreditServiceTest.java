package com.skillswap.service;

import com.skillswap.entity.CreditTransaction;
import com.skillswap.entity.SkillCredit;
import com.skillswap.entity.TransactionType;
import com.skillswap.repository.CreditTransactionRepository;
import com.skillswap.repository.SkillCreditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreditServiceTest {

    private final SkillCreditRepository skillCreditRepo = mock(SkillCreditRepository.class);
    private final CreditTransactionRepository txRepo = mock(CreditTransactionRepository.class);
    private final CreditService service = new CreditService(skillCreditRepo, txRepo);

    private SkillCredit creditOf(Long userId, int total) {
        SkillCredit c = new SkillCredit(userId);
        c.setTotalCredits(total);
        return c;
    }

    @Test
    void canAffordTrueWhenBalanceSufficient() {
        when(skillCreditRepo.findByUserId(1L)).thenReturn(Optional.of(creditOf(1L, 5)));
        assertThat(service.canAfford(1L)).isTrue();
    }

    @Test
    void canAffordFalseWhenBalanceZero() {
        when(skillCreditRepo.findByUserId(1L)).thenReturn(Optional.of(creditOf(1L, 0)));
        assertThat(service.canAfford(1L)).isFalse();
    }

    @Test
    void canAffordFalseWhenNoAccount() {
        when(skillCreditRepo.findByUserId(1L)).thenReturn(Optional.empty());
        assertThat(service.canAfford(1L)).isFalse();
    }

    @Test
    void settleMovesCreditsAndWritesLedgerForBothParties() {
        SkillCredit teacher = creditOf(10L, 10);
        SkillCredit learner = creditOf(20L, 10);
        when(skillCreditRepo.findByUserId(20L)).thenReturn(Optional.of(learner));
        when(skillCreditRepo.findByUserId(10L)).thenReturn(Optional.of(teacher));

        service.settle(10L, 20L, 99L);

        assertThat(learner.getTotalCredits()).isEqualTo(9);
        assertThat(learner.getCreditsSpent()).isEqualTo(1);
        assertThat(teacher.getTotalCredits()).isEqualTo(11);
        assertThat(teacher.getCreditsEarned()).isEqualTo(1);

        verify(skillCreditRepo).save(learner);
        verify(skillCreditRepo).save(teacher);

        var captor = org.mockito.ArgumentCaptor.forClass(CreditTransaction.class);
        verify(txRepo, times(2)).save(captor.capture());
        List<CreditTransaction> saved = captor.getAllValues();
        assertThat(saved).extracting(CreditTransaction::getTransactionType)
                .containsExactlyInAnyOrder(TransactionType.SPENT, TransactionType.EARNED);
        assertThat(saved).allMatch(t -> t.getSessionId().equals(99L) && t.getAmount() == 1);
    }

    @Test
    void settleThrowsWhenLearnerInsufficientAtSettleTime() {
        when(skillCreditRepo.findByUserId(20L)).thenReturn(Optional.of(creditOf(20L, 0)));
        assertThatThrownBy(() -> service.settle(10L, 20L, 99L))
                .isInstanceOf(ResponseStatusException.class);
        verify(skillCreditRepo, never()).save(any());
    }

    @Test
    void historyReturnsUserTransactionsDescending() {
        CreditTransaction t = new CreditTransaction();
        when(txRepo.findByUserIdOrderByTransactionDateDesc(1L)).thenReturn(List.of(t));
        assertThat(service.history(1L)).containsExactly(t);
    }
}
