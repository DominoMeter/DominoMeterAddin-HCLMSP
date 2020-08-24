# DominoUsageCollectorAddin-HCLMSP
Collects usage of Domino servers running in the HCL MSP program

# Installation
- build JAR file from project
- put it into ProminicAddin folder
- register file in the notes.ini (JAVAUSERCLASSES=.\ProminicAddin\DominoUsageCollectorAddin-44.jar). Take into account separator could be ; (semicolon, Windows, OS/2) or : (colon UNIX).
- run command: Load runjava DominoUsageCollectorAddin endpoint
- endpoint is a prominic central engine that collect data and also provide GET/POST API. For local testing it could be f.x. 127.0.0.1/duca.nsf

# DUCA.NSF (Domino Usage COllector Addin).
- there are 3 views: Config, Servers and Log.
a) Config must contain only 1 document (rest will be ignored). Config document contains latest version of Addin, it's version and setting that allow create new server connection automatically.
b) Server view shows information about client's servers (what version of Addin they run on, how many users in names.nsf etc).
c) Log - shows all requests (GET/POST) from clients to prominic DUCA.nsf

# Program documents
- there are 2 program documents created autoamtically when Addin starts. They help to keep Addin running when server is restarted or when new version is loaded.
