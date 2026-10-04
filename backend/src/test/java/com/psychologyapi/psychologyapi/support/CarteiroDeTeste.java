package com.psychologyapi.psychologyapi.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailSendException;

import com.psychologyapi.psychologyapi.service.aviso.Carteiro;
import com.psychologyapi.psychologyapi.service.aviso.Mensagem;

@TestConfiguration(proxyBeanMethods = false)
public class CarteiroDeTeste {

    @Bean
    @Primary
    Falso carteiroDeTeste() {
        return new Falso();
    }

    public static class Falso implements Carteiro {

        private final List<Mensagem> enviadas = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger falhasPendentes = new AtomicInteger();

        @Override
        public void enviar(Mensagem mensagem) {
            if (falhasPendentes.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
                throw new MailSendException("falha simulada");
            }
            enviadas.add(mensagem);
        }

        public List<Mensagem> enviadas() {
            synchronized (enviadas) {
                return new ArrayList<>(enviadas);
            }
        }

        public void limpar() {
            enviadas.clear();
            falhasPendentes.set(0);
        }

        public void falharNasProximas(int n) {
            falhasPendentes.set(n);
        }
    }
}
