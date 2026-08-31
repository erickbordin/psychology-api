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
@SpringBootTest(properties = "JWT_SECRET=c2VncmVkb2RldGVzdGVjb21wcmlkb29zdWZpY2llbnRlcGFyYWhtYWNzaGEyNTY=")
@Import(PostgresContainerConfig.class)
@AutoConfigureMockMvc
@Transactional
public @interface HttpTest {
}
