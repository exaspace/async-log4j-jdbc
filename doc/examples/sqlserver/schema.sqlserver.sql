
CREATE TABLE applog(
      [ID] [int] IDENTITY(1,1) NOT NULL,
      [LogDate] [datetime] NOT NULL,
      [Logger] [varchar](100) NOT NULL,
      [Priority] [varchar](20) NOT NULL,
      [ThreadID] [varchar](50) NULL,
      [Context] [varchar](100) NOT NULL,
      [Message] [text] NULL,
      [Trace] [text] NULL,
 CONSTRAINT [PK_tblLog] PRIMARY KEY NONCLUSTERED 
(
      [ID] ASC
)WITH (PAD_INDEX  = OFF, STATISTICS_NORECOMPUTE  = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS  = ON, ALLOW_PAGE_LOCKS  = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]

GO

CREATE PROCEDURE sp_log
      @logger varchar(50),
      @priority varchar(50),
      @threadid varchar(10),
      @context varchar(50),
      @message varchar(1000),
      @trace varchar(2000)
AS
BEGIN

    INSERT INTO applog([LogDate],[Logger],[Priority],[ThreadID],[Context],[Message],[Trace])
    VALUES(GetDate(),@logger,@priority,@threadid,@context,@message,@trace)
      
END
