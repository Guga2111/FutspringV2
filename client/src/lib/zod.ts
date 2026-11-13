import { z } from "zod"

// Every schema imports `z` from here, never from "zod": without jitless, zod 4 probes `new Function("")` when an
// object schema is defined (its JIT parser), which the Content-Security-Policy refuses (a violation in every
// browser). Configuring it in this module, which the schemas import, runs it before any definition whatever way
// the build splits the chunks (an import at the top of main.tsx doesn't: the schemas' shared chunk runs first).
z.config({ jitless: true })

export { z }
