package com.psychologyapi.psychology_api.service.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.psychologyapi.psychology_api.authsecurity.CustomAuthentication;
import com.psychologyapi.psychology_api.model.Usuario;

@Component
public class SecurityService {
    
    public Usuario obterUsuarioLogado(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if(authentication instanceof CustomAuthentication customAuth){
            return customAuth.getUsuario();
        }

        throw new IllegalStateException("a autenticacao nao esta configurada");
    }

}
