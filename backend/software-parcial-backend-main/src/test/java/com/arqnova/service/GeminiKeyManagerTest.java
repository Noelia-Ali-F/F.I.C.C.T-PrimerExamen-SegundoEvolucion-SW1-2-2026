package com.arqnova.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiKeyManagerTest {
    @Test
    void eliminaClavesVaciasYDuplicadasManteniendoCompatibilidadLegacy() {
        GeminiKeyManager manager = new GeminiKeyManager(" key-one ", "", "key-two", "key-one");

        assertThat(manager.keysFromActive()).containsExactly("key-one", "key-two");
    }

    @Test
    void avanzaDeFormaAtomicaAlMarcarCuotaAgotada() {
        GeminiKeyManager manager = new GeminiKeyManager("key-one", "key-two", "key-three", "");

        manager.markQuotaExhausted("key-one");

        assertThat(manager.keysFromActive()).containsExactly("key-two", "key-three", "key-one");
        assertThat(manager.activeIndex()).isEqualTo(1);
    }

    @Test
    void conservaLaClaveHistoricaComoPrincipalDuranteLaMigracion() {
        GeminiKeyManager manager = new GeminiKeyManager("", "key-two", "", "legacy-key");

        assertThat(manager.keysFromActive()).containsExactly("legacy-key", "key-two");
    }
}
