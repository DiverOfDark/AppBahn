export function formatDate(date?: string | null): string {
  if (!date) return '-'
  return new Date(date).toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatDateShort(date?: string | null): string {
  if (!date) return ''
  return new Date(date).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

const RELATIVE_UNITS: ReadonlyArray<readonly [Intl.RelativeTimeFormatUnit, number]> = [
  ['year', 365 * 24 * 60 * 60 * 1000],
  ['month', 30 * 24 * 60 * 60 * 1000],
  ['day', 24 * 60 * 60 * 1000],
  ['hour', 60 * 60 * 1000],
  ['minute', 60 * 1000],
  ['second', 1000],
] as const

export function formatRelativeTime(date?: string | null, now: Date = new Date()): string {
  if (!date) return ''
  const then = new Date(date).getTime()
  if (Number.isNaN(then)) return ''
  const diffMs = then - now.getTime()
  const absMs = Math.abs(diffMs)
  // RELATIVE_UNITS is non-empty; the last entry is the smallest threshold.
  const smallest = RELATIVE_UNITS[RELATIVE_UNITS.length - 1]
  if (!smallest || absMs < smallest[1]) return 'just now'
  const rtf = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })
  for (const [unit, unitMs] of RELATIVE_UNITS) {
    if (absMs >= unitMs) {
      return rtf.format(Math.round(diffMs / unitMs), unit)
    }
  }
  return ''
}
