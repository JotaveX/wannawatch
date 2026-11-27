const MAX_BYTES = 5 * 1024 * 1024;
const SIZE = 192;
/** onde a foto ficava quando era só um mock no navegador; hoje vai para o back */
const LEGACY_KEY = 'ww.photos';

/** A foto pronta para enviar: o arquivo (JPEG 192×192) e uma prévia para mostrar antes de salvar. */
export interface PreparedPhoto {
  blob: Blob;
  preview: string;
}

/**
 * Confere o arquivo e recorta o centro em quadrado, já reduzido. Enviar pequeno deixa o upload
 * rápido; o back refaz o recorte de qualquer jeito (ver profile/photo/PhotoImages.java).
 */
export async function preparePhoto(file: File): Promise<PreparedPhoto> {
  if (!file.type.startsWith('image/'))
    throw new PhotoError('Escolha um arquivo de imagem (JPG, PNG ou WebP).');
  if (file.size > MAX_BYTES) throw new PhotoError('A imagem pode ter até 5 MB.');
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file);
  } catch {
    throw new PhotoError('Não deu pra abrir essa imagem.');
  }
  const side = Math.min(bitmap.width, bitmap.height);
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = SIZE;
  canvas
    .getContext('2d')!
    .drawImage(
      bitmap,
      (bitmap.width - side) / 2,
      (bitmap.height - side) / 2,
      side,
      side,
      0,
      0,
      SIZE,
      SIZE,
    );
  bitmap.close();
  const preview = canvas.toDataURL('image/jpeg', 0.85);
  const blob = await new Promise<Blob | null>((done) => canvas.toBlob(done, 'image/jpeg', 0.85));
  if (!blob) throw new PhotoError('Não deu pra usar essa imagem.');
  return { blob, preview };
}

export class PhotoError extends Error {}

/** Apaga as fotos que o mock antigo guardava no localStorage (só ocupavam espaço). */
export function forgetLegacyPhotos(): void {
  try {
    globalThis.localStorage?.removeItem(LEGACY_KEY);
  } catch {
    // bloqueado: não tem o que limpar
  }
}
