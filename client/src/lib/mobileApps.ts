export type MobilePlatform = "ios" | "android"

export interface MobileApp {
  store: string
  device: string
  // store page of the app; null until it is published (the auth page shows "Em breve")
  url: string | null
}

export const MOBILE_APPS: Record<MobilePlatform, MobileApp> = {
  ios: { store: "App Store", device: "iPhone e iPad", url: null },
  android: { store: "Google Play", device: "Celulares Android", url: null },
}

export const MOBILE_PLATFORMS: MobilePlatform[] = ["ios", "android"]
