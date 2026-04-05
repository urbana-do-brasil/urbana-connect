package br.com.urbana.connect.infrastructure.whatsapp;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WhatsAppCloudApiGatewayTest {

    @Test
    void shouldSendGreetingButtonsToWhatsAppCloudApi() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://graph.facebook.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WhatsAppCloudApiGateway gateway = new WhatsAppCloudApiGateway(builder.build(), "phone-number-id", "access-token");

        server.expect(requestTo("https://graph.facebook.com/v18.0/phone-number-id/messages"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer access-token"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""
                {
                  "messaging_product": "whatsapp",
                  "to": "+5583999999999",
                  "type": "interactive",
                  "interactive": {
                    "type": "button",
                    "body": {
                      "text": "Precisando de ajuda para encontrar o serviço perfeito?"
                    },
                    "action": {
                      "buttons": [
                        {
                          "type": "reply",
                          "reply": {
                            "id": "YES_HELP",
                            "title": "✅ Sim, estou precisando"
                          }
                        },
                        {
                          "type": "reply",
                          "reply": {
                            "id": "NO_HELP",
                            "title": "🚫 Não, já sei o que quero"
                          }
                        }
                      ]
                    }
                  }
                }
                """))
            .andRespond(withSuccess());

        gateway.sendGreeting("+5583999999999");

        server.verify();
    }
}
