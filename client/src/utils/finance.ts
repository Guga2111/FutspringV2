// Keep exact amounts as strings throughout the API and forms. Formatting never computes ledger totals.
export function formatFinanceMoney(value: string) {
  const negative = value.startsWith("-")
  const [whole, cents] = (negative ? value.slice(1) : value).split(".")
  return `${negative ? "−" : ""}R$ ${whole.replace(/\B(?=(\d{3})+(?!\d))/g, ".")},${cents}`
}

export const financeOptions = (map: Record<string, string>) => Object.entries(map).map(([value, label]) => ({ value, label }))
