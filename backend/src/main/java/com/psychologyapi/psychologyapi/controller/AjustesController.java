package com.psychologyapi.psychologyapi.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psychologyapi.psychologyapi.authsecurity.CookieRefreshToken;
import com.psychologyapi.psychologyapi.controller.dto.ajustes.AjustesAtualizacaoDTO;
import com.psychologyapi.psychologyapi.controller.dto.ajustes.AjustesResponseDTO;
import com.psychologyapi.psychologyapi.controller.dto.ajustes.TrocaDeSenhaDTO;
import com.psychologyapi.psychologyapi.service.AjustesService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/ajustes")
@RequiredArgsConstructor
public class AjustesController {

    private final AjustesService ajustesService;
    private final CookieRefreshToken cookieRefreshToken;

    @GetMapping
    public ResponseEntity<AjustesResponseDTO> obter() {
        return ResponseEntity.ok(ajustesService.obter());
    }

    @PutMapping
    public ResponseEntity<AjustesResponseDTO> atualizar(@RequestBody @Valid AjustesAtualizacaoDTO dto) {
        return ResponseEntity.ok(ajustesService.atualizar(dto));
    }

    @PostMapping("/senha")
    public ResponseEntity<Void> trocarSenha(@RequestBody @Valid TrocaDeSenhaDTO dto) {
        String token = ajustesService.trocarSenha(dto);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRefreshToken.emitir(token).toString())
                .build();
    }
}
