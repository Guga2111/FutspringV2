import axios from "axios"
import { API_BASE } from "@/lib/config"

const apiClient = axios.create({
  baseURL: API_BASE,
})

export default apiClient
