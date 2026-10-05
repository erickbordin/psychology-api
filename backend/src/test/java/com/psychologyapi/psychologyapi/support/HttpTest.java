package com.psychologyapi.psychologyapi.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contexto compartilhado pelos testes da camada HTTP.
 *
 * O contexto so sobe com JWT_SECRET resolvivel (TokenService le a variavel
 * direto com @Value) e com um datasource valido, aqui fornecido pelo container.
 *
 * Duas razoes para a anotacao ser composta em vez de repetida em cada classe:
 * o MockMvc roda na mesma thread do teste, entao o @Transactional rola a
 * transacao de volta e uma classe nao suja o banco da outra; e anotacao
 * identica em todas elas significa a mesma chave de cache de contexto no
 * Spring, ou seja, um unico boot para a camada inteira.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        "JWT_SECRET=c2VncmVkb2RldGVzdGVjb21wcmlkb29zdWZpY2llbnRlcGFyYWhtYWNzaGEyNTY=",
        "app.avisos.habilitado=false",
        "app.termos.diretorio=classpath:termos-teste/",
        "app.cadastro.convites=teste-1,teste-2,teste-3,teste-4,teste-5",
        "app.limite.por-minuto=100000"
})
@Import({PostgresContainerConfig.class, CarteiroDeTeste.class})
@AutoConfigureMockMvc
@Transactional
public @interface HttpTest {
}
