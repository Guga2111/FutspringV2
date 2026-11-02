import type { DrawPlayer } from "@/utils/landingTeamDraw"

// Everything the landing page says, in one place. Names, dates and scores of the product
// fragments are sample data; they never claim real users, reviews or numbers. The champion
// photos are the owner's own pelada, captioned only with what they show.

// Photos the owner supplies: drop the file in public/landing/ and set `src` (e.g.
// "/landing/hero.webp"). While `src` is null the slot shows a flat field, with no request.
export interface LandingPhoto {
  src: string | null
  alt: string
  // shown on the flat field while the photo is missing
  stand: string
  credit?: string
}

export interface ChampionPhoto extends LandingPhoto {
  date: string
  caption: string
}

// The owner's pelada, newest first; 900 × 1200 (3:4) WebP. The prints by the headline are
// [4] and [0] (GallerySection)
export const CHAMPION_PHOTOS: ChampionPhoto[] = [
  { src: "/landing/champion-1.webp", alt: "Seis jogadores posando no campo à noite, dois agachados na frente, um deles com a bola", stand: "Qui, 01/10", date: "Qui, 01/10", caption: "Seis em campo e a bola na mão" },
  { src: "/landing/champion-2.webp", alt: "Dois jogadores com a camisa do Brasil segurando a bola juntos", stand: "Qui, 01/10", date: "Qui, 01/10", caption: "A dupla de amarelo" },
  { src: "/landing/champion-3.webp", alt: "Jogador de camisa vermelha fazendo sinal de silêncio para a câmera", stand: "Qui, 17/09", date: "Qui, 17/09", caption: "Silêncio no campo" },
  { src: "/landing/champion-4.webp", alt: "Cinco jogadores lado a lado perto do gol, à noite", stand: "Qui, 03/09", date: "Qui, 03/09", caption: "Cinco na trave depois do jogo" },
  { src: "/landing/champion-5.webp", alt: "Seis jogadores de preto posando no campo, com os refletores acesos e um prédio ao fundo", stand: "Qui, 09/07", date: "Qui, 09/07", caption: "O time de preto no fim da noite" },
  { src: "/landing/champion-6.webp", alt: "Dois jogadores rindo enquanto andam pelo campo", stand: "Qui, 11/06", date: "Qui, 11/06", caption: "Resenha no meio do campo" },
]

export const DRAW_PLAYERS: DrawPlayer[] = [
  { id: 1, name: "Leal", stars: 5 },
  { id: 2, name: "Guga", stars: 5 },
  { id: 3, name: "Rafa", stars: 4 },
  { id: 4, name: "Bruno", stars: 4 },
  { id: 5, name: "Tiago", stars: 4 },
  { id: 6, name: "Dudu", stars: 3 },
  { id: 7, name: "Caio", stars: 3 },
  { id: 8, name: "Vini", stars: 3 },
  { id: 9, name: "Léo", stars: 2 },
  { id: 10, name: "Márcio", stars: 1 },
]

export interface AttendanceRow {
  id: number
  name: string
  isMensalista: boolean
  at: string
}

export const ATTENDANCE: AttendanceRow[] = [
  { id: 1, name: "Leal", isMensalista: true, at: "seg, 09:12" },
  { id: 2, name: "Guga", isMensalista: true, at: "seg, 10:40" },
  { id: 3, name: "Rafa", isMensalista: true, at: "ter, 18:03" },
  { id: 6, name: "Dudu", isMensalista: false, at: "qua, 20:00" },
  { id: 9, name: "Léo", isMensalista: false, at: "qua, 20:01" },
]

export interface FaqItem {
  id: string
  question: string
  answer: string
}

export const FAQ: FaqItem[] = [
  {
    id: "mensalista",
    question: "Qual a diferença entre mensalista e diarista?",
    answer:
      "O mensalista confirma presença quando quiser. O diarista confirma a partir de 24 horas antes do jogo. Se a sessão lotar e um mensalista confirmar, o último diarista a entrar perde a vaga e recebe um aviso.",
  },
  {
    id: "sorteio",
    question: "Como o sorteio deixa os times equilibrados?",
    answer:
      "Cada jogador tem de 1 a 5 estrelas na pelada, definidas pelos admins. O sorteio embaralha quem tem as mesmas estrelas e coloca cada um no time com menos estrelas somadas, do mais forte para o mais fraco.",
  },
  {
    id: "vagas",
    question: "Quantos jogadores cabem numa sessão?",
    answer:
      "Você escolhe de 2 a 10 times e de 2 a 20 jogadores por time. As vagas da sessão são times × jogadores, e o admin ainda pode confirmar alguém acima do limite.",
  },
  {
    id: "placar",
    question: "O que acontece com os resultados?",
    answer:
      "Os placares e quem fez gol ou deu assistência entram na tabela da sessão. Ao finalizar, o ranking da pelada, as estatísticas de cada jogador e os prêmios (Artilheiro, Garçom, Puskás e Bola Murcha) são atualizados.",
  },
  {
    id: "convite",
    question: "Como a galera entra na pelada?",
    answer:
      "Um admin convida pelo nome de usuário ou compartilha um link que vale 7 dias. Quem recebe vê a pelada antes e decide se entra.",
  },
  {
    id: "avisos",
    question: "Vou ser avisado das mudanças?",
    answer:
      "Sim. O sino do app avisa na hora quando você perde a vaga, quando a sessão é cancelada ou quando os times saem. Por e-mail, você escolhe quais tipos de aviso quer receber.",
  },
  {
    id: "financeiro",
    question: "Dá para controlar a mensalidade?",
    answer:
      "Dá. Os responsáveis criam cobranças com a chave Pix da pelada, registram quem pagou e acompanham o caixa. O app não movimenta dinheiro: o pagamento continua sendo feito por Pix entre vocês.",
  },
]
