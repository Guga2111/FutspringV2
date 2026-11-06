import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('./client', () => ({ default: { post: vi.fn(), get: vi.fn() } }))

import apiClient from './client'
import { changePassword, deleteAccount, getDeletionBlockers, getUserPeladas, uploadUserImage } from './users'

const mockPost = vi.mocked(apiClient.post)

beforeEach(() => {
  mockPost.mockReset()
  mockPost.mockResolvedValue({ data: {} })
})

describe('uploadUserImage', () => {
  it('sends FormData to the correct URL', async () => {
    const file = new File(['bytes'], 'avatar.jpg', { type: 'image/jpeg' })
    await uploadUserImage(1, file)

    expect(mockPost).toHaveBeenCalledOnce()
    const [url, body] = mockPost.mock.calls[0]
    expect(url).toBe('/api/v1/users/1/image')
    expect(body).toBeInstanceOf(FormData)
    expect((body as FormData).get('file')).toBe(file)
  })

  it('does not pass an explicit Content-Type header', async () => {
    const file = new File(['bytes'], 'avatar.jpg', { type: 'image/jpeg' })
    await uploadUserImage(1, file)

    const config = mockPost.mock.calls[0][2] as Record<string, unknown> | undefined
    expect(config?.headers).toBeUndefined()
  })
})

describe('getUserPeladas', () => {
  it('calls the shared peladas endpoint', async () => {
    const mockGet = vi.mocked(apiClient.get)
    mockGet.mockResolvedValue({ data: [{ id: 1 }] })

    const result = await getUserPeladas(7)

    expect(mockGet).toHaveBeenCalledWith('/api/v1/users/7/peladas')
    expect(result).toEqual([{ id: 1 }])
  })
})

describe('account settings', () => {
  it('changes the password and returns the new session', async () => {
    mockPost.mockResolvedValue({ data: { token: 't', user: { id: 1 } } })
    const result = await changePassword({ currentPassword: 'velha', newPassword: 'nova-senha' })
    expect(mockPost).toHaveBeenCalledWith('/api/v1/users/me/password', { currentPassword: 'velha', newPassword: 'nova-senha' })
    expect(result.token).toBe('t')
  })

  it('reads the deletion blockers', async () => {
    const mockGet = vi.mocked(apiClient.get)
    mockGet.mockResolvedValue({ data: [{ type: 'CREATOR', peladaId: 3, peladaName: 'Pelada' }] })
    expect(await getDeletionBlockers()).toHaveLength(1)
    expect(mockGet).toHaveBeenCalledWith('/api/v1/users/me/deletion-blockers')
  })

  it('deletes the account with a POST carrying the password', async () => {
    await deleteAccount('senha')
    expect(mockPost).toHaveBeenCalledWith('/api/v1/users/me/delete', { password: 'senha' })
  })
})
