package com.skillswap.controller;

import com.skillswap.dto.CreateMatchRequest;
import com.skillswap.dto.MatchDto;
import com.skillswap.dto.MatchSuggestionDto;
import com.skillswap.dto.UpdateMatchRequest;
import com.skillswap.service.CurrentUser;
import com.skillswap.service.MatchService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matchService;
    private final CurrentUser currentUser;

    public MatchController(MatchService matchService, CurrentUser currentUser) {
        this.matchService = matchService;
        this.currentUser = currentUser;
    }

    @GetMapping("/suggestions")
    public List<MatchSuggestionDto> suggestions(@RequestParam(required = false) String city,
                                                @RequestParam(required = false) String category) {
        return matchService.suggestions(currentUser.require().getId(), city, category);
    }

    @GetMapping
    public List<MatchDto> myMatches() {
        return matchService.myMatches(currentUser.require().getId());
    }

    @PostMapping("/request")
    public ResponseEntity<MatchDto> request(@Valid @RequestBody CreateMatchRequest req) {
        MatchDto dto = matchService.request(currentUser.require().getId(), req.targetUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{id}")
    public MatchDto respond(@PathVariable Long id, @Valid @RequestBody UpdateMatchRequest req) {
        return matchService.respond(currentUser.require().getId(), id, req.status());
    }
}
