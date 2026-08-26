import { expect, test, type Page, type TestInfo } from '@playwright/test';

const runLiveQuality = process.env.PLAYWRIGHT_LIVE === '1'
  && process.env.PLAYWRIGHT_BASE_URL !== undefined;
const turnTimeoutMs = 120_000;

type CanonicalMessage = {
  id: string;
  eventId: string;
  correlationId: string;
  contactId: string;
  direction: 'INBOUND' | 'OUTBOUND';
  senderType: 'CONTACT' | 'URBA' | 'HUMAN' | 'SYSTEM';
  type: string;
  text: string | null;
  createdAt: string;
};

type ProjectionConversation = {
  mode?: 'AI' | 'HUMAN';
  ownership?: 'URBA' | 'HUMAN';
  version?: number;
  termsStatus?: string;
  paymentStatus?: string;
  commercialStage?: string;
  [key: string]: unknown;
};

type Projection = {
  contactId: string;
  messages: CanonicalMessage[];
  conversation: ProjectionConversation;
  ownership?: 'URBA' | 'HUMAN';
  resumeStatus?: string;
  resumeId?: string | null;
  controlAvailability?: {
    approvePaymentProof?: boolean;
    recordHumanMessage?: boolean;
    returnToUrba?: boolean;
  };
  turn: { status: string; correlationId: string } | null;
};

type QualityEvidence = {
  firstPresentation: boolean;
  discovery: boolean;
  comparison: boolean;
  catalogExplanation: boolean;
  noInformativeAdvance: boolean;
  icpPrompt: boolean;
  partialIcp: boolean;
  termsAfterIcp: boolean;
  ambiguousAcceptance: boolean;
  clearAcceptance: boolean;
  paymentInstruction: boolean;
  proofHandoff: boolean;
  humanSilence: boolean;
  canonicalHumanDecision: boolean;
  resumed: boolean;
  proactiveContinuation: boolean;
  transcriptIntegrity: boolean;
  safeLanguage: boolean;
  observations: string[];
};

type RubricResult = {
  score: number;
  maximumScore: number;
  failures: string[];
  observations: string[];
};

const handoffPattern = /(?:encaminh|transfer|atendimento humano|falar com (?:a )?arquitet[ao]|vou .*arquitet[ao]|continuar[aá].*(?:conversa|atendimento))/i;
const technicalLanguagePattern = /problema no sistema|erro interno|falha interna|ferramenta falhou|illegalstateexception|correlationid|\bapi\b|\bbanco de dados\b|stack trace|\bexception\b|\bretry\b|idempotenc|\bhermes\b|prepare[_ ]terms|\bicp\b/i;
const paymentInstructionPattern = /pagamento|pix|comprovante|instru[cç][aã]o|chave/i;

test.describe('qualidade conversacional ao vivo', () => {
  test.skip(
    !runLiveQuality,
    'execute com PLAYWRIGHT_LIVE=1 e PLAYWRIGHT_BASE_URL apontando para o stack local real',
  );

  test('reproduz o fluxo completo, preserva o transcript e avalia a rubric de qualidade', async ({ page }, testInfo) => {
    test.setTimeout(20 * 60_000);
    const suffix = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
    const contactName = `E2E Qualidade ${suffix}`;
    const evidence = createQualityEvidence();
    let alias = '';
    let latestProjection: Projection | null = null;

    try {
      await page.goto('/');
      await createContact(page, contactName);

      alias = await sendAndCaptureAlias(page, 'Olá!');
      latestProjection = await waitForCanonicalReply(page, alias, 0);
      evidence.firstPresentation = hasFirstPresentation(latestProjection);
      evidence.observations.push(`primeira apresentação: ${lastOutboundText(latestProjection)}`);

      latestProjection = await sendAndWait(
        page,
        alias,
        'É para um quarto infantil, com dinossauros e dragões. Quero uma pintura temática e reaproveitar os móveis que já tenho.',
        latestProjection,
      );
      evidence.discovery = hasPaintingRecommendation(latestProjection);

      latestProjection = await sendAndWait(
        page,
        alias,
        'Qual é a diferença entre Decor Pintura e Decor Interiores?',
        latestProjection,
      );
      evidence.comparison = hasServiceComparison(latestProjection);

      latestProjection = await sendAndWait(
        page,
        alias,
        'Confirmo Decor Pintura. Como funciona esse serviço?',
        latestProjection,
      );
      latestProjection = await sendAndWait(
        page,
        alias,
        'Pode detalhar entregas, responsabilidades, materiais, execução, prazo e suporte?',
        latestProjection,
      );
      evidence.catalogExplanation = hasCatalogExplanation(latestProjection);
      evidence.noInformativeAdvance = hasNoCommercialAdvance(latestProjection);

      latestProjection = await sendAndWait(
        page,
        alias,
        'Quero contratar o Decor Pintura para esse quarto.',
        latestProjection,
      );
      const initialIcpReply = lastOutboundTextFor(latestProjection, /quero contratar|contratar o decor pintura/i);
      evidence.icpPrompt = hasAllIcpTopics(initialIcpReply)
        && !hasTermsBeenPresented(latestProjection)
        && !hasPaymentBeenPrepared(latestProjection);
      evidence.observations.push(`campos solicitados no primeiro checkpoint: ${icpTopics(initialIcpReply).join(', ') || 'nenhum sinal semântico'}`);

      latestProjection = await sendAndWait(
        page,
        alias,
        'Pode me chamar de Dani.',
        latestProjection,
      );
      const partialIcpReply = lastOutboundTextFor(latestProjection, /pode me chamar de dani/i);
      evidence.partialIcp = hasRemainingIcpTopics(partialIcpReply)
        && !hasTopic(partialIcpReply, 'pronoun');
      evidence.observations.push(`campos restantes após resposta parcial: ${icpTopics(partialIcpReply).join(', ') || 'nenhum sinal semântico'}`);

      latestProjection = await sendAndWait(
        page,
        alias,
        'É a primeira vez que contrato esse tipo de serviço e trabalho como designer.',
        latestProjection,
      );
      evidence.termsAfterIcp = hasTermsBeenPresented(latestProjection)
        && !hasPaymentBeenPrepared(latestProjection);
      evidence.observations.push(`estado após concluir o checkpoint: termos=${termsStatus(latestProjection) ?? 'ausente'}, pagamento=${paymentStatus(latestProjection) ?? 'ausente'}`);

      latestProjection = await sendAndWait(page, alias, 'Ok.', latestProjection);
      const ambiguousReply = lastOutboundTextFor(latestProjection, /^ok\.?$/i);
      evidence.ambiguousAcceptance = termsStatus(latestProjection) === 'PRESENTED'
        && paymentStatus(latestProjection) === 'NOT_STARTED'
        && !paymentInstructionPattern.test(normalize(ambiguousReply));

      latestProjection = await sendAndWait(
        page,
        alias,
        'Aceito claramente os termos apresentados e quero seguir com a contratação.',
        latestProjection,
      );
      evidence.clearAcceptance = termsStatus(latestProjection) === 'ACCEPTED'
        && paymentStatus(latestProjection) === 'NOT_STARTED';

      latestProjection = await sendAndWait(page, alias, 'Prefiro pagar por PIX.', latestProjection);
      const paymentReply = lastOutboundTextFor(latestProjection, /prefiro pagar por pix/i);
      evidence.paymentInstruction = paymentStatus(latestProjection) === 'PREPARED'
        && paymentInstructionPattern.test(normalize(paymentReply))
        && hasOnlyTestResource(paymentReply);

      const outboundBeforeProof = outboundCount(latestProjection);
      await enqueueCustomerMessage(
        page,
        alias,
        'Envio agora o comprovante de teste para validação humana.',
        'PAYMENT_PROOF',
      );
      latestProjection = await waitForCanonicalReply(page, alias, outboundBeforeProof);
      const proofInbound = latestInboundByText(latestProjection, /comprovante de teste/);
      const proofReply = proofInbound === null ? [] : outboundForCorrelation(latestProjection, proofInbound.correlationId);
      const proofAckCount = proofReply.filter((message) => handoffPattern.test(message.text ?? '')).length;
      const allHandoffMessages = outboundMessages(latestProjection)
        .filter((message) => handoffPattern.test(message.text ?? ''));
      evidence.proofHandoff = paymentStatus(latestProjection) === 'PROOF_RECEIVED'
        && ownership(latestProjection) === 'HUMAN'
        && outboundCount(latestProjection) > outboundBeforeProof
        && proofReply.length === 1
        && proofAckCount === 1
        && allHandoffMessages.length === 1
        && proofInbound !== null
        && latestProjection.messages.indexOf(allHandoffMessages[0]!) > latestProjection.messages.indexOf(proofInbound);
      evidence.observations.push(`handoff após comprovante: ownership=${ownership(latestProjection) ?? 'ausente'}, acks canônicos=${proofAckCount}`);

      // The proof is injected as an external ingress event rather than typed
      // through the local composer. Reload so the POC consumes that canonical
      // projection through its normal initial synchronization path.
      await page.reload();
      await expect(page.getByRole('heading', { name: contactName })).toBeVisible();
      await expect(page.getByRole('textbox', { name: /^mensagem$/i })).toBeDisabled();
      await expect(page.getByLabel(/responsabilidade: atendimento humano/i)).toBeVisible();

      const outboundBeforeHumanMessage = outboundCount(latestProjection);
      await enqueueCustomerMessage(page, alias, 'Tenho uma dúvida enquanto aguardo o atendimento humano.');
      latestProjection = await waitForHumanMessageAndSilence(
        page,
        alias,
        /tenho uma dúvida enquanto aguardo o atendimento humano/i,
        outboundBeforeHumanMessage,
      );
      evidence.humanSilence = ownership(latestProjection) === 'HUMAN'
        && outboundCount(latestProjection) === outboundBeforeHumanMessage;

      const approveButton = page.getByRole('button', { name: /aprovar pagamento.*ação da arquiteta\/teste/i });
      await expect(approveButton).toBeVisible();
      await approveButton.click();
      latestProjection = await waitForProjection(
        page,
        alias,
        (candidate) => ownership(candidate) === 'HUMAN' && paymentStatus(candidate) === 'CONFIRMED',
        'pagamento confirmado pela ação local da arquiteta',
      );

      const humanDecision = 'Pagamento validado; pode seguir com o próximo passo do atendimento.';
      const humanMessageBox = page.getByRole('textbox', { name: /mensagem humana.*ação da arquiteta\/teste/i });
      await humanMessageBox.fill(humanDecision);
      await page.getByRole('button', { name: /registrar mensagem humana.*ação da arquiteta\/teste/i }).click();
      latestProjection = await waitForProjection(
        page,
        alias,
        (candidate) => candidate.messages.some((message) => message.senderType === 'HUMAN' && message.text === humanDecision),
        'mensagem de decisão humana canônica',
      );
      evidence.canonicalHumanDecision = latestProjection.messages.some(
        (message) => message.senderType === 'HUMAN' && message.text === humanDecision,
      );

      const projectionBeforeReturn = latestProjection;
      // Controls are backed by the canonical projection; resync after the
      // operator action before exercising the next control in this external
      // ingress scenario.
      await page.reload();
      await expect(page.getByRole('heading', { name: contactName })).toBeVisible();
      const returnButton = page.getByRole('button', { name: /devolver responsabilidade.*ação da arquiteta\/teste/i });
      await expect(returnButton).toBeVisible();
      const resumeObservation = waitForResumeAndProactiveReply(
        page,
        alias,
        projectionBeforeReturn,
      );
      await returnButton.click();
      const resumeResult = await resumeObservation;
      latestProjection = resumeResult.projection;
      evidence.resumed = ownership(latestProjection) === 'URBA'
        && resumeStatus(latestProjection) === 'COMPLETED'
        && resumeResult.pendingSnapshots.every((candidate) => ownership(candidate) === 'HUMAN'
          && outboundCount(candidate) <= outboundCount(projectionBeforeReturn));
      const resumedMessages = new Set(projectionBeforeReturn.messages.map((message) => message.id));
      const proactiveMessages = latestProjection.messages
        .filter((message) => !resumedMessages.has(message.id))
        .filter((message) => message.direction === 'OUTBOUND' && message.senderType === 'URBA');
      evidence.proactiveContinuation = proactiveMessages.length === 1
        && /briefing|medidas|fotos|v[ií]deos|material/i.test(normalize(proactiveMessages[0]?.text ?? ''));
      evidence.observations.push(`retomada: ownership=${ownership(latestProjection) ?? 'ausente'}, status=${resumeStatus(latestProjection) ?? 'ausente'}, mensagens proativas=${proactiveMessages.length}`);

      evidence.transcriptIntegrity = hasCanonicalTranscript(latestProjection)
        && latestProjection.messages.some((message) => message.senderType === 'HUMAN' && message.text === humanDecision);
      evidence.safeLanguage = hasSafeCustomerLanguage(latestProjection);

      const rubric = evaluateTranscript(latestProjection, evidence);
      await attachQualityReport(testInfo, alias, latestProjection, rubric);
      expect(rubric.failures, formatDiagnostic(rubric)).toEqual([]);
    } catch (error) {
      evidence.observations.push(`falha de execução: ${error instanceof Error ? error.message : 'erro não textual'}`);
      if (alias.length > 0) {
        latestProjection = await fetchProjectionSafely(page, alias, latestProjection);
      }
      const rubric = latestProjection === null
        ? unavailableProjectionRubric()
        : evaluateTranscript(latestProjection, evidence);
      await attachQualityReport(testInfo, alias, latestProjection, rubric);
      throw error;
    }
  });
});

async function createContact(page: Page, name: string): Promise<void> {
  await page.getByRole('textbox', { name: /nome do contato/i }).fill(name);
  await page.getByRole('button', { name: /criar contato/i }).click();
  await expect(page.getByRole('heading', { name })).toBeVisible();
}

async function sendAndCaptureAlias(page: Page, text: string): Promise<string> {
  const requestPromise = page.waitForRequest((request) => request.method() === 'POST'
    && /\/api\/poc\/conversations\/manual-[a-f0-9-]{36}\/messages$/.test(new URL(request.url()).pathname));
  await sendMessage(page, text);
  const request = await requestPromise;
  const match = new URL(request.url()).pathname.match(/\/conversations\/(manual-[a-f0-9-]{36})\/messages$/);
  if (match?.[1] === undefined) {
    throw new Error(`Não foi possível obter o alias local da URL ${request.url()}.`);
  }
  return match[1];
}

async function sendAndWait(
  page: Page,
  alias: string,
  text: string,
  previousProjection: Projection,
): Promise<Projection> {
  await sendMessage(page, text);
  return waitForCanonicalReply(page, alias, outboundCount(previousProjection));
}

async function sendMessage(page: Page, text: string): Promise<void> {
  const composer = page.getByRole('textbox', { name: /mensagem/i });
  await composer.fill(text);
  await composer.press('Enter');
  await expect(page.getByText(text, { exact: true })).toBeVisible();
}

async function enqueueCustomerMessage(
  page: Page,
  alias: string,
  text: string,
  type: 'TEXT' | 'PAYMENT_PROOF' = 'TEXT',
): Promise<void> {
  const result = await page.evaluate(async ({ contactAlias, message, messageType }) => {
    const response = await fetch(`/api/poc/conversations/${encodeURIComponent(contactAlias)}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        eventId: `ui-${crypto.randomUUID()}`,
        type: messageType,
        text: message,
        occurredAt: new Date().toISOString(),
      }),
    });
    return { status: response.status, body: await response.text() };
  }, { contactAlias: alias, message: text, messageType: type });
  if (result.status >= 500) {
    throw new Error(`A mensagem de observação humana retornou HTTP ${result.status}.`);
  }
}

async function waitForCanonicalReply(page: Page, alias: string, previousOutbound: number): Promise<Projection> {
  let last: Projection | null = null;
  await expect.poll(async () => {
    last = await fetchProjection(page, alias);
    return outboundCount(last);
  }, { timeout: turnTimeoutMs, intervals: [1_000, 2_000, 5_000] }).toBeGreaterThan(previousOutbound);
  return last ?? fetchProjection(page, alias);
}

async function waitForHumanMessageAndSilence(
  page: Page,
  alias: string,
  messagePattern: RegExp,
  previousOutbound: number,
): Promise<Projection> {
  let last: Projection | null = null;
  await expect.poll(async () => {
    last = await fetchProjection(page, alias);
    return last.messages.some((message) => message.direction === 'INBOUND'
      && message.senderType === 'CONTACT'
      && messagePattern.test(message.text ?? ''))
      && ownership(last) === 'HUMAN'
      && outboundCount(last) === previousOutbound;
  }, { timeout: turnTimeoutMs, intervals: [1_000, 2_000, 5_000] }).toBe(true);
  return last ?? fetchProjection(page, alias);
}

async function waitForProjection(
  page: Page,
  alias: string,
  predicate: (projection: Projection) => boolean,
  description: string,
): Promise<Projection> {
  let last: Projection | null = null;
  await expect.poll(async () => {
    last = await fetchProjection(page, alias);
    return predicate(last);
  }, { timeout: turnTimeoutMs, intervals: [1_000, 2_000, 5_000] }).toBe(true);
  if (last === null) {
    throw new Error(`A projeção não confirmou ${description}.`);
  }
  return last;
}

async function waitForResumeAndProactiveReply(
  page: Page,
  alias: string,
  previousProjection: Projection,
): Promise<{ projection: Projection; pendingSnapshots: Projection[] }> {
  const pendingSnapshots: Projection[] = [];
  let lastError: unknown = null;
  const deadline = Date.now() + turnTimeoutMs;
  while (Date.now() < deadline) {
    let candidate: Projection | null = null;
    try {
      candidate = await fetchProjection(page, alias);
    } catch (error) {
      lastError = error;
    }
    if (candidate !== null) {
      if (resumeStatus(candidate) === 'SYNCHRONIZING' || resumeStatus(candidate) === 'DECIDING'
        || resumeStatus(candidate) === 'RECONCILING' || resumeStatus(candidate) === 'PENDING') {
        pendingSnapshots.push(candidate);
      }
      if (ownership(candidate) === 'URBA' && resumeStatus(candidate) === 'COMPLETED') {
        if (pendingSnapshots.some((pending) => outboundCount(pending) > outboundCount(previousProjection))) {
          throw new Error('A projeção publicou resposta antes de concluir a sincronização da retomada.');
        }
        return { projection: candidate, pendingSnapshots };
      }
    }
    await new Promise((resolve) => globalThis.setTimeout(resolve, 1_000));
  }

  const detail = lastError instanceof Error ? `: ${lastError.message}` : '';
  throw new Error(`A retomada não retornou uma projeção final antes do timeout${detail}.`);
}

async function fetchProjection(page: Page, alias: string): Promise<Projection> {
  return page.evaluate(async (contactAlias) => {
    const response = await fetch(`/api/poc/conversations/${encodeURIComponent(contactAlias)}`);
    if (!response.ok) {
      throw new Error(`Projection HTTP ${response.status}`);
    }
    const value: unknown = await response.json();
    if (typeof value !== 'object' || value === null || !('messages' in value)
      || !Array.isArray(value.messages) || !('conversation' in value)
      || typeof value.conversation !== 'object' || value.conversation === null) {
      throw new Error('Projection without canonical messages or conversation state');
    }
    return value as Projection;
  }, alias);
}

async function fetchProjectionSafely(
  page: Page,
  alias: string,
  fallback: Projection | null,
): Promise<Projection | null> {
  try {
    return await fetchProjection(page, alias);
  } catch {
    return fallback;
  }
}

function createQualityEvidence(): QualityEvidence {
  return {
    firstPresentation: false,
    discovery: false,
    comparison: false,
    catalogExplanation: false,
    noInformativeAdvance: false,
    icpPrompt: false,
    partialIcp: false,
    termsAfterIcp: false,
    ambiguousAcceptance: false,
    clearAcceptance: false,
    paymentInstruction: false,
    proofHandoff: false,
    humanSilence: false,
    canonicalHumanDecision: false,
    resumed: false,
    proactiveContinuation: false,
    transcriptIntegrity: false,
    safeLanguage: false,
    observations: [],
  };
}

function evaluateTranscript(projection: Projection, evidence: QualityEvidence): RubricResult {
  const criteria: Array<[string, boolean]> = [
    ['primeira apresentação espontânea', evidence.firstPresentation],
    ['descoberta e recomendação de Decor Pintura', evidence.discovery],
    ['comparação factual entre serviços', evidence.comparison],
    ['explicação progressiva do catálogo', evidence.catalogExplanation],
    ['turno informativo sem avanço comercial', evidence.noInformativeAdvance],
    ['checkpoint com os campos de enriquecimento ausentes', evidence.icpPrompt],
    ['segunda mensagem somente com campos ainda ausentes', evidence.partialIcp],
    ['termos apresentados somente após o checkpoint', evidence.termsAfterIcp],
    ['aceite ambíguo não libera pagamento', evidence.ambiguousAcceptance],
    ['aceite textual claro precede o pagamento', evidence.clearAcceptance],
    ['instrução de pagamento de teste vinculada ao fluxo', evidence.paymentInstruction],
    ['comprovante gera handoff canônico único', evidence.proofHandoff],
    ['nenhuma resposta automática sob ownership humano', evidence.humanSilence],
    ['decisão humana preservada como mensagem canônica', evidence.canonicalHumanDecision],
    ['retorno HUMANO → URBA concluído', evidence.resumed],
    ['continuação proativa única após sincronização', evidence.proactiveContinuation],
    ['transcript integral sem duplicação', evidence.transcriptIntegrity],
    ['linguagem visível sem detalhes técnicos', evidence.safeLanguage],
  ];
  const failures = criteria
    .filter(([, passed]) => !passed)
    .map(([label]) => `Critério não comprovado: ${label}.`);
  const score = criteria.filter(([, passed]) => passed).length;
  return {
    score,
    maximumScore: criteria.length,
    failures,
    observations: [
      ...evidence.observations,
      `ownership final: ${ownership(projection) ?? 'ausente'}`,
      `resume final: ${resumeStatus(projection) ?? 'ausente'}`,
      `mensagens canônicas: ${projection.messages.length}; respostas visíveis: ${outboundCount(projection)}`,
      `status do último turno: ${projection.turn?.status ?? 'ausente'}`,
    ],
  };
}

function hasFirstPresentation(projection: Projection): boolean {
  const firstOutbound = outboundMessages(projection)[0];
  return firstOutbound?.senderType === 'URBA'
    && containsAll(normalize(firstOutbound.text ?? ''), ['urba', 'urbana do brasil']);
}

function hasPaintingRecommendation(projection: Projection): boolean {
  const text = normalize(repliesAfterCustomerQuestion(projection, /quarto infantil|pintura tem[aá]tica|reaproveitar/i).join('\n'));
  return text.includes('decor pintura') && /pintura|tematic|dinossauro|dr[aã]gao|reaproveit/.test(text);
}

function hasServiceComparison(projection: Projection): boolean {
  const text = normalize(repliesAfterCustomerQuestion(projection, /diferen[cç]a entre decor pintura e decor interiores/i).join('\n'));
  return text.includes('decor pintura')
    && text.includes('decor interiores')
    && /pintura|desenho|tinta/.test(text)
    && /layout|mobiliario|moveis|composicao/.test(text);
}

function hasCatalogExplanation(projection: Projection): boolean {
  const text = normalize(repliesAfterCustomerQuestion(projection, /como funciona|detalhar entregas|o que eu recebo|processo|suporte/i).join('\n'));
  return /consultoria\s+online|online.*consultoria/.test(text)
    && /manual/.test(text)
    && /tour\s+virtual/.test(text)
    && /(?:3|tres)\s+op(?:c|ç)oes?/.test(text)
    && /(?:2|duas)\s+rodadas?/.test(text);
}

function hasNoCommercialAdvance(projection: Projection): boolean {
  return termsStatus(projection) === 'NOT_PRESENTED'
    && paymentStatus(projection) === 'NOT_STARTED'
    && ownership(projection) === 'URBA';
}

function hasAllIcpTopics(text: string): boolean {
  return icpTopics(text).length === 3;
}

function hasRemainingIcpTopics(text: string): boolean {
  const topics = icpTopics(text);
  return topics.length > 0 && topics.length < 3;
}

function icpTopics(text: string): string[] {
  return (['pronoun', 'firstTimeHiring', 'occupation'] as const).filter((topic) => hasTopic(text, topic));
}

function hasTopic(text: string, topic: 'pronoun' | 'firstTimeHiring' | 'occupation'): boolean {
  const normalized = normalize(text);
  if (topic === 'pronoun') {
    return /pronome|tratamento|refir|cham[ae]d|senhor|senhora/.test(normalized);
  }
  if (topic === 'firstTimeHiring') {
    return /primeir|contrat(ou|ar|a).*vez|ja.*contrat/.test(normalized);
  }
  return /profiss|ocupacao|profissional|area|trabalh|atuacao/.test(normalized);
}

function termsStatus(projection: Projection): string | undefined {
  return projection.conversation.termsStatus;
}

function paymentStatus(projection: Projection): string | undefined {
  return projection.conversation.paymentStatus;
}

function hasTermsBeenPresented(projection: Projection): boolean {
  return termsStatus(projection) === 'PRESENTED' || termsStatus(projection) === 'ACCEPTED';
}

function hasPaymentBeenPrepared(projection: Projection): boolean {
  return paymentStatus(projection) === 'PREPARED'
    || paymentStatus(projection) === 'PROOF_RECEIVED'
    || paymentStatus(projection) === 'CONFIRMED';
}

function ownership(projection: Projection): 'URBA' | 'HUMAN' | undefined {
  return projection.ownership ?? projection.conversation.ownership
    ?? (projection.conversation.mode === 'HUMAN' ? 'HUMAN' : undefined);
}

function resumeStatus(projection: Projection): string | undefined {
  return projection.resumeStatus;
}

function outboundMessages(projection: Projection): CanonicalMessage[] {
  return projection.messages.filter((message) => message.direction === 'OUTBOUND'
    && (message.senderType === 'URBA' || message.senderType === 'HUMAN'));
}

function outboundCount(projection: Projection | null): number {
  return projection === null ? 0 : outboundMessages(projection).length;
}

function lastOutboundText(projection: Projection): string {
  return outboundMessages(projection).at(-1)?.text?.trim() ?? '';
}

function lastOutboundTextFor(projection: Projection, inboundPattern: RegExp): string {
  const inboundIndex = [...projection.messages].reverse().findIndex((message) => message.direction === 'INBOUND'
    && message.senderType === 'CONTACT'
    && inboundPattern.test(message.text ?? ''));
  if (inboundIndex < 0) {
    return lastOutboundText(projection);
  }
  const actualIndex = projection.messages.length - 1 - inboundIndex;
  const inbound = projection.messages[actualIndex];
  if (inbound === undefined) {
    return lastOutboundText(projection);
  }
  return outboundForCorrelation(projection, inbound.correlationId).at(-1)?.text?.trim()
    ?? projection.messages.slice(actualIndex + 1).find((message) => message.direction === 'OUTBOUND')?.text?.trim()
    ?? '';
}

function latestInboundByText(projection: Projection, pattern: RegExp): CanonicalMessage | null {
  return [...projection.messages].reverse().find((message) => message.direction === 'INBOUND'
    && message.senderType === 'CONTACT'
    && pattern.test(message.text ?? '')) ?? null;
}

function outboundForCorrelation(projection: Projection, correlationId: string): CanonicalMessage[] {
  return outboundMessages(projection).filter((message) => message.correlationId === correlationId);
}

function repliesAfterCustomerQuestion(projection: Projection, question: RegExp): string[] {
  const replies: string[] = [];
  projection.messages.forEach((message, index) => {
    if (message.direction !== 'INBOUND' || message.senderType !== 'CONTACT' || !question.test(message.text ?? '')) {
      return;
    }
    const correlated = outboundForCorrelation(projection, message.correlationId);
    if (correlated.length > 0) {
      replies.push(...correlated.map((candidate) => candidate.text ?? ''));
      return;
    }
    for (let next = index + 1; next < projection.messages.length; next += 1) {
      const candidate = projection.messages[next];
      if (candidate?.direction === 'INBOUND') {
        break;
      }
      if (candidate?.direction === 'OUTBOUND') {
        replies.push(candidate.text ?? '');
      }
    }
  });
  return replies;
}

function hasOnlyTestResource(text: string): boolean {
  const urls = text.match(/https?:\/\/[^\s)]+/gi) ?? [];
  return urls.every((url) => /localhost|127\.0\.0\.1|\.local(?:\/|$)|\.test(?:\/|$)|poc|teste|test/i.test(url));
}

function hasSafeCustomerLanguage(projection: Projection): boolean {
  return outboundMessages(projection).every((message) => !technicalLanguagePattern.test(normalize(message.text ?? '')));
}

function hasCanonicalTranscript(projection: Projection): boolean {
  const ids = new Set<string>();
  const eventFallbacks = new Set<string>();
  return projection.messages.length > 0
    && projection.messages.every((message) => {
      const eventFallback = `${message.eventId}|${message.direction}|${message.correlationId}`;
      if (ids.has(message.id) || eventFallbacks.has(eventFallback)) {
        return false;
      }
      ids.add(message.id);
      eventFallbacks.add(eventFallback);
      return message.eventId.length > 0
        && message.correlationId.length > 0
        && message.contactId === projection.contactId
        && (message.type === 'TEXT'
          || (message.direction === 'INBOUND'
            && message.senderType === 'CONTACT'
            && message.type === 'PAYMENT_PROOF'))
        && message.createdAt.length > 0;
    });
}

function containsAll(text: string, values: string[]): boolean {
  return values.every((value) => text.includes(value));
}

function normalize(value: string): string {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
}

function unavailableProjectionRubric(): RubricResult {
  return {
    score: 0,
    maximumScore: 18,
    failures: ['Infraestrutura/projeção: não foi possível obter o transcript canônico para uma avaliação honesta.'],
    observations: [],
  };
}

async function attachQualityReport(
  testInfo: TestInfo,
  alias: string,
  projection: Projection | null,
  rubric: RubricResult,
): Promise<void> {
  const report = {
    alias: alias || null,
    rubric,
    transcript: projection?.messages.map((message) => ({
      createdAt: message.createdAt,
      direction: message.direction,
      senderType: message.senderType,
      correlationId: message.correlationId,
      text: message.text,
    })) ?? [],
    ownership: projection === null ? null : ownership(projection) ?? null,
    resumeStatus: projection === null ? null : resumeStatus(projection) ?? null,
    turn: projection?.turn ?? null,
  };
  await testInfo.attach('quality-chat-transcript.json', {
    body: JSON.stringify(report, null, 2),
    contentType: 'application/json',
  });
}

function formatDiagnostic(rubric: RubricResult): string {
  return [
    `Score de qualidade: ${rubric.score}/${rubric.maximumScore}`,
    ...rubric.failures.map((failure) => `- ${failure}`),
    ...rubric.observations.map((observation) => `- ${observation}`),
  ].join('\n');
}
