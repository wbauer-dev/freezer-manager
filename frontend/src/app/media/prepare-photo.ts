/**
 * Prepares a photo for upload: turned the right way up (phones store the orientation separately) and scaled to at
 * most 1600 pixels, so uploads stay small. Falls back to the original file if the browser cannot decode it; the
 * server then decides.
 */
export async function preparePhoto(file: File, maxSize = 1600): Promise<Blob> {
  try {
    const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
    const scale = Math.min(1, maxSize / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    bitmap.close();
    return await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob(
        (blob) => (blob ? resolve(blob) : reject(new Error('No image'))),
        'image/jpeg',
        0.9,
      ),
    );
  } catch {
    return file;
  }
}
