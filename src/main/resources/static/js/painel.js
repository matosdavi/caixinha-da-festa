// Botões "Copiar" e confirmação de ações que não têm volta.
document.addEventListener('click', async (evento) => {
    const botao = evento.target.closest('[data-copiar]');
    if (!botao) return;
    const texto = botao.dataset.copiar;
    try {
        await navigator.clipboard.writeText(texto);
    } catch (e) {
        // navegador sem permissão de área de transferência: seleciona para o Ctrl+C
        const campo = document.createElement('textarea');
        campo.value = texto;
        document.body.appendChild(campo);
        campo.select();
        document.execCommand('copy');
        campo.remove();
    }
    const original = botao.textContent;
    botao.textContent = 'Copiado ✓';
    setTimeout(() => { botao.textContent = original; }, 2000);
});

document.addEventListener('submit', (evento) => {
    const mensagem = evento.target.dataset.confirmar;
    if (mensagem && !window.confirm(mensagem)) {
        evento.preventDefault();
    }
});
