import { useState, useRef } from 'react'
import { toast } from 'sonner'
import { updateUser, uploadUserImage, uploadBackgroundImage } from '../../api/users'
import { getFileUrl } from '../../lib/utils'
import type { ProfileDTO } from '../../types/user'
import { Button } from '../ui/button'

const POSITIONS = ['GOLEIRO', 'ZAGUEIRO', 'MEIO', 'ATACANTE'] as const

interface EditProfileModalProps {
  profile: ProfileDTO
  onClose: () => void
  onProfileUpdated: (updated: ProfileDTO) => void
}

export default function EditProfileModal({ profile, onClose, onProfileUpdated }: EditProfileModalProps) {
  const [username, setUsername] = useState(profile.username)
  const [position, setPosition] = useState(profile.position ?? '')
  const [stars, setStars] = useState(profile.stars)
  const [usernameError, setUsernameError] = useState('')
  const [saving, setSaving] = useState(false)
  const [avatarPreview, setAvatarPreview] = useState<string | null>(
    profile.image ? getFileUrl(profile.image)! : null,
  )
  const [bgPreview, setBgPreview] = useState<string | null>(
    profile.backgroundImage ? getFileUrl(profile.backgroundImage)! : null,
  )
  const avatarInputRef = useRef<HTMLInputElement>(null)
  const bgInputRef = useRef<HTMLInputElement>(null)

  const initials = profile.username
    .split(' ')
    .map((w) => w[0])
    .join('')
    .toUpperCase()
    .slice(0, 2)

  async function handleAvatarChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    try {
      const updated = await uploadUserImage(profile.id, file)
      setAvatarPreview(updated.image ? getFileUrl(updated.image)! : null)
      onProfileUpdated(updated)
    } catch {
      toast.error('Falha ao atualizar avatar')
    }
    e.target.value = ''
  }

  async function handleBgChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    try {
      const updated = await uploadBackgroundImage(profile.id, file)
      setBgPreview(updated.backgroundImage ? getFileUrl(updated.backgroundImage)! : null)
      onProfileUpdated(updated)
    } catch {
      toast.error('Falha ao enviar imagem de fundo')
    }
    e.target.value = ''
  }

  async function handleSave() {
    setUsernameError('')
    setSaving(true)
    try {
      const updated = await updateUser(profile.id, {
        username,
        position,
        stars,
      })
      onProfileUpdated(updated)
      onClose()
      toast.success('Perfil atualizado!')
    } catch (err: unknown) {
      const axiosErr = err as { response?: { status?: number } }
      if (axiosErr?.response?.status === 409) {
        setUsernameError('Nome de usuário já em uso!')
      } else {
        toast.error('Falha ao atualizar perfil')
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-end sm:items-center justify-center z-50">
      <div className="bg-background rounded-t-2xl sm:rounded-lg w-full sm:max-w-md overflow-hidden">
        {/* Background image area */}
        <label
          htmlFor="bg-input"
          className="w-full h-[120px] relative cursor-pointer group block"
          style={
            bgPreview
              ? { backgroundImage: `url(${bgPreview})`, backgroundSize: 'cover', backgroundPosition: 'center' }
              : { background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' }
          }
        >
          <div className="absolute inset-0 bg-black/30 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
            <span className="text-white text-sm font-medium">Alterar fundo</span>
          </div>
        </label>
        <input id="bg-input" ref={bgInputRef} type="file" accept="image/*" className="hidden" onChange={handleBgChange} />

        {/* Avatar */}
        <div className="px-6 -mt-10 mb-4">
          <label
            htmlFor="avatar-input"
            className="relative h-20 w-20 rounded-full border-4 border-background cursor-pointer group block"
          >
            {avatarPreview ? (
              <img src={avatarPreview} alt={profile.username} className="h-full w-full rounded-full object-cover" />
            ) : (
              <div className="h-full w-full rounded-full bg-muted flex items-center justify-center text-lg font-bold">
                {initials}
              </div>
            )}
            <div className="absolute inset-0 rounded-full bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
              <span className="text-white text-xs font-medium">Editar</span>
            </div>
          </label>
          <input id="avatar-input" ref={avatarInputRef} type="file" accept="image/*" className="hidden" onChange={handleAvatarChange} />
        </div>

        {/* Form fields */}
        <div className="px-6 pb-6 space-y-4">
          <div>
            <label className="text-sm font-medium block mb-1">Nome de usuário</label>
            <input
              type="text"
              value={username}
              onChange={(e) => { setUsername(e.target.value); setUsernameError('') }}
              minLength={3}
              maxLength={30}
              className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-ring"
            />
            {usernameError && <p className="text-destructive text-xs mt-1">{usernameError}</p>}
          </div>

          <div>
            <label className="text-sm font-medium block mb-1">Posição</label>
            <select
              value={position}
              onChange={(e) => setPosition(e.target.value)}
              className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-ring"
            >
              <option value="">Sem posição</option>
              {POSITIONS.map((p) => (
                <option key={p} value={p}>{p.charAt(0) + p.slice(1).toLowerCase()}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="text-sm font-medium block mb-1">Estrelas</label>
            <div className="flex gap-1">
              {[1, 2, 3, 4, 5].map((i) => (
                <button
                  key={i}
                  type="button"
                  onClick={() => setStars(i)}
                  className="text-2xl text-yellow-400 hover:scale-110 transition-transform"
                >
                  {i <= stars ? '★' : '☆'}
                </button>
              ))}
            </div>
          </div>

          <div className="flex gap-3 pt-2">
            <Button
              onClick={onClose}
              className="flex-1 px-4 py-2 border rounded-full text-sm hover:bg-muted transition-colors"
              variant="outline"
            >
              Cancelar
            </Button>
            <Button
              onClick={handleSave}
              disabled={saving}
              variant="gradient"
              className="flex-1 px-4 py-2 rounded-full text-sm font-medium hover:opacity-90 transition-opacity disabled:opacity-50"
            >
              {saving ? 'Salvando...' : 'Salvar'}
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
