-- Papel de administrador: por enquanto só libera a revisão dos vídeos do Reels (/admin/videos).
ALTER TABLE app_user ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE app_user SET is_admin = TRUE WHERE username = 'jvpiloni';

-- O Reels fica só com vídeo vertical: apaga os horizontais da primeira rodada de teste e libera
-- para buscar de novo os títulos que ficaram sem nenhum vídeo.
DELETE FROM title_video
WHERE source = 'YOUTUBE_SEARCH' AND NOT (COALESCE(height, 0) > COALESCE(width, 0));
DELETE FROM title_video_search s
WHERE NOT EXISTS (SELECT 1 FROM title_video v WHERE v.title_id = s.title_id);
