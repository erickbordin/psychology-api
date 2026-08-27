package com.psychologyapi.psychologyapi.service;

import com.psychologyapi.psychologyapi.model.RefreshToken;

/**
 * O token em claro so existe nesta viagem: dali em diante vive no cookie do
 * navegador e, do lado do servidor, apenas como hash na linha persistida.
 */
public record RefreshEmitido(String tokenEmClaro, RefreshToken registro) {
}
