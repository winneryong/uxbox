CREATE TABLE image_collection (
  id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  profile_id uuid NOT NULL REFERENCES profile(id) ON DELETE CASCADE,

  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  modified_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  deleted_at timestamptz DEFAULT NULL,

  name text NOT NULL
);

CREATE INDEX image_collection__profile_id__idx
    ON image_collection(profile_id);

CREATE TRIGGER image_collection__modified_at__tgr
BEFORE UPDATE ON image_collection
   FOR EACH ROW EXECUTE PROCEDURE update_modified_at();



CREATE TABLE image (
  id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  profile_id uuid NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
  collection_id uuid NOT NULL REFERENCES image_collection(id) ON DELETE CASCADE,

  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  modified_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  deleted_at timestamptz DEFAULT NULL,

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

CREATE INDEX image__profile_id__idx
    ON image(profile_id);

CREATE INDEX image__collection_id__idx
    ON image(collection_id);

CREATE TRIGGER image__modified_at__tgr
BEFORE UPDATE ON image
   FOR EACH ROW EXECUTE PROCEDURE update_modified_at();

