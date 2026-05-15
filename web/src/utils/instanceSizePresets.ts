export type PresetId = 'nano' | 'small' | 'medium' | 'large' | 'custom'

export interface InstanceSizePreset {
  id: PresetId
  label: string
  cpu: number
  memory: number
}

export const INSTANCE_SIZE_PRESETS: readonly InstanceSizePreset[] = [
  { id: 'nano', label: 'nano', cpu: 100, memory: 128 },
  { id: 'small', label: 'small', cpu: 500, memory: 1024 },
  { id: 'medium', label: 'medium', cpu: 1000, memory: 2048 },
  { id: 'large', label: 'large', cpu: 2000, memory: 4096 },
] as const

/** Returns the preset id whose cpu+memory matches, or 'custom' if none match. */
export function detectPreset(cpu: number, memory: number): PresetId {
  const match = INSTANCE_SIZE_PRESETS.find((p) => p.cpu === cpu && p.memory === memory)
  return match?.id ?? 'custom'
}
