# Landing page photos

Photos of the owner's pelada for the landing page (`CHAMPION_PHOTOS` in
`src/components/landing/content.ts`, newest first). Each file is `champion-<n>.webp`
for `CHAMPION_PHOTOS[n - 1]`.

| Where | Index | Shown as |
|-------|-------|----------|
| The two prints by "Campeões da semana" | `[4]`, `[0]` (`PRINTS` in `GallerySection.tsx`) | 3:4, cropped to fill |
| The carousel | all | whole photo in a 4:3 card, the sides filled with a blurred copy of it (`PhotoSlot fit="contain"`) |

Export 900 × 1200 (3:4) WebP, under ~250 KB, e.g.
`cwebp -q 78 -resize 900 1200 in.jpg -o champion-1.webp` (cwebp drops EXIF/GPS by default;
for a taller 9:16 phone photo crop first: `-crop 0 <top> 900 1200`). Ask the people in the
photo before publishing it. Set `credit` when a photo isn't yours; it shows in the footer.
While `src` is null the slot shows a flat field with the `stand` text.
