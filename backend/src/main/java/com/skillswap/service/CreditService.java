package com.skillswap.service;

import com.skillswap.entity.CreditTransaction;
import com.skillswap.entity.SkillCredit;
import com.skillswap.entity.TransactionType;
import com.skillswap.repository.CreditTransactionRepository;
import com.skillswap.repository.SkillCreditRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CreditService {

    /** Flat credit cost/reward per completed session. Upgrade path: per-skill or per-duration pricing. */
    public static final int SESSION_RATE = 1;

    private final SkillCreditRepository skillCreditRepository;
    private final CreditTransactionRepository creditTransactionRepository;

    public CreditService(SkillCreditRepository skillCreditRepository,
                         CreditTransactionRepository creditTransactionRepository) {
        this.skillCreditRepository = skillCreditRepository;
        this.creditTransactionRepository = creditTransactionRepository;
    }

    /** Read-only affordability check used to gate booking; does not mutate balance. */
    public boolean canAfford(Long userId) {
        return skillCreditRepository.findByUserId(userId)
                .map(c -> c.getTotalCredits() >= SESSION_RATE)
                .orElse(false);
    }

    /** The only place credits actually move: learner spends, teacher earns, both ledgered, atomically. */
    @Transactional
    public void settle(Long teacherUserId, Long learnerUserId, Long sessionId) {
        SkillCredit learnerCredit = skillCreditRepository.findByUserId(learnerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Learner credit account not found"));
        if (learnerCredit.getTotalCredits() < SESSION_RATE) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Learner has insufficient credits");
        }
        SkillCredit teacherCredit = skillCreditRepository.findByUserId(teacherUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Teacher credit account not found"));

        learnerCredit.setTotalCredits(learnerCredit.getTotalCredits() - SESSION_RATE);
        learnerCredit.setCreditsSpent(learnerCredit.getCreditsSpent() + SESSION_RATE);
        skillCreditRepository.save(learnerCredit);

        teacherCredit.setTotalCredits(teacherCredit.getTotalCredits() + SESSION_RATE);
        teacherCredit.setCreditsEarned(teacherCredit.getCreditsEarned() + SESSION_RATE);
        skillCreditRepository.save(teacherCredit);

        creditTransactionRepository.save(ledgerRow(learnerUserId, sessionId, TransactionType.SPENT));
        creditTransactionRepository.save(ledgerRow(teacherUserId, sessionId, TransactionType.EARNED));
    }

    public List<CreditTransaction> history(Long userId) {
        return creditTransactionRepository.findByUserIdOrderByTransactionDateDesc(userId);
    }

    private CreditTransaction ledgerRow(Long userId, Long sessionId, TransactionType type) {
        CreditTransaction t = new CreditTransaction();
        t.setUserId(userId);
        t.setSessionId(sessionId);
        t.setTransactionType(type);
        t.setAmount(SESSION_RATE);
        return t;
    }
}
