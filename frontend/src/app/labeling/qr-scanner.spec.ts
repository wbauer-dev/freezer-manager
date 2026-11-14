import type { Mock } from 'vitest';

import { QrScanner } from './qr-scanner';

/** A promise that the test settles. */
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

/** Lets pending promise callbacks run. */
function settle(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve));
}

describe('QrScanner', () => {
  let track: { stop: Mock<() => void> };
  let stream: MediaStream;
  let getUserMedia: Mock<() => Promise<MediaStream>>;
  let video: { play: Mock<() => Promise<void>>; srcObject: unknown; readyState: number };
  let detect: Mock<() => Promise<{ rawValue: string }[]>>;
  let onCode: Mock<(text: string) => void>;
  let scanner: QrScanner;

  beforeEach(() => {
    track = { stop: vi.fn() };
    stream = { getTracks: () => [track] } as unknown as MediaStream;
    getUserMedia = vi.fn(() => Promise.resolve(stream));
    video = { play: vi.fn(() => Promise.resolve()), srcObject: null, readyState: 4 };
    detect = vi.fn(() => Promise.resolve([{ rawValue: 'https://freezer.example/s/K7Q2M9XA' }]));
    onCode = vi.fn<(text: string) => void>();
    scanner = new QrScanner();

    Object.defineProperty(window, 'isSecureContext', { value: true, configurable: true });
    Object.defineProperty(navigator, 'mediaDevices', {
      value: { getUserMedia },
      configurable: true,
    });
    vi.stubGlobal(
      'BarcodeDetector',
      class {
        static getSupportedFormats = () => Promise.resolve(['qr_code']);
        detect = detect;
      },
    );
  });

  afterEach(() => {
    scanner.stop();
    vi.unstubAllGlobals();
  });

  function start(): Promise<void> {
    return scanner.start(video as unknown as HTMLVideoElement, onCode);
  }

  it('reads a code and switches the camera off', async () => {
    await start();
    await vi.waitFor(() =>
      expect(onCode).toHaveBeenCalledWith('https://freezer.example/s/K7Q2M9XA'),
    );

    expect(track.stop).toHaveBeenCalled();
    expect(video.srcObject).toBeNull();
  });

  it('switches the camera off when stopped while the permission prompt is open', async () => {
    const permission = deferred<MediaStream>();
    getUserMedia.mockReturnValue(permission.promise);

    const starting = start();
    scanner.stop();
    permission.resolve(stream);
    await starting;
    await settle();

    expect(track.stop).toHaveBeenCalled();
    expect(video.srcObject).toBeNull();
    expect(detect).not.toHaveBeenCalled();
    expect(onCode).not.toHaveBeenCalled();
  });

  it('switches the camera off when stopped while the video starts', async () => {
    const playing = deferred<void>();
    video.play.mockReturnValue(playing.promise);

    const starting = start();
    await settle();
    scanner.stop();
    playing.resolve();
    await starting;
    await settle();

    expect(track.stop).toHaveBeenCalled();
    expect(detect).not.toHaveBeenCalled();
    expect(onCode).not.toHaveBeenCalled();
  });

  it('does not report a refused permission after it was stopped', async () => {
    const permission = deferred<MediaStream>();
    getUserMedia.mockReturnValue(permission.promise);

    const starting = start();
    scanner.stop();
    permission.reject(new DOMException('Permission denied', 'NotAllowedError'));

    await expect(starting).resolves.toBeUndefined();
  });

  it('reports a refused permission', async () => {
    getUserMedia.mockRejectedValue(new DOMException('Permission denied', 'NotAllowedError'));

    await expect(start()).rejects.toMatchObject({ problem: 'denied' });
  });

  it('switches the camera off when the video cannot play', async () => {
    video.play.mockRejectedValue(new DOMException('No video', 'NotSupportedError'));

    await expect(start()).rejects.toMatchObject({ problem: 'unavailable' });
    expect(track.stop).toHaveBeenCalled();
    expect(video.srcObject).toBeNull();
  });
});
