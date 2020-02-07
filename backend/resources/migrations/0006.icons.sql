CREATE TABLE icon_collection (
  id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  profile_id uuid NOT NULL REFERENCES profile(id) ON DELETE CASCADE,

  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  modified_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  deleted_at timestamptz DEFAULT NULL,

  name text NOT NULL
);

CREATE INDEX icon_colection__profile_id__idx
    ON icon_collection (profile_id);

CREATE TRIGGER icon_collection__modified_at__tgr
BEFORE UPDATE ON icon_collection
   FOR EACH ROW EXECUTE PROCEDURE update_modified_at();



CREATE TABLE icon (
  id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  profile_id uuid NOT NULL REFERENCES profile(id) ON DELETE CASCADE,

  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  modified_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  deleted_at timestamptz DEFAULT NULL,

  collection_id uuid REFERENCES icon_collection(id)
                     ON DELETE CASCADE,

  name text NOT NULL,
  content text NOT NULL,
  metadata bytea NOT NULL
);

CREATE INDEX icon__profile_id__idx
    ON icon(profile_id);
CREATE INDEX icon__collection_id__idx
    ON icon(collection_id);

CREATE TRIGGER icon__modified_at__tgr
BEFORE UPDATE ON icon
   FOR EACH ROW EXECUTE PROCEDURE update_modified_at();
