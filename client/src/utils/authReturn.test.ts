import { describe, expect, it } from "vitest"
import { authReturnTo } from "@/utils/authReturn"

describe("invitation destination through authentication", () => {
  it("keeps pathname, query and hash from PrivateRoute", () => {
    expect(authReturnTo(null, { from: { pathname: "/invitations/5", search: "?source=email", hash: "#preview" } })).toBe("/invitations/5?source=email#preview")
  })
  it("keeps the share token and registration destination", () => {
    expect(authReturnTo("/join/MTI.signature?source=chat#preview", null)).toBe("/join/MTI.signature?source=chat#preview")
  })
  it.each(["https://example.org", "//example.org", "/\\example.org", "/\nexample.org"])("rejects an external/unsafe return destination %s", destination => {
    expect(authReturnTo(destination, null)).toBe("/home")
  })
})
