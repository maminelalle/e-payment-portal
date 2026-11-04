import { formatAmount, formatIsoDate, formatPhone, fromIsoDate, toIsoDate } from './formatting';

describe('formatting', () => {
  it('affiche les montants comme la maquette (12 500,00 MRU)', () => {
    expect(formatAmount(12500).replace(/\s/g, ' ')).toBe('12 500,00 MRU');
    expect(formatAmount(null)).toBe('—');
  });

  it('convertit les dates ISO sans décalage de fuseau', () => {
    expect(formatIsoDate('2026-11-01')).toBe('01/11/2026');
    const date = fromIsoDate('2027-10-31');
    expect(toIsoDate(date)).toBe('2027-10-31');
    expect(toIsoDate(null)).toBeNull();
  });

  it('groupe les numéros mauritaniens par deux chiffres', () => {
    expect(formatPhone('+22222000007')).toBe('+222 22 00 00 07');
    expect(formatPhone('+33612345678')).toBe('+33612345678');
    expect(formatPhone(null)).toBe('—');
  });
});
