package com.skillswap.controller;

import com.skillswap.dto.SkillDto;
import com.skillswap.service.SkillService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) { this.skillService = skillService; }

    @GetMapping("/skills")
    public List<SkillDto> skills() { return skillService.catalog(); }

    @GetMapping("/categories")
    public List<String> categories() { return skillService.categories(); }
}
