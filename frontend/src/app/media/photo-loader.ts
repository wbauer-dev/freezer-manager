import { HttpClient } from '@angular/common/http';
import { Injectable, Signal, inject, signal } from '@angular/core';

export type PhotoSize = 'THUMBNAIL' | 'LARGE';

/**
 * Photos need the access token, which an <img src> cannot send. They are loaded through HttpClient instead and shown
 * as object URLs. Photos never change, so each one is loaded once per session.
 */
@Injectable({ providedIn: 'root' })
export class PhotoLoader {
  private readonly http = inject(HttpClient);
  private readonly urls = new Map<string, Signal<string | null>>();

  url(householdId: string, photoId: string, size: PhotoSize = 'THUMBNAIL'): Signal<string | null> {
    const key = `${photoId}:${size}`;
    let url = this.urls.get(key);
    if (!url) {
      const loaded = signal<string | null>(null);
      this.http
        .get(`/api/households/${householdId}/photos/${photoId}`, {
          params: { size },
          responseType: 'blob',
        })
        .subscribe({
          next: (photo) => loaded.set(URL.createObjectURL(photo)),
          error: () => loaded.set(null),
        });
      url = loaded.asReadonly();
      this.urls.set(key, url);
    }
    return url;
  }

  /** A photo that was just uploaded is shown from the local file, without loading it again. */
  remember(photoId: string, photo: Blob): string {
    const url = URL.createObjectURL(photo);
    this.urls.set(`${photoId}:THUMBNAIL`, signal(url).asReadonly());
    this.urls.set(`${photoId}:LARGE`, signal(url).asReadonly());
    return url;
  }
}
