package com.vivekkrishnan.fitbook.service;

import com.vivekkrishnan.fitbook.dto.HomeDTO;
import com.vivekkrishnan.fitbook.repository.HomeRepository;
import org.springframework.stereotype.Service;

@Service
public class HomeService {

    private final HomeRepository homeRepository;

    public HomeService(HomeRepository homeRepository) {
        this.homeRepository = homeRepository;
    }

    public HomeDTO getHomeData() {
        return new HomeDTO(
                homeRepository.findAllUsers(),
                homeRepository.findAllTrainers(),
                homeRepository.findAllServices()
        );
    }
}
