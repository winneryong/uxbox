CREATE TABLE project (
  id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  team_id uuid NOT NULL REFERENCES team(id) ON DELETE CASCADE,

  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  modified_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  deleted_at timestamptz DEFAULT NULL,

  name text NOT NULL
);

CREATE INDEX project__team_id__idx
    ON project(team_id);

CREATE TRIGGER project__modified_at__tgr
BEFORE UPDATE ON projects
   FOR EACH ROW EXECUTE PROCEDURE update_modified_at();



CREATE TABLE project_profile_rel (
  profile_id uuid NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
  project_id uuid NOT NULL REFERENCES project(id) ON DELETE CASCADE,

  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  modified_at timestamptz NOT NULL DEFAULT clock_timestamp(),

  is_owner boolean DEFAULT false,
  is_admin boolean DEFAULT false,
  can_edit boolean DEFAULT false

  PRIMARY KEY (profile_id, project_id)
);

COMMENT ON TABLE project_profile_rel
     IS 'Relation between projects and profiles (NM)';

CREATE INDEX project_profile_rel__profile_id__idx
    ON project_profile_rel(profile_id);

CREATE INDEX project_profile_rel__project_id__idx
    ON project_profile_rel(project_id);



CREATE TABLE file (
  id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  project_id uuid NULL REFERENCES project(id) ON DELETE CASCADE,

  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  modified_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  deleted_at timestamptz DEFAULT NULL,

  name text NOT NULL
);

CREATE INDEX file__profile_id__idx
    ON file(profile_id);

CREATE TRIGGER file__modified_at__tgr
BEFORE UPDATE ON file
   FOR EACH ROW EXECUTE PROCEDURE update_modified_at();



CREATE TABLE file_profile_rel (
  file_id uuid NOT NULL REFERENCES file(id) ON DELETE CASCADE,
  profile_id uuid NOT NULL REFERENCES profile(id) ON DELETE CASCADE,

  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  modified_at timestamptz NOT NULL DEFAULT clock_timestamp(),

  is_owner boolean DEFAULT false,
  is_admin boolean DEFAULT false,
  can_edit boolean DEFAULT false

  PRIMARY KEY (user_id, file_id)
);

COMMENT ON TABLE file_profile_rel
     IS 'Relation between files and profiles (NM)';

CREATE INDEX file_profile_rel__user_id__idx
    ON file_profile_rel(user_id);

CREATE INDEX file_profile_rel__file_id__idx
    ON file_profile_rel(file_id);



CREATE TABLE file_image (
  id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  file_id uuid NOT NULL REFERENCES file(id) ON DELETE CASCADE,

  name text NOT NULL,

  path text NOT NULL,
  width int NOT NULL,
  height int NOT NULL,
  mtype text NOT NULL,

  thumb_path text NOT NULL,
  thumb_width int NOT NULL,
  thumb_height int NOT NULL,
  thumb_quality int NOT NULL,
  thumb_mtype text NOT NULL
);

CREATE INDEX file_image__file_id__idx
    ON file_image(file_id);

CREATE INDEX file_image__profile_id__idx
    ON file_image(profile_id);


CREATE TABLE page (
  id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  file_id uuid NOT NULL REFERENCES file(id) ON DELETE CASCADE,
  profile_id uuid REFERENCES profile(id) ON SET NULL,

  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  modified_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  deleted_at timestamptz DEFAULT NULL,

  version bigint NOT NULL,
  ordering smallint NOT NULL,

  name text NOT NULL,
  data bytea NOT NULL
);

CREATE INDEX page__profile_id__idx
    ON page(profile_id);

CREATE INDEX page__file_id__idx
    ON page(file_id);

CREATE FUNCTION handle_page_update()
  RETURNS TRIGGER AS $pagechange$
  DECLARE
    current_dt timestamptz := clock_timestamp();
    proj_id uuid;
  BEGIN
    UPDATE file
       SET modified_at = current_dt
     WHERE id = OLD.file_id
    RETURNING project_id
      INTO STRICT proj_id;

    --- Update projects modified_at attribute when a
    --- page of that project is modified.
    UPDATE projects
       SET modified_at = current_dt
     WHERE id = proj_id;

    RETURN NEW;
  END;
$pagechange$ LANGUAGE plpgsql;

CREATE TRIGGER page__on_update__tgr
BEFORE UPDATE ON page
   FOR EACH ROW EXECUTE PROCEDURE handle_page_update();

CREATE TRIGGER page__modified_at__tgr
BEFORE UPDATE ON project_pages
   FOR EACH ROW EXECUTE PROCEDURE update_modified_at();



CREATE TABLE page_version (
  id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),

  page_id uuid NOT NULL REFERENCES project_pages(id) ON DELETE CASCADE,
  profile_id uuid NULL REFERENCES profile(id) ON DELETE SET NULL,

  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  modified_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  version bigint NOT NULL DEFAULT 0,

  label text NOT NULL DEFAULT '',
  data bytea NOT NULL,

  changes bytea NULL DEFAULT NULL
);

CREATE INDEX page_version__profile_id__idx
    ON page_version(profile_id);

CREATE INDEX page_version__page_id__idx
    ON page_version(page_id);

CREATE TRIGGER page_version__modified_at__tgr
BEFORE UPDATE ON page_version
   FOR EACH ROW EXECUTE PROCEDURE update_modified_at();



CREATE TABLE page_change (
  id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),

  page_id uuid NOT NULL REFERENCES project_pages(id) ON DELETE CASCADE,
  profile_id uuid NULL REFERENCES profile(id) ON DELETE SET NULL,

  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  modified_at timestamptz NOT NULL DEFAULT clock_timestamp(),

  rev bigint NOT NULL DEFAULT 0,

  label text NOT NULL DEFAULT '',
  data bytea NOT NULL,

  operations bytea NULL DEFAULT NULL
);

CREATE INDEX page_change__profile_id__idx
    ON page_change(profile_id);

CREATE INDEX page_change__page_id__idx
    ON page_change(page_id);

CREATE TRIGGER page_change__modified_at__tgr
BEFORE UPDATE ON page_change
   FOR EACH ROW EXECUTE PROCEDURE update_modified_at();
