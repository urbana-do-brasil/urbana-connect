package br.com.urbana.connect.infrastructure.whatsapp;

import br.com.urbana.connect.domain.servicecatalog.model.ServiceCatalogItem;
import br.com.urbana.connect.domain.servicecatalog.model.ServiceType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

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

    @Test
    void shouldSendGuidedTriageListToWhatsAppCloudApi() {
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
                    "type": "list",
                    "body": {
                      "text": "Das opções abaixo, qual você se identifica mais?"
                    },
                    "action": {
                      "button": "Ver opções",
                      "sections": [
                        {
                          "rows": [
                            {
                              "id": "DECOR",
                              "title": "🛋️ Decor",
                              "description": "Quero renovar meu espaço interno sem gastar muito, nada de quebra-quebra."
                            }
                          ]
                        }
                      ]
                    }
                  }
                }
                """))
            .andRespond(withSuccess());

        gateway.sendGuidedTriageOptions("+5583999999999", List.of(decor()));

        server.verify();
    }

    @Test
    void shouldSendServicePresentationButtonsToWhatsAppCloudApi() {
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
                      "text": "Acho que encontramos o serviço certo para você! 😃\\n\\nPara espaços de até 20m², temos a Decor 🛋️\\n\\nCriamos uma solução de espaço, de acordo com seu estilo e orçamento.\\n\\nR$ 400,00 por ambiente\\n\\nEra isso que você estava buscando?"
                    },
                    "action": {
                      "buttons": [
                        {
                          "type": "reply",
                          "reply": {
                            "id": "CONFIRM_SERVICE",
                            "title": "✅ Sim, acertou em cheio"
                          }
                        },
                        {
                          "type": "reply",
                          "reply": {
                            "id": "RESELECT_SERVICE",
                            "title": "🚫 Não, foi quase"
                          }
                        }
                      ]
                    }
                  }
                }
                """))
            .andRespond(withSuccess());

        gateway.sendServicePresentation("+5583999999999", decor());

        server.verify();
    }

    @Test
    void shouldSendTermsOfUseTextMessage() {
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
                  "type": "text"
                }
                """, false))
            .andRespond(withSuccess());

        gateway.sendTermsOfUse("+5583999999999");

        server.verify();
    }

    @Test
    void shouldSendPaymentMethodButtons() {
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
                      "text": "Voce ira realizar o pagamento via PIX ou cartao de credito?"
                    },
                    "action": {
                      "buttons": [
                        {
                          "type": "reply",
                          "reply": {
                            "id": "PAYMENT_PIX",
                            "title": "PIX"
                          }
                        },
                        {
                          "type": "reply",
                          "reply": {
                            "id": "PAYMENT_CARD",
                            "title": "Cartao"
                          }
                        }
                      ]
                    }
                  }
                }
                """))
            .andRespond(withSuccess());

        gateway.sendPaymentMethodOptions("+5583999999999");

        server.verify();
    }
    private ServiceCatalogItem decor() {
        return new ServiceCatalogItem(
            ServiceType.DECOR,
            "Decor",
            "🛋️",
            "Quero renovar meu espaço interno sem gastar muito, nada de quebra-quebra.",
            "Para espaços de até 20m², temos a Decor 🛋️\n\nCriamos uma solução de espaço, de acordo com seu estilo e orçamento.",
            new BigDecimal("400.00"),
            "https://mpago.la/1TbJFYx",
            "https://forms.gle/W4zBPwusPZeJ2cnD7",
            true
        );
    }
}
