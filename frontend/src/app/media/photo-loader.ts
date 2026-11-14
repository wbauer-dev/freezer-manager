import { HttpClient } from '@angular/common/http';
import { Injectable, Signal, inject, signal, untracked } from '@angular/core';

export type PhotoSize = 'THUMBNAIL' | 'LARGE';

/** Large photos take a lot of memory; only the ones shown last are kept. */
const LARGE_PHOTOS_KEPT = 4;

/**
 * Photos need the access token, which an <img src> cannot send. They are loaded through HttpClient instead and shown
 * as object URLs. Photos never change, so each thumbnail is loaded once per session; of the large photos, only the
 * last few shown are kept. A photo that could not be loaded is tried again the next time it is asked for.
 */
@Injectable({ providedIn: 'root' })
export class PhotoLoader {
  private readonly http = inject(HttpClient);
  private readonly urls = new Map<string, Signal<string | null>>();
  /** The large photos loaded from the server, least recently used first. */
  private readonly large = new Set<string>();

  url(householdId: string, photoId: string, size: PhotoSize = 'THUMBNAIL'): Signal<string | null> {
    const key = `${photoId}:${size}`;
    const cached = this.urls.get(key);
    if (this.large.delete(key)) {
      this.large.add(key);
    }
    if (cached) {
      return cached;
    }
    const loaded = signal<string | null>(null);
    const url = loaded.asReadonly();
    this.urls.set(key, url);
    if (size === 'LARGE') {
      this.large.add(key);
      this.evictLarge();
    }
    this.http
      .get(`/api/households/${householdId}/photos/${photoId}`, {
        params: { size },
        responseType: 'blob',
      })
      .subscribe({
        next: (photo) => {
          // A photo dropped while it was loading is not needed any more.
          if (this.urls.get(key) === url) {
            loaded.set(URL.createObjectURL(photo));
          }
        },
        error: () => {
          if (this.urls.get(key) === url) {
            this.drop(key);
          }
        },
      });
    return url;
  }

  /** A photo that was just uploaded is shown from the local file, without loading it again. */
  remember(photoId: string, photo: Blob): string {
    const url = URL.createObjectURL(photo);
    this.urls.set(`${photoId}:THUMBNAIL`, signal(url).asReadonly());
    this.urls.set(`${photoId}:LARGE`, signal(URL.createObjectURL(photo)).asReadonly());
    return url;
  }

  /** Releases a photo that won't be shown again, e.g. one removed from the freeze form before freezing. */
  forget(photoId: string): void {
    this.drop(`${photoId}:THUMBNAIL`);
    this.drop(`${photoId}:LARGE`);
  }

  private evictLarge(): void {
    for (const key of this.large) {
      if (this.large.size <= LARGE_PHOTOS_KEPT) {
        return;
      }
      this.drop(key);
    }
  }

  private drop(key: string): void {
    const url = this.urls.get(key);
    // Called while a template reads a photo: the dropped photo must not become a dependency of that template.
    const objectUrl = url ? untracked(url) : null;
    if (objectUrl) {
      URL.revokeObjectURL(objectUrl);
    }
    this.urls.delete(key);
    this.large.delete(key);
  }
}
