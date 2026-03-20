export interface PeladaMember {
  id: number
  username: string
  image: string | null
  stars: number
  position: string | null
  isAdmin: boolean
}

export interface PeladaResponse {
  id: number
  name: string
  dayOfWeek: string
  timeOfDay: string
  duration: number
  address: string | null
  reference: string | null
  image: string | null
  autoCreateDailyEnabled: boolean
  memberCount: number
}

export interface PeladaDetail extends PeladaResponse {
  creatorId: number | null
  members: PeladaMember[]
}
