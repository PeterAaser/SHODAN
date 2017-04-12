CREATE TABLE IF NOT EXISTS experimentInfo (
    id serial NOT NULL,
    experimentTimeStamp timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    comment varchar
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
    MEAName varchar NOT NULL, -- I AM NOT A NUMBER, I AM A FREE MAN!
    info varchar NOT NULL -- All sorts of exciting info about MEAs
);

CREATE TABLE IF NOT EXISTS channelRecording (
    experimentId integer NOT NULL,
    channelRecordingId serial NOT NULL UNIQUE,
    channelNumber integer NOT NULL
);

CREATE TABLE IF NOT EXISTS datapiece (
    channelRecordingId int NOT NULL,
    index serial NOT NULL,
    sample char[120000] NOT NULL -- A seconds worth of data at 40 khz. We do not care to save lengths below one second.
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


ALTER TABLE channelRecording
    ADD CONSTRAINT experimentInfoRecording_fkey FOREIGN KEY (experimentId) REFERENCES experimentInfo(id)
;

ALTER TABLE datapiece
    ADD CONSTRAINT channelRecordingPiece_fkey FOREIGN KEY (channelRecordingId) REFERENCES channelRecording(channelRecordingId)
;
