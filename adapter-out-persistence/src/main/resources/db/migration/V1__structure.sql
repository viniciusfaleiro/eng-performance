-- S1 · organization cadastro (structure, identities, repositories)

CREATE TABLE vertical (
    id         VARCHAR(128) PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    manager_id VARCHAR(128)
);

CREATE TABLE team (
    id                        VARCHAR(128) PRIMARY KEY,
    name                      VARCHAR(200) NOT NULL,
    vertical_id               VARCHAR(128) NOT NULL,
    manager_id                VARCHAR(128),
    production_stage_override VARCHAR(200)
);
CREATE INDEX idx_team_vertical ON team (vertical_id);

CREATE TABLE person (
    id    VARCHAR(128) PRIMARY KEY,
    name  VARCHAR(200) NOT NULL,
    email VARCHAR(320)
);

CREATE TABLE team_membership (
    person_id  VARCHAR(128) NOT NULL REFERENCES person (id) ON DELETE CASCADE,
    team_id    VARCHAR(128) NOT NULL,
    start_date DATE NOT NULL,
    end_date   DATE
);
CREATE INDEX idx_membership_person ON team_membership (person_id);

CREATE TABLE repository (
    repo_key VARCHAR(200) PRIMARY KEY,
    project  VARCHAR(200) NOT NULL,
    team_id  VARCHAR(128)
);

CREATE TABLE committer_identity (
    identity     VARCHAR(320) PRIMARY KEY,
    display_name VARCHAR(200),
    person_id    VARCHAR(128),
    commit_count BIGINT NOT NULL DEFAULT 0
);
