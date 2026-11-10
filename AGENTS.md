# AGENTS Guidelines for This Repository

This is a JAVA 27 Library which parses demo files created after Counter-Strike 2 matches.  
The library should be modular.  
After parsing an entity is expected that contains all information/game events from the match on what happened when.  
The match entity should also contain a scoreboard information.  

## 1. Project structure

├── /src  
│   ├── /main/java  
│   │   ├── Contains the source code of the parser library  
│   │   │   
│   │   ├── /dev/lordfetznschaedl  
│   │   │   └── Contains the entry point class of the library  
│   │   │   
│   │   ├── /dev/lordfetznschaedl.entities  
│   │   │   └── Contains all entities needed for the library  
│   │   │   
│   │   ├── /dev/lordfetznschaedl.businesslogic  
│   │   │   └── Contains all the logic for the parser  
│   │   │   
│   │  
│   ├── /test/java  
│   │   ├── Contains all files related to unit testing the parser  
│   │   └── Create similiar packages as in /main/java  
│   │  
│   ├── /test/resources/demos  
│   │   └── Contains 3 demo files from matches played recently. Those files can be used for testing

## 2. Testing

The application needs to be tested with unit tests and integration tests. Use JUnit for this. 

## 3. Documentation

Write documentation on how to use the Counter-Strike 2 demo parser in the README.md