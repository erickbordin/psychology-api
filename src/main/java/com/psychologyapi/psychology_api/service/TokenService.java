package com.psychologyapi.psychology_api.service;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;

@Service
public class TokenService {
    
    @Value("${JWT_SECRET}")
    private String secret;

    private SecretKey gerarChave(){
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private String gerarToken(String email){
        return Jwts.builder()
        .subject(email)
        .issuedAt(pegarHoraAtual())
        .expiration(expiracaoDoToken())
        .signWith(gerarChave())
        .compact();
    }

    public String validarTokenERetornarEmail(String token){
        JwtParser jwtParser = Jwts.parser()
        .verifyWith(gerarChave())
        .build();
        Claims claims = jwtParser.parseSignedClaims(token).getPayload();
        return claims.getSubject();
    }

    private Date pegarHoraAtual(){
        return new Date(System.currentTimeMillis());
    }

    private Date expiracaoDoToken(){
        return new Date(System.currentTimeMillis() + 900000 );
    }

}
