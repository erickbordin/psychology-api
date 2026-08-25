package com.psychologyapi.psychologyapi.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.controller.dto.auditoria.LogAuditoriaResponseDTO;
import com.psychologyapi.psychologyapi.service.AuditoriaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    @GetMapping
    public ResponseEntity<PagedModel<LogAuditoriaResponseDTO>> listar(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<LogAuditoriaResponseDTO> logs = auditoriaService.listar(pageable);
        return ResponseEntity.status(HttpStatus.OK).body(new PagedModel<>(logs));
    }

}
