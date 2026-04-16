import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('./client', () => ({ default: { post: vi.fn() } }))

import apiClient from './client'
import { uploadUserImage, uploadBackgroundImage } from './users'

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

describe('uploadBackgroundImage', () => {
  it('sends FormData to the correct URL', async () => {
    const file = new File(['bytes'], 'bg.png', { type: 'image/png' })
    await uploadBackgroundImage(1, file)

    expect(mockPost).toHaveBeenCalledOnce()
    const [url, body] = mockPost.mock.calls[0]
    expect(url).toBe('/api/v1/users/1/background-image')
    expect(body).toBeInstanceOf(FormData)
    expect((body as FormData).get('file')).toBe(file)
  })
})
