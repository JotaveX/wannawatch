import { Injectable, signal } from '@angular/core';

const KEY = 'ww.photos';
const MAX_BYTES = 5 * 1024 * 1024;
const SIZE = 192;

/**
 * Foto de perfil — MOCK. Ainda não há upload no back: a imagem é reduzida a 192×192 aqui no
 * navegador e guardada no localStorage, por usuário. Só quem enviou vê a foto (neste aparelho);
 * os amigos continuam vendo o Bu ou as iniciais.
 *
 * Quando existir o endpoint, `prepare()` continua igual e `save()` passa a enviar para o back.
 */
@Injectable({ providedIn: 'root' })
export class PhotoStore {
  private readonly photos = signal<Record<number, string>>(read());

  photo(userId: number): string | null {
    return this.photos()[userId] ?? null;
  }

  save(userId: number, dataUrl: string | null): void {
    this.photos.update((all) => {
      const next = { ...all };
      if (dataUrl) next[userId] = dataUrl;
      else delete next[userId];
      write(next);
      return next;
    });
  }

  /** Confere o arquivo e devolve a foto recortada no centro, quadrada, como data URL. */
  async prepare(file: File): Promise<string> {
    if (!file.type.startsWith('image/')) throw new PhotoError('Escolha um arquivo de imagem (JPG, PNG ou WebP).');
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
      .drawImage(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side, 0, 0, SIZE, SIZE);
    bitmap.close();
    return canvas.toDataURL('image/jpeg', 0.85);
  }
}

export class PhotoError extends Error {}

function read(): Record<number, string> {
  try {
    const raw = globalThis.localStorage?.getItem(KEY);
    return raw ? (JSON.parse(raw) as Record<number, string>) : {};
  } catch {
    return {};
  }
}

function write(all: Record<number, string>): void {
  try {
    globalThis.localStorage?.setItem(KEY, JSON.stringify(all));
  } catch {
    // sem espaço ou bloqueado: a foto vale só até recarregar
  }
}
