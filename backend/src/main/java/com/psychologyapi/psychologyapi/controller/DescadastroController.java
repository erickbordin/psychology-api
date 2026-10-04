package com.psychologyapi.psychologyapi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.controller.dto.paciente.DescadastroDTO;
import com.psychologyapi.psychologyapi.service.DescadastroService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/descadastro")
@RequiredArgsConstructor
public class DescadastroController {

    private final DescadastroService descadastroService;

    @GetMapping("/{token}")
    public ResponseEntity<DescadastroDTO> consultar(@PathVariable String token) {
        return ResponseEntity.ok(descadastroService.consultar(token));
    }

    @PostMapping("/{token}")
    public ResponseEntity<DescadastroDTO> descadastrar(@PathVariable String token) {
        return ResponseEntity.ok(descadastroService.descadastrar(token));
    }
}
