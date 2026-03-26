package br.com.urbana.connect;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import br.com.urbana.connect.application.health.MongoConnectivityVerifier;

@SpringBootTest
@ActiveProfiles("test")
class UrbanaConnectApplicationTests {

    @MockitoBean
    private MongoConnectivityVerifier mongoConnectivityVerifier;

    @Test
    void contextLoads() {
    }
}
