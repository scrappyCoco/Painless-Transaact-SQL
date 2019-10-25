CREATE TABLE dbo.MyTable
(
    Id   INT          NOT NULL PRIMARY KEY,
    Name VARCHAR(200) NOT NULL
);
GO

CREATE FUNCTION dbo.MyFun(@Name VARCHAR(200))
    RETURNS VARCHAR(200)
AS
BEGIN
    RETURN TRIM(@Name);
END
GO

MERGE dbo.MyTable AS SOURCE
USING (
    SELECT Name = '', Id = 1
) AS TARGET
ON SOURCE.Id = TARGET.Id
WHEN MATCHED THEN
    UPDATE
    SET Name = SOURCE.Name
WHEN NOT MATCHED THEN
    INSERT (Id, Name)
    VALUES (SOURCE.Id, dbo.MyFun(SOURCE.Name));