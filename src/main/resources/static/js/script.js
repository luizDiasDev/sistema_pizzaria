// Alternar sidebar em telas pequenas
document.querySelector('.toggle-btn').addEventListener('click', () => {
    document.getElementById('sidebar').classList.toggle('active');
});

// Alternar submenu
function toggleSubmenu(event, element) {
    event.preventDefault();
    element.parentElement.classList.toggle('open');
}

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
    try { dados = await resposta.json(); } catch (e) { }
    if (!resposta.ok) {
        const mensagem = (dados && dados.mensagem) ? dados.mensagem : ('Erro ' + resposta.status);
        throw new Error(mensagem);
    }
    return dados;
}

function esc(valor) {
    return String(valor == null ? '' : valor)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}
