/**
 * Where the queued labels will land — the same rule as the backend's LabelSheet: the free positions of the current
 * sheet in order, the rest on a new sheet. Used only for the preview.
 */
export function plannedPositions(capacity: number, used: number[], count: number) {
  const free = Array.from({ length: capacity }, (_, position) => position).filter(
    (p) => !used.includes(p),
  );
  return { onCurrentSheet: free.slice(0, count), onNewSheets: Math.max(count - free.length, 0) };
}
