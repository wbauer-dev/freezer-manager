import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { PhotoLoader } from './photo-loader';

const { createObjectURL, revokeObjectURL } = URL;

describe('PhotoLoader', () => {
  let http: HttpTestingController;
  let loader: PhotoLoader;
  let revoked: string[];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    loader = TestBed.inject(PhotoLoader);

    let created = 0;
    revoked = [];
    // The test browser has no object URLs.
    URL.createObjectURL = () => `blob:${++created}`;
    URL.revokeObjectURL = (url) => revoked.push(url);
  });

  afterEach(() => {
    http.verify();
    URL.createObjectURL = createObjectURL;
    URL.revokeObjectURL = revokeObjectURL;
  });

  function load(photoId: string, size: 'THUMBNAIL' | 'LARGE' = 'THUMBNAIL') {
    const url = loader.url('h1', photoId, size);
    http.expectOne(`/api/households/h1/photos/${photoId}?size=${size}`).flush(new Blob(['jpeg']));
    return url;
  }

  it('loads each thumbnail once', () => {
    const url = load('p1');

    expect(url()).toBe('blob:1');
    expect(loader.url('h1', 'p1')()).toBe('blob:1');
  });

  it('tries a photo again after it could not be loaded', () => {
    const failed = loader.url('h1', 'p1');
    http.expectOne('/api/households/h1/photos/p1?size=THUMBNAIL').error(new ProgressEvent('error'));
    expect(failed()).toBeNull();

    expect(load('p1')()).toBe('blob:1');
  });

  it('keeps only the large photos shown last', () => {
    for (const photoId of ['p1', 'p2', 'p3', 'p4']) {
      load(photoId, 'LARGE');
    }
    // p1 is shown again, so p2 is now the one shown longest ago.
    loader.url('h1', 'p1', 'LARGE');

    load('p5', 'LARGE');

    expect(revoked).toEqual(['blob:2']);
    expect(loader.url('h1', 'p1', 'LARGE')()).toBe('blob:1');
    load('p2', 'LARGE');
    expect(revoked).toEqual(['blob:2', 'blob:3']);
  });

  it('shows an uploaded photo without loading it, and releases it when asked to', () => {
    const preview = loader.remember('p1', new Blob(['jpeg']));

    expect(loader.url('h1', 'p1')()).toBe(preview);
    loader.forget('p1');

    expect(revoked).toEqual(['blob:1', 'blob:2']);
  });
});
