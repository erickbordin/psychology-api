package com.psychologyapi.psychologyapi.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.service.SaudeService;
import com.psychologyapi.psychologyapi.service.SaudeService.Backup;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SaudeController {

    private final SaudeService saudeService;

    @GetMapping("/saude")
    public ResponseEntity<Map<String, String>> saude() {
        Backup backup = saudeService.estado();
        HttpStatus status = backup == Backup.ATRASADO ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
        return ResponseEntity.status(status).body(Map.of("status", "ok", "backup", backup.rotulo()));
    }
}
