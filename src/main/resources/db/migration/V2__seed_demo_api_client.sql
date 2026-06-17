-- Client API de démonstration (hash SHA-256 de "demo-api-key-2024")
INSERT INTO cin_api_client (id, client_name, api_key_hash, allowed_fields, can_access_photo, active, expires_at)
VALUES (
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'crm-demo',
    'bc639ba4b0be8526fe018fbeb010106ebf0e60c0fd6044a9f2d299f408412a3c',
    'nin,nom,prenom,date_naissance,sexe,departement',
    false,
    true,
    NOW() + INTERVAL '90 days'
);
