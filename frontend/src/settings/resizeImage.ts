/** Largest side of a stored logo, in pixels: enough to stay sharp at the sizes the app shows it. */
export const LOGO_SIZE = 128;

/**
 * Shrinks an image to fit in a square of `maxSide` pixels, keeping its proportion and its
 * transparency, and returns it as a PNG. Whatever the browser can show (JPEG, WebP, SVG...) can
 * be given; a smaller image is not enlarged. Rejects when the file is not an image.
 */
export async function resizeImage(file: Blob, maxSide: number = LOGO_SIZE): Promise<Blob> {
  const url = URL.createObjectURL(file);
  try {
    const image = new Image();
    image.src = url;
    await image.decode();
    // An SVG may have no size of its own: it is drawn as large as allowed.
    const width = image.naturalWidth || maxSide;
    const height = image.naturalHeight || maxSide;
    const scale = Math.min(1, maxSide / Math.max(width, height));

    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(width * scale));
    canvas.height = Math.max(1, Math.round(height * scale));
    const context = canvas.getContext('2d');
    if (!context) {
      throw new Error('Canvas is not available');
    }
    context.imageSmoothingQuality = 'high';
    context.drawImage(image, 0, 0, canvas.width, canvas.height);

    return await new Promise<Blob>((resolve, reject) => {
      canvas.toBlob(
        (blob) => (blob ? resolve(blob) : reject(new Error('The image could not be encoded'))),
        'image/png',
      );
    });
  } finally {
    URL.revokeObjectURL(url);
  }
}
