# Futspring

Management for amateur soccer groups: attendance, team sorting, match results,
rankings, awards, chat and financial tracking.

- `client/`: React + TypeScript SPA, managed with **bun**.
- `core/`: Spring Boot API, Java 25 and PostgreSQL 17.
- `ops/`: observability, nginx configuration and local load testing.
- `scripts/deploy.sh`: deployment used by CI/CD.

Start with the [documentation index](docs/README.md) for setup, commands,
architecture and operations. Contributors and coding agents must follow
[AGENTS.md](AGENTS.md) and read both the
[frontend](client/docs/README.md) and [backend](core/docs/README.md) guide indexes.
