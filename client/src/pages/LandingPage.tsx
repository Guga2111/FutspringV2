import { Navigate, Link } from "react-router-dom";
import { Calendar, Users, BarChart2, MessageSquare, Github, Linkedin, Mail } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";

const features = [
  {
    icon: Calendar,
    title: "Planeje Sessões",
    description:
      "Planeje suas peladas e faça todos saberem quando é o seu próximo jogo.",
  },
  {
    icon: Users,
    title: "Times Auto-Balanceados",
    description:
      "Sorteio automatico e justo de jogadores, baseado no seu nivel de habilidade.",
  },
  {
    icon: BarChart2,
    title: "Acompanhe suas Métricas",
    description:
      "Mantenha o placa de gols, assistências e notas para saber quem está 'On Fire'.",
  },
  {
    icon: MessageSquare,
    title: "Chat do Grupo",
    description: "Esteja sincronizado em tempo real com seu grupo através do chat da pelada.",
  },
];

const steps = [
  {
    number: "1",
    title: "Crie uma Pelada",
    description: "Monte seu grupo, coloque um banner e chame a galera.",
  },
  {
    number: "2",
    title: "Convide Amigos",
    description:
      "Compartilhe um link ou procure pelo nome de usúario — traga todo mundo.",
  },
  {
    number: "3",
    title: "Jogue & Acompanhe métricas",
    description: "Rode sessões, sorteie times, e grave resultados após o jogo.",
  },
];

export default function LandingPage() {
  const { token } = useAuth();

  if (token) {
    return <Navigate to="/home" replace />;
  }

  return (
    <div className="page-enter">
      {/* Hero */}
      <div className="landing-hero-bg min-h-screen flex items-center relative overflow-hidden">
        {/* Decorative blobs */}
        <div className="landing-blob absolute -top-24 -left-24 w-96 h-96 rounded-full bg-green-400/20 blur-3xl pointer-events-none" />
        <div className="landing-blob-2 absolute top-1/3 -right-32 w-80 h-80 rounded-full bg-emerald-300/20 blur-3xl pointer-events-none" />
        <div className="landing-blob absolute bottom-0 left-1/2 w-64 h-64 rounded-full bg-teal-400/15 blur-2xl pointer-events-none" />

        <div className="container mx-auto px-6 py-16 flex items-center justify-between gap-12 relative z-10">
          {/* Left: text + CTAs */}
          <div className="flex-1 max-w-xl">
            <h1 className="landing-slide-up landing-slide-up-2 text-4xl md:text-6xl font-bold text-white leading-tight">
              Jogue, organize,{" "}
              <span className="text-green-200 drop-shadow-[0_0_20px_rgba(187,247,208,0.6)]">
                conecte.
              </span>
            </h1>
            <p className="landing-slide-up landing-slide-up-3 mt-4 text-lg text-green-100/90">
              Organize suas partidas de futebol, acompanhe comprometimento e estatisticas, e mantenha
              seu time em campo. Futspring é a platforma feita para aqueles
              que amam peladas com os amigos.
            </p>
            <div className="landing-slide-up landing-slide-up-3 mt-8 flex flex-wrap gap-4">
              <Link
                to="/auth?tab=register"
                className="landing-shimmer-btn inline-flex items-center justify-center px-6 py-3 rounded-full font-semibold text-green-700 shadow-lg shadow-green-900/30 hover:shadow-xl hover:shadow-green-900/40 transition-shadow duration-200"
              >
                Começe já →
              </Link>
              <Link
                to="/auth"
                className="inline-flex items-center justify-center px-6 py-3 rounded-full font-semibold border-2 border-white/70 text-white hover:bg-white/15 hover:border-white backdrop-blur-sm transition-all duration-200"
              >
                Entrar
              </Link>
            </div>
          </div>

          {/* Right: floating image — desktop only */}
          <div className="hidden md:flex flex-1 items-center justify-center">
            <img
              src="/gerrard.png"
              alt="Football"
              className="w-60 h-60 rounded-full shadow-2xl select-none"
            />
          </div>
        </div>

        {/* Bottom wave */}
        <div className="absolute bottom-0 left-0 right-0 overflow-hidden leading-none">
          <svg
            viewBox="0 0 1440 56"
            xmlns="http://www.w3.org/2000/svg"
            className="w-full h-14 fill-green-50 dark:fill-[#1e1e1e]"
            preserveAspectRatio="none"
          >
            <path d="M0,32 C360,60 1080,4 1440,32 L1440,56 L0,56 Z" />
          </svg>
        </div>
      </div>

      {/* Features */}
      <section className="bg-green-50 dark:bg-green-950/20 py-20">
        <div className="container mx-auto px-6">
          <h2 className="text-3xl font-bold text-center mb-2">
            Tudo que sua galera precisa
          </h2>
          <p className="text-center text-muted-foreground mb-12">
            Desde o primeiro apito até o placar final.
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
            {features.map(({ icon: Icon, title, description }, i) => (
              <div
                key={title}
                className="landing-card rounded-xl border bg-background shadow-sm p-6 flex flex-col gap-3"
                style={{ animationDelay: `${i * 0.08}s` }}
              >
                <div className="landing-icon-wrap w-10 h-10 rounded-lg bg-green-100 dark:bg-green-900/40 flex items-center justify-center">
                  <Icon className="w-5 h-5 text-green-600 dark:text-green-400" />
                </div>
                <h3 className="font-semibold text-lg">{title}</h3>
                <p className="text-sm text-muted-foreground">{description}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* How It Works */}
      <section className="py-20 relative overflow-hidden">
        {/* Subtle bg accent */}
        <div className="absolute inset-0 pointer-events-none">
          <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] rounded-full bg-green-100/40 dark:bg-green-900/10 blur-3xl" />
        </div>
        <div className="container mx-auto px-6 relative z-10">
          <h2 className="text-3xl font-bold text-center mb-2">Como Funciona</h2>
          <p className="text-center text-muted-foreground mb-12">
            Funcionamento em 3 etapas simples.
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-8 max-w-3xl mx-auto">
            {steps.map(({ number, title, description }, i) => (
              <div
                key={number}
                className="flex flex-col items-center text-center gap-4"
              >
                <div className="relative">
                  <div
                    className="landing-pulse-ring relative w-14 h-14 rounded-full bg-gradient-to-br from-green-500 to-emerald-600 flex items-center justify-center text-white font-bold text-xl shadow-lg shadow-green-500/30"
                    style={{ animationDelay: `${i * 0.7}s` }}
                  >
                    {number}
                  </div>
                </div>
                <h3 className="font-semibold text-lg">{title}</h3>
                <p className="text-sm text-muted-foreground">{description}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA Banner */}
      <section className="py-16 relative overflow-hidden">
        <div className="absolute inset-0 pointer-events-none">
          <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] rounded-full bg-green-100/40 dark:bg-green-900/10 blur-3xl" />
        </div>
        <div className="container mx-auto px-6 text-center relative z-10">
          <h2 className="text-3xl font-bold text-neutral-900 dark:text-foreground mb-4">
            Pronto para seu próximo jogo?
          </h2>
          <p className="text-neutral-500 dark:text-muted-foreground mb-8 max-w-md mx-auto">
            Junte-se a sua galera no FutSpring e nunca se estresse com organizar a
            pelada novamente.
          </p>
          <Link
            to="/auth?tab=register"
            className="landing-shimmer-btn inline-flex items-center justify-center px-8 py-4 rounded-full font-bold text-green-700 text-lg shadow-xl shadow-green-900/30 hover:shadow-2xl transition-shadow duration-200"
          >
            Começe Gratuitamente →
          </Link>
        </div>
      </section>

      {/* Footer */}
      <footer className="bg-green-50 dark:bg-card text-neutral-500 dark:text-muted-foreground pt-14 pb-6">
        <div className="container mx-auto px-6">
          {/* 3-column grid */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-10 pb-10">
            {/* Brand */}
            <div className="flex flex-col gap-2">
              <span className="text-xl font-bold text-green-600">FutSpring</span>
              <p className="text-sm leading-relaxed">
                Organize seus jogos de futebol com amigos.
              </p>
            </div>

            {/* Product links */}
            <div className="flex flex-col gap-3">
              <span className="text-sm font-semibold text-neutral-900 dark:text-foreground tracking-wide">Produto</span>
              <Link to="/#features" className="text-sm hover:text-neutral-900 dark:hover:text-foreground transition-colors duration-150">Funcionalidades</Link>
              <Link to="/#how-it-works" className="text-sm hover:text-neutral-900 dark:hover:text-foreground transition-colors duration-150">Como Funciona</Link>
              <Link to="/auth?tab=register" className="text-sm hover:text-neutral-900 dark:hover:text-foreground transition-colors duration-150">Comece Já</Link>
            </div>

            {/* Social */}
            <div className="flex flex-col gap-3">
              <span className="text-sm font-semibold text-neutral-900 dark:text-foreground tracking-wide">Contato</span>
              <div className="flex gap-3">
                <a
                  href="https://github.com"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="w-9 h-9 rounded-full border border-neutral-300 dark:border-border flex items-center justify-center hover:border-neutral-600 dark:hover:border-foreground hover:text-neutral-900 dark:hover:text-foreground transition-colors duration-150"
                  aria-label="GitHub"
                >
                  <Github className="w-4 h-4" />
                </a>
                <a
                  href="https://linkedin.com"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="w-9 h-9 rounded-full border border-neutral-300 dark:border-border flex items-center justify-center hover:border-neutral-600 dark:hover:border-foreground hover:text-neutral-900 dark:hover:text-foreground transition-colors duration-150"
                  aria-label="LinkedIn"
                >
                  <Linkedin className="w-4 h-4" />
                </a>
                <a
                  href="mailto:contact@futspring.com"
                  className="w-9 h-9 rounded-full border border-neutral-300 dark:border-border flex items-center justify-center hover:border-neutral-600 dark:hover:border-foreground hover:text-neutral-900 dark:hover:text-foreground transition-colors duration-150"
                  aria-label="Email"
                >
                  <Mail className="w-4 h-4" />
                </a>
              </div>
            </div>
          </div>

          {/* Bottom bar */}
          <div className="border-t border-green-200 dark:border-border pt-6 text-center text-xs text-neutral-400 dark:text-muted-foreground">
            © {new Date().getFullYear()} FutSpring. Construido com Spring Boot, React, Vite & TypeScript e muito ☕️.
          </div>
        </div>
      </footer>
    </div>
  );
}
