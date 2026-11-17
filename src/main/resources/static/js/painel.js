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

// Formulário de lançamento: mostra só os campos do tipo escolhido e sugere o valor do depósito.
document.querySelectorAll('[data-lancamento]').forEach((form) => {
    const tipo = form.querySelector('[data-tipo]');
    const pessoa = form.querySelector('[data-pessoa]');
    const valor = form.querySelector('[data-valor]');
    let sugerido = '';

    const atualizar = () => {
        form.querySelectorAll('[data-so-para]').forEach((campo) => {
            const visivel = campo.dataset.soPara.split(' ').includes(tipo.value);
            campo.hidden = !visivel;
            campo.querySelectorAll('select, input').forEach((c) => { c.disabled = !visivel; });
        });
    };

    pessoa.addEventListener('change', () => {
        const opcao = pessoa.selectedOptions[0];
        const sugestao = opcao ? opcao.dataset.sugestao || '' : '';
        // só troca o valor se ele estiver vazio ou ainda for a sugestão anterior
        if (tipo.value === 'DEPOSITO' && (valor.value === '' || valor.value === sugerido)) {
            valor.value = sugestao;
            sugerido = sugestao;
        }
    });

    tipo.addEventListener('change', atualizar);
    atualizar();
});
