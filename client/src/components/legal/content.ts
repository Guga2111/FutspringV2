// Text of the terms of use and the privacy policy. Describe only what the app really does; when the text
// changes, bump LEGAL_VERSION (lib/legal.ts) and LegalTerms.CURRENT_VERSION in the backend.
// [PLACEHOLDERS] in brackets are highlighted on the page until the owner fills them in.

// a block is a paragraph, or a bullet list
export type LegalBlock = string | string[]

export interface LegalSection {
  id: string
  title: string
  blocks: LegalBlock[]
}

export interface LegalDocument {
  title: string
  intro: string
  sections: LegalSection[]
}

const CONTROLLER = "Luís Gustavo"
const CONTACT = "[E-MAIL DE CONTATO]"

export const TERMS: LegalDocument = {
  title: "Termos de uso",
  intro:
    "Estas são as regras para usar o Futspring. Escrevemos do jeito mais direto possível: leia com calma, porque ao criar a conta você concorda com elas.",
  sections: [
    {
      id: "quem-somos",
      title: "Quem somos",
      blocks: [
        `O Futspring é um app para organizar peladas: membros, sessões, presença, times, resultados, ranking, prêmios, chat e o financeiro do grupo. Ele é mantido por ${CONTROLLER}. Fale com a gente pelo e-mail ${CONTACT}.`,
      ],
    },
    {
      id: "aceite",
      title: "Aceite",
      blocks: [
        "Ao criar sua conta, você declara que leu e aceita estes Termos de uso e a Política de privacidade. Se não concordar, não crie a conta.",
      ],
    },
    {
      id: "conta",
      title: "Sua conta",
      blocks: [
        [
          "Você precisa ter pelo menos 18 anos.",
          "Use dados verdadeiros e um e-mail que seja seu.",
          "A senha é pessoal. Você responde pelo que for feito com a sua conta; se achar que alguém a usou, troque a senha, o que encerra todas as sessões abertas.",
        ],
      ],
    },
    {
      id: "peladas",
      title: "Peladas e papéis",
      blocks: [
        "Quem cria uma pelada é o criador. O criador e os admins configuram a pelada, convidam e removem membros, definem mensalistas e as estrelas de cada jogador, criam sessões, montam os times e lançam os resultados. O criador pode transferir a pelada para um admin.",
        "Os membros confirmam presença, conversam no chat e veem as sessões, o ranking, os prêmios e os números da pelada. As regras de cada pelada (horário, vagas, quem joga) são combinadas entre os próprios membros.",
      ],
    },
    {
      id: "assinatura",
      title: "Assinatura",
      blocks: [
        "Cada pelada tem a sua assinatura, paga pelo criador. A primeira pelada criada por uma conta ganha 30 dias de teste grátis; cada conta tem um teste só. Durante o teste, e enquanto a assinatura não é paga, a pelada aceita até 30 membros.",
        [
          "Os planos são mensal e anual. O preço depende do número de membros e aparece na área Assinatura da pelada.",
          "O pagamento é feito por Pix, na chave mostrada nessa área. Depois de pagar, o criador avisa pelo app e nós confirmamos o pagamento manualmente, o que pode levar algum tempo.",
          "Se a assinatura vencer (no plano pago, depois de 7 dias de carência), a pelada fica em somente leitura: nada é apagado, mas não dá para criar sessões, confirmar presença, montar times, lançar resultados nem mexer no financeiro até a renovação. Membros, chat e configurações continuam funcionando.",
          "Se a pelada for transferida, a assinatura vai junto com ela, e o teste grátis não recomeça.",
        ],
      ],
    },
    {
      id: "conteudo",
      title: "O que você envia",
      blocks: [
        "Fotos de perfil, da pelada e do campeão, mensagens do chat e comprovantes de pagamento continuam sendo seus. Você nos autoriza apenas a guardar esse conteúdo e mostrá-lo às pessoas que podem vê-lo dentro do app.",
        "Não envie conteúdo ilegal, ofensivo ou discriminatório, nem fotos e dados de outras pessoas sem a autorização delas.",
      ],
    },
    {
      id: "financeiro",
      title: "Financeiro",
      blocks: [
        "O financeiro da pelada serve para registrar as cobranças e os pagamentos que os membros combinam entre si (é diferente da assinatura do Futspring). O Futspring não processa pagamentos, não recebe nem guarda dinheiro e não é uma instituição financeira.",
        "A chave Pix e o QR Code são de quem recebe, indicado pelos responsáveis financeiros da pelada. Valores, prazos e eventuais divergências são acertados entre os membros.",
      ],
    },
    {
      id: "uso-proibido",
      title: "O que não é permitido",
      blocks: [
        [
          "Tentar acessar peladas, perfis ou dados que não são seus.",
          "Burlar limites do app, criar contas em massa ou usar robôs.",
          "Assediar, ameaçar ou expor outros usuários.",
          "Usar o app para qualquer atividade ilegal.",
        ],
      ],
    },
    {
      id: "suspensao",
      title: "Suspensão",
      blocks: ["Podemos suspender ou encerrar contas que descumpram estes termos, especialmente quando houver risco para outros usuários."],
    },
    {
      id: "encerrar-conta",
      title: "Encerrar sua conta",
      blocks: [
        "Você pode excluir sua conta quando quiser, em Configurações › Zona de perigo. O que acontece com os seus dados está na Política de privacidade.",
      ],
    },
    {
      id: "responsabilidade",
      title: "Limites da nossa responsabilidade",
      blocks: [
        "O Futspring é oferecido do jeito que está. Fazemos o possível para mantê-lo no ar e seguro, mas não garantimos que funcione sem interrupções. Não respondemos por desentendimentos entre membros, pelo que acontece nas partidas nem por valores combinados no financeiro.",
      ],
    },
    {
      id: "mudancas",
      title: "Mudanças nestes termos",
      blocks: [
        "Podemos atualizar estes termos. A versão e a data em que entraram em vigor ficam no topo da página, e mudanças relevantes são avisadas no app ou por e-mail.",
      ],
    },
    {
      id: "lei",
      title: "Lei e foro",
      blocks: ["Estes termos seguem as leis do Brasil. Fica eleito o foro da comarca de Recife-PE."],
    },
  ],
}

export const PRIVACY: LegalDocument = {
  title: "Política de privacidade",
  intro:
    "Esta política explica quais dados pessoais o Futspring trata, para quê, quem pode vê-los e como você exerce seus direitos, de acordo com a Lei Geral de Proteção de Dados (Lei 13.709/2018).",
  sections: [
    {
      id: "controlador",
      title: "Quem cuida dos seus dados",
      blocks: [
        `O controlador dos dados é ${CONTROLLER}. Para qualquer assunto de privacidade, inclusive falar com o encarregado, escreva para ${CONTACT}.`,
      ],
    },
    {
      id: "dados-coletados",
      title: "Dados que tratamos",
      blocks: [
        [
          "Conta: nome de usuário, e-mail, data de verificação do endereço, senha (guardada como um hash, sem guardar o texto original), posição e foto.",
          "Peladas: de quais você participa, se é admin ou mensalista, presença e horário da confirmação, times, gols, assistências, vitórias, prêmios, as estrelas que os admins dão a você, mensagens do chat e fotos.",
          "Financeiro: cobranças, valores, pagamentos, avisos de pagamento, comprovantes enviados, chave Pix e QR Code de quem recebe e o histórico de alterações.",
          "Assinatura das peladas que você criou: plano, validade, situação, quando você avisou que fez o Pix e os pagamentos registrados (valor, data, plano, nome da pelada e de quem pagou). Também guardamos se a sua conta já usou o teste grátis.",
          "Notificações, suas preferências de e-mail e quais guias do app você já viu.",
          "Dados técnicos: endereço IP, usado para limitar tentativas de login e acessos a links públicos, e registros do servidor.",
          "A versão destes documentos que você aceitou e quando.",
        ],
      ],
    },
    {
      id: "finalidades",
      title: "Para que usamos e com qual base legal",
      blocks: [
        [
          "Para o app funcionar (conta, peladas, sessões, estatísticas, financeiro, chat e o e-mail de troca de senha): execução do contrato com você (art. 7º, V).",
          "Para cobrar e controlar as assinaturas das peladas e dar suporte: execução do contrato com você (art. 7º, V).",
          "Para guardar os registros dos pagamentos de assinatura: cumprimento de obrigação legal, inclusive fiscal (art. 7º, II).",
          "Para segurança e prevenção de abuso (IP, limites de tentativas, registros): legítimo interesse (art. 7º, IX).",
          "Para e-mails de notificação: legítimo interesse, com desativação por categoria em um clique em cada e-mail ou nas configurações de notificação.",
          "Para guardar a prova de que você aceitou estes documentos: exercício regular de direitos (art. 7º, VI).",
        ],
        "Não vendemos dados, não mostramos anúncios e não usamos ferramentas de análise ou rastreamento de terceiros.",
      ],
    },
    {
      id: "quem-ve",
      title: "Quem vê seus dados",
      blocks: [
        [
          "Os membros das peladas de que você participa veem seu nome, foto, posição, presença, estatísticas e mensagens no chat.",
          "Seu perfil e sua foto só podem ser acessados por você e por quem divide uma pelada com você, após entrar na conta. Fotos de peladas e campeões exigem que você seja membro da pelada. Ter o endereço de uma imagem não dá acesso a ela. Quem já viu uma foto pode ter guardado uma cópia.",
          "O link de convite mostra apenas o nome, o dia e o horário da pelada, sem a foto. Dentro do app, o seu e-mail só você vê.",
          "A área Assinatura de uma pelada (situação, planos e pagamentos) só é vista pelo criador.",
          "Nós, que mantemos o Futspring, temos um painel de administração restrito, usado para dar suporte e cobrar as assinaturas. Nele vemos o nome, o e-mail e a data de cadastro de cada conta, as peladas que ela criou, de quantas participa e se já usou o teste grátis, e a assinatura, os pagamentos e o histórico de alterações da assinatura de cada pelada.",
          "No financeiro em modo individual, cada membro vê só as próprias cobranças. No modo transparente, os membros veem participantes, totais e o caixa. Comprovantes só são vistos por você e pelos responsáveis financeiros.",
        ],
      ],
    },
    {
      id: "operadores",
      title: "Serviços que usamos",
      blocks: [
        "Alguns fornecedores tratam dados em nosso nome, só para o app funcionar:",
        ["Supabase: banco de dados.", "Resend: envio de e-mails.", "Hostinger: servidor onde o app roda e onde ficam fotos e comprovantes."],
        "Esses fornecedores podem guardar dados fora do Brasil. Nesses casos, a transferência segue as garantias contratuais de proteção de dados de cada um.",
      ],
    },
    {
      id: "navegador",
      title: "O que fica no seu navegador",
      blocks: [
        "Usamos o armazenamento local do navegador (localStorage) só para manter você conectado e lembrar preferências de tela, como o tema e a barra lateral. Não usamos cookies de rastreamento.",
      ],
    },
    {
      id: "retencao",
      title: "Por quanto tempo guardamos",
      blocks: [
        [
          "Conta e dados das peladas: enquanto a conta existir.",
          "Comprovantes de pagamento: ficam disponíveis por 7 dias (cobranças de sessão) ou 30 dias (mensalidades e outras) e depois o arquivo é apagado.",
          "Notificações: as lidas são apagadas após 90 dias e todas após 180 dias.",
          "Pagamentos de assinatura: guardados pelo prazo exigido pela lei, inclusive a fiscal, mesmo depois que a pelada ou a conta é excluída, com o nome da pelada e de quem pagou.",
          "Links de troca de senha e de verificação de e-mail: valem 30 minutos. Guardamos apenas o hash dos tokens de verificação, até serem usados, substituídos ou a conta ser excluída. Links expirados não permitem verificar a conta.",
          "Links para desativar e-mails: valem 30 dias. Alterar suas preferências de e-mail nas configurações invalida os links anteriores. Para reativar e-mails, entre na conta.",
          "Relatórios de erros do navegador: guardados nos registros de observabilidade por até 14 dias, com remoção de credenciais e tokens reconhecidos antes do registro.",
          "Cópias de segurança do banco: mantemos as 10 mais recentes.",
        ],
        "Quando você exclui a conta, seus dados pessoais são apagados e, nas sessões passadas, você aparece como “Ex-jogador”. Registros que a pelada precisa manter, como resultados das partidas e lançamentos do financeiro, continuam existindo sem os seus dados pessoais. A exceção são os pagamentos de assinatura, guardados como descrito acima.",
      ],
    },
    {
      id: "direitos",
      title: "Seus direitos",
      blocks: [
        "Pela LGPD (art. 18), você pode pedir a confirmação de que tratamos seus dados, o acesso a eles, a correção, a portabilidade, informações sobre com quem compartilhamos e a exclusão.",
        [
          "Corrigir: você mesmo edita nome, posição e foto no seu perfil.",
          "Acessar e levar seus dados (portabilidade): em Configurações › Zona de perigo, “Baixar meus dados” gera um arquivo JSON com tudo o que guardamos da sua conta.",
          "Excluir: em Configurações › Zona de perigo.",
          `Os demais pedidos: pelo e-mail ${CONTACT}. Respondemos em até 15 dias.`,
        ],
        "Você também pode reclamar à Autoridade Nacional de Proteção de Dados (ANPD).",
      ],
    },
    {
      id: "seguranca",
      title: "Segurança",
      blocks: [
        "A conexão com o app é criptografada (HTTPS), as senhas são guardadas só de forma criptografada, trocar a senha encerra todas as sessões abertas e os arquivos do financeiro ficam numa área privada, acessível só a quem tem permissão.",
      ],
    },
    {
      id: "idade",
      title: "Idade mínima",
      blocks: ["O Futspring é para maiores de 18 anos. Se soubermos de uma conta de alguém mais novo, ela será excluída."],
    },
    {
      id: "mudancas",
      title: "Mudanças nesta política",
      blocks: [
        "Podemos atualizar esta política. A versão e a data em que entrou em vigor ficam no topo da página, e mudanças relevantes são avisadas no app ou por e-mail.",
      ],
    },
  ],
}
