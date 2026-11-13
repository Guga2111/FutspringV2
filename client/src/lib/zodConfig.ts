import { z } from "zod"

// Without this, zod 4 probes `new Function("")` when the first object schema is built (its JIT parser):
// the Content-Security-Policy refuses it and the browser reports a violation, even though zod catches the error.
// Imported first in main.tsx, so it runs before any module defines a schema.
z.config({ jitless: true })
