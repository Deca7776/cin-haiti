-- Aligne le schema cin_identite avec CinIdentiteEntity (colonnes manquantes depuis V1)
ALTER TABLE cin_identite
    ADD COLUMN nin_display  VARCHAR(13),
    ADD COLUMN numero_carte VARCHAR(20),
    ADD COLUMN nationalite  VARCHAR(10);
