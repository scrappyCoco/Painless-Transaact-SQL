CREATE TABLE T1
(
    ID INT
);
CREATE TABLE T2
(
    ID INT
);
CREATE TABLE T3
(
    ID INT
);

CREATE TABLE #T
(
    ID INT
);
DECLARE @T TABLE
           (
               ID INT
           );

DELETE T1;

DELETE  T1
OUTPUT deleted.ID
INTO  T3  (ID)
FROM T1
         INNER JOIN T2 ON T1.ID = T2.ID;

TRUNCATE TABLE T1;

UPDATE T1
SET ID = 1
OUTPUT inserted.ID INTO T2 (ID);

DELETE @T;
DELETE #T;
TRUNCATE TABLE #T;

INSERT INTO T1  (ID)
OUTPUT inserted.ID
INTO T2 (ID)
SELECT ID
FROM T2;

MERGE T1 AS Target
USING T2 AS Source
ON Source.ID = Target.ID
WHEN NOT MATCHED BY SOURCE THEN DELETE
WHEN NOT MATCHED BY TARGET THEN
    INSERT (ID)
    VALUES (ID)
    OUTPUT inserted.ID INTO T3 (ID);


BULK  INSERT t1
    FROM 'f:\orders\lineitem.tbl'
    WITH
    (
    FIELDTERMINATOR =' |'
    , ROWTERMINATOR = ':\n'
    , FIRE_TRIGGERS
    );