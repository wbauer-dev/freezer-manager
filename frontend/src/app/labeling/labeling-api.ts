import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, from, switchMap, throwError } from 'rxjs';

import { readProblem } from '../shared/problem-message';
import { ScannedLabel, StickerLayout } from './labeling.model';

@Injectable({ providedIn: 'root' })
export class LabelingApi {
  private readonly http = inject(HttpClient);

  /** What a scanned code means; the household comes from the label. */
  scan(code: string): Observable<ScannedLabel> {
    return this.http.get<ScannedLabel>(`/api/labels/${encodeURIComponent(code)}`);
  }

  /** Queues a sticker; stickers with contents describe the given item (or the label's current one). */
  enqueue(
    householdId: string,
    code: string,
    layout: StickerLayout,
    itemId?: string,
  ): Observable<void> {
    return this.http.post<void>(`/api/households/${householdId}/label-sheet/queue`, {
      code,
      layout,
      itemId: itemId ?? null,
    });
  }

  dequeue(householdId: string, code: string, layout: StickerLayout): Observable<void> {
    return this.http.delete<void>(`/api/households/${householdId}/label-sheet/queue/${code}`, {
      params: { layout },
    });
  }

  /** The label PDF. Errors carry the problem details as an object, like those of the other requests. */
  print(householdId: string): Observable<Blob> {
    return this.http
      .post(`/api/households/${householdId}/label-sheet/print`, null, { responseType: 'blob' })
      .pipe(
        catchError((error) => from(readProblem(error)).pipe(switchMap((e) => throwError(() => e)))),
      );
  }

  startNewSheet(householdId: string): Observable<void> {
    return this.http.post<void>(`/api/households/${householdId}/label-sheet/new-sheet`, null);
  }

  togglePosition(householdId: string, position: number): Observable<void> {
    return this.http.post<void>(
      `/api/households/${householdId}/label-sheet/positions/${position}/toggle`,
      null,
    );
  }

  createPersistentLabels(householdId: string, count: number): Observable<string[]> {
    return this.http.post<string[]>(`/api/households/${householdId}/labels/persistent`, { count });
  }
}
