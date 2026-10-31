import { expect, it } from "vitest"
import { formatFinanceMoney } from "@/utils/finance"
it("formats cents without floating point rounding", () => {
  expect(formatFinanceMoney("999999999999.99")).toBe("R$ 999.999.999.999,99")
  expect(formatFinanceMoney("-0.01")).toBe("−R$ 0,01")
  expect(formatFinanceMoney("0.00")).toBe("R$ 0,00")
})
