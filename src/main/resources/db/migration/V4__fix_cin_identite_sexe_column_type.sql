-- Hibernate valide 'sexe' comme VARCHAR(1) (String sans columnDefinition), mais V1 l'a cree en CHAR(1)
ALTER TABLE cin_identite
    ALTER COLUMN sexe TYPE VARCHAR(1);
