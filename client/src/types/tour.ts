// mirrors TourKey: one guided tour per screen and role
export type TourKey = "FINANCE_MANAGER" | "FINANCE_MEMBER"

// mirrors TourStateDTO: the tours the caller has already seen (finished or closed)
export interface TourState {
  seen: TourKey[]
}
