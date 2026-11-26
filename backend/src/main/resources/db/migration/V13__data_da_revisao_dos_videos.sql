-- Quando o vídeo foi aprovado ou reprovado (histórico da revisão em /admin/videos).
-- O updated_at não serve: muda também quando a busca no YouTube roda de novo.
ALTER TABLE title_video ADD COLUMN reviewed_at TIMESTAMPTZ;
UPDATE title_video SET reviewed_at = updated_at WHERE status <> 'PENDING';
