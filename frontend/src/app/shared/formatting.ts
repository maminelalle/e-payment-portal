/** Mise en forme commune : montants « 12 500,00 MRU », dates « 02/11/2026 » (charte docs/01-ecrans.md). */

const AMOUNT = new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

export function formatAmount(value: number | null | undefined, currency = 'MRU'): string {
  return value == null ? '—' : `${AMOUNT.format(value)} ${currency}`;
}

/** 'yyyy-MM-dd' → 'dd/MM/yyyy', sans passer par un fuseau horaire. */
export function formatIsoDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const [y, m, d] = iso.split('-');
  return `${d}/${m}/${y}`;
}

/** Date locale (sélecteur de date) → 'yyyy-MM-dd'. */
export function toIsoDate(date: Date | null | undefined): string | null {
  if (!date) return null;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** 'yyyy-MM-dd' → Date locale à minuit. */
export function fromIsoDate(iso: string | null | undefined): Date | null {
  if (!iso) return null;
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y!, m! - 1, d!);
}

/** '+22222000007' → '+222 22 00 00 07' (numéros mauritaniens) ; autre format : inchangé. */
export function formatPhone(phone: string | null | undefined): string {
  if (!phone) return '—';
  const match = /^\+222(\d{2})(\d{2})(\d{2})(\d{2})$/.exec(phone.replace(/\s/g, ''));
  return match ? `+222 ${match.slice(1).join(' ')}` : phone;
}
