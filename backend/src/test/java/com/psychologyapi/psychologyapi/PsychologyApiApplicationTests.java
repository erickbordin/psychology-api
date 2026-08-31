package com.psychologyapi.psychologyapi;

import org.junit.jupiter.api.Test;

import com.psychologyapi.psychologyapi.support.HttpTest;

/**
 * Usa a mesma anotacao composta dos testes de HTTP para compartilhar o contexto
 * cacheado com eles, em vez de subir um segundo. Ver support/HttpTest.
 */
@HttpTest
class PsychologyApiApplicationTests {

    @Test
    void contextoSobe() {
    }
}
