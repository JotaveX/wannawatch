package br.com.jotave.wannawatch.profile.photo;

import br.com.jotave.wannawatch.shared.web.BadRequestException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Normaliza a foto enviada: recorta o centro em quadrado, reduz para {@value #SIZE}px e grava
 * de novo como JPEG. O front já manda assim, mas o back refaz para nunca guardar o arquivo
 * original (metadados, EXIF com localização, formatos estranhos ou algo que nem é imagem).
 */
final class PhotoImages {

    static final int SIZE = 192;
    private static final float QUALITY = 0.85f;
    /** acima disso nem decodifica: protege a memória de imagens "bomba" (pequenas no arquivo, enormes em pixels) */
    private static final long MAX_PIXELS = 40_000_000L;

    private PhotoImages() {}

    static byte[] normalize(byte[] original) {
        BufferedImage image = read(original);
        int side = Math.min(image.getWidth(), image.getHeight());
        BufferedImage square = image.getSubimage(
                (image.getWidth() - side) / 2, (image.getHeight() - side) / 2, side, side);
        return writeJpeg(scale(square, SIZE));
    }

    private static BufferedImage read(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) throw unreadable();
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) throw new BadRequestException("Essa imagem é grande demais. Tente uma menor.");
                BufferedImage image = reader.read(0);
                if (image.getWidth() < 1 || image.getHeight() < 1) throw unreadable();
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BadRequestException bad) throw bad;
            throw unreadable();
        }
    }

    /** Reduz pela metade em passos até chegar perto do tamanho final: fica bem mais nítido que de uma vez. */
    private static BufferedImage scale(BufferedImage source, int target) {
        BufferedImage current = source;
        int size = source.getWidth();
        do {
            size = Math.max(target, size / 2);
            current = draw(current, size);
        } while (size > target);
        return current;
    }

    /** Desenha num RGB opaco com fundo branco (PNG transparente não fica preto no JPEG). */
    private static BufferedImage draw(BufferedImage source, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, size, size);
            g.drawImage(source, 0, 0, size, size, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] writeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static BadRequestException unreadable() {
        return new BadRequestException("Não deu pra abrir essa imagem. Envie um JPG ou PNG.");
    }
}
