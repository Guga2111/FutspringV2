import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('./client', () => ({
  default: { post: vi.fn(), get: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import apiClient from './client'
import { uploadPeladaImage } from './peladas'

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
