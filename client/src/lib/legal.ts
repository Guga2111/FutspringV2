// Version of the terms of use and privacy policy, sent on registration. Mirrors LegalTerms.CURRENT_VERSION
// in the backend, which rejects any other value: change both, and the text in components/legal/content.ts, together
export const LEGAL_VERSION = "2026-11-06"

// "06/11/2026", the date the current version took effect
export const LEGAL_EFFECTIVE_DATE = LEGAL_VERSION.split("-").reverse().join("/")
