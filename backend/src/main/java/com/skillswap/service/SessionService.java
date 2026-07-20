package com.skillswap.service;

import com.skillswap.dto.CreateSessionRequest;
import com.skillswap.dto.RescheduleSessionRequest;
import com.skillswap.dto.SessionDto;
import com.skillswap.entity.Match;
import com.skillswap.entity.MatchStatus;
import com.skillswap.entity.Session;
import com.skillswap.entity.SessionMode;
import com.skillswap.entity.SessionStatus;
import com.skillswap.repository.MatchRepository;
import com.skillswap.repository.SessionRepository;
import com.skillswap.repository.SkillRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final MatchRepository matchRepository;
    private final CreditService creditService;
    private final SkillRepository skillRepository;
    private final BadgeService badgeService;

    public SessionService(SessionRepository sessionRepository, MatchRepository matchRepository,
                          CreditService creditService, SkillRepository skillRepository,
                          BadgeService badgeService) {
        this.sessionRepository = sessionRepository;
        this.matchRepository = matchRepository;
        this.creditService = creditService;
        this.skillRepository = skillRepository;
        this.badgeService = badgeService;
    }

    public SessionDto create(Long meId, CreateSessionRequest req) {
        Match match = matchRepository.findById(req.matchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found"));
        if (!match.getUserAId().equals(meId) && !match.getUserBId().equals(meId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found");
        }
        if (match.getStatus() != MatchStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Match is not accepted");
        }
        Long other = match.getUserAId().equals(meId) ? match.getUserBId() : match.getUserAId();
        if (!req.teacherUserId().equals(meId) && !req.teacherUserId().equals(other)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "teacherUserId must be a match participant");
        }
        Long learnerUserId = req.teacherUserId().equals(meId) ? other : meId;

        if (!creditService.canAfford(learnerUserId)) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Learner has insufficient credits");
        }

        if (skillRepository.findById(req.skillId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found");
        }

        Session s = new Session();
        s.setMatchId(match.getId());
        s.setSkillId(req.skillId());
        s.setTeacherUserId(req.teacherUserId());
        s.setLearnerUserId(learnerUserId);
        s.setScheduledByUserId(meId);
        s.setSessionDate(req.sessionDate());
        s.setStartTime(req.startTime());
        s.setEndTime(req.endTime());
        s.setMode(parseMode(req.mode()));
        s.setLocationOrLink(req.locationOrLink());
        s.setStatus(SessionStatus.PENDING);
        return toDto(sessionRepository.save(s));
    }

    public SessionDto confirm(Long meId, Long sessionId) {
        Session s = requireParticipant(meId, sessionId);
        if (s.getScheduledByUserId().equals(meId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only the other participant can confirm");
        }
        if (s.getStatus() != SessionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is not pending");
        }
        s.setStatus(SessionStatus.CONFIRMED);
        return toDto(sessionRepository.save(s));
    }

    public SessionDto cancel(Long meId, Long sessionId) {
        Session s = requireParticipant(meId, sessionId);
        if (s.getStatus() == SessionStatus.COMPLETED || s.getStatus() == SessionStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session already finalized");
        }
        s.setStatus(SessionStatus.CANCELLED);
        return toDto(sessionRepository.save(s));
    }

    public SessionDto reschedule(Long meId, Long sessionId, RescheduleSessionRequest req) {
        Session s = requireParticipant(meId, sessionId);
        if (s.getStatus() != SessionStatus.PENDING && s.getStatus() != SessionStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending or confirmed sessions can be rescheduled");
        }
        s.setSessionDate(req.sessionDate());
        s.setStartTime(req.startTime());
        s.setEndTime(req.endTime());
        s.setScheduledByUserId(meId); // rescheduler proposes; the other party must reconfirm
        s.setStatus(SessionStatus.PENDING);
        return toDto(sessionRepository.save(s));
    }

    @Transactional
    public SessionDto complete(Long meId, Long sessionId) {
        Session s = requireParticipant(meId, sessionId);
        if (s.getStatus() != SessionStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only confirmed sessions can be completed");
        }
        creditService.settle(s.getTeacherUserId(), s.getLearnerUserId(), sessionId);
        badgeService.evaluateAndAward(s.getTeacherUserId(), s.getSkillId());
        s.setStatus(SessionStatus.COMPLETED);
        return toDto(sessionRepository.save(s));
    }

    public List<SessionDto> mySessions(Long meId, String filter) {
        List<Session> all = sessionRepository.findByTeacherUserIdOrLearnerUserId(meId, meId);
        List<Session> filtered = switch (filter == null ? "all" : filter) {
            case "upcoming" -> all.stream()
                    .filter(s -> s.getStatus() == SessionStatus.PENDING || s.getStatus() == SessionStatus.CONFIRMED)
                    .toList();
            case "past" -> all.stream().filter(s -> s.getStatus() == SessionStatus.COMPLETED).toList();
            case "cancelled" -> all.stream().filter(s -> s.getStatus() == SessionStatus.CANCELLED).toList();
            default -> all;
        };
        return filtered.stream()
                .sorted(Comparator.comparing(Session::getSessionDate).thenComparing(Session::getStartTime))
                .map(this::toDto)
                .toList();
    }

    private Session requireParticipant(Long meId, Long sessionId) {
        Session s = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        if (!s.getTeacherUserId().equals(meId) && !s.getLearnerUserId().equals(meId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        return s;
    }

    private SessionMode parseMode(String raw) {
        try {
            return SessionMode.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode must be ONLINE or OFFLINE");
        }
    }

    private SessionDto toDto(Session s) {
        return new SessionDto(s.getId(), s.getMatchId(), s.getSkillId(), s.getTeacherUserId(), s.getLearnerUserId(),
                s.getScheduledByUserId(), s.getSessionDate(), s.getStartTime(), s.getEndTime(),
                s.getMode() == null ? null : s.getMode().name(), s.getLocationOrLink(),
                s.getStatus().name(), s.getCreatedDate());
    }
}
