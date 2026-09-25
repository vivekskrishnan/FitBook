package com.vivekkrishnan.fitbook.controller;

import com.vivekkrishnan.fitbook.dto.HomeDTO;
import com.vivekkrishnan.fitbook.service.HomeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping("/")
    public HomeDTO home() {
        return homeService.getHomeData();
    }
}
