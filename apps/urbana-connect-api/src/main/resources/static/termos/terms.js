(function () {
  'use strict';

  const content = document.getElementById('terms-content');
  const serviceLabel = document.getElementById('service-label');
  const status = document.getElementById('status');
  const accept = document.getElementById('accept');
  const decline = document.getElementById('decline');
  const scrollHint = document.getElementById('scroll-hint');
  const serviceLabels = {
    DECOR_INTERIORES: 'Decor Interiores',
    DECOR_PINTURA: 'Decor Pintura',
    DECOR_FACHADA: 'Decor Fachada',
    DECOR_REFORMA: 'Decor Reforma'
  };
  const token = new URLSearchParams((window.location.hash || '').replace(/^#/, '')).get('t');
  let endReached = false;
  let decisionMade = false;
  let endRequestInFlight = false;

  // The fragment never goes to the server, but it must not remain in browser
  // history or in a copied URL after this page starts handling it.
  window.history.replaceState(null, document.title, window.location.pathname);

  function setStatus(message, kind) {
    status.textContent = message;
    status.className = 'status' + (kind ? ' ' + kind : '');
  }

  function authHeaders(json) {
    const headers = { Authorization: 'Bearer ' + token };
    if (json) headers['Content-Type'] = 'application/json';
    return headers;
  }

  async function request(path, options) {
    const response = await window.fetch(path, Object.assign({
      credentials: 'same-origin',
      cache: 'no-store',
      headers: authHeaders(Boolean(options && options.body))
    }, options || {}));
    if (!response.ok) {
      const error = new Error('request failed');
      error.httpStatus = response.status;
      throw error;
    }
    if (response.status === 204) return null;
    return response.json();
  }

  function isAtEnd() {
    return content.scrollTop + content.clientHeight >= content.scrollHeight - 4;
  }

  async function recordEndReached() {
    if (endReached || endRequestInFlight || decisionMade || !isAtEnd()) return;
    endRequestInFlight = true;
    try {
      await request('/api/terms/end-reached', { method: 'POST' });
      endReached = true;
      accept.disabled = false;
      scrollHint.textContent = 'Você percorreu todo o conteúdo. Escolha uma opção.';
      setStatus('O aceite está disponível.', 'success');
    } catch (error) {
      // A lost network response must never unlock the decision button.
      setStatus('Não foi possível confirmar o fim da leitura. Verifique a conexão e tente novamente.', 'error');
    } finally {
      endRequestInFlight = false;
    }
  }

  async function load() {
    if (!token) {
      setStatus('Este link de termos não é válido.', 'error');
      return;
    }
    try {
      const presentation = await request('/api/terms/presentation', { method: 'POST' });
      const customerServiceName = serviceLabels[presentation.serviceType] || presentation.serviceType;
      serviceLabel.textContent = customerServiceName + ' · versão ' + presentation.termsVersion;
      // Plain text is intentional: HTML from a legal artifact is never trusted
      // or inserted into the DOM as markup.
      content.textContent = presentation.content;
      content.setAttribute('aria-busy', 'false');
      decline.disabled = false;
      setStatus('Leia os termos até o final para continuar.');
      window.requestAnimationFrame(recordEndReached);
    } catch (error) {
      content.setAttribute('aria-busy', 'false');
      setStatus(error.httpStatus === 410
        ? 'Este link expirou. Solicite um novo link pelo WhatsApp.'
        : 'Não foi possível carregar os termos. Solicite um novo link pelo WhatsApp.', 'error');
    }
  }

  content.addEventListener('scroll', recordEndReached, { passive: true });
  window.addEventListener('resize', recordEndReached);

  accept.addEventListener('click', async function () {
    if (!endReached || decisionMade) return;
    accept.disabled = true;
    decline.disabled = true;
    try {
      const result = await request('/api/terms/decision', {
        method: 'POST',
        body: JSON.stringify({ decision: 'ACCEPT' })
      });
      decisionMade = true;
      setStatus(result.idempotent ? 'Seu aceite já estava registrado.' : 'Termos aceitos. Você pode voltar ao WhatsApp.', 'success');
    } catch (error) {
      accept.disabled = false;
      decline.disabled = false;
      setStatus('Não foi possível registrar o aceite. Nenhum pagamento foi liberado.', 'error');
    }
  });

  decline.addEventListener('click', async function () {
    if (decisionMade) return;
    accept.disabled = true;
    decline.disabled = true;
    try {
      const result = await request('/api/terms/decision', {
        method: 'POST',
        body: JSON.stringify({ decision: 'DECLINE' })
      });
      decisionMade = true;
      setStatus(result.idempotent ? 'Sua recusa já estava registrada.' : 'Tudo bem. Você pode voltar ao WhatsApp.', 'success');
    } catch (error) {
      accept.disabled = !endReached;
      decline.disabled = false;
      setStatus('Não foi possível registrar sua decisão. Tente novamente.', 'error');
    }
  });

  load();
}());
