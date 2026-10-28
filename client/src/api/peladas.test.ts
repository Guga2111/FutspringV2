import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('./client', () => ({
  default: { post: vi.fn(), get: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import apiClient from './client'
import {
  uploadPeladaImage,
  searchUsers,
  getPeladaAttendance,
  setMensalista,
  setAdmins,
  setMensalistas,
  setPlayersStars,
  removePlayers,
} from './peladas'

const mockPost = vi.mocked(apiClient.post)

beforeEach(() => {
  mockPost.mockReset()
  mockPost.mockResolvedValue({ data: {} })
})

describe('uploadPeladaImage', () => {
  it('sends FormData to the correct URL', async () => {
    const file = new File(['bytes'], 'cover.jpg', { type: 'image/jpeg' })
    await uploadPeladaImage(1, file)

    expect(mockPost).toHaveBeenCalledOnce()
    const [url, body] = mockPost.mock.calls[0]
    expect(url).toBe('/api/v1/peladas/1/image')
    expect(body).toBeInstanceOf(FormData)
    expect((body as FormData).get('file')).toBe(file)
  })

  it('does not pass an explicit Content-Type header', async () => {
    const file = new File(['bytes'], 'cover.jpg', { type: 'image/jpeg' })
    await uploadPeladaImage(1, file)

    const config = mockPost.mock.calls[0][2] as Record<string, unknown> | undefined
    expect(config?.headers).toBeUndefined()
  })
})

describe('searchUsers', () => {
  it('sends the query as a param', async () => {
    const mockGet = vi.mocked(apiClient.get)
    mockGet.mockResolvedValue({ data: [] })

    await searchUsers('leal')

    expect(mockGet).toHaveBeenCalledWith('/api/v1/users/search', { params: { q: 'leal' } })
  })
})

describe('getPeladaAttendance', () => {
  it('requests the pelada attendance ranking', async () => {
    const mockGet = vi.mocked(apiClient.get)
    mockGet.mockResolvedValue({ data: { totalSessions: 0, rows: [] } })

    await getPeladaAttendance(7)

    expect(mockGet).toHaveBeenCalledWith('/api/v1/peladas/7/attendance')
  })
})

describe('setMensalista', () => {
  it('puts the flag on the member route and returns the member', async () => {
    const mockPut = vi.mocked(apiClient.put)
    const member = { id: 2, isMensalista: true }
    mockPut.mockResolvedValue({ data: member })

    await expect(setMensalista(1, 2, true)).resolves.toEqual(member)
    expect(mockPut).toHaveBeenCalledWith('/api/v1/peladas/1/players/2/mensalista', { isMensalista: true })
  })
})

describe('bulk member actions', () => {
  it('put the ids and the value on the pelada routes and return the members', async () => {
    const mockPut = vi.mocked(apiClient.put)
    const members = [{ id: 2 }, { id: 3 }]
    mockPut.mockResolvedValue({ data: members })

    await expect(setAdmins(1, [2, 3], true)).resolves.toEqual(members)
    expect(mockPut).toHaveBeenLastCalledWith('/api/v1/peladas/1/players/admin', { userIds: [2, 3], isAdmin: true })

    await expect(setMensalistas(1, [2, 3], false)).resolves.toEqual(members)
    expect(mockPut).toHaveBeenLastCalledWith('/api/v1/peladas/1/players/mensalista', { userIds: [2, 3], isMensalista: false })

    await expect(setPlayersStars(1, [2, 3], 4)).resolves.toEqual(members)
    expect(mockPut).toHaveBeenLastCalledWith('/api/v1/peladas/1/players/stars', { userIds: [2, 3], stars: 4 })
  })

  it('removes with a POST carrying the ids', async () => {
    await removePlayers(1, [2, 3])

    expect(mockPost).toHaveBeenCalledWith('/api/v1/peladas/1/players/remove', { userIds: [2, 3] })
  })
})
