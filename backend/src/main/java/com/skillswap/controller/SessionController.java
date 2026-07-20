package com.skillswap.controller;

import com.skillswap.dto.CreateSessionRequest;
import com.skillswap.dto.RescheduleSessionRequest;
import com.skillswap.dto.SessionDto;
import com.skillswap.service.CurrentUser;
import com.skillswap.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final CurrentUser currentUser;

    public SessionController(SessionService sessionService, CurrentUser currentUser) {
        this.sessionService = sessionService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<SessionDto> create(@Valid @RequestBody CreateSessionRequest req) {
        SessionDto dto = sessionService.create(currentUser.require().getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    public List<SessionDto> mySessions(@RequestParam(required = false) String filter) {
        return sessionService.mySessions(currentUser.require().getId(), filter);
    }

    @PutMapping("/{id}/confirm")
    public SessionDto confirm(@PathVariable Long id) {
        return sessionService.confirm(currentUser.require().getId(), id);
    }

    @PutMapping("/{id}/cancel")
    public SessionDto cancel(@PathVariable Long id) {
        return sessionService.cancel(currentUser.require().getId(), id);
    }

    @PutMapping("/{id}/reschedule")
    public SessionDto reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleSessionRequest req) {
        return sessionService.reschedule(currentUser.require().getId(), id, req);
    }

    @PutMapping("/{id}/complete")
    public SessionDto complete(@PathVariable Long id) {
        return sessionService.complete(currentUser.require().getId(), id);
    }
}
