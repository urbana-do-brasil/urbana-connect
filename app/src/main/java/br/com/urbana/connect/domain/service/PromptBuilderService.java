package br.com.urbana.connect.domain.service;

import br.com.urbana.connect.domain.model.ConversationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Serviço responsável pela construção e otimização de prompts para a API da OpenAI.
 * Formata as instruções do sistema, histórico da conversa e mensagem atual, otimizando
 * o uso do contexto para melhorar a qualidade e relevância das respostas.
 */
@Service
@Slf4j
public class PromptBuilderService {
    
    @Value("${openai.system-prompt:Você é Urba 😉, assistente virtual da Urbana do Brasil, especialista em Arquitetura e Decoração.}")
    private String defaultSystemPrompt;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withLocale(new Locale("pt", "BR"));
    
    /**
     * Constrói um prompt completo para a API da OpenAI, combinando as instruções do sistema,
     * o histórico da conversa e a mensagem atual do usuário, incorporando informações de contexto.
     * 
     * @param userMessage Mensagem atual do usuário
     * @param conversationHistory Histórico formatado da conversa
     * @param context Objeto de contexto da conversa (opcional)
     * @return Prompt completo para envio à API da OpenAI
     */
    public String buildPrompt(String userMessage, String conversationHistory, ConversationContext context) {
        log.debug("Construindo prompt com histórico de {} caracteres", 
                conversationHistory != null ? conversationHistory.length() : 0);
        
        StringBuilder promptBuilder = new StringBuilder();
        
        // Adicionar instruções do sistema (comportamento e diretrizes)
        promptBuilder.append(getSystemInstructions(context))
                .append("\n\n");
        
        // Adicionar contexto da conversa se disponível
        if (context != null) {
            promptBuilder.append(buildContextSection(context))
                    .append("\n\n");
        }
        
        // Adicionar histórico da conversa se existir
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            promptBuilder.append("### Histórico da conversa:\n")
                    .append(conversationHistory)
                    .append("\n\n");
        }
        
        // Adicionar mensagem atual do usuário
        promptBuilder.append("### Mensagem atual:\n")
                .append(userMessage);
        
        String prompt = promptBuilder.toString();
        log.debug("Prompt construído com {} caracteres", prompt.length());
        
        return prompt;
    }
    
    /**
     * Sobrecarga do método buildPrompt que não requer o contexto da conversa.
     * 
     * @param userMessage Mensagem atual do usuário
     * @param conversationHistory Histórico formatado da conversa
     * @return Prompt completo para envio à API da OpenAI
     */
    public String buildPrompt(String userMessage, String conversationHistory) {
        return buildPrompt(userMessage, conversationHistory, null);
    }
    
    /**
     * Constrói a seção de contexto da conversa com informações relevantes para o modelo.
     * 
     * @param context Objeto de contexto da conversa
     * @return String formatada com informações de contexto
     */
    private String buildContextSection(ConversationContext context) {
        if (context == null) {
            return "";
        }
        
        StringBuilder contextBuilder = new StringBuilder("### Informações de contexto:\n");
        
        // Adicionar tópico detectado se disponível
        if (context.getLastDetectedTopic() != null) {
            contextBuilder.append("- Tópico atual: ").append(context.getLastDetectedTopic()).append("\n");
        }
        
        // Adicionar intenção se disponível
        if (context.getCustomerIntent() != null) {
            contextBuilder.append("- Intenção do cliente: ").append(context.getCustomerIntent()).append("\n");
        }
        
        // Adicionar entidades identificadas
        if (context.getIdentifiedEntities() != null && !context.getIdentifiedEntities().isEmpty()) {
            contextBuilder.append("- Entidades mencionadas: ")
                    .append(String.join(", ", context.getIdentifiedEntities()))
                    .append("\n");
        }
        
        // Adicionar estado da conversa
        if (context.getConversationState() != null) {
            contextBuilder.append("- Estado da conversa: ").append(context.getConversationState()).append("\n");
        }
        
        // Adicionar hora da última interação se disponível
        if (context.getLastInteractionTime() != null) {
            contextBuilder.append("- Última interação em: ")
                    .append(DATE_FORMATTER.format(context.getLastInteractionTime()))
                    .append("\n");
        }
        
        // Adicionar resumo da conversa se disponível
        if (context.getConversationSummary() != null) {
            contextBuilder.append("- Resumo da conversa: ").append(context.getConversationSummary()).append("\n");
        }
        
        return contextBuilder.toString();
    }
    
    /**
     * Obtém as instruções do sistema (system prompt) personalizadas com base no contexto.
     * 
     * @param context Objeto de contexto da conversa (opcional)
     * @return Instruções do sistema formatadas
     */
    private String getSystemInstructions(ConversationContext context) {
        StringBuilder instructionsBuilder = new StringBuilder();
        
        // Personalizar conforme o estado da conversa, se disponível
        String basePrompt = defaultSystemPrompt;
        if (context != null && context.isNeedsHumanIntervention()) {
            basePrompt += " Percebo que esta conversa pode requerer atendimento humano em breve.";
        }
        
        instructionsBuilder.append(basePrompt).append("\n\n");
        
        // Adicionar diretrizes de comportamento
        instructionsBuilder.append("""
                ## Instruções:
                - Você é Urba 😉, assistente virtual da Urbana do Brasil, empresa de Arquitetura e Decoração ("Made in Paraíba").
                - Use linguagem informal, acessível, entusiasmada, com tom amigável e positivo.
                - Utilize emojis frequentemente (💜, 😉, 🤔, 🛋️, 🏡, 🎨, 🎉, ✨, 👍, 🤩, ✌️, etc.) para transmitir emoção e engajamento.
                - Suas respostas devem ser descomplicadas, transparentes e empáticas.
                - Forneça informações sobre os serviços de decoração: Decor (Interiores), Decor Fachada e Decor Pintura.
                - Enfatize que nossos serviços renovam espaços sem "quebra-quebra".
                - IMPORTANTE: Todos os nossos serviços têm preços fixos e pré-estabelecidos de R$350.
                - NUNCA sugira a elaboração de orçamentos personalizados - não fazemos isso.
                - Para iniciar qualquer projeto, o cliente precisa apenas fornecer informações básicas (fotos, medidas, descrição).
                - Se o cliente demonstrar frustração ou pedir explicitamente, ofereça transferir para um atendente humano.
                - Se não souber a resposta, seja honesta e diga que não tem essa informação.
                - Nunca invente informações sobre preços, prazos ou serviços que não conhece.
                - Mantenha o foco nos serviços de renovação e decoração da Urbana do Brasil.
                """);
        
        return instructionsBuilder.toString();
    }
    
    /**
     * Constrói um prompt para análise de intenção do usuário.
     * 
     * @param userMessage Mensagem do usuário a ser analisada
     * @return Prompt para análise de intenção
     */
    public String buildIntentAnalysisPrompt(String userMessage) {
        return """
                Analise a seguinte mensagem do usuário e determine sua intenção principal.
                
                ## Mensagem:
                "%s"
                
                ## Instruções:
                - Identifique a principal intenção do usuário na mensagem acima.
                - Responda APENAS com uma das categorias abaixo, sem explicações adicionais:
                
                ## Categorias:
                - DUVIDA_SERVICO: Quando o usuário pergunta sobre serviços de decoração ou como funcionam
                - AGENDAMENTO: Quando o usuário quer agendar, remarcar ou verificar um projeto
                - RECLAMACAO: Quando o usuário expressa insatisfação ou relata um problema
                - CANCELAMENTO: Quando o usuário quer cancelar um serviço ou contrato
                - CONTATO_HUMANO: Quando o usuário solicita explicitamente falar com um atendente humano
                - PRECO_PAGAMENTO: Quando o usuário pergunta sobre preços, formas de pagamento ou faturas
                - ELOGIO: Quando o usuário expressa satisfação ou agradecimento
                - OUTRO: Para intenções que não se encaixam nas categorias acima
                """.formatted(userMessage);
    }
    
    /**
     * Constrói um prompt para avaliar se a mensagem requer intervenção humana.
     * 
     * @param userMessage Mensagem do usuário
     * @param conversationHistory Histórico da conversa
     * @return Prompt para avaliação de necessidade de intervenção humana
     */
    public String buildHumanInterventionPrompt(String userMessage, String conversationHistory) {
        StringBuilder promptBuilder = new StringBuilder();
        
        promptBuilder.append("""
                Você é um analisador especializado em determinar quando uma conversa deve ser transferida para atendimento humano no contexto de uma empresa de decoração e arquitetura.
                                
                ## Instruções:
                - Avalie se esta conversa sobre decoração e arquitetura deve ser transferida para um especialista humano.
                - Responda APENAS com "SIM" ou "NÃO" sem explicações adicionais.
                
                ## Critérios para transferir (responder SIM):
                - O usuário pede explicitamente para falar com um humano/atendente/pessoa/decorador/arquiteto
                - O usuário demonstra frustração significativa ou irritação com o atendimento automático
                - O usuário repete a mesma pergunta após receber resposta (indicando insatisfação)
                - O usuário faz perguntas técnicas específicas sobre projetos de decoração que exigem avaliação especializada
                - O usuário menciona medidas, plantas, planos de obra, ou detalhes técnicos complexos
                - O usuário pede opiniões estéticas específicas sobre combinações de cores, estilos ou móveis
                - O usuário está tentando agendar uma visita técnica ou orçamento personalizado
                - O usuário descreve um ambiente com muitos detalhes técnicos (dimensões, acabamentos, estrutura)
                - O usuário menciona problemas específicos como infiltrações, rachaduras, problemas elétricos
                - O usuário está em fase avançada de negociação ou fechamento de contrato
                - O usuário menciona valores, custos ou formas de pagamento específicas
                - O usuário expressa dúvidas sobre garantias ou cronogramas específicos de execução
                
                ## Contexto da empresa:
                A Urbana do Brasil é uma empresa de Arquitetura e Decoração que oferece serviços de:
                - Decor Interiores (decoração de ambientes internos). Valor: R$350
                - Decor Fachada (renovação de fachadas). Valor: R$350
                - Decor Pintura (renovação com pintura). Valor: R$200
                Todos os serviços têm preço fixo e são realizados sem obras estruturais ("sem quebra-quebra").
                """);
        
        // Adicionar histórico se existir
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            promptBuilder.append("## Histórico da conversa:\n")
                    .append(conversationHistory)
                    .append("\n\n");
        }
        
        // Adicionar mensagem atual
        promptBuilder.append("## Mensagem atual do usuário:\n")
                .append(userMessage);
        
        return promptBuilder.toString();
    }
    
    /**
     * Constrói um prompt para extração de entidades relevantes da mensagem do usuário.
     * 
     * @param userMessage Mensagem do usuário
     * @return Prompt para extração de entidades
     */
    public String buildEntityExtractionPrompt(String userMessage) {
        return """
                Você é um sistema de extração de entidades especializado em identificar informações relevantes em mensagens.
                
                ## Mensagem:
                "%s"
                
                ## Instruções:
                - Extraia entidades relevantes da mensagem acima.
                - Responda em formato JSON com apenas as entidades presentes na mensagem.
                - Não invente ou adicione informações que não estão explícitas na mensagem.
                - Se uma categoria não estiver presente na mensagem, omita-a completamente do resultado.
                
                ## Categorias a extrair:
                - nome: Nome completo do cliente
                - endereco: Endereço completo ou parcial mencionado
                - bairro: Bairro mencionado
                - cidade: Cidade mencionada
                - telefone: Número de telefone mencionado
                - email: Endereço de email mencionado
                - data: Qualquer data mencionada (agendamento, visita, etc.)
                - horario: Qualquer horário mencionado
                - servico: Tipo de serviço de decoração mencionado (Decor, Decor Fachada, Decor Pintura)
                - ambiente: Ambientes ou espaços mencionados (sala, quarto, cozinha, área externa, etc.)
                - estilo: Estilos de decoração mencionados (moderno, rústico, minimalista, etc.)
                - valor: Valores monetários, preços ou referências a dinheiro
                - problema: Descrição de problemas ou reclamações específicas
                """.formatted(userMessage);
    }
    
    /**
     * Constrói um prompt para gerar um resumo da conversa.
     * 
     * @param conversationHistory Histórico formatado da conversa
     * @return Prompt para gerar resumo
     */
    public String buildSummaryPrompt(String conversationHistory) {
        return """
                ## Tarefa: Resumir Conversa
                
                Você é um resumidor profissional de conversas. Sua tarefa é criar um resumo conciso
                da conversa abaixo, destacando pontos principais, perguntas do cliente, 
                informações fornecidas e eventuais problemas/soluções discutidos.
                
                ## Conversa a ser resumida:
                %s
                
                ## Instruções:
                - Seja objetivo e direto
                - Capture os pontos principais da interação
                - Identifique tópicos/serviços discutidos
                - Destaque qualquer necessidade especial mencionada pelo cliente
                - Não ultrapasse 3-4 frases no total
                """.formatted(conversationHistory);
    }
    
    /**
     * Constrói um prompt específico para gerar saudações iniciais.
     * Este prompt é otimizado para criar respostas de boas-vindas
     * no estilo da persona "Urba", com tom amigável e uso de emojis.
     * 
     * @return Prompt para gerar saudação personalizada
     */
    public String buildGreetingPrompt() {
        return """
                ## Tarefa: Gerar Saudação Inicial
                
                Como Urba, assistente virtual da Urbana do Brasil (empresa de Arquitetura e Decoração),
                crie uma saudação calorosa e amigável para iniciar a conversa com o cliente.
                
                ## Persona "Urba":
                - Extremamente amigável e acolhedora
                - Entusiasmada e positiva
                - Usa linguagem informal/coloquial
                - Utiliza MUITOS emojis (😉, 🤔, 🛋️, 🏡, 🎨, 🎉, ✨, 👍, 🤩, 💜)
                - Tom descomplicado e acessível
                
                ## Elementos que a saudação deve conter:
                - Dar boas-vindas calorosas
                - Apresentar-se brevemente como assistente da Urbana do Brasil (especialista em Arquitetura e Decoração)
                - Mencionar que ajuda com serviços de renovação "sem quebra-quebra"
                - Opcionalmente, mencionar que todos os serviços têm preços fixos (R$350) sem necessidade de orçamentos
                - Sugerir o que o usuário pode perguntar (sobre serviços, preços, como funciona)
                - Terminar com pergunta aberta sobre como pode ajudar hoje
                - Incluir pelo menos 3-4 emojis diferentes
                
                ## Importante:
                - Mantenha a resposta concisa (máximo 3-4 frases)
                - Seja calorosa mas não excessivamente formal
                - NÃO mencione serviços de coleta de lixo ou limpeza urbana
                - Enfatize os serviços: Decor Interiores, Decor Fachada, e Decor Pintura
                - NUNCA mencione a elaboração de orçamentos personalizados
                """;
    }
    
    /**
     * Constrói um prompt para perguntas frequentes (FAQ) incorporando uma base de conhecimento
     * com respostas pré-definidas para perguntas comuns sobre os serviços.
     * 
     * @param userMessage Mensagem do usuário
     * @param conversationHistory Histórico da conversa
     * @param context Objeto de contexto da conversa (opcional)
     * @return Prompt otimizado para respostas de FAQ
     */
    public String buildFaqPrompt(String userMessage, String conversationHistory, ConversationContext context) {
        StringBuilder promptBuilder = new StringBuilder();
        
        // Adicionar instruções do sistema (comportamento e diretrizes)
        promptBuilder.append(getSystemInstructions(context))
                .append("\n\n");
        
        // Adicionar base de conhecimento (FAQ)
        promptBuilder.append("""
                ## Base de Conhecimento - Perguntas Frequentes
                Consulte estas informações ANTES de responder. Se a pergunta do usuário for similar a alguma destas,
                use a resposta correspondente como base, mantendo o tom e estilo da persona Urba.
                
                [PERGUNTA]: Quais serviços vocês oferecem?
                [RESPOSTA]: Que legal que perguntou! 🎉 Oferecemos soluções de decoração super bacanas e sem quebra-quebra! Temos o Decor Interiores 🛋️, Decor Fachada 🏡 e Decor Pintura 🎨. Todos com preços fixos e sem necessidade de orçamentos! Quer saber mais sobre algum deles? 😉
                
                [PERGUNTA]: O que significa "sem quebra-quebra"?
                [RESPOSTA]: Significa que nossas soluções focam em renovar seu espaço usando decoração, pintura, móveis e objetos, evitando grandes reformas estruturais, poeira e o stress de uma obra tradicional! ✨
                
                [PERGUNTA]: Como funciona o "faça você mesmo"?
                [RESPOSTA]: Para o Decor Interiores e Decor Pintura, temos uma opção onde te entregamos um guia super detalhado com vídeos e tutoriais para você mesmo(a) colocar a mão na massa e economizar! 👷‍♀️👷‍♂️
                
                [PERGUNTA]: Qual o preço do Decor Interiores?
                [RESPOSTA]: Nosso Decor Interiores tem um valor fixo de R$350 por ambiente (até 20m²)! 😊 O Decor Fachada e o Decor Pintura também têm valores pré-estabelecidos de R$350 por projeto. Não fazemos orçamentos personalizados, nossos preços são padronizados para facilitar! 💜
                
                [PERGUNTA]: Que informações vocês precisam para iniciar um projeto?
                [RESPOSTA]: Para iniciar seu projeto, precisamos de fotos ou vídeos do espaço 📷, as medidas básicas (largura x comprimento) 📐 e uma descrição do que você deseja! Com isso já conseguimos começar! Bem simples e sem complicações! 😄
                
                [PERGUNTA]: Quais cidades/regiões vocês atendem?
                [RESPOSTA]: Somos de Campina Grande, PB, com muito orgulho! 🌵 Atendemos principalmente a região do Nordeste, mas fala pra gente onde você está que vemos o que podemos fazer! 😉
                """)
                .append("\n\n");
        
        // Adicionar contexto da conversa se disponível
        if (context != null) {
            promptBuilder.append(buildContextSection(context))
                    .append("\n\n");
        }
        
        // Adicionar histórico da conversa se existir
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            promptBuilder.append("### Histórico da conversa:\n")
                    .append(conversationHistory)
                    .append("\n\n");
        }
        
        // Adicionar mensagem atual do usuário
        promptBuilder.append("### Mensagem atual:\n")
                .append(userMessage);
        
        return promptBuilder.toString();
    }
} 