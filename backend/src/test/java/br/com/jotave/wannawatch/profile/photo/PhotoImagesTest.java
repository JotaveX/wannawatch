package br.com.jotave.wannawatch.profile.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.jotave.wannawatch.shared.web.BadRequestException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PhotoImagesTest {

    @Test
    void recortaOCentroEmQuadradoEReduzPara192() throws IOException {
        // 600×300: metade esquerda vermelha, o quarto central azul, o resto verde
        BufferedImage wide = new BufferedImage(600, 300, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 600; x++)
            for (int y = 0; y < 300; y++)
                wide.setRGB(x, y, (x >= 150 && x < 450 ? Color.BLUE : Color.RED).getRGB());

        BufferedImage out = decode(PhotoImages.normalize(encode(wide, "png")));

        assertThat(out.getWidth()).isEqualTo(192);
        assertThat(out.getHeight()).isEqualTo(192);
        // o recorte pegou só o miolo azul, nada das bordas vermelhas
        Color corner = new Color(out.getRGB(2, 2));
        assertThat(corner.getBlue()).isGreaterThan(200);
        assertThat(corner.getRed()).isLessThan(60);
    }

    @Test
    void transparenciaViraFundoBranco() throws IOException {
        BufferedImage clear = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);

        BufferedImage out = decode(PhotoImages.normalize(encode(clear, "png")));

        Color c = new Color(out.getRGB(96, 96));
        assertThat(c.getRed()).isGreaterThan(240);
        assertThat(c.getGreen()).isGreaterThan(240);
        assertThat(c.getBlue()).isGreaterThan(240);
    }

    @Test
    void imagemPequenaTambemViraJpegDe192() throws IOException {
        BufferedImage tiny = new BufferedImage(40, 60, BufferedImage.TYPE_INT_RGB);

        byte[] jpeg = PhotoImages.normalize(encode(tiny, "jpg"));

        assertThat(jpeg).startsWith(0xFF, 0xD8); // assinatura do JPEG
        assertThat(decode(jpeg).getWidth()).isEqualTo(192);
    }

    @Test
    void arquivoQueNaoEImagemDa400() {
        assertThatThrownBy(() -> PhotoImages.normalize("não sou foto".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Não deu pra abrir essa imagem");
    }

    @Test
    void imagemTruncadaDa400() throws IOException {
        byte[] png = encode(new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB), "png");
        byte[] cut = java.util.Arrays.copyOf(png, 40);

        assertThatThrownBy(() -> PhotoImages.normalize(cut)).isInstanceOf(BadRequestException.class);
    }

    static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }
}
