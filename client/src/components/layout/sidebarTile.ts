// Collapsed sidebar (64 px, 63 inside its border): every row is the same 40 px square starting 12 px from
// the left (px-3, start-aligned: centering 40 in 63 lands on a half pixel), so icons and avatars share one
// axis and hover/active fills have one size.
export const collapsedTile =
  "group-data-[collapsible=icon]:!size-10 group-data-[collapsible=icon]:!p-0 group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:[&>span]:hidden"

export const collapsedColumn = "group-data-[collapsible=icon]:px-3"
