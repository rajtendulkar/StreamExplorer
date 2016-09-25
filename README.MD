StreamExplorer is a framework for solving mapping and scheduling problem for dataflow graph.
It can be built using Eclipse or from the command line.

Check if your machine is 32-bit or 64-bit.
This determines which Z3 shared libraries will be used.

==================================================
To compile the source code from the command line :
==================================================

1. Let ROOT_DIR point to the top level directory where you have downloaded StreamExplorer code.
   (Note: you can simply replace ROOT_DIR with PWD bash variable)

2. Change the directory to ROOT_DIR 

3. Compile the tool from command line as follows :
javac -sourcepath src/ -d bin/ $(find src/ -name *.java) -classpath $ROOT_DIR/dep/Z3Lib/64-bit/com.microsoft.z3.jar

==================================================
Validate the StreamExplorer with basic tests :
==================================================

1. Let ROOT_DIR point to the top level directory where you have downloaded StreamExplorer code.

2. Change the directory to ROOT_DIR 

3. Run basic StreamExplorer tests as follows:
java -Djava.library.path=$ROOT_DIR/dep/Z3Lib/64-bit -classpath $ROOT_DIR/dep/Z3Lib/64-bit/com.microsoft.z3.jar:$ROOT_DIR/bin:$ROOT_DIR/dep/Z3Lib/64-bit tests.TestCompiler

==================================================
To compile the source code from the Eclipse JDK :
==================================================

1. Create an eclipse project and add entire source code to the project.

2. add Z3 JAR in project properties by clicking Add JARs

3. set the native library location for Z3 jar to dep/Z3Lib/<32-bit> or <64-bit> 

4. Also make sure LD LIBRARY PATH points to the same directory.
   (Note : set LD LIBRARY PATH in shell and execute eclipse in the same shell. Eclipse will pick up this variable.)

5. Press <Ctrl> B to compile the entire source code

==================================================
Validate the StreamExplorer with basic tests :
==================================================

1. Open src/tests/TestCompiler.java in Eclipse

2. Press <Ctrl> F-11 to execute tests in this file
