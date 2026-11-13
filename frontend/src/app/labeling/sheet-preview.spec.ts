import { plannedPositions } from './sheet-preview';

describe('plannedPositions', () => {
  it('uses the free positions of the current sheet in order', () => {
    expect(plannedPositions(24, [0, 1, 3], 3)).toEqual({
      onCurrentSheet: [2, 4, 5],
      onNewSheets: 0,
    });
  });

  it('continues on a new sheet when the current one is full', () => {
    const used = Array.from({ length: 23 }, (_, i) => i);
    expect(plannedPositions(24, used, 3)).toEqual({ onCurrentSheet: [23], onNewSheets: 2 });
  });
});
