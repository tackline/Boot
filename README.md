# Boot
Launch Multi-File Source-Code Programs from a Single-File Source-Code Program

Together with a JDK, this is all you need to launch multi-file source-code programs. I don't know why JEP 330 doesn't do this - it'll be discussed on some mailing list somewhere.

Runs Main.main(String...), from Java source code, from src directory within directory with same name as the class used for this project.

Run the launcher with

    java Boot.java

You may want to rename the class. There are no further references to the name in the .java file beyond the class declaration.

No tests (yet), obviously.
