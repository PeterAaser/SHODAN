CREATE TABLE IF NOT EXISTS experimentInfo (
    id serial NOT NULL,
    experimentTimeStamp timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    comment varchar NOT NULL
);


-- How to ensure duration is stored? allow NULL and then
-- have a separate script to calculate?
CREATE TABLE IF NOT EXISTS experimentParams (
    experimentId integer NOT NULL,
    sampleRate integer NOT NULL,
    segmentLength integer NOT NULL,
    duration integer
);


CREATE TABLE IF NOT EXISTS dataRecording (
    experimentId integer NOT NULL,
    resourcePath varchar NOT NULL, -- a filepath
    resourceType varchar NOT NULL  -- CSV, zipped, tarred etc
);

-- wouldn't be a database without this one I guess
CREATE TABLE IF NOT EXISTS person (
    id integer NOT NULL,
    PersonName varchar NOT NULL,
    email varchar,
    phoneNo integer
);


CREATE TABLE IF NOT EXISTS experimentPerformedBy (
    experimentId integer NOT NULL,
    experimentStaffId integer NOT NULL
);


CREATE TABLE IF NOT EXISTS experimentSubject (
    experimentId integer NOT NULL,
    MEAId integer NOT NULL
);


CREATE TABLE IF NOT EXISTS MEA (
    id integer NOT NULL,
    MEAname varchar NOT NULL, -- I AM NOT A NUMBER, I AM A FREE MAN!
    info varchar NOT NULL -- Placeholder for all sorts of exciting information about Neural cultures
);


ALTER TABLE experimentInfo
    ADD CONSTRAINT experimentInfo_pkey PRIMARY KEY (id)
;

ALTER TABLE person
    ADD CONSTRAINT person_pkey PRIMARY KEY (id)
;

ALTER TABLE MEA
    ADD CONSTRAINT MEA_pkey PRIMARY KEY (id)
;

ALTER TABLE experimentPerformedBy
    ADD CONSTRAINT experimentInfoStaff_fk FOREIGN KEY (experimentId) REFERENCES experimentInfo(id),
    ADD CONSTRAINT experimentStaff_fk FOREIGN KEY (experimentStaffId) REFERENCES person(id)
;

ALTER TABLE experimentSubject
    ADD CONSTRAINT experimentInfoSubject_fk FOREIGN KEY (experimentId) REFERENCES experimentInfo(id),
    ADD CONSTRAINT MEA_fk FOREIGN KEY (MEAId) REFERENCES MEA(id)
;


ALTER TABLE dataRecording
    ADD CONSTRAINT experimentInfoRecording_fkey FOREIGN KEY (experimentId) REFERENCES experimentInfo(id)
;
