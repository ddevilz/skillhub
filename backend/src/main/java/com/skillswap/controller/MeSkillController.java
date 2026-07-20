package com.skillswap.controller;

import com.skillswap.dto.AddUserSkillRequest;
import com.skillswap.dto.UserSkillDto;
import com.skillswap.service.CurrentUser;
import com.skillswap.service.SkillService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/me/skills")
public class MeSkillController {

    private final SkillService skillService;
    private final CurrentUser currentUser;

    public MeSkillController(SkillService skillService, CurrentUser currentUser) {
        this.skillService = skillService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<UserSkillDto> mySkills() {
        return skillService.mySkills(currentUser.require().getId());
    }

    @PostMapping
    public ResponseEntity<UserSkillDto> add(@Valid @RequestBody AddUserSkillRequest req) {
        UserSkillDto dto = skillService.add(currentUser.require().getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable Long id) {
        skillService.remove(currentUser.require().getId(), id);
        return ResponseEntity.noContent().build();
    }
}
