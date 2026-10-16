// Alternar sidebar em telas pequenas
document.querySelector('.toggle-btn').addEventListener('click', () => {
    document.getElementById('sidebar').classList.toggle('active');
});

// Alternar submenu
function toggleSubmenu(event, element) {
    event.preventDefault();
    element.parentElement.classList.toggle('open');
}

// ---------------------------------------------------------------------------
// Chamadas à API com proteção CSRF.
// O token é lido das meta tags do layout e enviado no cabeçalho exigido pelo
// Spring Security. Use apiJson() em qualquer POST/PUT/DELETE feito via fetch().
// ---------------------------------------------------------------------------
function csrfHeaders() {
    const token = document.querySelector('meta[name="_csrf"]');
    const header = document.querySelector('meta[name="_csrf_header"]');
    const headers = { 'Content-Type': 'application/json' };
    if (token && header && token.content) {
        headers[header.content] = token.content;
    }
    return headers;
}

async function apiJson(url, method, corpo) {
    const resposta = await fetch(url, {
        method: method,
        headers: csrfHeaders(),
        body: corpo === undefined ? undefined : JSON.stringify(corpo)
    });
    let dados = null;
    try { dados = await resposta.json(); } catch (e) { /* resposta sem corpo */ }
    if (!resposta.ok) {
        const mensagem = (dados && dados.mensagem) ? dados.mensagem : ('Erro ' + resposta.status);
        throw new Error(mensagem);
    }
    return dados;
}

// Escapa texto antes de inseri-lo em innerHTML (evita XSS com dados cadastrados)
function esc(valor) {
    return String(valor == null ? '' : valor)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}
