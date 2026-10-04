// Backend base URL, read at build time. Production: https://futspring.luisgosampaio.com/api
export const API_BASE: string = import.meta.env.VITE_API_URL ?? "http://localhost:8080"
