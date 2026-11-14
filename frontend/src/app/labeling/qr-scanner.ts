/** Why the camera cannot be used. */
export type CameraProblem = 'insecure' | 'unavailable' | 'denied';

export class CameraError extends Error {
  constructor(readonly problem: CameraProblem) {
    super(`Camera ${problem}`);
  }
}

type Decode = (video: HTMLVideoElement) => Promise<string | null>;

/** The Shape Detection API, available e.g. in Chrome on Android; not part of TypeScript's DOM types. */
interface BarcodeDetectorConstructor {
  new (options: { formats: string[] }): {
    detect(source: CanvasImageSource): Promise<{ rawValue: string }[]>;
  };
  getSupportedFormats(): Promise<string[]>;
}

const FRAME_INTERVAL_MS = 150;

/**
 * Reads QR codes from the camera. Uses the browser's BarcodeDetector where it reads QR codes, the jsQR library
 * elsewhere (loaded only then). The camera is switched off as soon as a code was read.
 */
export class QrScanner {
  private stream: MediaStream | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;
  /**
   * Counts the calls of start() and stop(). Starting takes a while (permission prompt, video, decoder): a start that
   * was overtaken by stop() notices it after its next await and switches off what it got meanwhile.
   */
  private run = 0;

  /** Resolves when the camera is on — or when stop() was called in the meantime. */
  async start(video: HTMLVideoElement, onCode: (text: string) => void): Promise<void> {
    this.stop();
    const run = this.run;
    const stopped = () => run !== this.run;
    if (!window.isSecureContext) {
      // Browsers only give pages served over HTTPS (or from localhost) access to the camera.
      throw new CameraError('insecure');
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new CameraError('unavailable');
    }
    let stream: MediaStream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: false,
        video: {
          facingMode: { ideal: 'environment' },
          width: { ideal: 1280 },
          height: { ideal: 720 },
        },
      });
    } catch (error) {
      if (stopped()) {
        return;
      }
      const denied = error instanceof DOMException && error.name === 'NotAllowedError';
      throw new CameraError(denied ? 'denied' : 'unavailable');
    }
    if (stopped()) {
      switchOff(stream);
      return;
    }
    // From here on, stop() switches the camera off.
    this.stream = stream;
    video.srcObject = stream;
    let decode: Decode;
    try {
      await video.play();
      decode = await decoder();
    } catch {
      if (stopped()) {
        return;
      }
      this.stop();
      video.srcObject = null;
      throw new CameraError('unavailable');
    }
    if (stopped()) {
      return;
    }

    const readFrame = async (): Promise<void> => {
      const text =
        video.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA
          ? await decode(video).catch(() => null)
          : null;
      if (stopped()) {
        return;
      }
      if (text) {
        this.stop();
        video.srcObject = null;
        onCode(text);
      } else {
        this.timer = setTimeout(readFrame, FRAME_INTERVAL_MS);
      }
    };
    void readFrame();
  }

  stop(): void {
    this.run++;
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    if (this.stream) {
      switchOff(this.stream);
      this.stream = null;
    }
  }
}

function switchOff(stream: MediaStream): void {
  stream.getTracks().forEach((track) => track.stop());
}

async function decoder(): Promise<Decode> {
  const native = (globalThis as { BarcodeDetector?: BarcodeDetectorConstructor }).BarcodeDetector;
  if (native && (await native.getSupportedFormats()).includes('qr_code')) {
    const detector = new native({ formats: ['qr_code'] });
    return async (video) => (await detector.detect(video))[0]?.rawValue ?? null;
  }
  const { default: jsQR } = await import('jsqr');
  const canvas = document.createElement('canvas');
  const context = canvas.getContext('2d', { willReadFrequently: true });
  return async (video) => {
    if (!context || !video.videoWidth) {
      return null;
    }
    // Smaller frames decode faster; QR codes on stickers stay readable.
    const scale = Math.min(1, 720 / Math.max(video.videoWidth, video.videoHeight));
    canvas.width = Math.round(video.videoWidth * scale);
    canvas.height = Math.round(video.videoHeight * scale);
    context.drawImage(video, 0, 0, canvas.width, canvas.height);
    const image = context.getImageData(0, 0, canvas.width, canvas.height);
    return (
      jsQR(image.data, image.width, image.height, { inversionAttempts: 'dontInvert' })?.data ?? null
    );
  };
}
