package com.psychologyapi.psychologyapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.psychologyapi.psychologyapi.support.PostgresContainerConfig;

/**
 * O contexto so sobe com JWT_SECRET resolvivel (TokenService le a variavel
 * direto com @Value) e com um datasource valido, aqui fornecido pelo container.
 */
@SpringBootTest(properties = "JWT_SECRET=c2VncmVkb2RldGVzdGVjb21wcmlkb29zdWZpY2llbnRlcGFyYWhtYWNzaGEyNTY=")
@Import(PostgresContainerConfig.class)
class PsychologyApiApplicationTests {

    @Test
    void contextoSobe() {
    }
}
