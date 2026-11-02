# Landing page photos

Photos of real peladas for the landing page (`src/components/landing/content.ts`).
Drop a file here and set the matching `src` (e.g. `"/landing/champion-1.webp"`);
while `src` is null the slot shows a flat field with a short caption.

| Constant | File | Ratio | Suggested size |
|----------|------|-------|----------------|
| `CHAMPION_PHOTOS[0..1]` (the two prints by the headline, also in the carousel) | `champion-1.webp`, `champion-2.webp` | 3:4 print, 4:3 in the carousel (center the subject) | 1200 × 1200 or larger |
| `CHAMPION_PHOTOS[2..5]` (carousel) | `champion-3.webp` … `champion-6.webp` | 4:3 | 1280 × 960 |

WebP or JPEG, under ~250 KB each. Set `credit` when a photo isn't yours; it shows in the footer.
