import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface UploadedPhoto {
  id: string;
  width: number;
  height: number;
}

@Injectable({ providedIn: 'root' })
export class MediaApi {
  private readonly http = inject(HttpClient);

  upload(householdId: string, photo: Blob): Observable<UploadedPhoto> {
    const form = new FormData();
    form.append('file', photo, 'photo.jpg');
    return this.http.post<UploadedPhoto>(`/api/households/${householdId}/photos`, form);
  }
}
