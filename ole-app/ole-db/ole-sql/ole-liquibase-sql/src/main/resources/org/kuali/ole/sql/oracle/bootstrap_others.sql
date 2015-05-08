-- *********************************************************************
-- Update Database Script
-- *********************************************************************
-- Change Log: bootstrap_others.xml
-- *********************************************************************

-- Lock Database
-- Changeset bootstrap_others.xml::KRIM_ORACLE_TRIGGERS::ole
CREATE OR REPLACE TRIGGER KRIM_ENTITY_TR BEFORE  UPDATE ON KRIM_ENTITY_T FOR EACH ROW DECLARE BEGIN :new.LAST_UPDT_DT := sysdate; END;/

CREATE OR REPLACE TRIGGER KRIM_ENTITY_ENT_TYP_TR BEFORE  UPDATE ON KRIM_ENTITY_ENT_TYP_T FOR EACH ROW DECLARE BEGIN :new.LAST_UPDT_DT := sysdate; END;/

CREATE OR REPLACE TRIGGER KRIM_ENTITY_NM_TR BEFORE  UPDATE ON KRIM_ENTITY_NM_T FOR EACH ROW DECLARE BEGIN :new.LAST_UPDT_DT := sysdate; END;/

CREATE OR REPLACE TRIGGER KRIM_ENTITY_EMAIL_TR BEFORE  UPDATE ON KRIM_ENTITY_EMAIL_T FOR EACH ROW DECLARE BEGIN :new.LAST_UPDT_DT := sysdate; END;/

CREATE OR REPLACE TRIGGER KRIM_ENTITY_PHONE_TR BEFORE  UPDATE ON KRIM_ENTITY_PHONE_T FOR EACH ROW DECLARE BEGIN :new.LAST_UPDT_DT := sysdate; END;/

CREATE OR REPLACE TRIGGER KRIM_ENTITY_ADDR_TR BEFORE  UPDATE ON KRIM_ENTITY_ADDR_T FOR EACH ROW DECLARE BEGIN :new.LAST_UPDT_DT := sysdate; END;/

CREATE OR REPLACE TRIGGER KRIM_ENTITY_AFLTN_TR BEFORE  UPDATE ON KRIM_ENTITY_ADDR_T FOR EACH ROW DECLARE BEGIN :new.LAST_UPDT_DT := sysdate; END;/

CREATE OR REPLACE TRIGGER KRIM_ENTITY_EMP_INFO_TR BEFORE  UPDATE ON KRIM_ENTITY_EMP_INFO_TFOR EACH ROW DECLARE BEGIN :new.LAST_UPDT_DT := sysdate; END;/

INSERT INTO DATABASECHANGELOG (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, LIQUIBASE) VALUES ('KRIM_ORACLE_TRIGGERS', 'ole', 'bootstrap_others.xml', SYSTIMESTAMP, 1, '7:e6d725910c1d40239f497243c6a54326', 'sql (x8)', '', 'EXECUTED', '3.2.0')
/

-- Release Database Lock
-- Release Database Lock
