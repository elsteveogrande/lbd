CP=bin:lib/java-getopt-1.0.13.jar
JAVA=java -cp $(CP)
JAVAC=javac -cp $(CP)

all: java

javatest: java
	$(JAVA) cc.obrien.lbd.LBD -s 1G -E /tmp/x

java:
	rm -rf bin
	mkdir bin
	$(JAVAC) -d bin -sourcepath src `find src -name \*\.java`
