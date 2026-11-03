import { formatAmount, formatIsoDate, fromIsoDate, toIsoDate } from './formatting';

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
});
