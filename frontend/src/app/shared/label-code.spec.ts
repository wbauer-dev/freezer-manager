import { labelCodeFromQr } from './label-code';

describe('labelCodeFromQr', () => {
  it('reads the code from a scan link, whatever address the sticker was printed with', () => {
    expect(labelCodeFromQr('https://192.168.1.20:8443/s/K7Q2M9XA')).toBe('K7Q2M9XA');
    expect(labelCodeFromQr('http://localhost:4200/s/K7Q2M9XA/')).toBe('K7Q2M9XA');
  });

  it('reads a bare code as people type it', () => {
    expect(labelCodeFromQr(' k7q2-m9xa ')).toBe('K7Q2M9XA');
    expect(labelCodeFromQr('P3RS-1ST3')).toBe('P3RS1ST3');
  });

  it('ignores QR codes that are not labels', () => {
    expect(labelCodeFromQr('https://example.com/menu')).toBeNull();
    expect(labelCodeFromQr('WIFI:S:home;T:WPA;P:secret;;')).toBeNull();
    expect(labelCodeFromQr('https://example.com/s/TOOLONGCODE')).toBeNull();
    expect(labelCodeFromQr('K7Q2-M9XU')).toBeNull();
  });
});
