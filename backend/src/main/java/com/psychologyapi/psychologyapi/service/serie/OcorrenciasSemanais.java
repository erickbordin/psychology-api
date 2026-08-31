package com.psychologyapi.psychologyapi.service.serie;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Fonte unica da regra semanal da consulta recorrente.
 *
 * Existe como classe propria porque duas camadas precisam da mesma aritmetica:
 * o validador do DTO, para contar quantas ocorrencias um dtLimite produz, e o
 * service, para gerar as ocorrencias. Duplicar a regra e como a serie passaria a
 * divergir da validacao.
 *
 * plusWeeks preserva a hora local de proposito: "toda terca as 15h" continua as
 * 15h mesmo que o horario de verao volte.
 */
public final class OcorrenciasSemanais {

    /** Limites da serie. Moram aqui, e nao no validador do DTO, porque o service
     *  tambem precisa deles: service importando classe de validacao de DTO seria
     *  inversao de camada. */
    public static final int MIN_OCORRENCIAS = 2;
    public static final int MAX_OCORRENCIAS = 52;

    private OcorrenciasSemanais() {
    }

    public static List<LocalDateTime> aPartirDe(LocalDateTime primeira, int quantidade) {
        List<LocalDateTime> ocorrencias = new ArrayList<>(quantidade);
        for (int i = 0; i < quantidade; i++) {
            ocorrencias.add(primeira.plusWeeks(i));
        }
        return List.copyOf(ocorrencias);
    }

    /**
     * Para em teto + 1 ocorrencias: quem chama so precisa saber que estourou, e
     * um dtLimite digitado errado nao vira uma lista de milhares de datas.
     */
    public static List<LocalDateTime> ate(LocalDateTime primeira, LocalDate limite, int teto) {
        LocalDateTime fim = limite.atTime(LocalTime.MAX);
        List<LocalDateTime> ocorrencias = new ArrayList<>();
        LocalDateTime atual = primeira;
        while (!atual.isAfter(fim) && ocorrencias.size() <= teto) {
            ocorrencias.add(atual);
            atual = atual.plusWeeks(1);
        }
        return List.copyOf(ocorrencias);
    }
}
