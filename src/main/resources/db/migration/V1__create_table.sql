CREATE TABLE tb_historico_consulta (
    consulta_id      INTEGER PRIMARY KEY,
    tutor_nome       VARCHAR(100) NOT NULL,
    pet_nome         VARCHAR(100) NOT NULL,
    veterinario_nome VARCHAR(100) NOT NULL,
    dia_hora         TIMESTAMP    NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    atualizado_em    TIMESTAMP    NOT NULL
);