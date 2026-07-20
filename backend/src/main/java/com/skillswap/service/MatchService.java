package com.skillswap.service;

import com.skillswap.dto.MatchDto;
import com.skillswap.dto.MatchSuggestionDto;
import com.skillswap.entity.Match;
import com.skillswap.entity.MatchStatus;
import com.skillswap.entity.NotificationType;
import com.skillswap.entity.SkillType;
import com.skillswap.entity.User;
import com.skillswap.repository.MatchProjection;
import com.skillswap.repository.MatchRepository;
import com.skillswap.repository.UserRepository;
import com.skillswap.repository.UserSkillRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class MatchService {

    private final UserSkillRepository userSkillRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public MatchService(UserSkillRepository userSkillRepository, MatchRepository matchRepository,
                        UserRepository userRepository, NotificationService notificationService) {
        this.userSkillRepository = userSkillRepository;
        this.matchRepository = matchRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Cacheable(value = "suggestions", key = "#userId + '-' + (#city == null ? '' : #city) + '-' + (#category == null ? '' : #category)")
    public List<MatchSuggestionDto> suggestions(Long userId, String city, String category) {
        long wanted = userSkillRepository.countByUserIdAndSkillType(userId, SkillType.WANT_TO_LEARN);
        List<MatchProjection> rows = userSkillRepository.findSuggestions(
                userId, city == null ? "" : city, category == null ? "" : category);
        return rows.stream().map(r -> new MatchSuggestionDto(
                r.getUserId(), r.getFullName(), r.getCity(), r.getMatchedSkills(),
                score(r.getMatchedSkills(), wanted))).toList();
    }

    public MatchDto request(Long meId, Long targetId) {
        if (meId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot match with yourself");
        }
        User target = userRepository.findById(targetId)
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (matchRepository.existsByUserAIdAndUserBIdAndStatus(meId, target.getId(), MatchStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Match request already pending");
        }
        Match m = new Match();
        m.setUserAId(meId);
        m.setUserBId(target.getId());
        m.setStatus(MatchStatus.PENDING);
        Match saved = matchRepository.save(m);
        notificationService.notify(target.getId(), NotificationType.MATCH, "You have a new match request.");
        return toDto(saved);
    }

    public MatchDto respond(Long meId, Long matchId, String status) {
        MatchStatus newStatus = parseStatus(status);
        Match m = matchRepository.findByIdAndUserBId(matchId, meId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found"));
        m.setStatus(newStatus);
        Match saved = matchRepository.save(m);
        notificationService.notify(m.getUserAId(), NotificationType.MATCH,
                "Your match request was " + newStatus.name().toLowerCase() + ".");
        return toDto(saved);
    }

    public List<MatchDto> myMatches(Long meId) {
        return matchRepository.findByUserAIdOrUserBId(meId, meId).stream().map(this::toDto).toList();
    }

    private int score(long matched, long wanted) {
        if (wanted <= 0) return 0;
        return (int) Math.round(100.0 * matched / wanted);
    }

    private MatchStatus parseStatus(String raw) {
        MatchStatus s;
        try {
            s = MatchStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be ACCEPTED or REJECTED");
        }
        if (s != MatchStatus.ACCEPTED && s != MatchStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be ACCEPTED or REJECTED");
        }
        return s;
    }

    private MatchDto toDto(Match m) {
        return new MatchDto(m.getId(), m.getUserAId(), m.getUserBId(), m.getStatus().name(), m.getCreatedDate());
    }
}
