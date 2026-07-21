package com.skillswap.controller;

import com.skillswap.dto.AdminSkillRequest;
import com.skillswap.dto.SkillDto;
import com.skillswap.service.CurrentUser;
import com.skillswap.service.SkillService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/skills")
public class AdminSkillController {

    private final SkillService skillService;
    private final CurrentUser currentUser;

    public AdminSkillController(SkillService skillService, CurrentUser currentUser) {
        this.skillService = skillService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<SkillDto> create(@Valid @RequestBody AdminSkillRequest req) {
        currentUser.requireAdmin();
        return ResponseEntity.status(HttpStatus.CREATED).body(skillService.createSkill(req));
    }

    @PutMapping("/{id}")
    public SkillDto update(@PathVariable Long id, @Valid @RequestBody AdminSkillRequest req) {
        currentUser.requireAdmin();
        return skillService.updateSkill(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        currentUser.requireAdmin();
        skillService.deleteSkill(id);
        return ResponseEntity.noContent().build();
    }
}
